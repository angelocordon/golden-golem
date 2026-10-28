package dev.angelocordon.goldengolem.golem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowTargetTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID LEADER = UUID.randomUUID();

    @Test
    void followsTheLeaderWhenOneIsSet() {
        assertEquals(LEADER, FollowTarget.choose(LEADER, OWNER));
    }

    @Test
    void fallsBackToTheOwnerForGolemsSavedWithoutALeader() {
        assertEquals(OWNER, FollowTarget.choose(null, OWNER));
    }

    @Test
    void hasNoTargetWithoutLeaderOrOwner() {
        assertNull(FollowTarget.choose(null, null));
    }

    @Test
    void leaderOutranksOwnerForRelinking() {
        assertTrue(FollowTarget.isLedBy(LEADER, OWNER, LEADER));
        assertFalse(FollowTarget.isLedBy(LEADER, OWNER, OWNER));
    }

    @Test
    void ownerLeadsAnOldGolemWithNoLeader() {
        assertTrue(FollowTarget.isLedBy(null, OWNER, OWNER));
        assertFalse(FollowTarget.isLedBy(null, OWNER, LEADER));
    }

    @Test
    void nobodyLeadsAGolemWithNeitherLeaderNorOwner() {
        assertFalse(FollowTarget.isLedBy(null, null, LEADER));
    }
}
