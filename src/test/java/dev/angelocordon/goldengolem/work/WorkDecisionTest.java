package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorkDecisionTest {

    /** A golem with everything it needs; each test changes one thing. */
    private static WorkDecision check(boolean hasWorkCenter, boolean storageFull, boolean hasTool,
            boolean toolFits, boolean toolLow) {
        return WorkDecision.check(hasWorkCenter, storageFull, hasTool, toolFits, toolLow);
    }

    @Test
    void continuesWithAFittingHealthyToolAndRoom() {
        assertEquals(WorkDecision.CONTINUE, check(true, false, true, true, false));
    }

    @Test
    void idlesWithoutWorkCenter() {
        assertEquals(WorkDecision.IDLE, check(false, false, true, true, false));
        assertEquals(WorkDecision.IDLE, check(false, true, false, false, true));
    }

    @Test
    void returnsWhenStorageIsFull() {
        assertEquals(WorkDecision.RETURN, check(true, true, true, true, false));
    }

    @Test
    void returnsWhenToolIsLow() {
        assertEquals(WorkDecision.RETURN, check(true, false, true, true, true));
    }

    @Test
    void blocksWithNoToolWhenToolIsMissing() {
        assertEquals(WorkDecision.NO_TOOL, check(true, false, false, false, false));
    }

    @Test
    void blocksWithCantHarvestWhenToolDoesNotFitTarget() {
        assertEquals(WorkDecision.CANT_HARVEST, check(true, false, true, false, false));
    }

    @Test
    void cantHarvestWinsOverALowTool() {
        // Swapping the worn tool at the chest wouldn't help; the player has to fix the setup.
        assertEquals(WorkDecision.CANT_HARVEST, check(true, false, true, false, true));
    }

    @Test
    void fullStorageIsDeliveredEvenWithoutATool() {
        assertEquals(WorkDecision.RETURN, check(true, true, false, false, false));
    }

    @Test
    void exhaustedAreaWithLootReturns() {
        assertEquals(WorkDecision.RETURN, WorkDecision.onAreaExhausted(true));
    }

    @Test
    void exhaustedAreaWithEmptyStorageIdles() {
        assertEquals(WorkDecision.IDLE, WorkDecision.onAreaExhausted(false));
    }
}
