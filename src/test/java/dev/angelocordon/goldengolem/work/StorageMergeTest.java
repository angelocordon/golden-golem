package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class StorageMergeTest {

    /** A stand-in for ItemStack, which needs a server. */
    record Item(String kind, int amount, int max) {
    }

    private static final StorageMerge.Stacks<Item> STACKS = new StorageMerge.Stacks<>() {
        @Override
        public boolean similar(Item a, Item b) {
            return a.kind().equals(b.kind());
        }

        @Override
        public int amount(Item stack) {
            return stack.amount();
        }

        @Override
        public int maxStackSize(Item stack) {
            return stack.max();
        }

        @Override
        public Item withAmount(Item stack, int amount) {
            return new Item(stack.kind(), amount, stack.max());
        }
    };

    private static Item cobble(int amount) {
        return new Item("cobble", amount, 64);
    }

    private static Item flint(int amount) {
        return new Item("flint", amount, 64);
    }

    private static Item pick(int amount) {
        return new Item("pick", amount, 1);
    }

    private static int total(Item[] slots, List<Item> leftovers) {
        return Arrays.stream(slots).filter(Objects::nonNull).mapToInt(Item::amount).sum()
                + leftovers.stream().mapToInt(Item::amount).sum();
    }

    @Test
    void fillsAnEmptyStorage() {
        Item[] slots = new Item[3];
        List<Item> leftovers = StorageMerge.add(slots, List.of(cobble(1)), STACKS);
        assertTrue(leftovers.isEmpty());
        assertArrayEquals(new Item[] {cobble(1), null, null}, slots);
    }

    @Test
    void topsUpMatchingStacksBeforeUsingEmptySlots() {
        Item[] slots = {null, cobble(10), flint(5)};
        StorageMerge.add(slots, List.of(cobble(3)), STACKS);
        assertArrayEquals(new Item[] {null, cobble(13), flint(5)}, slots);
    }

    @Test
    void spillsOverIntoEmptySlotsWhenAStackIsFull() {
        Item[] slots = {cobble(62), null, null};
        List<Item> leftovers = StorageMerge.add(slots, List.of(cobble(5)), STACKS);
        assertTrue(leftovers.isEmpty());
        assertArrayEquals(new Item[] {cobble(64), cobble(3), null}, slots);
    }

    @Test
    void returnsWhatDoesNotFit() {
        Item[] slots = {cobble(63), flint(64)};
        List<Item> leftovers = StorageMerge.add(slots, List.of(cobble(4), flint(1)), STACKS);
        assertArrayEquals(new Item[] {cobble(64), flint(64)}, slots);
        assertEquals(List.of(cobble(3), flint(1)), leftovers);
    }

    @Test
    void neverStacksUnstackableItems() {
        Item[] slots = {pick(1), null};
        List<Item> leftovers = StorageMerge.add(slots, List.of(pick(1), pick(1)), STACKS);
        assertArrayEquals(new Item[] {pick(1), pick(1)}, slots);
        assertEquals(List.of(pick(1)), leftovers);
    }

    @Test
    void keepsTheTotalItemCount() {
        Item[] slots = {cobble(60), null, flint(64), cobble(1)};
        List<Item> items = List.of(cobble(70), flint(10), cobble(64), flint(3));
        int before = total(slots, items);
        List<Item> leftovers = StorageMerge.add(slots, items, STACKS);
        assertEquals(before, total(slots, leftovers));
        // Cobble fills 4 + 63 in the old stacks and 64 in the empty slot (3 + 61); flint has no room.
        assertArrayEquals(new Item[] {cobble(64), cobble(64), flint(64), cobble(64)}, slots);
        assertEquals(List.of(flint(10), cobble(3), flint(3)), leftovers);
    }

    @Test
    void ignoresEmptyItems() {
        Item[] slots = new Item[1];
        List<Item> leftovers = StorageMerge.add(slots, List.of(cobble(0)), STACKS);
        assertTrue(leftovers.isEmpty());
        assertArrayEquals(new Item[] {null}, slots);
    }

    @Test
    void hasRoomForAnEmptySlotOrAMatchingStackWithSpace() {
        assertTrue(StorageMerge.hasRoomFor(new Item[] {flint(64), null}, cobble(1), STACKS));
        assertTrue(StorageMerge.hasRoomFor(new Item[] {flint(64), cobble(63)}, cobble(1), STACKS));
        assertFalse(StorageMerge.hasRoomFor(new Item[] {flint(10), cobble(64)}, cobble(1), STACKS));
    }

    @Test
    void hasRoomForAllNeedsRoomForEveryDrop() {
        Item[] slots = {cobble(10), flint(64)};
        assertTrue(StorageMerge.hasRoomForAll(slots, List.of(cobble(1)), STACKS));
        assertFalse(StorageMerge.hasRoomForAll(slots, List.of(cobble(1), flint(1)), STACKS));
    }

    @Test
    void reportsEmptySlotsAndEmptiness() {
        assertTrue(StorageMerge.isEmpty(new Item[2]));
        assertFalse(StorageMerge.isEmpty(new Item[] {null, cobble(1)}));
        assertTrue(StorageMerge.hasEmptySlot(new Item[] {cobble(1), null}));
        assertFalse(StorageMerge.hasEmptySlot(new Item[] {cobble(1)}));
    }
}
