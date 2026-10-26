package dev.angelocordon.goldengolem.golem.movement;

/**
 * The math {@link PathMover} uses to walk a golem along path points by hand. Pure (plain doubles
 * in, plain values out) so it can be unit-tested without a server.
 *
 * <p>Waypoints are block positions: x/z are the block center, y is the block's floor, which is
 * where the golem's feet stand.
 */
final class PathSteps {

    /**
     * Ground friction per tick for a mob on a normal block (block friction 0.6 times air drag
     * 0.91). A walking mob's steady-state speed is its movement-speed attribute divided by
     * {@code 1 - GROUND_FRICTION}, which is what "speed 1.0 = normal walking speed" maps to.
     */
    static final double GROUND_FRICTION = 0.6 * 0.91;

    /** Hard cap so a bad attribute or speed can't fling the golem across the map. */
    static final double MAX_BLOCKS_PER_TICK = 0.5;

    /**
     * How close (per horizontal axis) the golem's feet must be to a waypoint's center to count
     * as there. Vanilla uses {@code 0.75 - width / 2} for narrow mobs; the copper golem is
     * about half a block wide.
     */
    static final double WAYPOINT_TOLERANCE = 0.5;

    private PathSteps() {
    }

    /** A horizontal move for one tick. */
    record Step(double dx, double dz) {

        double length() {
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /** Blocks per tick for a mob with this movement-speed attribute at a speed multiplier. */
    static double blocksPerTick(double movementSpeedAttribute, double speed) {
        double perTick = movementSpeedAttribute * speed / (1 - GROUND_FRICTION);
        return Math.max(0, Math.min(MAX_BLOCKS_PER_TICK, perTick));
    }

    /**
     * The horizontal move from {@code (x, z)} toward the waypoint center {@code (wx, wz)}: at
     * most {@code blocksPerTick} long, and never past the waypoint.
     */
    static Step step(double x, double z, double wx, double wz, double blocksPerTick) {
        double dx = wx - x;
        double dz = wz - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1e-6) {
            return new Step(0, 0);
        }
        double length = Math.min(blocksPerTick, distance);
        return new Step(dx / distance * length, dz / distance * length);
    }

    /** True once the feet at {@code (x, y, z)} are on the waypoint, so the next one is due. */
    static boolean reached(double x, double y, double z, double wx, double wy, double wz) {
        return Math.abs(x - wx) < WAYPOINT_TOLERANCE
                && Math.abs(z - wz) < WAYPOINT_TOLERANCE
                && Math.abs(y - wy) < 1.0;
    }

    /** True if the waypoint's floor is higher than the golem can step up, so it must jump. */
    static boolean needsJump(double y, double waypointY, double stepHeight) {
        return waypointY - y > stepHeight + 1e-3;
    }

    /**
     * Bukkit yaw (degrees, 0 = +z/south, 90 = -x/west) for facing along {@code (dx, dz)}, so the
     * golem looks where it walks.
     */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Whether a path that can't reach its target is still worth walking: its end must be at
     * least half a block closer to the target than the golem is now. Paths are capped by the
     * mob's follow range, so long trips come back partial and get chained.
     */
    static boolean makesProgress(double hereToTarget, double finalPointToTarget) {
        return finalPointToTarget + 0.5 < hereToTarget;
    }
}
