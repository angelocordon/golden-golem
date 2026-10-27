package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.angelocordon.goldengolem.GolemConfig;
import org.junit.jupiter.api.Test;

class DurabilityTest {

    @Test
    void freshToolIsNotLow() {
        assertFalse(Durability.isLow(1561, 0, 5.0, 3));
    }

    @Test
    void lowWhenUsesLeftReachMinUses() {
        assertFalse(Durability.isLow(100, 96, 0.0, 3));
        assertTrue(Durability.isLow(100, 97, 0.0, 3));
        assertTrue(Durability.isLow(100, 99, 0.0, 3));
    }

    @Test
    void lowWhenUsesLeftDropBelowPercent() {
        // 5% of 200 is 10: exactly 10 left is not low, 9 left is.
        assertFalse(Durability.isLow(200, 190, 5.0, 0));
        assertTrue(Durability.isLow(200, 191, 5.0, 0));
    }

    @Test
    void percentUsesFractionalThreshold() {
        // 5% of 59 is 2.95: 3 left is not low, 2 left is.
        assertFalse(Durability.isLow(59, 56, 5.0, 0));
        assertTrue(Durability.isLow(59, 57, 5.0, 0));
    }

    @Test
    void eitherRuleIsEnough() {
        // Percent says no (4 >= 5% of 32 = 1.6), minUses says yes.
        assertTrue(Durability.isLow(32, 28, 5.0, 4));
        // minUses says no, percent says yes (40 < 50% of 100).
        assertTrue(Durability.isLow(100, 60, 50.0, 3));
    }

    @Test
    void lastUseIsAlwaysLowEvenWithZeroThresholds() {
        assertFalse(Durability.isLow(100, 98, 0.0, 0));
        assertTrue(Durability.isLow(100, 99, 0.0, 0));
        assertTrue(Durability.isLow(100, 100, 0.0, 0));
    }

    @Test
    void damageBeyondMaxIsLow() {
        assertTrue(Durability.isLow(100, 150, 5.0, 3));
    }

    @Test
    void itemWithoutDurabilityIsNeverLow() {
        assertFalse(Durability.isLow(0, 0, 5.0, 3));
        assertFalse(Durability.isLow(0, 10, 50.0, 64));
        assertFalse(Durability.isLow(-1, 0, 5.0, 3));
    }

    @Test
    void unbreakableToolIsNeverLow() {
        assertFalse(Durability.isLow(100, 100, 50.0, 64, true));
        assertTrue(Durability.isLow(100, 100, 50.0, 64, false));
    }

    @Test
    void itemReadingUsesConfigThresholds() {
        GolemConfig config = new GolemConfig(16, 4, 256, 64, 0.0, 3);
        assertFalse(Durability.isLow(1561, 0, false, config));
        assertFalse(Durability.isLow(1561, 1557, false, config));
        assertTrue(Durability.isLow(1561, 1558, false, config));
    }

    @Test
    void itemWithoutMaxDamageIsNeverLow() {
        assertFalse(Durability.isLow(null, 0, false, GolemConfig.DEFAULTS));
    }

    @Test
    void unbreakableItemIsNeverLow() {
        assertFalse(Durability.isLow(1561, 1560, true, GolemConfig.DEFAULTS));
    }

    @Test
    void missingItemIsNotLow() {
        assertFalse(Durability.isLow((org.bukkit.inventory.ItemStack) null, GolemConfig.DEFAULTS));
    }

    @Test
    void usesLeftIsMaxDamageMinusDamage() {
        assertEquals(61, Durability.usesLeft(250, 189, false));
    }

    @Test
    void usesLeftIsUnlimitedWithoutDurability() {
        assertEquals(Integer.MAX_VALUE, Durability.usesLeft(null, 0, false));
        assertEquals(Integer.MAX_VALUE, Durability.usesLeft(250, 249, true));
    }
}
