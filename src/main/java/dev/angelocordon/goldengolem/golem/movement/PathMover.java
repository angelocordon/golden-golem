package dev.angelocordon.goldengolem.golem.movement;

import com.destroystokyo.paper.entity.Pathfinder.PathResult;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.CopperGolem;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * Steers a golden golem along Paper pathfinder paths.
 *
 * <p>Golden golems are unaware ({@link AiTakeover}), so vanilla navigation never ticks. This
 * mover asks the pathfinder for a path ({@code findPath} only computes one, it doesn't need the
 * AI) and walks the points itself by setting the golem's horizontal velocity each tick. Vertical
 * velocity is left to vanilla physics, so gravity, step-up and collisions still apply.
 */
public final class PathMover implements Mover {

    /** Within this many blocks of the target the trip is done and {@link #isMoving()} is false. */
    static final double REACH = 1.5;
    /** Pathfinder reach (Manhattan distance), so a solid target block or a player's spot counts. */
    private static final int PATH_REACH_RANGE = 1;
    /** A new target this close to the old one keeps the current path. */
    private static final double RETARGET_DISTANCE = 1.0;
    /** Minimum ticks between path searches, so a moving target can't trigger one every tick. */
    private static final int REPATH_COOLDOWN_TICKS = 10;
    /** Ticks without real progress before the golem counts as stuck and searches again. */
    private static final int STUCK_TICKS = 40;
    /** How long an unreachable target is left alone before searching for it again. */
    private static final int UNREACHABLE_RETRY_TICKS = 40;
    /** Movement that counts as progress for the stuck check. */
    private static final double PROGRESS_DISTANCE = 0.5;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.2;
    private static final double DEFAULT_STEP_HEIGHT = 0.6;
    private static final double DEFAULT_JUMP_STRENGTH = 0.42;
    /** Upward push in water: unaware mobs don't swim, so without it the golem walks the bottom. */
    private static final double SWIM_UP_VELOCITY = 0.08;

    private final CopperGolem golem;

    private @Nullable Location target;
    private double speed = 1.0;
    private @Nullable List<Location> points;
    private int nextPoint;
    private boolean needsPath;
    private boolean unreachable;
    private int ticksSinceSearch = REPATH_COOLDOWN_TICKS;
    private @Nullable Location progressMark;
    private int ticksWithoutProgress;
    private boolean steering;

    public PathMover(CopperGolem golem) {
        this.golem = golem;
    }

    @Override
    public boolean moveTo(Location target, double speed) {
        this.speed = speed;
        Location current = this.target;
        if (current != null && sameWorld(current, target)
                && current.distanceSquared(target) <= RETARGET_DISTANCE * RETARGET_DISTANCE) {
            if (unreachable) {
                return false;
            }
            if (points == null && !withinReach()) {
                // Arrived earlier but got pushed away, or the last path ran out: head back.
                needsPath = true;
            }
            return true;
        }
        this.target = target.clone();
        unreachable = false;
        needsPath = true;
        if (!withinReach() && ticksSinceSearch >= REPATH_COOLDOWN_TICKS) {
            search();
        }
        return !unreachable;
    }

    @Override
    public void stop() {
        target = null;
        points = null;
        needsPath = false;
        unreachable = false;
        progressMark = null;
        halt();
    }

    @Override
    public boolean isMoving() {
        return target != null && !unreachable && !withinReach();
    }

    @Override
    public boolean isNear(Location target, double distance) {
        Location here = golem.getLocation();
        return sameWorld(here, target) && here.distanceSquared(target) <= distance * distance;
    }

    @Override
    public void tick() {
        ticksSinceSearch++;
        Location target = this.target;
        if (target == null) {
            return;
        }
        if (!sameWorld(golem.getLocation(), target)) {
            stop();
            return;
        }
        if (withinReach()) {
            points = null;
            needsPath = false;
            halt();
            return;
        }
        if (unreachable) {
            if (ticksSinceSearch < UNREACHABLE_RETRY_TICKS) {
                return;
            }
            // The world may have changed (a block mined, a door opened): try the same target again.
            unreachable = false;
            needsPath = true;
        }
        if (needsPath && ticksSinceSearch >= REPATH_COOLDOWN_TICKS) {
            search();
        }
        if (points == null) {
            halt();
            return;
        }
        checkStuck();
        walk();
    }

