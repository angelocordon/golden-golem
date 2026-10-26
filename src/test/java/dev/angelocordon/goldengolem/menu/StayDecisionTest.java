package dev.angelocordon.goldengolem.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StayDecisionTest {

    private static final int MAX = 64;

    @Test
    void worksWithFittingToolAndTargetAndNoChest() {
        assertEquals(StayDecision.WORK, StayDecision.decide(true, true, false, 0, MAX));
    }

    @Test
    void worksWhenChestIsInRange() {
        assertEquals(StayDecision.WORK, StayDecision.decide(true, true, true, 10, MAX));
    }

    @Test
    void worksWhenChestIsExactlyAtMaxDistance() {
        assertEquals(StayDecision.WORK, StayDecision.decide(true, true, true, MAX, MAX));
    }

    @Test
    void idlesWithoutTool() {
        assertEquals(StayDecision.IDLE, StayDecision.decide(false, false, true, 5, MAX));
    }

    @Test
    void idlesWhenToolDoesNotFitTarget() {
        assertEquals(StayDecision.IDLE, StayDecision.decide(true, false, true, 5, MAX));
    }

    @Test
    void refusesWhenChestIsTooFar() {
        assertEquals(StayDecision.TOO_FAR, StayDecision.decide(true, true, true, MAX + 0.5, MAX));
    }

    @Test
    void refusesWhenChestIsInAnotherWorld() {
        assertEquals(StayDecision.TOO_FAR,
                StayDecision.decide(true, true, true, Double.POSITIVE_INFINITY, MAX));
    }

    @Test
    void refusesTooFarEvenWithoutAToolSoTheRangeProblemIsReportedFirst() {
        assertEquals(StayDecision.TOO_FAR, StayDecision.decide(false, false, true, 1000, MAX));
    }

    @Test
    void treatsUnknownDistanceAsTooFar() {
        assertEquals(StayDecision.TOO_FAR, StayDecision.decide(true, true, true, Double.NaN, MAX));
    }

    @Test
    void ignoresDistanceWhenNoChestIsLinked() {
        assertEquals(StayDecision.WORK,
                StayDecision.decide(true, true, false, Double.POSITIVE_INFINITY, MAX));
    }
}
