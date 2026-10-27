package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DepositTest {

    /** A stand-in for an item stack: {@code new ItemStack} needs a server. */
    record Item(String type, int amount, int max) {
    }

    static final Deposit.Stacks<Item> STACKS = new Deposit.Stacks<>() {
        @Override
        public int amount(Item stack) {
            return stack.amount();
        }

        @Override
        public int maxStack(Item stack) {
            return stack.max();
        }

        @Override
        public boolean similar(Item a, Item b) {
            return a.type().equals(b.type()) && a.max() == b.max();
        }

        @Override
        public Item withAmount(Item stack, int amount) {
            return new Item(stack.type(), amount, stack.max());
        }
    };

    private static Item stone(int amount) {
        return new Item("stone", amount, 64);
    }

    private static Item dirt(int amount) {
        return new Item("dirt", amount, 64);
    }

    private static Item pick() {
        return new Item("pickaxe", 1, 1);
    }

    static List<@Nullable Item> slots(int size, @Nullable Item... filled) {
        List<@Nullable Item> slots = new ArrayList<>(Arrays.asList(new Item[size]));
        for (int i = 0; i < filled.length; i++) {
            slots.set(i, filled[i]);
        }
        return slots;
    }

    static Map<String, Integer> totals(List<@Nullable Item> a, List<@Nullable Item> b) {
        Map<String, Integer> totals = new TreeMap<>();
        for (List<@Nullable Item> list : List.of(a, b)) {
            for (Item item : list) {
                if (item != null) {
                    totals.merge(item.type(), item.amount(), Integer::sum);
                }
            }
        }
        return totals;
    }

    @Test
    void topsUpPartialStacksBeforeUsingEmptySlots() {
        List<@Nullable Item> chest = slots(27, stone(60), null, stone(10));
        Deposit.Result<Item> result = Deposit.into(chest, slots(15, stone(20)), STACKS);

        assertEquals(stone(64), result.chest().get(0));
        assertNull(result.chest().get(1));
        assertEquals(stone(26), result.chest().get(2));
        assertTrue(result.allDeposited());
    }

    @Test
    void splitsAcrossEmptySlotsAtMaxStackSize() {
        Deposit.Result<Item> result = Deposit.into(slots(27), slots(15, stone(64), stone(64), stone(10)), STACKS);

        assertEquals(stone(64), result.chest().get(0));
        assertEquals(stone(64), result.chest().get(1));
        assertEquals(stone(10), result.chest().get(2));
        assertTrue(result.allDeposited());
    }

    @Test
    void unstackableItemsTakeOneSlotEach() {
        Deposit.Result<Item> result = Deposit.into(slots(27, pick()), slots(15, pick(), pick()), STACKS);

        assertEquals(pick(), result.chest().get(1));
        assertEquals(pick(), result.chest().get(2));
        assertTrue(result.allDeposited());
    }

    @Test
    void fullChestKeepsEverythingInItsStorageSlot() {
        List<@Nullable Item> chest = slots(27);
        for (int i = 0; i < 27; i++) {
            chest.set(i, dirt(64));
        }
        List<@Nullable Item> storage = slots(15, null, stone(5), null, dirt(3));
        Deposit.Result<Item> result = Deposit.into(chest, storage, STACKS);

        assertFalse(result.allDeposited());
        assertEquals(chest, result.chest());
        assertEquals(storage, result.leftovers());
    }

    @Test
    void partlyFullChestLeavesOnlyTheRest() {
        List<@Nullable Item> chest = slots(27);
        for (int i = 0; i < 26; i++) {
            chest.set(i, dirt(64));
        }
        chest.set(26, stone(50));
        Deposit.Result<Item> result = Deposit.into(chest, slots(15, stone(20), dirt(1)), STACKS);

        assertEquals(stone(64), result.chest().get(26));
        assertEquals(stone(6), result.leftovers().get(0));
        assertEquals(dirt(1), result.leftovers().get(1));
        assertFalse(result.allDeposited());
    }

    @Test
    void doubleChestUsesAllFiftyFourSlots() {
        List<@Nullable Item> chest = slots(54);
        for (int i = 0; i < 40; i++) {
            chest.set(i, dirt(64));
        }
        List<@Nullable Item> storage = slots(15);
        for (int i = 0; i < 15; i++) {
            storage.set(i, stone(64));
        }
        Deposit.Result<Item> result = Deposit.into(chest, storage, STACKS);

        assertEquals(54, result.chest().size());
        for (int i = 40; i < 54; i++) {
            assertEquals(stone(64), result.chest().get(i));
        }
        // 14 free slots for 15 stacks: the last one stays behind.
        assertEquals(stone(64), result.leftovers().get(14));
        assertEquals(1, result.leftovers().stream().filter(item -> item != null).count());
    }

    @Test
    void mixedItemsOnlyMergeWithTheirOwnKind() {
        List<@Nullable Item> chest = slots(27, dirt(60), null, stone(63));
        Deposit.Result<Item> result = Deposit.into(chest, slots(15, stone(3), dirt(10), pick()), STACKS);

        assertEquals(dirt(64), result.chest().get(0));
        assertEquals(stone(64), result.chest().get(2));
        // Stone overflow takes the first free slot, then the dirt rest, then the pickaxe.
        assertEquals(stone(2), result.chest().get(1));
        assertEquals(dirt(6), result.chest().get(3));
        assertEquals(pick(), result.chest().get(4));
        assertTrue(result.allDeposited());
    }

    @Test
    void itemTotalsNeverChange() {
        List<@Nullable Item> chest = slots(27);
        for (int i = 0; i < 25; i++) {
            chest.set(i, i % 2 == 0 ? dirt(64) : stone(40));
        }
        List<@Nullable Item> storage = slots(15, stone(64), dirt(64), pick(), stone(30), dirt(5), pick());
        Deposit.Result<Item> result = Deposit.into(chest, storage, STACKS);

        assertEquals(totals(chest, storage), totals(result.chest(), result.leftovers()));
        assertEquals(27, result.chest().size());
        assertEquals(15, result.leftovers().size());
    }

    @Test
    void neverModifiesInputLists() {
        List<@Nullable Item> chest = slots(27, stone(10));
        List<@Nullable Item> storage = slots(15, stone(10));
        List<@Nullable Item> chestBefore = new ArrayList<>(chest);
        List<@Nullable Item> storageBefore = new ArrayList<>(storage);

        Deposit.into(chest, storage, STACKS);

        assertEquals(chestBefore, chest);
        assertEquals(storageBefore, storage);
    }

    /**
     * Bukkit inventory contents can be live mirrors, so a changed slot must be a fresh copy from
     * {@code withAmount}, never the input object (which a mutating merge would hand back).
     */
    @Test
    void changedSlotsAreFreshCopies() {
        Item partial = stone(10);
        Item stored = stone(70);
        Deposit.Result<Item> result = Deposit.into(slots(2, partial), slots(1, stored), STACKS);

        assertEquals(stone(64), result.chest().get(0));
        assertNotSame(partial, result.chest().get(0));
        assertEquals(stone(16), result.chest().get(1));
        assertNotSame(stored, result.chest().get(1));
    }

    @Test
    void overfullChestStackIsLeftAlone() {
        Deposit.Result<Item> result = Deposit.into(slots(2, stone(70)), slots(1, stone(5)), STACKS);

        assertEquals(stone(70), result.chest().get(0));
        assertEquals(stone(5), result.chest().get(1));
    }
}
