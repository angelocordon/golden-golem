package dev.angelocordon.goldengolem.menu;

import dev.angelocordon.goldengolem.golem.GolemState;

/** What a golem does when told to Stay. Pure, so the rule is unit-tested. */
public enum StayDecision {
    /** Has a tool and a target it fits: start working around the new work center. */
    WORK,
    /** Missing a tool, a target, or they don't fit: stand by at the new work center. */
    IDLE,
    /** Its linked chest is out of range (or in another world): refuse and block with TOO_FAR. */
    TOO_FAR;

    /**
     * @param distanceToChest blocks between the work center and the linked chest; pass
     *     {@link Double#POSITIVE_INFINITY} when the chest is in another world. Ignored without a
     *     chest.
     */
    public static StayDecision decide(boolean hasTool, boolean toolFitsTarget, boolean hasChest,
            double distanceToChest, int maxDistance) {
        // Written as !(<=) so NaN counts as too far rather than slipping through.
        if (hasChest && !(distanceToChest <= maxDistance)) {
            return TOO_FAR;
        }
        return hasTool && toolFitsTarget ? WORK : IDLE;
    }

    /**
     * True if a menu edit should re-run the Stay decision: the golem was told to Stay (IDLE with
     * a work center) and its tool or target just changed, so giving it a tool starts work
     * without clicking Stay again.
     */
    public static boolean shouldRecheck(GolemState state, boolean hasWorkCenter, boolean equipmentChanged) {
        return state == GolemState.IDLE && hasWorkCenter && equipmentChanged;
    }
}
