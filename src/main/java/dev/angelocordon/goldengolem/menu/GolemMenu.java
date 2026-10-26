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
import java.util.logging.Logger;
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
 * <p>The golem's saved data stays the source of truth. The menu is a view that {@link #sync}
 * merges back slot by slot against the last state both agreed on ({@link SlotMerge}), so player
 * edits and golem edits made in between (mining, depositing) both survive. Main thread only.
 */
public final class GolemMenu implements InventoryHolder {

    private static final Component TITLE = Component.text("Golden Golem");

    private final GoldenGolem golem;
    private final Logger logger;
    private final Inventory inventory;
    private Contents base;
    private boolean dirty;
    private @Nullable GolemState shownState;
    private @Nullable BlockedReason shownReason;

    GolemMenu(Server server, Logger logger, GoldenGolem golem) {
        this.golem = golem;
        this.logger = logger;
        this.inventory = server.createInventory(this, MenuLayout.SIZE, TITLE);
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            if (MenuLayout.roleOf(slot) == MenuLayout.Role.FILLER) {
                inventory.setItem(slot, icon(Material.GRAY_STAINED_GLASS_PANE, Component.space(), List.of()));
            }
        }
        inventory.setItem(MenuLayout.TOOL_HINT, icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Component.text("<- Tool"), List.of(Component.text("A pickaxe, axe or shovel"))));
        inventory.setItem(MenuLayout.TARGET_HINT, icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Component.text("<- Target block"), List.of(Component.text("One block its tool can gather"))));
        this.base = Contents.read(golem);
        show(base);
        refreshControls();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public GoldenGolem golem() {
        return golem;
    }

    /** Something in the view may have changed; {@link #sync} it at the next tick start. */
    void markDirty() {
        dirty = true;
    }

    boolean isDirty() {
        return dirty;
    }

    /**
     * Writes the player's edits to the golem and pulls in the golem's own edits, then makes the
     * view match. Cheap enough to run every tick while the menu is open.
     */
    void sync() {
        Contents view = readView();
        Contents current = Contents.read(golem);

        ItemStack tool = SlotMerge.pick(base.tool(), view.tool(), current.tool(), Objects::equals);
        Material target = SlotMerge.pick(base.target(), view.target(), current.target(), Objects::equals);
        SlotMerge.Result<ItemStack> storage = SlotMerge.merge(base.storage(), view.storage(), current.storage(),
                Objects::equals);
        int conflicts = storage.conflicts()
                + (SlotMerge.conflicts(base.tool(), view.tool(), current.tool(), Objects::equals) ? 1 : 0)
                + (SlotMerge.conflicts(base.target(), view.target(), current.target(), Objects::equals) ? 1 : 0);
        if (conflicts > 0) {
            // Both sides changed the same slot between two syncs; the player's edit wins.
            logger.warning("Golden golem " + golem.id() + ": menu and golem both changed " + conflicts
                    + " slot(s) in the same tick; kept the menu's version");
        }
        Contents merged = new Contents(tool, target, storage.slots());

        if (!Objects.equals(merged.tool(), current.tool())) {
            golem.setTool(merged.tool());
        }
        if (merged.target() != current.target()) {
            golem.setTarget(merged.target());
        }
        if (!merged.storage().equals(current.storage())) {
            golem.setStorage(merged.storage().toArray(new ItemStack[0]));
        }
        base = merged;
        show(merged);
        dirty = false;
    }

    /** Updates the toggle and status items if the golem's state or blocked reason changed. */
    void refreshControls() {
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
        if (SlotRules.acceptsTarget(toolType, candidate, candidate.isBlock())) {
            return null;
        }
        return toolType == null ? "Put a tool in first" : "Its tool can't gather that";
    }

    /** Reads the view. Copies every item, since the inventory hands out live mirrors. */
    private Contents readView() {
        ItemStack tool = takeOne(MenuLayout.TOOL);
        ItemStack targetItem = takeOne(MenuLayout.TARGET);
        List<@Nullable ItemStack> storage = new ArrayList<>(GoldenGolem.STORAGE_SIZE);
        for (int i = 0; i < GoldenGolem.STORAGE_SIZE; i++) {
            storage.add(normalize(inventory.getItem(MenuLayout.storageSlot(i))));
        }
        return new Contents(tool, targetItem == null ? null : targetItem.getType(), storage);
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

    private void show(Contents contents) {
        setIfDifferent(MenuLayout.TOOL, contents.tool());
        Material target = contents.target();
        setIfDifferent(MenuLayout.TARGET, target == null || !target.isItem() ? null : new ItemStack(target));
        for (int i = 0; i < GoldenGolem.STORAGE_SIZE; i++) {
            setIfDifferent(MenuLayout.storageSlot(i), contents.storage().get(i));
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
                        : "Click to Follow its owner", NamedTextColor.GRAY)));
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

    /** One snapshot of everything the menu can edit. */
    private record Contents(@Nullable ItemStack tool, @Nullable Material target, List<@Nullable ItemStack> storage) {

        static Contents read(GoldenGolem golem) {
            List<@Nullable ItemStack> storage = new ArrayList<>(Arrays.asList(golem.storage()));
            storage.replaceAll(GolemMenu::normalize);
            return new Contents(normalize(golem.tool()), golem.target(), storage);
        }
    }
}
