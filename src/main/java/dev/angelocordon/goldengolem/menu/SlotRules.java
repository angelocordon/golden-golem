package dev.angelocordon.goldengolem.menu;

import dev.angelocordon.goldengolem.menu.MenuLayout.Role;
import dev.angelocordon.goldengolem.work.HarvestRule;
import dev.angelocordon.goldengolem.work.HarvestRule.ToolKind;
import java.util.Collection;
import java.util.function.BiPredicate;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.jspecify.annotations.Nullable;

/**
 * The menu's slot rules as pure functions of plain values, so they're unit-tested without a
 * server. {@link GolemMenuListener} applies them to real events.
 */
public final class SlotRules {

    /** What the listener should do with a click in an open golem menu. */
    public enum ClickHandling {
        /** Let vanilla handle it; it can only touch storage or the player's own inventory. */
        ALLOW,
        /** Cancel it. */
        DENY,
        /** Cancel it and run the one-item tool slot logic instead. */
        TOOL_SLOT,
        /** Cancel it and run the one-item target slot logic instead. */
        TARGET_SLOT,
        /** Cancel it and flip Follow/Stay. */
        TOGGLE,
        /** A shift-click from the player's inventory: cancel it and move the item into storage. */
        MOVE_INTO_MENU
    }

    private SlotRules() {
    }

    /**
     * Classifies a click by the raw slot it hit. Vanilla only ever gets to move items in storage
     * slots and the player's own inventory: anything that could reach the control slots
     * (shift-click into the menu, double-click collect, number keys, offhand swap, creative
     * set-slot) is denied or reimplemented.
     */
    public static ClickHandling classify(int rawSlot, ClickType click, InventoryAction action) {
        // Collecting pulls matching items from every slot in the view, panes included.
        if (action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.UNKNOWN) {
            return ClickHandling.DENY;
        }
        boolean plainClick = click == ClickType.LEFT || click == ClickType.RIGHT;
        boolean shiftClick = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        return switch (MenuLayout.roleOf(rawSlot)) {
            case OUTSIDE -> action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    ? ClickHandling.MOVE_INTO_MENU
                    : ClickHandling.ALLOW;
            case STORAGE -> ClickHandling.ALLOW;
            case TOOL -> plainClick || shiftClick ? ClickHandling.TOOL_SLOT : ClickHandling.DENY;
            case TARGET -> plainClick || shiftClick ? ClickHandling.TARGET_SLOT : ClickHandling.DENY;
            case TOGGLE -> plainClick ? ClickHandling.TOGGLE : ClickHandling.DENY;
            case STATUS, FILLER -> ClickHandling.DENY;
        };
    }

    /** A drag may only spread items over storage slots and the player's own inventory. */
    public static boolean dragAllowed(Collection<Integer> rawSlots) {
        for (int slot : rawSlots) {
            Role role = MenuLayout.roleOf(slot);
            if (role != Role.STORAGE && role != Role.OUTSIDE) {
                return false;
            }
        }
        return true;
    }

    /** The tool slot takes pickaxes, axes and shovels only. */
    public static boolean acceptsTool(@Nullable Material item) {
        return ToolKind.of(item).isPresent();
    }

    /**
     * The target slot takes a block the current tool is for. With no tool there's nothing to
     * check against, so it takes nothing. {@code candidateIsBlock} is passed in because
     * {@link Material#isBlock()} needs a server.
     */
    public static boolean acceptsTarget(@Nullable Material tool, Material candidate, boolean candidateIsBlock) {
        return acceptsTarget(tool, candidate, candidateIsBlock, HarvestRule::toolFits);
    }

    /** {@link #acceptsTarget(Material, Material, boolean)} with the fit rule injected, for tests. */
    static boolean acceptsTarget(@Nullable Material tool, Material candidate, boolean candidateIsBlock,
            BiPredicate<Material, Material> toolFits) {
        return tool != null && acceptsTool(tool) && candidateIsBlock && toolFits.test(tool, candidate);
    }

    /**
     * Plans a shift-click into storage: how many items to add to each storage slot. Fills stacks
     * of the same item first, then empty slots, like vanilla.
     *
     * @param amount how many items are being moved
     * @param maxStack the most one slot may hold of this item
     * @param slots per storage slot: -1 if it holds a different item, 0 if empty, otherwise how
     *     many of the same item it already holds
     * @return how many to add to each slot; the sum is at most {@code amount}
     */
    public static int[] planFill(int amount, int maxStack, int[] slots) {
        int[] add = new int[slots.length];
        int left = amount;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int i = 0; i < slots.length && left > 0; i++) {
                boolean matches = pass == 0 ? slots[i] > 0 : slots[i] == 0;
                if (matches) {
                    int moved = Math.min(left, Math.max(0, maxStack - slots[i]));
                    add[i] = moved;
                    left -= moved;
                }
            }
        }
        return add;
    }
}
