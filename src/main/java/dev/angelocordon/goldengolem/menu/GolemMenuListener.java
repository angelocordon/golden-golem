package dev.angelocordon.goldengolem.menu;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
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
 * <p>Follows {@link GolemMenu}'s write/refresh contract: pending edits are written at tick start
 * (before the controller), the view is refreshed at tick end and after every write made here
 * between ticks (toggle, close). Menus close, after writing pending edits, before the golem
 * dies, unloads, changes world or the plugin disables, so items are never in the menu and in
 * the golem's death drops or saved data at once.
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

    /** HIGHEST so no later plugin can un-cancel a denied click on a control slot. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
        // No resend needed: Paper broadcasts the changed slots and cursor after a denied click.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
        writePending(menu);
        boolean othersViewing = menu.getInventory().getViewers().stream()
                .anyMatch(viewer -> !viewer.equals(event.getPlayer()));
        if (othersViewing) {
            menu.refresh();
        } else {
            menus.remove(menu.golem().id(), menu);
        }
    }

    /**
     * Runs before the scheduler, so menu edits land before the controller ticks the golem. A
     * waiting golem whose tool or target changed re-runs the Stay decision.
     */
    @EventHandler
    public void onTickStart(ServerTickStartEvent event) {
        for (GolemMenu menu : List.copyOf(menus.values())) {
            GoldenGolem golem = menu.golem();
            if (!menu.isDirty() || !golem.isValid()) {
                continue;
            }
            boolean equipmentChanged = menu.write();
            if (StayDecision.shouldRecheck(golem.state(), golem.workCenter() != null, equipmentChanged)) {
                followStay.recheck(golem, firstPlayer(menu));
                menu.refresh();
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
            writePending(menu);
            menu.refresh();
        }
    }

    /** LOWEST so pending edits are written and the menu closed before anything reads the golem's death drops. */
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

    /**
     * A golem changing world is copied from its saved data before the old entity goes away, so
     * close (writing pending edits) while the old entity is still the live one.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(EntityTeleportEvent event) {
        Location to = event.getTo();
        if (to != null && !Objects.equals(to.getWorld(), event.getFrom().getWorld())) {
            closeFor(event.getEntity());
        }
    }

    /** Backstop for any other removal. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        closeFor(event.getEntity());
    }

    /** Writes pending edits and closes every open menu. Call from the plugin's onDisable. */
    public void closeAll() {
        for (GolemMenu menu : List.copyOf(menus.values())) {
            close(menu);
        }
    }

    private void open(Player player, GoldenGolem golem) {
        GolemMenu menu = menus.get(golem.id());
        if (menu == null) {
            menu = new GolemMenu(services.plugin().getServer(), golem);
            menus.put(golem.id(), menu);
        } else {
            writePending(menu);
            menu.refresh();
        }
        player.openInventory(menu.getInventory());
    }

    private void toggle(GoldenGolem golem, Player player) {
        GolemMenu menu = menus.get(golem.id());
        if (menu != null) {
            writePending(menu);
        }
        followStay.toggle(golem, player);
        if (menu != null) {
            // Transition hooks run between ticks and may move items; show them now.
            menu.refresh();
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
        writePending(menu);
        menus.remove(menu.golem().id(), menu);
        for (HumanEntity viewer : List.copyOf(menu.getInventory().getViewers())) {
            viewer.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    private static void writePending(GolemMenu menu) {
        if (menu.isDirty()) {
            menu.write();
        }
    }

    private static @Nullable Player firstPlayer(GolemMenu menu) {
        for (HumanEntity viewer : menu.getInventory().getViewers()) {
            if (viewer instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static boolean canControl(Player player, GoldenGolem golem) {
        return golem.isOwnedBy(player.getUniqueId()) || player.hasPermission(ADMIN_PERMISSION);
    }

    private static @Nullable GolemMenu menuOf(Inventory inventory) {
        return inventory.getHolder(false) instanceof GolemMenu menu ? menu : null;
    }
}
