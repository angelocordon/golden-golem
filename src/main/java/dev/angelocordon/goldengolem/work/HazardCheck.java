package dev.angelocordon.goldengolem.work;

import dev.angelocordon.goldengolem.util.BlockPos;
import java.util.List;

/**
 * Whether breaking a block is safe for the golem. Pure, over a {@link KindProbe} that classifies
 * blocks, so the rules are unit-tested with a fake world.
 *
 * <ul>
 *   <li>Lava next to the target (any of its 6 neighbours) would flow into the hole: unsafe.
 *   <li>A target directly under the golem's feet drops it into the hole; a drop of more than
 *       {@link #MAX_SAFE_DROP} blocks (where vanilla fall damage starts), or onto lava, is unsafe.
 *       Water below breaks the fall. Targets beside or above the golem never drop it.
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
        return !lavaNextTo(target, probe) && !dropsTooFar(target, feet, probe);
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

    /**
     * Feet positions one block away from {@code feet}, best first, for stepping clear of blocks
     * about to fall: straight away from the target, then the two sideways spots, then toward it.
     * When the target is straight above or below, it starts with +x.
     */
    public static List<BlockPos> stepBackSpots(BlockPos target, BlockPos feet) {
        int offX = feet.x() - target.x();
        int offZ = feet.z() - target.z();
        int ax;
        int az;
        if (offX == 0 && offZ == 0) {
            ax = 1;
            az = 0;
        } else if (Math.abs(offX) >= Math.abs(offZ)) {
            ax = Integer.signum(offX);
            az = 0;
        } else {
            ax = 0;
            az = Integer.signum(offZ);
        }
        return List.of(
                feet.offset(ax, 0, az),
                feet.offset(-az, 0, ax),
                feet.offset(az, 0, -ax),
                feet.offset(-ax, 0, -az));
    }
}
