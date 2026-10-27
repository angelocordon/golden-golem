package dev.angelocordon.goldengolem.work;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Puts drops into the golem's storage slots the way a vanilla inventory does: top up matching
 * stacks first, then fill empty slots, and hand back whatever didn't fit. The total item count
 * in (slots + items) always equals the total out (slots + leftovers).
 *
 * <p>The merge is written against {@link Stacks} so it runs in unit tests, where {@code new
 * ItemStack(...)} needs a server; {@link #ITEMS} is the Bukkit implementation.
 */
public final class StorageMerge {

    /** How to read and copy one kind of stack. */
    public interface Stacks<T> {

        /** True if {@code a} and {@code b} can share a slot (same item and data). */
        boolean similar(T a, T b);

        int amount(T stack);

        int maxStackSize(T stack);

        /** A copy of {@code stack} with a different amount; never modifies {@code stack}. */
        T withAmount(T stack, int amount);
    }

    /** Bukkit item stacks. Main thread only. */
    public static final Stacks<ItemStack> ITEMS = new Stacks<>() {
        @Override
        public boolean similar(ItemStack a, ItemStack b) {
            return a.isSimilar(b);
        }

        @Override
        public int amount(ItemStack stack) {
            return stack.isEmpty() ? 0 : stack.getAmount();
        }

        @Override
        public int maxStackSize(ItemStack stack) {
            return stack.getMaxStackSize();
        }

        @Override
        public ItemStack withAmount(ItemStack stack, int amount) {
            return stack.asQuantity(amount);
        }
    };

    private StorageMerge() {
    }

    /** {@link #add(Object[], Collection, Stacks)} for Bukkit item stacks. */
    public static List<ItemStack> add(@Nullable ItemStack[] slots, Collection<ItemStack> items) {
        return add(slots, items, ITEMS);
    }

    /**
     * Adds {@code items} into {@code slots} (null = empty), modifying the array in place, and
     * returns the leftovers that didn't fit. Items with an amount of 0 or less are ignored.
     */
    public static <T> List<T> add(@Nullable T[] slots, Collection<T> items, Stacks<T> stacks) {
        List<T> leftovers = new ArrayList<>();
        for (T item : items) {
            int remaining = stacks.amount(item);
            if (remaining <= 0) {
                continue;
            }
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                T slot = slots[i];
                if (slot == null || !stacks.similar(slot, item)) {
                    continue;
                }
                int room = maxOf(slot, stacks) - stacks.amount(slot);
                if (room > 0) {
                    int moved = Math.min(room, remaining);
                    slots[i] = stacks.withAmount(slot, stacks.amount(slot) + moved);
                    remaining -= moved;
                }
            }
            int max = maxOf(item, stacks);
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                if (slots[i] == null) {
                    int moved = Math.min(max, remaining);
                    slots[i] = stacks.withAmount(item, moved);
                    remaining -= moved;
                }
            }
            if (remaining > 0) {
                leftovers.add(stacks.withAmount(item, remaining));
            }
        }
        return leftovers;
    }

    /** True if at least one more of {@code item} fits: an empty slot or a matching stack with room. */
    public static <T> boolean hasRoomFor(@Nullable T[] slots, T item, Stacks<T> stacks) {
        for (T slot : slots) {
            if (slot == null || (stacks.similar(slot, item) && stacks.amount(slot) < maxOf(slot, stacks))) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if one more of every item in {@code items} still fits. After a break, "no room for
     * the same drops again" is how the golem knows its storage is full before anything spills.
     */
    public static <T> boolean hasRoomForAll(@Nullable T[] slots, Collection<T> items, Stacks<T> stacks) {
        for (T item : items) {
            if (stacks.amount(item) > 0 && !hasRoomFor(slots, item, stacks)) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if {@link #add} would leave nothing over. Merges into a copy of {@code slots}, so
     * the real storage is untouched: used to decide before breaking a block whether its whole
     * drop fits, since multi-item drops (lapis, redstone, Fortune) can overflow a "room for one
     * more" check.
     */
    public static <T> boolean fitsAll(@Nullable T[] slots, Collection<T> items, Stacks<T> stacks) {
        return add(slots.clone(), items, stacks).isEmpty();
    }

    /** {@link #fitsAll(Object[], Collection, Stacks)} for Bukkit item stacks. */
    public static boolean fitsAll(@Nullable ItemStack[] slots, Collection<ItemStack> items) {
        return fitsAll(slots, items, ITEMS);
    }

    /** {@link #hasRoomForAll(Object[], Collection, Stacks)} for Bukkit item stacks. */
    public static boolean hasRoomForAll(@Nullable ItemStack[] slots, Collection<ItemStack> items) {
        return hasRoomForAll(slots, items, ITEMS);
    }

    public static <T> boolean hasEmptySlot(@Nullable T[] slots) {
        for (T slot : slots) {
            if (slot == null) {
                return true;
            }
        }
        return false;
    }

    public static <T> boolean isEmpty(@Nullable T[] slots) {
        for (T slot : slots) {
            if (slot != null) {
                return false;
            }
        }
        return true;
    }

    private static <T> int maxOf(T stack, Stacks<T> stacks) {
        return Math.max(1, stacks.maxStackSize(stack));
    }
}
