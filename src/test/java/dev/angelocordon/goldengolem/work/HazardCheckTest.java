package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.angelocordon.goldengolem.util.BlockPos;
import dev.angelocordon.goldengolem.work.HazardCheck.BlockKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HazardCheckTest {

    private static final BlockPos TARGET = new BlockPos(0, 64, 0);
    /** Standing beside the target, at its level. */
    private static final BlockPos BESIDE = new BlockPos(1, 64, 0);
    /** Standing on top of the target. */
    private static final BlockPos ON_TOP = new BlockPos(0, 65, 0);

    /** A solid world with the listed exceptions. */
    private static final class FakeWorld implements HazardCheck.KindProbe {
        private final Map<BlockPos, BlockKind> blocks = new HashMap<>();

        FakeWorld set(int x, int y, int z, BlockKind kind) {
            blocks.put(new BlockPos(x, y, z), kind);
            return this;
        }

        /** Air in the column under the target, {@code depth} blocks deep. */
        FakeWorld airBelowTarget(int depth) {
            for (int i = 1; i <= depth; i++) {
                set(TARGET.x(), TARGET.y() - i, TARGET.z(), BlockKind.AIR);
            }
            return this;
        }

        @Override
        public BlockKind kindAt(int x, int y, int z) {
            return blocks.getOrDefault(new BlockPos(x, y, z), BlockKind.SOLID);
        }
    }

    @Test
    void solidSurroundingsAreSafe() {
        assertTrue(HazardCheck.isSafe(TARGET, BESIDE, new FakeWorld()));
        assertTrue(HazardCheck.isSafe(TARGET, ON_TOP, new FakeWorld()));
    }

    @Test
    void unsafeWhenAnyNeighbourIsLava() {
        int[][] neighbours = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] n : neighbours) {
            FakeWorld world = new FakeWorld().set(n[0], 64 + n[1], n[2], BlockKind.LAVA);
            assertFalse(HazardCheck.isSafe(TARGET, new BlockPos(5, 64, 5), world),
                    "lava at offset " + n[0] + "," + n[1] + "," + n[2]);
        }
    }

    @Test
    void lavaDiagonallyAdjacentIsNotANeighbour() {
        FakeWorld world = new FakeWorld().set(1, 65, 0, BlockKind.LAVA);
        assertTrue(HazardCheck.isSafe(TARGET, new BlockPos(-1, 64, 0), world));
    }

    @Test
    void dropOfThreeUnderTheGolemIsSafe() {
        // The target's own space plus two air below: a 3-block fall, no damage.
        assertTrue(HazardCheck.isSafe(TARGET, ON_TOP, new FakeWorld().airBelowTarget(2)));
    }

    @Test
    void dropOfMoreThanThreeUnderTheGolemIsUnsafe() {
        assertFalse(HazardCheck.isSafe(TARGET, ON_TOP, new FakeWorld().airBelowTarget(3)));
        assertFalse(HazardCheck.isSafe(TARGET, ON_TOP, new FakeWorld().airBelowTarget(40)));
    }

    @Test
    void deepDropBesideTheGolemIsSafe() {
        // The golem stands next to the hole, not over it.
        assertTrue(HazardCheck.isSafe(TARGET, BESIDE, new FakeWorld().airBelowTarget(10)));
        assertTrue(HazardCheck.isSafe(TARGET, new BlockPos(1, 65, 0), new FakeWorld().airBelowTarget(10)));
    }

    @Test
    void waterBreaksTheFall() {
        FakeWorld world = new FakeWorld().airBelowTarget(2).set(0, 61, 0, BlockKind.WATER);
        assertTrue(HazardCheck.isSafe(TARGET, ON_TOP, world));
    }

    @Test
    void fallingIntoLavaIsUnsafe() {
        FakeWorld world = new FakeWorld().airBelowTarget(1).set(0, 62, 0, BlockKind.LAVA);
        assertFalse(HazardCheck.isSafe(TARGET, ON_TOP, world));
    }

    @Test
    void sandOrGravelAboveIsReported() {
        assertTrue(HazardCheck.hasFallingAbove(TARGET, new FakeWorld().set(0, 65, 0, BlockKind.FALLING)));
        assertFalse(HazardCheck.hasFallingAbove(TARGET, new FakeWorld()));
        assertFalse(HazardCheck.hasFallingAbove(TARGET, new FakeWorld().set(0, 63, 0, BlockKind.FALLING)));
    }

    @Test
    void stepsBackAwayFromTheTargetFirst() {
        List<BlockPos> spots = HazardCheck.stepBackSpots(TARGET, BESIDE);
        assertEquals(new BlockPos(2, 64, 0), spots.get(0));
        assertEquals(new BlockPos(0, 64, 0), spots.get(3));
        assertEquals(4, spots.stream().distinct().count());
    }

    @Test
    void stepsBackAlongTheLongerAxis() {
        List<BlockPos> spots = HazardCheck.stepBackSpots(TARGET, new BlockPos(1, 63, -2));
        assertEquals(new BlockPos(1, 63, -3), spots.get(0));
    }

    @Test
    void stepsBackSomewhereWhenTheTargetIsOverhead() {
        List<BlockPos> spots = HazardCheck.stepBackSpots(TARGET, new BlockPos(0, 63, 0));
        assertEquals(4, spots.stream().distinct().count());
        for (BlockPos spot : spots) {
            assertEquals(1, spot.distanceSquared(new BlockPos(0, 63, 0)));
        }
    }
}
