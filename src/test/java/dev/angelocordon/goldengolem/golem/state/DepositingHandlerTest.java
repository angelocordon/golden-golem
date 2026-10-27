package dev.angelocordon.goldengolem.golem.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.state.DepositingHandler.Outcome;
import dev.angelocordon.goldengolem.golem.state.DepositingHandler.SwapResult;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DepositingHandlerTest {

    @Test
    void returnsToWorkWithAFittingTool() {
        assertEquals(Outcome.to(GolemState.WORKING),
                DepositingHandler.next(true, SwapResult.KEPT, true, Material.STONE, Material.IRON_PICKAXE));
        assertEquals(Outcome.to(GolemState.WORKING),
                DepositingHandler.next(true, SwapResult.SWAPPED, true, Material.OAK_LOG, Material.STONE_AXE));
    }

    @Test
    void leftoversBlockWithChestFullBeforeAnythingElse() {
        assertEquals(Outcome.blocked(BlockedReason.CHEST_FULL),
                DepositingHandler.next(false, SwapResult.KEPT, false, null, null));
        assertEquals(Outcome.blocked(BlockedReason.CHEST_FULL),
                DepositingHandler.next(true, SwapResult.CHEST_FULL, true, Material.STONE, Material.IRON_PICKAXE));
    }

    @Test
    void idlesWithoutWorkCenterOrTarget() {
        assertEquals(Outcome.to(GolemState.IDLE),
                DepositingHandler.next(true, SwapResult.KEPT, false, Material.STONE, Material.IRON_PICKAXE));
        assertEquals(Outcome.to(GolemState.IDLE),
                DepositingHandler.next(true, SwapResult.NO_TOOL, true, null, null));
    }

    @Test
    void blocksWithNoToolWhenNothingAcceptableWasFound() {
        assertEquals(Outcome.blocked(BlockedReason.NO_TOOL),
                DepositingHandler.next(true, SwapResult.NO_TOOL, true, Material.STONE, Material.IRON_PICKAXE));
        assertEquals(Outcome.blocked(BlockedReason.NO_TOOL),
                DepositingHandler.next(true, SwapResult.NO_TOOL, true, Material.STONE, null));
    }

    @Test
    void blocksWhenTheToolDoesNotFitTheTarget() {
        assertEquals(Outcome.blocked(BlockedReason.CANT_HARVEST),
                DepositingHandler.next(true, SwapResult.KEPT, true, Material.OAK_LOG, Material.IRON_PICKAXE));
    }
}
