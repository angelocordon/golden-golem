package dev.angelocordon.goldengolem.menu;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Opens the equipment menu on right-click, handles Follow/Stay (menu item and sneak +
 * right-click), and enforces the menu's slot rules.
 *
 * <p>Open menus sync to the golem at the start of every tick that follows a click (before the
 * controller ticks golems) and at the end of every tick (to show the golem's own changes). Menus
 * close, after a final sync, before the golem dies, unloads or the plugin disables, so items are
 * never in the menu and in the golem's death drops or saved data at once.
 */
public final class GolemMenuListener implements Listener {

    /** Lets a player open and control golems they don't own. */
    public static final String ADMIN_PERMISSION = "goldengolem.admin";

    private final Services services;
    private final FollowStay followStay;
    private final Map<UUID, GolemMenu> menus = new HashMap<>();

    public GolemMenuListener(Services services) {
        this.services = services;
        this.followStay = new FollowStay(services);
    }

    /**
     * HIGH and ignoreCancelled so features that consume a right-click on the golem at a lower
     * priority (e.g. healing with a gold ingot) win by cancelling the event.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Optional<GoldenGolem> found = GoldenGolem.of(event.getRightClicked());
        if (found.isEmpty()) {
            return;
        }
        // Block vanilla copper golem interactions on both hands; act once, on the main-hand
        // INTERACT packet (the client also sends INTERACT_AT, which shares this handler list).
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        GoldenGolem golem = found.get();
        Player player = event.getPlayer();
        if (!canControl(player, golem)) {
            player.sendActionBar(Component.text("This Golden Golem belongs to someone else", NamedTextColor.RED));
            return;
        }
        if (player.isSneaking()) {
            toggle(golem, player);
        } else {
            open(player, golem);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        GolemMenu menu = menuOf(event.getView().getTopInventory());
        if (menu == null) {
            return;
        }
        menu.markDirty();
        SlotRules.ClickHandling handling = SlotRules.classify(event.getRawSlot(), event.getClick(), event.getAction());
        if (handling == SlotRules.ClickHandling.ALLOW) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (handling) {
            case TOOL_SLOT, TARGET_SLOT -> menu.clickControlSlot(event.getRawSlot(), event.getView(), player,
                    event.isShiftClick());
            case TOGGLE -> toggle(menu.golem(), player);
            case MOVE_INTO_MENU -> moveIntoMenu(menu, event);
            case ALLOW, DENY -> {
            }
        }
        // The click was cancelled but the server state changed; resend it so the client agrees.
        services.plugin().getServer().getScheduler().runTask(services.plugin(), player::updateInventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        GolemMenu menu = menuOf(event.getView().getTopInventory());
        if (menu == null) {
            return;
        }
        if (SlotRules.dragAllowed(event.getRawSlots())) {
            menu.markDirty();
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        GolemMenu menu = menuOf(event.getInventory());
        if (menu == null) {
            return;
        }
        menu.sync();
        boolean othersViewing = menu.getInventory().getViewers().stream()
                .anyMatch(viewer -> !viewer.equals(event.getPlayer()));
        if (!othersViewing) {
            menus.remove(menu.golem().id(), menu);
        }
    }

    /** Runs before the scheduler, so menu edits land before the controller ticks the golem. */
    @EventHandler
    public void onTickStart(ServerTickStartEvent event) {
        for (GolemMenu menu : List.copyOf(menus.values())) {
            if (menu.isDirty() && menu.golem().isValid()) {
                menu.sync();
            }
        }
    }

    /** Shows what the golem changed this tick and drops menus nobody is viewing. */
    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        for (GolemMenu menu : List.copyOf(menus.values())) {
            if (!menu.golem().isValid() || menu.getInventory().getViewers().isEmpty()) {
                close(menu);
                continue;
            }
            menu.sync();
            menu.refreshControls();
        }
    }

    /** LOWEST so the menu is closed and synced before anything reads the golem's death drops. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent event) {
        closeFor(event.getEntity());
    }

    /**
     * Closes menus as the golem's chunk unloads. Edits are already written at each tick start,
     * so at most a same-tick edit is flushed here.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            closeFor(entity);
        }
    }

    /** Backstop for any other removal. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        closeFor(event.getEntity());
    }

    /** Syncs and closes every open menu. Call from the plugin's onDisable. */
    public void closeAll() {
        for (GolemMenu menu : List.copyOf(menus.values())) {
            close(menu);
        }
    }

    private void open(Player player, GoldenGolem golem) {
        GolemMenu menu = menus.get(golem.id());
        if (menu == null) {
            menu = new GolemMenu(services.plugin().getServer(), services.plugin().getLogger(), golem);
            menus.put(golem.id(), menu);
        } else {
            menu.sync();
        }
        player.openInventory(menu.getInventory());
    }

    private void toggle(GoldenGolem golem, Player player) {
        GolemMenu menu = menus.get(golem.id());
        if (menu != null) {
            menu.sync();
        }
        followStay.toggle(golem, player);
        if (menu != null) {
            menu.refreshControls();
        }
    }

    private void moveIntoMenu(GolemMenu menu, InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        ItemStack item = GolemMenu.normalize(event.getCurrentItem());
        if (clicked == null || item == null) {
            return;
        }
        clicked.setItem(event.getSlot(), menu.moveIntoMenu(item));
    }

    private void closeFor(Entity entity) {
        GolemMenu menu = menus.get(entity.getUniqueId());
        if (menu != null) {
            close(menu);
        }
    }

    private void close(GolemMenu menu) {
        menu.sync();
        menus.remove(menu.golem().id(), menu);
        for (HumanEntity viewer : List.copyOf(menu.getInventory().getViewers())) {
            viewer.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    private static boolean canControl(Player player, GoldenGolem golem) {
        return golem.isOwnedBy(player.getUniqueId()) || player.hasPermission(ADMIN_PERMISSION);
    }

    private static @Nullable GolemMenu menuOf(Inventory inventory) {
        return inventory.getHolder(false) instanceof GolemMenu menu ? menu : null;
    }
}
