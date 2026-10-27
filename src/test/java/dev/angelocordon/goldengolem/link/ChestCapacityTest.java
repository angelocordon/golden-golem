package dev.angelocordon.goldengolem.link;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChestCapacityTest {

    @Test
    void singleChestWithNoGolemsHasOneSlot() {
        assertEquals(1, ChestCapacity.free(1, 0));
    }

    @Test
    void singleChestWithOneGolemIsFull() {
        assertEquals(0, ChestCapacity.free(1, 1));
    }

    @Test
    void doubleChestWithOneGolemHasOneSlotLeft() {
        assertEquals(1, ChestCapacity.free(2, 1));
    }

    @Test
    void overCapacityChestReportsZeroNotNegative() {
        assertEquals(0, ChestCapacity.free(1, 2));
    }

    @Test
    void plainChestHasNoRoom() {
        assertEquals(0, ChestCapacity.free(0, 0));
    }
}
