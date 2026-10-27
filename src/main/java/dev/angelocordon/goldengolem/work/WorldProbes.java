package dev.angelocordon.goldengolem.work;

import dev.angelocordon.goldengolem.util.BlockPos;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

/**
 * Reads the world for the working golem without ever loading a chunk: every lookup checks
 * {@code isChunkLoaded} and the world height first, as the {@link BlockProbe} doc requires.
 * Main thread only.
 */
public final class WorldProbes {

    private WorldProbes() {
    }

    /**
     * The scanner probe: loaded, in height, of the target type, and harvestable with
     * {@code tool}. The type check comes first because it is cheap; {@link HarvestRule#canHarvest}
     * rolls the loot table and only runs on blocks of the target type.
     */
    public static BlockProbe targets(World world, Material target, ItemStack tool) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        return (x, y, z) -> y >= minY && y < maxY
                && world.isChunkLoaded(x >> 4, z >> 4)
                && world.getType(x, y, z) == target
                && HarvestRule.canHarvest(world.getBlockAt(x, y, z), tool);
    }

    /**
     * Classifies blocks for {@link HazardCheck}. Outside the world height is air (the void is a
     * long drop); an unloaded chunk counts as solid, which only matters at the edge of what the
     * server simulates, never next to a ticking golem.
     */
    public static HazardCheck.KindProbe kinds(World world) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        return (x, y, z) -> {
            if (y < minY || y >= maxY) {
                return HazardCheck.BlockKind.AIR;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return HazardCheck.BlockKind.SOLID;
            }
            return kindOf(world.getType(x, y, z));
        };
    }

    static HazardCheck.BlockKind kindOf(Material type) {
        if (type == Material.LAVA) {
            return HazardCheck.BlockKind.LAVA;
        }
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN) {
            return HazardCheck.BlockKind.WATER;
        }
        if (type.hasGravity()) {
            return HazardCheck.BlockKind.FALLING;
        }
        return type.isSolid() ? HazardCheck.BlockKind.SOLID : HazardCheck.BlockKind.AIR;
    }

    /** True if the block at {@code pos} is loaded and still of type {@code target}. */
    public static boolean isType(World world, BlockPos pos, Material target) {
        return pos.y() >= world.getMinHeight() && pos.y() < world.getMaxHeight()
                && world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)
                && world.getType(pos.x(), pos.y(), pos.z()) == target;
    }

    /** True if a golem can stand with its feet in {@code feet}: room there and solid ground below. */
    public static boolean isStandable(World world, BlockPos feet) {
        HazardCheck.KindProbe kinds = kinds(world);
        HazardCheck.BlockKind floor = kinds.kindAt(feet.x(), feet.y() - 1, feet.z());
        return kinds.kindAt(feet.x(), feet.y(), feet.z()) == HazardCheck.BlockKind.AIR
                && (floor == HazardCheck.BlockKind.SOLID || floor == HazardCheck.BlockKind.FALLING);
    }
}
