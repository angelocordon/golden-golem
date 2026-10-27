package dev.angelocordon.goldengolem.golem.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathStepsTest {

    private static final double EPS = 1e-9;

    @Test
    void normalSpeedMatchesVanillaCopperGolemWalk() {
        // Copper golem movement_speed 0.2: (0.2)^2 / (1 - 0.546) = 0.0881 blocks/tick.
        assertEquals(0.0881, PathSteps.blocksPerTick(0.2, 1.0), 0.0005);
    }

    @Test
    void speedGrowsWithSquareOfMultiplierLikeVanilla() {
        assertEquals(PathSteps.blocksPerTick(0.2, 1.0) * 4, PathSteps.blocksPerTick(0.2, 2.0), EPS);
        assertEquals(0.0, PathSteps.blocksPerTick(0.2, 0.0), EPS);
    }

    @Test
    void followSpeedKeepsUpWithAWalkingPlayer() {
        // A walking player covers about 0.216 blocks/tick.
        assertTrue(PathSteps.blocksPerTick(0.2, 1.7) > 0.216);
    }

    @Test
    void blocksPerTickIsClamped() {
        assertEquals(PathSteps.MAX_BLOCKS_PER_TICK, PathSteps.blocksPerTick(10.0, 1.0), EPS);
        assertEquals(0.0, PathSteps.blocksPerTick(0.1, -1.0), EPS);
    }

    @Test
    void stepPointsAtWaypointWithSpeedLength() {
        PathSteps.Step step = PathSteps.step(0.5, 0.5, 3.5, 4.5, 0.25);

        assertEquals(0.25, step.length(), EPS);
        // 3-4-5 triangle: direction (0.6, 0.8).
        assertEquals(0.15, step.dx(), EPS);
        assertEquals(0.20, step.dz(), EPS);
    }

    @Test
    void stepNeverOvershootsWaypoint() {
        PathSteps.Step step = PathSteps.step(0.5, 0.5, 0.6, 0.5, 0.25);

        assertEquals(0.1, step.dx(), EPS);
        assertEquals(0.0, step.dz(), EPS);
    }

    @Test
    void stepIsZeroWhenAlreadyOnWaypoint() {
        PathSteps.Step step = PathSteps.step(2.5, -7.5, 2.5, -7.5, 0.25);

        assertEquals(0.0, step.length(), EPS);
    }

    @Test
    void reachedWhenFeetAreOnWaypointBlock() {
        assertTrue(PathSteps.reached(10.3, 64.0, 5.7, 10.5, 64, 5.5));
        assertTrue(PathSteps.reached(10.5, 64.5, 5.5, 10.5, 64, 5.5));
    }

    @Test
    void notReachedWhenHorizontallyOff() {
        assertFalse(PathSteps.reached(11.1, 64.0, 5.5, 10.5, 64, 5.5));
        assertFalse(PathSteps.reached(10.5, 64.0, 4.9, 10.5, 64, 5.5));
    }

    @Test
    void notReachedWhenStillBelowARaisedWaypoint() {
        assertFalse(PathSteps.reached(10.5, 64.0, 5.5, 10.5, 65, 5.5));
    }

    @Test
    void jumpsOnlyWhenWaypointIsHigherThanStepHeight() {
        assertTrue(PathSteps.needsJump(64.0, 65, 0.6));
        assertFalse(PathSteps.needsJump(64.0, 64, 0.6));
        assertFalse(PathSteps.needsJump(64.0, 63, 0.6));
        // Stairs and slabs are half a block: a normal step, no jump.
        assertFalse(PathSteps.needsJump(64.0, 64.5, 0.6));
        // A mob with a full-block step height walks up blocks.
        assertFalse(PathSteps.needsJump(64.0, 65, 1.0));
    }

    @Test
    void yawFacesTheWalkingDirection() {
        assertEquals(0f, PathSteps.yaw(0, 1), 1e-4);
        assertEquals(90f, PathSteps.yaw(-1, 0), 1e-4);
        assertEquals(-90f, PathSteps.yaw(1, 0), 1e-4);
        assertEquals(180f, Math.abs(PathSteps.yaw(0, -1)), 1e-4);
    }
}
