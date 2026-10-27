package dev.angelocordon.goldengolem.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.angelocordon.goldengolem.util.BlockPos;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.Test;

class ChestHalvesTest {

    private static final BlockPos POS = new BlockPos(10, 64, -5);

    @Test
    void singleChestHasNoPartner() {
        assertNull(ChestHalves.partner(POS, BlockFace.NORTH, Chest.Type.SINGLE));
    }

    @Test
    void leftHalfPartnerIsClockwiseOfFacing() {
        assertEquals(POS.offset(1, 0, 0), ChestHalves.partner(POS, BlockFace.NORTH, Chest.Type.LEFT));
        assertEquals(POS.offset(0, 0, 1), ChestHalves.partner(POS, BlockFace.EAST, Chest.Type.LEFT));
        assertEquals(POS.offset(-1, 0, 0), ChestHalves.partner(POS, BlockFace.SOUTH, Chest.Type.LEFT));
        assertEquals(POS.offset(0, 0, -1), ChestHalves.partner(POS, BlockFace.WEST, Chest.Type.LEFT));
    }

    @Test
    void rightHalfPartnerIsCounterClockwiseOfFacing() {
        assertEquals(POS.offset(-1, 0, 0), ChestHalves.partner(POS, BlockFace.NORTH, Chest.Type.RIGHT));
        assertEquals(POS.offset(0, 0, -1), ChestHalves.partner(POS, BlockFace.EAST, Chest.Type.RIGHT));
        assertEquals(POS.offset(1, 0, 0), ChestHalves.partner(POS, BlockFace.SOUTH, Chest.Type.RIGHT));
        assertEquals(POS.offset(0, 0, 1), ChestHalves.partner(POS, BlockFace.WEST, Chest.Type.RIGHT));
    }

    @Test
    void halvesPointAtEachOther() {
        for (BlockFace facing : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            BlockPos right = ChestHalves.partner(POS, facing, Chest.Type.LEFT);
            assertEquals(POS, ChestHalves.partner(right, facing, Chest.Type.RIGHT));
        }
    }

    @Test
    void bothHalvesShareTheLeftHalfAsCanonical() {
        for (BlockFace facing : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            BlockPos right = ChestHalves.partner(POS, facing, Chest.Type.LEFT);
            assertEquals(POS, ChestHalves.canonical(POS, facing, Chest.Type.LEFT));
            assertEquals(POS, ChestHalves.canonical(right, facing, Chest.Type.RIGHT));
        }
    }

    @Test
    void singleChestIsItsOwnCanonicalHalf() {
        assertEquals(POS, ChestHalves.canonical(POS, BlockFace.SOUTH, Chest.Type.SINGLE));
    }

    @Test
    void rejectsVerticalFacing() {
        assertThrows(IllegalArgumentException.class, () -> ChestHalves.partner(POS, BlockFace.UP, Chest.Type.LEFT));
    }
}
