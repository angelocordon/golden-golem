package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Break times against the vanilla values (Minecraft wiki "Breaking" table, in ticks: seconds x
 * 20). Tool speeds: wood 2, iron 6, diamond 8; Efficiency adds level² + 1. Hardness: stone 1.5,
 * oak log 2.
 */
class BreakTimeTest {

    private static final float STONE = 1.5f;
    private static final float OAK_LOG = 2.0f;

    private static int ticks(float speed, float hardness, boolean canHarvest) {
        return BreakTime.ticksToBreak(BreakTime.progressPerTick(speed, hardness, canHarvest));
    }

    @Test
    void stoneWithWoodenPickaxeTakes23Ticks() {
        // Wiki: 1.15 s.
        assertEquals(23, ticks(2f, STONE, true));
    }

    @Test
    void stoneWithIronPickaxeTakes8Ticks() {
        // Wiki: 0.4 s.
        assertEquals(8, ticks(6f, STONE, true));
    }

    @Test
    void oakLogWithDiamondAxeTakes8Ticks() {
        // Wiki: 0.4 s.
        assertEquals(8, ticks(8f, OAK_LOG, true));
    }

    @Test
    void efficiencyIsPartOfTheDestroySpeed() {
        // Diamond pickaxe with Efficiency V: 8 + 26 = 34. Wiki: 0.1 s.
        assertEquals(2, ticks(34f, STONE, true));
    }

    @Test
    void oakLogByHandMatchesVanillaButAGolemNeverUsesItsHand() {
        // Wiki: 3 s. The formula is right, but a golem never mines without a tool.
        assertEquals(60, ticks(1f, OAK_LOG, true));
        assertFalse(HarvestRule.toolFits(Material.AIR, Material.OAK_LOG));
    }

    @Test
    void withoutTheRightToolTheDivisorIs100() {
        // Stone by hand drops nothing. Wiki: 7.5 s.
        assertEquals(150, ticks(1f, STONE, false));
    }

    @Test
    void zeroHardnessBreaksInOneTick() {
        assertEquals(1, ticks(1f, 0f, true));
    }

    @Test
    void negativeHardnessNeverBreaks() {
        // Bedrock.
        assertEquals(BreakTime.NEVER, ticks(8f, -1f, true));
    }

    @Test
    void progressOfAtLeastOneBreaksInOneTick() {
        assertEquals(1, BreakTime.ticksToBreak(1f));
        assertEquals(1, BreakTime.ticksToBreak(5f));
    }
}
