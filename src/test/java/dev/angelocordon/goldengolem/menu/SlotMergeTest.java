package dev.angelocordon.goldengolem.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Strings stand in for item stacks: "dirt x3" vs "dirt x5" are different slot contents. */
class SlotMergeTest {

    @Test
    void keepsGolemValueWhenPlayerDidNotTouchSlot() {
        assertEquals("cobble x10", SlotMerge.pick("cobble x5", "cobble x5", "cobble x10", Objects::equals));
    }

    @Test
    void keepsPlayerValueWhenOnlyPlayerChangedSlot() {
        assertEquals("diamond x1", SlotMerge.pick(null, "diamond x1", null, Objects::equals));
    }

    @Test
    void playerTakingAnItemOutClearsTheSlot() {
        assertNull(SlotMerge.pick("pickaxe", null, "pickaxe", Objects::equals));
    }

    @Test
    void mergesDisjointEditsWithoutLosingEither() {
        List<String> base = Arrays.asList("dirt x3", null, "sand x1");
        List<String> view = Arrays.asList(null, null, "sand x1");      // player took the dirt
        List<String> current = Arrays.asList("dirt x3", null, "sand x9"); // golem mined sand
        SlotMerge.Result<String> result = SlotMerge.merge(base, view, current, Objects::equals);
        assertEquals(Arrays.asList(null, null, "sand x9"), result.slots());
        assertEquals(0, result.conflicts());
    }

    @Test
    void countsConflictsWhenBothChangedTheSameSlotDifferently() {
        List<String> base = Arrays.asList("dirt x3");
        List<String> view = Arrays.asList("dirt x1");
        List<String> current = Arrays.asList((String) null);
        SlotMerge.Result<String> result = SlotMerge.merge(base, view, current, Objects::equals);
        assertEquals(1, result.conflicts());
        assertEquals(Arrays.asList("dirt x1"), result.slots());
    }

    @Test
    void identicalEditsOnBothSidesAreNotAConflict() {
        assertFalse(SlotMerge.conflicts("a", "b", "b", Objects::equals));
        assertTrue(SlotMerge.conflicts("a", "b", "c", Objects::equals));
    }

    @Test
    void nullOnlyEqualsNull() {
        assertTrue(SlotMerge.equal(null, null, Objects::equals));
        assertFalse(SlotMerge.equal(null, "a", Objects::equals));
        assertFalse(SlotMerge.equal("a", null, Objects::equals));
    }

    @Test
    void usesTheGivenEqualityRule() {
        // e.g. two stacks that are equal by content but not by identity
        assertEquals("golem", SlotMerge.pick("x", "X", "golem", String::equalsIgnoreCase));
    }
}
