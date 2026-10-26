package dev.angelocordon.goldengolem.work;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Which tools can gather which blocks. The golem never mines by hand.
 *
 * <p>{@link #toolFits} uses explicit name rules and sets instead of {@code Tag.*}, which needs a
 * running server, so it stays fully unit-testable:
 *
 * <ul>
 *   <li><b>Axe</b>: names ending in {@code _LOG}, {@code _WOOD}, {@code _HYPHAE} or {@code _STEM}
 *       (stripped variants included), plus bamboo blocks. Plant stems that aren't wood (melon,
 *       pumpkin, big dripleaf, mushroom) are excluded.
 *   <li><b>Pickaxe</b>: names ending in {@code _ORE} (overworld, deepslate and nether ores), plus
 *       a fixed set of natural stone-like blocks ({@link #PICKAXE_BLOCKS}).
 *   <li><b>Shovel</b>: a fixed set of soft ground blocks ({@link #SHOVEL_BLOCKS}).
 * </ul>
 *
 * <p>{@code LEGACY_*} materials never fit.
 */
public final class HarvestRule {

    /** The tool types a golem can use. */
    public enum ToolKind {
        PICKAXE, AXE, SHOVEL;

        /** The kind of {@code tool}, or empty if it isn't a pickaxe, axe or shovel. */
        public static Optional<ToolKind> of(@Nullable Material tool) {
            if (tool == null) {
                return Optional.empty();
            }
            String name = tool.name();
            if (name.endsWith("_PICKAXE")) {
                return Optional.of(PICKAXE);
            }
            if (name.endsWith("_AXE")) {
                return Optional.of(AXE);
            }
            if (name.endsWith("_SHOVEL")) {
                return Optional.of(SHOVEL);
            }
            return Optional.empty();
        }
    }

    /** Natural stone-like blocks a pickaxe is for, besides ores. */
    static final Set<Material> PICKAXE_BLOCKS = EnumSet.of(
            Material.STONE,
            Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE,
            Material.DEEPSLATE,
            Material.COBBLED_DEEPSLATE,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.TUFF,
            Material.CALCITE,
            Material.DRIPSTONE_BLOCK,
            Material.BLACKSTONE,
            Material.GILDED_BLACKSTONE,
            Material.BASALT,
            Material.SMOOTH_BASALT,
            Material.NETHERRACK,
            Material.MAGMA_BLOCK,
            Material.SANDSTONE,
            Material.RED_SANDSTONE,
            Material.END_STONE,
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN,
            Material.ANCIENT_DEBRIS);

    /** Soft ground blocks a shovel is for. */
    static final Set<Material> SHOVEL_BLOCKS = EnumSet.of(
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.MYCELIUM,
            Material.SAND,
            Material.RED_SAND,
            Material.GRAVEL,
            Material.CLAY,
            Material.MUD,
            Material.SOUL_SAND,
            Material.SOUL_SOIL,
            Material.SNOW_BLOCK);

    /** Blocks named {@code *_STEM} that are plants, not wood. */
    private static final Set<Material> NON_WOOD_STEMS = EnumSet.of(
            Material.MELON_STEM,
            Material.PUMPKIN_STEM,
            Material.ATTACHED_MELON_STEM,
            Material.ATTACHED_PUMPKIN_STEM,
            Material.BIG_DRIPLEAF_STEM,
            Material.MUSHROOM_STEM);

    private HarvestRule() {
    }

    /**
     * True if {@code target} is a block this kind of tool is for (logs with an axe, stone with a
     * pickaxe, dirt/sand/gravel with a shovel). Used to validate the menu's target slot. See the
     * class doc for the exact rules.
     */
    public static boolean toolFits(Material tool, Material target) {
        Optional<ToolKind> kind = ToolKind.of(tool);
        if (kind.isEmpty() || target.name().startsWith("LEGACY_")) {
            return false;
        }
        return switch (kind.get()) {
            case AXE -> isWood(target);
            case PICKAXE -> isStoneOrOre(target);
            case SHOVEL -> SHOVEL_BLOCKS.contains(target);
        };
    }

    /** True if breaking {@code block} with {@code tool} would drop something. Main thread only. */
    public static boolean canHarvest(Block block, @Nullable ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            return false;
        }
        return block.isPreferredTool(tool) && !block.getDrops(tool).isEmpty();
    }

    private static boolean isWood(Material target) {
        String name = target.name();
        if (name.endsWith("_STEM")) {
            return !NON_WOOD_STEMS.contains(target);
        }
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_HYPHAE")
                || target == Material.BAMBOO_BLOCK
                || target == Material.STRIPPED_BAMBOO_BLOCK;
    }

    private static boolean isStoneOrOre(Material target) {
        return target.name().endsWith("_ORE") || PICKAXE_BLOCKS.contains(target);
    }
}
