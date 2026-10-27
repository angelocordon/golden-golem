package dev.angelocordon.goldengolem.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HealingTest {

    @Test
    void hurtGolemNeedsHealing() {
        assertTrue(Healing.needsHealing(99.5, 100));
    }

    @Test
    void fullHealthGolemDoesNotNeedHealing() {
        assertFalse(Healing.needsHealing(100, 100));
    }

    @Test
    void healsByTheAmount() {
        assertEquals(65, Healing.healed(40, 100, GolemLifecycle.HEAL_AMOUNT));
    }

    @Test
    void healingCapsAtMaxHealth() {
        assertEquals(100, Healing.healed(90, 100, GolemLifecycle.HEAL_AMOUNT));
    }
}
