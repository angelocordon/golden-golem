package dev.angelocordon.goldengolem.golem.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.angelocordon.goldengolem.golem.state.FollowingHandler.Action;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class FollowingHandlerTest {

    private static Action at(double distance) {
        return FollowingHandler.decide(distance * distance);
    }

    @Test
    void stopsWhenCloseToOwner() {
        assertEquals(Action.STOP, at(0.0));
        assertEquals(Action.STOP, at(2.0));
    }

    @Test
    void keepsCurrentMotionBetweenStopAndFollowDistance() {
        assertEquals(Action.KEEP, at(2.5));
        assertEquals(Action.KEEP, at(3.0));
    }

    @Test
    void walksWhenFartherThanThreeBlocks() {
        assertEquals(Action.WALK, at(3.1));
        assertEquals(Action.WALK, at(24.0));
    }

    @Test
    void teleportsWhenFartherThanTwentyFourBlocks() {
        assertEquals(Action.TELEPORT, at(24.1));
        assertEquals(Action.TELEPORT, at(500.0));
    }

    private static boolean safe(boolean dead, boolean flying, boolean gliding, boolean inVehicle, boolean inLava,
            Material atFeet, boolean solidBelow) {
        return FollowingHandler.isSafeTeleport(dead, flying, gliding, inVehicle, inLava, atFeet, solidBelow);
    }

    @Test
    void safeToTeleportOntoOwnerStandingOnGround() {
        assertTrue(safe(false, false, false, false, false, Material.AIR, true));
        assertTrue(safe(false, false, false, false, false, Material.SHORT_GRASS, true));
    }

    @Test
    void unsafeWhenOwnerIsDeadOrAirborneOrRiding() {
        assertFalse(safe(true, false, false, false, false, Material.AIR, true));
        assertFalse(safe(false, true, false, false, false, Material.AIR, true));
        assertFalse(safe(false, false, true, false, false, Material.AIR, true));
        assertFalse(safe(false, false, false, true, false, Material.AIR, true));
        assertFalse(safe(false, false, false, false, false, Material.AIR, false));
    }

    @Test
    void unsafeWithLavaOrFireAtOwnersFeet() {
        assertFalse(safe(false, false, false, false, true, Material.AIR, true));
        assertFalse(safe(false, false, false, false, false, Material.LAVA, true));
        assertFalse(safe(false, false, false, false, false, Material.FIRE, true));
        assertFalse(safe(false, false, false, false, false, Material.SOUL_FIRE, true));
    }
}
