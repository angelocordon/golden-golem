package dev.angelocordon.goldengolem.golem.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GolemState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BlockedRecheckTest {

    @Test
    void chestFullReturnsOnceChestHasRoom() {
        assertEquals(GolemState.RETURNING,
                BlockedRecheck.decide(BlockedReason.CHEST_FULL, true, true, true, true, true));
    }

    @Test
    void chestFullStaysBlockedWhileChestIsFull() {
        assertEquals(GolemState.BLOCKED,
                BlockedRecheck.decide(BlockedReason.CHEST_FULL, true, true, true, false, true));
    }

    @Test
    void chestFullStaysBlockedWhenChestIsGone() {
        assertEquals(GolemState.BLOCKED,
                BlockedRecheck.decide(BlockedReason.CHEST_FULL, true, true, false, false, true));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"NO_TOOL", "CANT_HARVEST"})
    void toolProblemResumesWorkingWhenFittingToolAndWorkCenter(BlockedReason reason) {
        assertEquals(GolemState.WORKING, BlockedRecheck.decide(reason, true, true, false, false, true));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"NO_TOOL", "CANT_HARVEST"})
    void toolProblemGoesIdleWhenFittingToolButNoWorkCenter(BlockedReason reason) {
        assertEquals(GolemState.IDLE, BlockedRecheck.decide(reason, true, true, true, true, false));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"NO_TOOL", "CANT_HARVEST"})
    void toolProblemStaysBlockedWithoutTool(BlockedReason reason) {
        assertEquals(GolemState.BLOCKED, BlockedRecheck.decide(reason, false, false, true, true, true));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"NO_TOOL", "CANT_HARVEST"})
    void toolProblemStaysBlockedWhenToolDoesNotFit(BlockedReason reason) {
        assertEquals(GolemState.BLOCKED, BlockedRecheck.decide(reason, true, false, true, true, true));
    }

    @Test
    void chestMissingReturnsOnceGoldenChestIsLinked() {
        assertEquals(GolemState.RETURNING,
                BlockedRecheck.decide(BlockedReason.CHEST_MISSING, false, false, true, false, false));
    }

    @Test
    void chestMissingStaysBlockedWithoutChest() {
        assertEquals(GolemState.BLOCKED,
                BlockedRecheck.decide(BlockedReason.CHEST_MISSING, true, true, false, false, true));
    }

    @Test
    void tooFarAlwaysStaysBlocked() {
        assertEquals(GolemState.BLOCKED,
                BlockedRecheck.decide(BlockedReason.TOO_FAR, true, true, true, true, true));
    }

    @Test
    void unreachableRetriesWorking() {
        assertEquals(GolemState.WORKING,
                BlockedRecheck.decide(BlockedReason.UNREACHABLE, false, false, false, false, false));
    }

    @Test
    void missingReasonStaysBlocked() {
        assertEquals(GolemState.BLOCKED, BlockedRecheck.decide(null, true, true, true, true, true));
    }
}
