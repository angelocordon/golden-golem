package dev.angelocordon.goldengolem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GolemConfigTest {

    @Test
    void clampsOutOfRangeValues() {
        GolemConfig config = new GolemConfig(500, -1, 1, 0, 90.0, -5);

        assertEquals(GolemConfig.MAX_RADIUS, config.radius());
        assertEquals(0, config.verticalRange());
        assertEquals(16, config.scanBlocksPerTick());
        assertEquals(1, config.maxChestDistance());
        assertEquals(50.0, config.lowDurabilityPercent());
        assertEquals(0, config.lowDurabilityUses());
    }

    @Test
    void defaultsMatchTheSpec() {
        assertEquals(16, GolemConfig.DEFAULTS.radius());
        assertEquals(4, GolemConfig.DEFAULTS.verticalRange());
        assertEquals(64, GolemConfig.DEFAULTS.maxChestDistance());
    }
}
