package dev.angelocordon.goldengolem.work;

import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Which tools can gather which blocks. The golem never mines by hand.
 *
 * <p>TODO(LAB-18): implement {@link #toolFits} and {@link #canHarvest}. Stubs say no.
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

    private HarvestRule() {
    }

    /**
     * True if {@code target} is a block this kind of tool is for (logs with an axe, stone with a
     * pickaxe, dirt/sand/gravel with a shovel). Used to validate the menu's target slot.
     */
    public static boolean toolFits(Material tool, Material target) {
        return false;
    }

    /** True if breaking {@code block} with {@code tool} would drop something. Main thread only. */
    public static boolean canHarvest(Block block, @Nullable ItemStack tool) {
        return false;
    }
}
