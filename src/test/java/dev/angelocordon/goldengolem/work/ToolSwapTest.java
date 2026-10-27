package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.angelocordon.goldengolem.work.HarvestRule.ToolKind;
import dev.angelocordon.goldengolem.work.ToolSwap.Candidate;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ToolSwapTest {

    @Test
    void picksHigherTierFirst() {
        List<Candidate> candidates = List.of(
                new Candidate(0, Material.STONE_PICKAXE, 131, false),
                new Candidate(1, Material.DIAMOND_PICKAXE, 10, false),
                new Candidate(2, Material.IRON_PICKAXE, 250, false));

        assertEquals(1, ToolSwap.pick(candidates).orElseThrow().slot());
    }

    @Test
    void breaksTierTiesByUsesLeft() {
        List<Candidate> candidates = List.of(
                new Candidate(0, Material.IRON_PICKAXE, 100, false),
                new Candidate(1, Material.IRON_PICKAXE, 200, false));

        assertEquals(1, ToolSwap.pick(candidates).orElseThrow().slot());
    }

    @Test
    void equalCandidatesPickTheLowerSlot() {
        List<Candidate> candidates = List.of(
                new Candidate(5, Material.IRON_AXE, 100, false),
                new Candidate(2, Material.IRON_AXE, 100, false));

        assertEquals(2, ToolSwap.pick(candidates).orElseThrow().slot());
    }

    @Test
    void neverPicksALowTool() {
        List<Candidate> candidates = List.of(
                new Candidate(0, Material.NETHERITE_SHOVEL, 2, true),
                new Candidate(1, Material.WOODEN_SHOVEL, 50, false));

        assertEquals(1, ToolSwap.pick(candidates).orElseThrow().slot());
    }

    @Test
    void emptyWhenEveryCandidateIsLowOrNoneExist() {
        assertEquals(Optional.empty(), ToolSwap.pick(List.of()));
        assertEquals(Optional.empty(), ToolSwap.pick(List.of(new Candidate(0, Material.IRON_AXE, 1, true))));
    }

    @Test
    void unbreakableBeatsSameTierWithFewerUses() {
        List<Candidate> candidates = List.of(
                new Candidate(0, Material.IRON_PICKAXE, 250, false),
                new Candidate(1, Material.IRON_PICKAXE, Integer.MAX_VALUE, false));

        assertEquals(1, ToolSwap.pick(candidates).orElseThrow().slot());
    }

    @Test
    void tierTableMatchesDocs() {
        assertEquals(0, ToolSwap.tierRank(Material.WOODEN_PICKAXE));
        assertEquals(1, ToolSwap.tierRank(Material.STONE_AXE));
        assertEquals(1, ToolSwap.tierRank(Material.COPPER_PICKAXE));
        assertEquals(1, ToolSwap.tierRank(Material.GOLDEN_SHOVEL));
        assertEquals(2, ToolSwap.tierRank(Material.IRON_AXE));
        assertEquals(3, ToolSwap.tierRank(Material.DIAMOND_SHOVEL));
        assertEquals(4, ToolSwap.tierRank(Material.NETHERITE_PICKAXE));
        assertEquals(-1, ToolSwap.tierRank(Material.STICK));
    }

    @Test
    void replacementMustMatchTheWornKind() {
        assertTrue(ToolSwap.acceptable(Material.DIAMOND_PICKAXE, ToolKind.PICKAXE, Material.STONE));
        assertFalse(ToolSwap.acceptable(Material.DIAMOND_AXE, ToolKind.PICKAXE, Material.OAK_LOG));
    }

    @Test
    void withoutAWornToolAnyToolThatFitsTheTargetIsAcceptable() {
        assertTrue(ToolSwap.acceptable(Material.IRON_AXE, null, Material.OAK_LOG));
        assertFalse(ToolSwap.acceptable(Material.IRON_PICKAXE, null, Material.OAK_LOG));
        assertFalse(ToolSwap.acceptable(Material.IRON_AXE, null, null));
    }

    @Test
    void nonToolsAreNeverAcceptable() {
        assertFalse(ToolSwap.acceptable(Material.STICK, ToolKind.PICKAXE, Material.STONE));
        assertFalse(ToolSwap.acceptable(Material.IRON_SWORD, null, Material.STONE));
    }
}
