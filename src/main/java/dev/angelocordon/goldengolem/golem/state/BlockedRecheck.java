package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GolemState;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a blocked golem's problem is fixed and where it goes next. Pure so every
 * reason's rule is unit-tested; {@link BlockedHandler} gathers the facts from the world.
 */
public final class BlockedRecheck {

    private BlockedRecheck() {
    }

    /**
     * The state a golem blocked for {@code reason} should move to, or BLOCKED if it's still stuck.
     *
     * @param hasTool        the tool slot holds something
     * @param toolFitsTarget the tool is the right kind and tier for the target block
     * @param hasChest       a chest is linked, loaded, and is a golden chest
     * @param chestHasRoom   that chest has at least one empty slot
     * @param hasWorkCenter  the golem has been told to Stay somewhere
     */
    public static GolemState decide(@Nullable BlockedReason reason, boolean hasTool, boolean toolFitsTarget,
            boolean hasChest, boolean chestHasRoom, boolean hasWorkCenter) {
        if (reason == null) {
            return GolemState.BLOCKED;
        }
        return switch (reason) {
            case CHEST_FULL -> hasChest && chestHasRoom ? GolemState.RETURNING : GolemState.BLOCKED;
            case NO_TOOL, CANT_HARVEST -> {
                if (!hasTool || !toolFitsTarget) {
                    yield GolemState.BLOCKED;
                }
                yield hasWorkCenter ? GolemState.WORKING : GolemState.IDLE;
            }
            case CHEST_MISSING -> hasChest ? GolemState.RETURNING : GolemState.BLOCKED;
            // Only a new Stay (a transition from the menu) can bring the work area back in range.
            case TOO_FAR -> GolemState.BLOCKED;
            // Paths change as blocks are placed and broken, so just try again.
            case UNREACHABLE -> GolemState.WORKING;
        };
    }
}
