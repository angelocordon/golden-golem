package dev.angelocordon.goldengolem.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.Test;

class GoldenChestsTest {

    @Test
    void plainChestsHoldNoGolems() {
        assertEquals(0, GoldenChests.capacityFor(false, Chest.Type.SINGLE));
        assertEquals(0, GoldenChests.capacityFor(false, Chest.Type.LEFT));
        assertEquals(0, GoldenChests.capacityFor(false, Chest.Type.RIGHT));
    }

    @Test
    void singleGoldenChestHoldsOneGolem() {
        assertEquals(1, GoldenChests.capacityFor(true, Chest.Type.SINGLE));
    }

    @Test
    void eitherHalfOfDoubleGoldenChestHoldsTwoGolems() {
        assertEquals(2, GoldenChests.capacityFor(true, Chest.Type.LEFT));
        assertEquals(2, GoldenChests.capacityFor(true, Chest.Type.RIGHT));
    }

    @Test
    void mergesOnlyMatchingChests() {
        assertTrue(GoldenChests.mayMerge(true, true));
        assertTrue(GoldenChests.mayMerge(false, false));
        assertFalse(GoldenChests.mayMerge(true, false));
        assertFalse(GoldenChests.mayMerge(false, true));
    }

    @Test
    void keepsVanillaTypeWhenBothHalvesMatch() {
        assertEquals(Chest.Type.LEFT, GoldenChests.allowedPlacementType(Chest.Type.LEFT, true, true));
        assertEquals(Chest.Type.RIGHT, GoldenChests.allowedPlacementType(Chest.Type.RIGHT, false, false));
    }

    @Test
    void forcesSingleWhenGoldenWouldMergeWithPlain() {
        assertEquals(Chest.Type.SINGLE, GoldenChests.allowedPlacementType(Chest.Type.LEFT, true, false));
        assertEquals(Chest.Type.SINGLE, GoldenChests.allowedPlacementType(Chest.Type.RIGHT, false, true));
    }

    @Test
    void leavesSingleChestsAlone() {
        assertEquals(Chest.Type.SINGLE, GoldenChests.allowedPlacementType(Chest.Type.SINGLE, true, false));
        assertEquals(Chest.Type.SINGLE, GoldenChests.allowedPlacementType(Chest.Type.SINGLE, true, true));
    }

    @Test
    void singleChestHasNoPartner() {
        assertNull(GoldenChests.partnerFace(BlockFace.NORTH, Chest.Type.SINGLE));
    }

    @Test
    void leftHalfPartnerIsClockwiseOfFacing() {
        assertEquals(BlockFace.EAST, GoldenChests.partnerFace(BlockFace.NORTH, Chest.Type.LEFT));
        assertEquals(BlockFace.SOUTH, GoldenChests.partnerFace(BlockFace.EAST, Chest.Type.LEFT));
        assertEquals(BlockFace.WEST, GoldenChests.partnerFace(BlockFace.SOUTH, Chest.Type.LEFT));
        assertEquals(BlockFace.NORTH, GoldenChests.partnerFace(BlockFace.WEST, Chest.Type.LEFT));
    }

    @Test
    void rightHalfPartnerIsCounterClockwiseOfFacing() {
        assertEquals(BlockFace.WEST, GoldenChests.partnerFace(BlockFace.NORTH, Chest.Type.RIGHT));
        assertEquals(BlockFace.NORTH, GoldenChests.partnerFace(BlockFace.EAST, Chest.Type.RIGHT));
        assertEquals(BlockFace.EAST, GoldenChests.partnerFace(BlockFace.SOUTH, Chest.Type.RIGHT));
        assertEquals(BlockFace.SOUTH, GoldenChests.partnerFace(BlockFace.WEST, Chest.Type.RIGHT));
    }

    @Test
    void rejectsVerticalFacing() {
        assertThrows(IllegalArgumentException.class, () -> GoldenChests.partnerFace(BlockFace.UP, Chest.Type.LEFT));
    }
}
