package dev.angelocordon.goldengolem.menu;

import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The equipment menu for one golem. One instance per golem is shared by every viewer, so two
 * players can't each hold a copy of the same items.
 *
 * <p>The golem's saved data is the source of truth and the menu is a view of it. They never
 * both change at once because of when each side writes:
 * <ul>
 *   <li>Players click only between ticks. A click marks the menu dirty, and at the next tick
 *       start (before the controller runs) {@link #write} copies the menu's tool, target and
 *       storage to the golem.</li>
 *   <li>The controller writes golem items only during the tick. At tick end {@link #refresh}
 *       shows the golem's data again, so the next click acts on fresh contents.</li>
 * </ul>
 * <b>Any code that writes golem items outside the controller tick must call {@link #refresh}
 * on the open menu right after</b> (and {@link #write} pending edits first), or the view goes
 * stale and the next click can duplicate or lose items. Main thread only.
 */
public final class GolemMenu implements InventoryHolder {

    private static final Component TITLE = Component.text("Golden Golem");

    private final GoldenGolem golem;
    private final Inventory inventory;
    private boolean dirty;
    private @Nullable GolemState shownState;
    private @Nullable BlockedReason shownReason;

    GolemMenu(Server server, GoldenGolem golem) {
        this.golem = golem;
        this.inventory = server.createInventory(this, MenuLayout.SIZE, TITLE);
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            if (MenuLayout.roleOf(slot) == MenuLayout.Role.FILLER) {
                inventory.setItem(slot, icon(Material.GRAY_STAINED_GLASS_PANE, Component.space(), List.of()));
            }
        }
        inventory.setItem(MenuLayout.TOOL_HINT, icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Component.text("<- Tool"), List.of(Component.text("A pickaxe, axe or shovel"))));
        inventory.setItem(MenuLayout.TARGET_HINT, icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Component.text("<- Target block"), List.of(Component.text("One plain block its tool can gather"))));
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public GoldenGolem golem() {
        return golem;
    }

    /** A player may have changed the view; {@link #write} it at the next tick start. */
    void markDirty() {
        dirty = true;
    }

    boolean isDirty() {
        return dirty;
    }

    /**
     * Copies the menu's tool, target and storage to the golem, writing only what differs.
     *
     * @return true if the tool or target changed, so callers can re-check a waiting golem
     */
    boolean write() {
        ItemStack tool = takeOne(MenuLayout.TOOL);
        ItemStack targetItem = takeOne(MenuLayout.TARGET);
        Material target = targetItem == null ? null : targetItem.getType();
        @Nullable ItemStack[] storage = new ItemStack[GoldenGolem.STORAGE_SIZE];
        for (int i = 0; i < storage.length; i++) {
            storage[i] = normalize(inventory.getItem(MenuLayout.storageSlot(i)));
        }

        boolean equipmentChanged = false;
        if (!Objects.equals(tool, normalize(golem.tool()))) {
            golem.setTool(tool);
            equipmentChanged = true;
        }
        if (target != golem.target()) {
            golem.setTarget(target);
            equipmentChanged = true;
        }
        @Nullable ItemStack[] saved = golem.storage();
        for (int i = 0; i < saved.length; i++) {
            saved[i] = normalize(saved[i]);
        }
        if (!Arrays.equals(storage, saved)) {
            golem.setStorage(storage);
        }
        dirty = false;
        return equipmentChanged;
    }

    /** Shows the golem's current items, state and blocked reason. Discards unwritten edits. */
    void refresh() {
        setIfDifferent(MenuLayout.TOOL, normalize(golem.tool()));
        Material target = golem.target();
        setIfDifferent(MenuLayout.TARGET, target == null || !target.isItem() ? null : new ItemStack(target));
        @Nullable ItemStack[] storage = golem.storage();
        for (int i = 0; i < storage.length; i++) {
            setIfDifferent(MenuLayout.storageSlot(i), normalize(storage[i]));
        }
        dirty = false;
        refreshControls();
    }

    /** Updates the toggle and status items if the golem's state or blocked reason changed. */
    private void refreshControls() {
        GolemState state = golem.state();
        BlockedReason reason = golem.blockedReason();
        if (state == shownState && reason == shownReason) {
            return;
        }
        shownState = state;
        shownReason = reason;
        inventory.setItem(MenuLayout.TOGGLE, toggleItem(state));
        inventory.setItem(MenuLayout.STATUS, statusItem(state, reason));
    }

    /**
     * A plain or shift click on the tool or target slot. Each holds one item: a valid cursor
     * item goes in one at a time (the rest stays on the cursor, with the player), the old item
     * comes back to the cursor, and shift-click moves it into the player's inventory.
     */
    void clickControlSlot(int slot, InventoryView view, Player player, boolean shift) {
        ItemStack current = normalize(inventory.getItem(slot));
        if (shift) {
            if (current != null) {
                Map<Integer, ItemStack> left = player.getInventory().addItem(current);
                inventory.setItem(slot, left.isEmpty() ? null : left.values().iterator().next());
            }
            return;
        }
        ItemStack cursor = normalize(view.getCursor());
        if (cursor == null) {
            if (current != null) {
                inventory.setItem(slot, null);
                view.setCursor(current);
            }
            return;
        }
        String refusal = slot == MenuLayout.TOOL ? toolRefusal(cursor) : targetRefusal(cursor);
        if (refusal != null) {
            player.sendActionBar(Component.text(refusal, NamedTextColor.RED));
            return;
        }
        if (current == null) {
            inventory.setItem(slot, cursor.asOne());
            view.setCursor(cursor.getAmount() > 1 ? cursor.asQuantity(cursor.getAmount() - 1) : null);
        } else if (cursor.getAmount() == 1) {
            inventory.setItem(slot, cursor);
            view.setCursor(current);
        } else {
            player.sendActionBar(Component.text("Take the old item out first", NamedTextColor.RED));
        }
    }

    /**
     * A shift-click from the player's inventory. A tool goes to an empty tool slot, anything
     * else into storage only; never into the target slot or the panes.
     *
     * @return what's left of {@code item} that didn't fit, or null if it all moved
     */
    @Nullable ItemStack moveIntoMenu(ItemStack item) {
        if (item.getAmount() == 1 && SlotRules.acceptsTool(item.getType())
                && normalize(inventory.getItem(MenuLayout.TOOL)) == null) {
            inventory.setItem(MenuLayout.TOOL, item.clone());
            return null;
        }
        int[] slots = new int[GoldenGolem.STORAGE_SIZE];
        for (int i = 0; i < slots.length; i++) {
            ItemStack held = normalize(inventory.getItem(MenuLayout.storageSlot(i)));
            slots[i] = held == null ? 0 : held.isSimilar(item) ? held.getAmount() : -1;
        }
        int maxStack = Math.min(item.getMaxStackSize(), inventory.getMaxStackSize());
        int[] add = SlotRules.planFill(item.getAmount(), maxStack, slots);
        int moved = 0;
        for (int i = 0; i < add.length; i++) {
            if (add[i] > 0) {
                inventory.setItem(MenuLayout.storageSlot(i), item.asQuantity(Math.max(0, slots[i]) + add[i]));
                moved += add[i];
            }
        }
        int left = item.getAmount() - moved;
        return left > 0 ? item.asQuantity(left) : null;
    }

    private @Nullable String toolRefusal(ItemStack cursor) {
        return SlotRules.acceptsTool(cursor.getType()) ? null : "Only a pickaxe, axe or shovel goes here";
    }

    private @Nullable String targetRefusal(ItemStack cursor) {
        ItemStack tool = normalize(inventory.getItem(MenuLayout.TOOL));
        Material toolType = tool == null ? null : tool.getType();
        Material candidate = cursor.getType();
        boolean plain = cursor.asOne().isSimilar(new ItemStack(candidate));
        return switch (SlotRules.checkTarget(toolType, candidate, candidate.isBlock(), plain)) {
            case OK -> null;
            case NO_TOOL -> "Put a tool in first";
            case NOT_PLAIN -> "Use a plain block as the target";
            case DOES_NOT_FIT -> "Its tool can't gather that";
        };
    }

    /**
     * The single item in a control slot. The click rules keep these at one item; if something
     * still stacked more, hand the extras back rather than lose them on save.
     */
    private @Nullable ItemStack takeOne(int slot) {
        ItemStack item = normalize(inventory.getItem(slot));
        if (item == null || item.getAmount() == 1) {
            return item;
        }
        ItemStack extras = item.asQuantity(item.getAmount() - 1);
        inventory.setItem(slot, item.asOne());
        giveBack(extras);
        return item.asOne();
    }

    private void giveBack(ItemStack item) {
        List<HumanEntity> viewers = inventory.getViewers();
        if (viewers.isEmpty()) {
            golem.entity().getWorld().dropItem(golem.entity().getLocation(), item);
            return;
        }
        HumanEntity viewer = viewers.getFirst();
        for (ItemStack left : viewer.getInventory().addItem(item).values()) {
            viewer.getWorld().dropItem(viewer.getLocation(), left);
        }
    }

    private void setIfDifferent(int slot, @Nullable ItemStack item) {
        if (!Objects.equals(normalize(inventory.getItem(slot)), item)) {
            inventory.setItem(slot, item);
        }
    }

    private static ItemStack toggleItem(GolemState state) {
        boolean following = state == GolemState.FOLLOWING;
        return icon(following ? Material.LEAD : Material.COMPASS,
                Component.text(following ? "Following" : "Staying", NamedTextColor.GOLD),
                List.of(Component.text(following
                        ? "Click to Stay and work here"
                        : "Click to Follow you", NamedTextColor.GRAY)));
    }

    private static ItemStack statusItem(GolemState state, @Nullable BlockedReason reason) {
        Material color = switch (state) {
            case BLOCKED -> Material.RED_DYE;
            case WORKING, RETURNING, DEPOSITING -> Material.LIME_DYE;
            case IDLE, FOLLOWING -> Material.YELLOW_DYE;
        };
        List<Component> lore = new ArrayList<>();
        if (state == GolemState.BLOCKED && reason != null) {
            lore.add(Component.text("Golden Golem " + reason.message(), NamedTextColor.RED));
        }
        return icon(color, Component.text("Status: " + pretty(state), NamedTextColor.GOLD), lore);
    }

    private static String pretty(GolemState state) {
        String name = state.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }

    /** A detached copy, or null for an empty slot. */
    static @Nullable ItemStack normalize(@Nullable ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }
}
