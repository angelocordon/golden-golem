package dev.angelocordon.goldengolem.work;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Merges golem storage into chest slots the way a player shift-clicking would: top up similar
 * partial stacks first, then fill empty slots, never past the max stack size.
 *
 * <p>Generic over the stack type so the merge is unit-tested with plain records ({@code new
 * ItemStack} needs a server). {@link #itemStacks(int)} adapts it to Bukkit items. Inputs are never
 * mutated: a changed stack is always a fresh copy from {@link Stacks#withAmount}, which matters
 * because Bukkit inventory contents can be live mirrors.
 */
public final class Deposit {

    /** How to read and copy one kind of stack. */
    public interface Stacks<T> {
        int amount(T stack);

        /** The most this stack may hold in one chest slot. */
        int maxStack(T stack);

        /** True if the two may share a slot (same item and data, amounts ignored). */
        boolean similar(T a, T b);

        /** A copy of {@code stack} holding {@code amount}. */
        T withAmount(T stack, int amount);
    }

    /**
     * The merge outcome. {@code chest} has the chest's slot count; {@code leftovers} has the
     * storage's slot count, with whatever didn't fit left in the slot it came from. Empty slots
     * are null.
     */
    public record Result<T>(List<@Nullable T> chest, List<@Nullable T> leftovers) {

        /** True if every stored item made it into the chest. */
        public boolean allDeposited() {
            return leftovers.stream().allMatch(stack -> stack == null);
        }
    }

    private Deposit() {
    }

    /**
     * Moves as much of {@code storage} into {@code chest} as fits. The total amount of each item
     * across chest and leftovers is unchanged.
     */
    public static <T> Result<T> into(
            List<? extends @Nullable T> chest, List<? extends @Nullable T> storage, Stacks<T> stacks) {
        List<@Nullable T> slots = new ArrayList<>(chest);
        List<@Nullable T> leftovers = new ArrayList<>(storage.size());
        for (T stored : storage) {
            leftovers.add(stored == null ? null : insert(slots, stored, stacks));
        }
        return new Result<>(Collections.unmodifiableList(slots), Collections.unmodifiableList(leftovers));
    }

    /** Puts {@code stack} into {@code slots}; returns what didn't fit, or null if it all did. */
    private static <T> @Nullable T insert(List<@Nullable T> slots, T stack, Stacks<T> stacks) {
        int remaining = stacks.amount(stack);
        if (remaining <= 0) {
            return null;
        }
        int max = Math.max(1, stacks.maxStack(stack));
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            T slot = slots.get(i);
            if (slot == null || !stacks.similar(slot, stack)) {
                continue;
            }
            int room = max - stacks.amount(slot);
            if (room > 0) {
                int moved = Math.min(room, remaining);
                slots.set(i, stacks.withAmount(slot, stacks.amount(slot) + moved));
                remaining -= moved;
            }
        }
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            if (slots.get(i) == null) {
                int moved = Math.min(max, remaining);
                slots.set(i, stacks.withAmount(stack, moved));
                remaining -= moved;
            }
        }
        if (remaining == stacks.amount(stack)) {
            return stack;
        }
        return remaining == 0 ? null : stacks.withAmount(stack, remaining);
    }

    /**
     * Bukkit item stacks. A slot holds at most the item's own max stack size and the inventory's
     * limit ({@code Inventory#getMaxStackSize}), whichever is lower. Empty stacks count as 0.
     */
    public static Stacks<ItemStack> itemStacks(int inventoryMaxStack) {
        return new Stacks<>() {
            @Override
            public int amount(ItemStack stack) {
                return stack.isEmpty() ? 0 : stack.getAmount();
            }

            @Override
            public int maxStack(ItemStack stack) {
                return Math.min(stack.getMaxStackSize(), inventoryMaxStack);
            }

            @Override
            public boolean similar(ItemStack a, ItemStack b) {
                return !a.isEmpty() && a.isSimilar(b);
            }

            @Override
            public ItemStack withAmount(ItemStack stack, int amount) {
                return stack.asQuantity(amount);
            }
        };
    }
}