    /** Asks the pathfinder for a fresh path to {@link #target}. */
    private void search() {
        Location target = this.target;
        if (target == null) {
            return;
        }
        // Ground navigation refuses to plan mid-air; keep needsPath set and retry next tick.
        if (!golem.isOnGround() && !golem.isInWater()) {
            return;
        }
        ticksSinceSearch = 0;
        needsPath = false;
        PathResult result = golem.getPathfinder().findPath(target, PATH_REACH_RANGE);
        if (result == null || result.getPoints().isEmpty() || !worthWalking(result, target)) {
            unreachable = true;
            points = null;
            halt();
            return;
        }
        points = List.copyOf(result.getPoints());
        nextPoint = 0;
        progressMark = golem.getLocation();
        ticksWithoutProgress = 0;
    }

    private boolean worthWalking(PathResult result, Location target) {
        if (result.canReachFinalPoint()) {
            return true;
        }
        Location end = result.getFinalPoint();
        if (end == null || !sameWorld(end, target)) {
            return false;
        }
        return PathSteps.makesProgress(golem.getLocation().distance(target), end.distance(target));
    }

    /** Searches again if the golem hasn't really moved for {@link #STUCK_TICKS}. */
    private void checkStuck() {
        Location here = golem.getLocation();
        Location mark = progressMark;
        if (mark == null || !sameWorld(mark, here)
                || mark.distanceSquared(here) > PROGRESS_DISTANCE * PROGRESS_DISTANCE) {
            progressMark = here;
            ticksWithoutProgress = 0;
        } else if (++ticksWithoutProgress >= STUCK_TICKS) {
            needsPath = true;
            progressMark = here;
            ticksWithoutProgress = 0;
        }
    }

    /** Moves one tick toward the next waypoint, skipping waypoints already stood on. */
    private void walk() {
        List<Location> points = this.points;
        if (points == null) {
            return;
        }
        Location here = golem.getLocation();
        double wx;
        double wy;
        double wz;
        while (true) {
            if (nextPoint >= points.size()) {
                // Path done but not within reach (a partial path): plan the next leg.
                this.points = null;
                needsPath = true;
                halt();
                return;
            }
            Location point = points.get(nextPoint);
            // Points are block positions; floor then center so this holds whether or not the
            // API already centered them.
            wx = point.getBlockX() + 0.5;
            wy = point.getBlockY();
            wz = point.getBlockZ() + 0.5;
            if (!PathSteps.reached(here.getX(), here.getY(), here.getZ(), wx, wy, wz)) {
                break;
            }
            nextPoint++;
        }

        double perTick = PathSteps.blocksPerTick(attribute(Attribute.MOVEMENT_SPEED, DEFAULT_MOVEMENT_SPEED), speed);
        PathSteps.Step step = PathSteps.step(here.getX(), here.getZ(), wx, wz, perTick);
        double vy = golem.getVelocity().getY();
        if (golem.isOnGround()
                && PathSteps.needsJump(here.getY(), wy, attribute(Attribute.STEP_HEIGHT, DEFAULT_STEP_HEIGHT))) {
            vy = attribute(Attribute.JUMP_STRENGTH, DEFAULT_JUMP_STRENGTH);
        } else if (golem.isInWater() && wy >= here.getY() - 0.1) {
            vy = Math.max(vy, SWIM_UP_VELOCITY);
        }
        golem.setVelocity(new Vector(step.dx(), vy, step.dz()));
        steering = true;
        if (step.length() > 1e-4) {
            golem.setRotation(PathSteps.yaw(step.dx(), step.dz()), 0f);
        }
    }

    /** Kills the horizontal velocity we set, once, so the golem stops instead of sliding. */
    private void halt() {
        if (!steering) {
            return;
        }
        steering = false;
        Vector velocity = golem.getVelocity();
        golem.setVelocity(new Vector(0, velocity.getY(), 0));
    }

    private boolean withinReach() {
        Location target = this.target;
        return target != null && isNear(target, REACH);
    }

    private double attribute(Attribute attribute, double fallback) {
        AttributeInstance instance = golem.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private static boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }
}
