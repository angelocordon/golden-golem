package dev.angelocordon.goldengolem.golem.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.angelocordon.goldengolem.GolemConfig;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.state.BlockedRecheck.Facts;
import dev.angelocordon.goldengolem.work.Durability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BlockedRecheckTest {

    /** Everything fine: usable fitting tool, golden chest with room, work center. */
    private static final Facts ALL_GOOD = new Facts(true, true, true, true, true);

    private static Facts facts(boolean usableTool, boolean fits, boolean chest, boolean room, boolean workCenter) {
        return new Facts(usableTool, fits, chest, room, workCenter);
    }

    @Test
    void chestFullReturnsOnceChestHasRoom() {
        assertEquals(GolemState.RETURNING, BlockedRecheck.decide(BlockedReason.CHEST_FULL, ALL_GOOD));
    }

    @Test
    void chestFullStaysBlockedWhileChestIsFull() {
        assertEquals(GolemState.BLOCKED,
                BlockedRecheck.decide(BlockedReason.CHEST_FULL, facts(true, true, true, false, true)));
    }

    @Test
    void chestFullStaysBlockedWhenChestIsGone() {
        assertEquals(GolemState.BLOCKED,
                BlockedRecheck.decide(BlockedReason.CHEST_FULL, facts(true, true, false, false, true)));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"NO_TOOL", "CANT_HARVEST"})
    void toolProblemResumesWorkingWhenFittingToolAndWorkCenter(BlockedReason reason) {
        assertEquals(GolemState.WORKING, BlockedRecheck.decide(reason, facts(true, true, false, false, true)));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"NO_TOOL", "CANT_HARVEST"})
    void toolProblemGoesIdleWhenFittingToolButNoWorkCenter(BlockedReason reason) {
        assertEquals(GolemState.IDLE, BlockedRecheck.decide(reason, facts(true, true, true, true, false)));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"NO_TOOL", "CANT_HARVEST"})
    void toolProblemStaysBlockedWithoutUsableTool(BlockedReason reason) {
        assertEquals(GolemState.BLOCKED, BlockedRecheck.decide(reason, facts(false, true, true, true, true)));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"NO_TOOL", "CANT_HARVEST"})
    void toolProblemStaysBlockedWhenToolDoesNotFit(BlockedReason reason) {
        assertEquals(GolemState.BLOCKED, BlockedRecheck.decide(reason, facts(true, false, true, true, true)));
    }

    /**
     * The loop from review: a worn-out tool with no replacement in the chest. If the recheck sent
     * it to WORKING, WORKING would see the low tool and go RETURNING, the deposit would find no
     * replacement and block NO_TOOL again, forever. A low tool must keep it blocked.
     */
    @Test
    void wornOutToolWithNoReplacementStaysBlockedNoTool() {
        int maxDamage = 1561;
        int damage = 1559; // 2 uses left: low by the default 3-use threshold
        boolean usable = !Durability.isLow(maxDamage, damage, false, GolemConfig.DEFAULTS);
        Facts wornTool = facts(usable, true, true, true, true);

        assertFalse(usable);
        assertEquals(GolemState.BLOCKED, BlockedRecheck.decide(BlockedReason.NO_TOOL, wornTool));
        assertEquals(BlockedReason.NO_TOOL, BlockedRecheck.currentReason(BlockedReason.NO_TOOL, wornTool));
    }

    @Test
    void missingToolIsNotUsable() {
        assertFalse(BlockedRecheck.usableTool(null, GolemConfig.DEFAULTS));
    }

    @Test
    void chestMissingReturnsOnceGoldenChestIsLinked() {
        assertEquals(GolemState.RETURNING,
                BlockedRecheck.decide(BlockedReason.CHEST_MISSING, facts(false, false, true, false, false)));
    }

    @Test
    void chestMissingStaysBlockedWithoutChest() {
        assertEquals(GolemState.BLOCKED,
                BlockedRecheck.decide(BlockedReason.CHEST_MISSING, facts(true, true, false, false, true)));
    }

    @Test
    void tooFarAlwaysStaysBlocked() {
        assertEquals(GolemState.BLOCKED, BlockedRecheck.decide(BlockedReason.TOO_FAR, ALL_GOOD));
    }

    @Test
    void unreachableRetriesWorkingWithWorkCenter() {
        assertEquals(GolemState.WORKING,
                BlockedRecheck.decide(BlockedReason.UNREACHABLE, facts(false, false, false, false, true)));
    }

    @Test
    void unreachableGoesIdleWithoutWorkCenter() {
        assertEquals(GolemState.IDLE,
                BlockedRecheck.decide(BlockedReason.UNREACHABLE, facts(true, true, true, true, false)));
    }

    @Test
    void missingReasonStaysBlocked() {
        assertEquals(GolemState.BLOCKED, BlockedRecheck.decide(null, ALL_GOOD));
    }

    @Test
    void noToolBecomesCantHarvestWhenToolDoesNotFit() {
        assertEquals(BlockedReason.CANT_HARVEST,
                BlockedRecheck.currentReason(BlockedReason.NO_TOOL, facts(true, false, true, true, true)));
    }

    @Test
    void cantHarvestBecomesNoToolWhenToolIsRemoved() {
        assertEquals(BlockedReason.NO_TOOL,
                BlockedRecheck.currentReason(BlockedReason.CANT_HARVEST, facts(false, false, true, true, true)));
    }

    @Test
    void cantHarvestStaysWhenToolStillDoesNotFit() {
        assertEquals(BlockedReason.CANT_HARVEST,
                BlockedRecheck.currentReason(BlockedReason.CANT_HARVEST, facts(true, false, true, true, true)));
    }

    @ParameterizedTest
    @EnumSource(value = BlockedReason.class, names = {"CHEST_FULL", "CHEST_MISSING", "TOO_FAR", "UNREACHABLE"})
    void otherReasonsAreNotRecomputed(BlockedReason reason) {
        assertEquals(reason, BlockedRecheck.currentReason(reason, facts(false, false, false, false, false)));
    }

    @Test
    void missingReasonIsNotRecomputed() {
        assertNull(BlockedRecheck.currentReason(null, ALL_GOOD));
    }
}
