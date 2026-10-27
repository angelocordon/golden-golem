package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.GolemConfig;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.work.Durability;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a blocked golem's problem is fixed and where it goes next. Pure so every
 * reason's rule is unit-tested; {@link BlockedHandler} gathers the {@link Facts} from the world.
 */
public final class BlockedRecheck {

    /**
     * What the recheck knows about the golem.
     *
     * @param hasUsableTool  the tool slot holds a tool that isn't worn out ({@link #usableTool})
     * @param toolFitsTarget there is a target, and the tool is the right kind and tier for it
     * @param hasChest       a chest is linked, loaded, and is a golden chest
     * @param chestHasRoom   that chest can take at least some of the golem's storage
     * @param hasWorkCenter  the golem has been told to Stay somewhere
     */
    public record Facts(boolean hasUsableTool, boolean toolFitsTarget, boolean hasChest, boolean chestHasRoom,
            boolean hasWorkCenter) {
    }

    private BlockedRecheck() {
    }

    /**
     * True if the tool slot holds a tool that can still work. A worn-out tool counts as no tool:
     * WORKING would just send it straight back to the chest for a replacement that isn't there,
     * then block NO_TOOL again, forever. Uses {@link Durability#isLow(ItemStack, GolemConfig)}, the
     * same reading as WORKING and DEPOSITING, so the states can't disagree.
     */
    public static boolean usableTool(@Nullable ItemStack tool, GolemConfig config) {
        return tool != null && !tool.isEmpty() && !Durability.isLow(tool, config);
    }

    /** The state a golem blocked for {@code reason} should move to, or BLOCKED if it's still stuck. */
    public static GolemState decide(@Nullable BlockedReason reason, Facts facts) {
        if (reason == null) {
            return GolemState.BLOCKED;
        }
        return switch (reason) {
            case CHEST_FULL -> facts.hasChest() && facts.chestHasRoom() ? GolemState.RETURNING : GolemState.BLOCKED;
            case NO_TOOL, CANT_HARVEST -> facts.hasUsableTool() && facts.toolFitsTarget()
                    ? workOrIdle(facts)
                    : GolemState.BLOCKED;
            case CHEST_MISSING -> facts.hasChest() ? GolemState.RETURNING : GolemState.BLOCKED;
            // Only a new Stay (a transition from the menu) can bring the work area back in range.
            case TOO_FAR -> GolemState.BLOCKED;
            // Paths change as blocks are placed and broken, so just try again.
            case UNREACHABLE -> workOrIdle(facts);
        };
    }

    /**
     * The reason a still-blocked golem should show now. A tool problem can change while blocked
     * (a tool that doesn't fit is put in, or the tool is taken out), so it's recomputed from the
     * facts to keep the message accurate. Other reasons are returned unchanged.
     */
    public static @Nullable BlockedReason currentReason(@Nullable BlockedReason reason, Facts facts) {
        if (reason != BlockedReason.NO_TOOL && reason != BlockedReason.CANT_HARVEST) {
            return reason;
        }
        if (!facts.hasUsableTool()) {
            return BlockedReason.NO_TOOL;
        }
        return facts.toolFitsTarget() ? reason : BlockedReason.CANT_HARVEST;
    }

    /** Every path into WORKING needs a work center; without one there's nothing to work on. */
    private static GolemState workOrIdle(Facts facts) {
        return facts.hasWorkCenter() ? GolemState.WORKING : GolemState.IDLE;
    }
}
