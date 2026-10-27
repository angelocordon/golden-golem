package dev.angelocordon.goldengolem.link;

import dev.angelocordon.goldengolem.util.BlockPos;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.jspecify.annotations.Nullable;

/**
 * Double-chest geometry from plain values (position, facing, chest type), so a double chest can
 * be treated as one chest. Pure, so it is unit-tested.
 *
 * <p>The LEFT half (or a single chest itself) is the canonical half: new links are stored against
 * it, and links to either half count against the same chest.
 */
public final class ChestHalves {

    private ChestHalves() {
    }

    /**
     * Where the other half of a double chest is, or null for a single chest. Mirrors vanilla: a
     * LEFT half's partner is clockwise of its facing, a RIGHT half's is counter-clockwise.
     * Keep in sync with {@code GoldenChests.partnerFace}, which applies the same rule.
     */
    public static @Nullable BlockPos partner(BlockPos pos, BlockFace facing, Chest.Type type) {
        BlockFace side = switch (type) {
            case SINGLE -> null;
            case LEFT -> clockwise(facing);
            case RIGHT -> clockwise(clockwise(clockwise(facing)));
        };
        return side == null ? null : pos.offset(side.getModX(), 0, side.getModZ());
    }

    /** The canonical half: the partner for a RIGHT half, otherwise {@code pos} itself. */
    public static BlockPos canonical(BlockPos pos, BlockFace facing, Chest.Type type) {
        if (type == Chest.Type.RIGHT) {
            BlockPos partner = partner(pos, facing, type);
            if (partner != null) {
                return partner;
            }
        }
        return pos;
    }

    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("Chests only face horizontally: " + face);
        };
    }
}
