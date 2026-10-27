package dev.angelocordon.goldengolem.golem.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.angelocordon.goldengolem.golem.BlockedReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockedAnnouncementsTest {

    private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final BlockedAnnouncements announcements = new BlockedAnnouncements(100);

    @Test
    void firstBlockIsAnnounced() {
        assertTrue(announcements.shouldAnnounce(GOLEM, BlockedReason.NO_TOOL, 0));
    }

    @Test
    void sameReasonAgainSoonIsSilent() {
        announcements.shouldAnnounce(GOLEM, BlockedReason.UNREACHABLE, 0);
        assertFalse(announcements.shouldAnnounce(GOLEM, BlockedReason.UNREACHABLE, 60));
        assertFalse(announcements.shouldAnnounce(GOLEM, BlockedReason.UNREACHABLE, 99));
    }

    @Test
    void differentReasonIsAnnounced() {
        announcements.shouldAnnounce(GOLEM, BlockedReason.NO_TOOL, 0);
        assertTrue(announcements.shouldAnnounce(GOLEM, BlockedReason.CHEST_FULL, 10));
        assertTrue(announcements.shouldAnnounce(GOLEM, BlockedReason.NO_TOOL, 20));
    }

    @Test
    void sameReasonIsAnnouncedAgainAfterTheWindow() {
        announcements.shouldAnnounce(GOLEM, BlockedReason.CHEST_FULL, 0);
        assertTrue(announcements.shouldAnnounce(GOLEM, BlockedReason.CHEST_FULL, 100));
    }

    @Test
    void golemsAreTrackedSeparately() {
        announcements.shouldAnnounce(GOLEM, BlockedReason.NO_TOOL, 0);
        assertTrue(announcements.shouldAnnounce(OTHER, BlockedReason.NO_TOOL, 1));
    }

    @Test
    void expiredEntriesArePruned() {
        announcements.shouldAnnounce(GOLEM, BlockedReason.NO_TOOL, 0);
        announcements.shouldAnnounce(OTHER, BlockedReason.NO_TOOL, 150);
        assertEquals(1, announcements.size());
    }
}
