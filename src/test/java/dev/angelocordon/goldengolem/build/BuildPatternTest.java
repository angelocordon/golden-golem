package dev.angelocordon.goldengolem.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class BuildPatternTest {

    @Test
    void matchesCarvedPumpkinOnGoldBlock() {
        assertTrue(BuildPattern.matches(Material.CARVED_PUMPKIN, Material.GOLD_BLOCK));
    }

    @Test
    void matchesJackOLanternOnGoldBlock() {
        assertTrue(BuildPattern.matches(Material.JACK_O_LANTERN, Material.GOLD_BLOCK));
    }

    @Test
    void leavesVanillaCopperGolemPatternAlone() {
        assertFalse(BuildPattern.matches(Material.CARVED_PUMPKIN, Material.COPPER_BLOCK));
        assertFalse(BuildPattern.matches(Material.JACK_O_LANTERN, Material.WAXED_COPPER_BLOCK));
        assertFalse(BuildPattern.matches(Material.CARVED_PUMPKIN, Material.EXPOSED_COPPER));
    }

    @Test
    void leavesIronAndSnowGolemPatternsAlone() {
        assertFalse(BuildPattern.matches(Material.CARVED_PUMPKIN, Material.IRON_BLOCK));
        assertFalse(BuildPattern.matches(Material.JACK_O_LANTERN, Material.SNOW_BLOCK));
    }

    @Test
    void rejectsUncarvedPumpkin() {
        assertFalse(BuildPattern.matches(Material.PUMPKIN, Material.GOLD_BLOCK));
    }

    @Test
    void rejectsOtherGoldLookalikesBelow() {
        assertFalse(BuildPattern.matches(Material.CARVED_PUMPKIN, Material.RAW_GOLD_BLOCK));
        assertFalse(BuildPattern.matches(Material.CARVED_PUMPKIN, Material.GOLD_ORE));
        assertFalse(BuildPattern.matches(Material.CARVED_PUMPKIN, Material.AIR));
    }

    @Test
    void rejectsGoldBlockPlacedOnPumpkin() {
        assertFalse(BuildPattern.matches(Material.GOLD_BLOCK, Material.CARVED_PUMPKIN));
    }

    @Test
    void rejectsNulls() {
        assertFalse(BuildPattern.matches(null, Material.GOLD_BLOCK));
        assertFalse(BuildPattern.matches(Material.CARVED_PUMPKIN, null));
    }
}
