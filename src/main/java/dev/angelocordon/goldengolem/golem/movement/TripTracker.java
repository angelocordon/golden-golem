package dev.angelocordon.goldengolem.golem.movement;

/**
 * Decides, for one trip to one target, whether a path is worth walking, when the trip is done
 * and when to give up. Pure (plain distances in, a {@link Decision} out) so the loops a mover
 * can fall into are unit-tested.
 *
 * <p>Giving up matters: handlers turn a failed trip into the UNREACHABLE blocked reason, so every
 * way of never arriving must end in {@link Decision#UNREACHABLE} instead of searching forever.
 * Distances to the target are measured from path-end block centers.
 */
final class TripTracker {

    enum Decision {
        /** Walk the new path. */
        WALK,
        /** That leg is done but the target isn't reached: search again from here. */
        SEARCH,
        /** As close as a path can get: stop, the trip is over. */
        ARRIVED,
        /** No path gets closer: give up on this target. */
        UNREACHABLE
    }

    /** A new path must end at least this much closer to the target to count as progress. */
    static final double MIN_IMPROVEMENT = 0.5;
    /** Golem progress (closer to the target than ever) that forgives earlier stuck searches. */
    static final double PROGRESS_FOR_FORGIVENESS = 1.0;
    /** Stuck searches in a row that bring nothing better before giving up. */
    static final int MAX_STUCK_STRIKES = 2;

    private final double completeTolerance;
    private double best = Double.POSITIVE_INFINITY;
    private double closest = Double.POSITIVE_INFINITY;
    private boolean complete;
    private boolean stuckSearch;
    private int stuckStrikes;

    /**
     * @param completeTolerance how far from the target a path that "can reach" may end and still
     *     count as reaching it; farther ends are treated as partial paths
     */
    TripTracker(double completeTolerance) {
        this.completeTolerance = completeTolerance;
    }

    /** Forgets everything; call for a new target or a fresh retry. */
    void reset() {
        best = Double.POSITIVE_INFINITY;
        closest = Double.POSITIVE_INFINITY;
        complete = false;
        stuckSearch = false;
        stuckStrikes = 0;
    }

    /**
     * Judges a search result.
     *
     * @param found whether the pathfinder returned a non-empty path
     * @param canReach the pathfinder's own "can reach the final point"
     * @param endToTarget distance from the path end's block center to the target
     * @param hereToTarget distance from the golem to the target
     * @param endsWhereGolemStands whether the path's last node is the golem's current block
     */
    Decision onPath(boolean found, boolean canReach, double endToTarget, double hereToTarget,
            boolean endsWhereGolemStands) {
        boolean afterStuck = stuckSearch;
        stuckSearch = false;
        if (!found) {
            return Decision.UNREACHABLE;
        }
        boolean reaches = canReach && endToTarget <= completeTolerance;
        if (reaches) {
            if (endsWhereGolemStands) {
                return Decision.ARRIVED;
            }
        } else if (endsWhereGolemStands
                || endToTarget + MIN_IMPROVEMENT > Math.min(best, hereToTarget)) {
            return Decision.UNREACHABLE;
        }
        boolean improves = endToTarget + MIN_IMPROVEMENT <= best;
        if (afterStuck && !improves && ++stuckStrikes >= MAX_STUCK_STRIKES) {
            return Decision.UNREACHABLE;
        }
        best = Math.min(best, endToTarget);
        complete = reaches;
        return Decision.WALK;
    }

    /** The current path's last waypoint was reached. */
    Decision onLegFinished() {
        return complete ? Decision.ARRIVED : Decision.SEARCH;
    }

    /** A waypoint was reached with the golem {@code hereToTarget} from the target. */
    void onWaypointReached(double hereToTarget) {
        if (hereToTarget + PROGRESS_FOR_FORGIVENESS < closest) {
            stuckStrikes = 0;
        }
        closest = Math.min(closest, hereToTarget);
    }

    /** The golem stopped making progress; the next {@link #onPath} is a stuck search. */
    void onStuck() {
        stuckSearch = true;
    }
}
