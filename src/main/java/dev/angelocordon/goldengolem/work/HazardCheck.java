package dev.angelocordon.goldengolem.work;

import dev.angelocordon.goldengolem.util.BlockPos;

/**
 * Whether breaking a block is safe for the golem. Pure, over a {@link KindProbe} that classifies
 * blocks, so the rules are unit-tested with a fake world.
 *
 * <ul>
 *   <li>Lava next to the target (any of its 6 neighbours) would flow into the hole: unsafe.
 *   <li>A target directly under the golem's feet drops it into the hole; a drop of more than
 *       {@link #MAX_SAFE_DROP} blocks (where vanilla fall damage starts), or onto lava, is unsafe.
 *       Water below breaks the fall. Targets beside the golem never drop it.
 *   <li>A target in the golem's own column, above its feet, with sand or gravel on top would let
 *       that block fall through the hole onto the golem and bury it: unsafe. The golem can't
 *       step aside in time (its mover treats 1-block moves as already there), so it never digs it.
 * </ul>
 */
public final class HazardCheck {

    /** What a block is, as far as hazards go. */
    public enum BlockKind {
        /** Nothing to stand on: air, plants, torches. */
        AIR,
        SOLID,
        LAVA,
        WATER,
        /** Sand, gravel and other blocks that fall when unsupported. Solid to stand on. */
        FALLING
    }

    /** Classifies the block at a position. Must never load a chunk. */
    @FunctionalInterface
    public interface KindProbe {
        BlockKind kindAt(int x, int y, int z);
    }

    /** The longest fall, in blocks, that does no damage. */
    public static final int MAX_SAFE_DROP = 3;

    private static final int[][] NEIGHBOURS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private HazardCheck() {
    }

    /** True if the golem standing with its feet in {@code feet} may break {@code target}. */
    public static boolean isSafe(BlockPos target, BlockPos feet, KindProbe probe) {
        return !lavaNextTo(target, probe)
                && !dropsTooFar(target, feet, probe)
                && !buriesGolem(target, feet, probe);
    }

    /**
     * True if the target is above the golem's feet in the same column and a falling block sits on
     * it: once the target is gone that block falls down the column into the golem's own space.
     */
    static boolean buriesGolem(BlockPos target, BlockPos feet, KindProbe probe) {
        return target.x() == feet.x() && target.z() == feet.z() && target.y() > feet.y()
                && hasFallingAbove(target, probe);
    }

    /** True if any of the target's 6 neighbours is lava. */
    public static boolean lavaNextTo(BlockPos target, KindProbe probe) {
        for (int[] n : NEIGHBOURS) {
            if (probe.kindAt(target.x() + n[0], target.y() + n[1], target.z() + n[2]) == BlockKind.LAVA) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the target is the block the golem stands on and removing it drops the golem more
     * than {@link #MAX_SAFE_DROP} blocks, or into lava.
     */
    static boolean dropsTooFar(BlockPos target, BlockPos feet, KindProbe probe) {
        if (!target.equals(feet.offset(0, -1, 0))) {
            return false;
        }
        // The target's own space is the first block of the fall.
        int drop = 1;
        for (int y = target.y() - 1; drop <= MAX_SAFE_DROP; y--) {
            switch (probe.kindAt(target.x(), y, target.z())) {
                case AIR -> drop++;
                case LAVA -> {
                    return true;
                }
                case SOLID, FALLING, WATER -> {
                    return false;
                }
            }
        }
        return true;
    }

    /** True if the block above the target will fall into the hole once the target is gone. */
    public static boolean hasFallingAbove(BlockPos target, KindProbe probe) {
        return probe.kindAt(target.x(), target.y() + 1, target.z()) == BlockKind.FALLING;
    }
}
