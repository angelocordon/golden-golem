package dev.angelocordon.goldengolem.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ControlInputTest {

    @Test
    void plainHitOpensTheMenu() {
        assertEquals(ControlInput.OPEN, ControlInput.decide(false, false, false));
        assertEquals(ControlInput.OPEN, ControlInput.decide(false, false, true));
    }

    @Test
    void sneakingTogglesFollowAndStay() {
        assertEquals(ControlInput.TOGGLE, ControlInput.decide(true, false, false));
        assertEquals(ControlInput.TOGGLE, ControlInput.decide(true, false, true));
    }

    @Test
    void goldIngotOnAHurtGolemHeals() {
        assertEquals(ControlInput.HEAL, ControlInput.decide(false, true, true));
    }

    @Test
    void goldIngotAtFullHealthOpensTheMenu() {
        assertEquals(ControlInput.OPEN, ControlInput.decide(false, true, false));
    }

    @Test
    void sneakingWithGoldIngotOnAHurtGolemStillHeals() {
        assertEquals(ControlInput.HEAL, ControlInput.decide(true, true, true));
    }

    @Test
    void sneakingWithGoldIngotAtFullHealthToggles() {
        assertEquals(ControlInput.TOGGLE, ControlInput.decide(true, true, false));
    }
}
