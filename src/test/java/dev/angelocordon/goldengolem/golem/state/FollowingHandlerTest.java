package dev.angelocordon.goldengolem.golem.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.angelocordon.goldengolem.golem.state.FollowingHandler.Action;
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
}
