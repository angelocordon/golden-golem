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
 * velocity is left to vanilla physics, so gravity, step-up and collisions still apply. When to
 * search again, stop or give up is decided by {@link TripTracker}.
 */
public final class PathMover implements Mover {

    /** Within this many blocks of the target the trip is done and {@link #isMoving()} is false. */
    static final double REACH = 1.5;
    /**
     * A path the pathfinder says reaches the target may end up to this far from it (its last node
     * is next to the target, and the target may be a block corner) and still count as reaching.
     */
    private static final double COMPLETE_TOLERANCE = REACH + 0.5;
    /** Pathfinder reach (Manhattan distance), so a solid target block or a player's spot counts. */
    private static final int PATH_REACH_RANGE = 1;
    /** A new target this close to the old one keeps the current trip. */
    private static final double RETARGET_DISTANCE = 1.0;
    /** Minimum ticks between path searches, so a moving target can't trigger one every tick. */
    private static final int SEARCH_COOLDOWN_TICKS = 10;
    /** After giving up, {@link #moveTo} refuses the same target for this long (5 s). */
    private static final int RETRY_COOLDOWN_TICKS = 100;
    /** Ticks without real progress before the golem counts as stuck and searches again. */
    private static final int STUCK_TICKS = 40;
    /** Movement that counts as progress for the stuck check. */
    private static final double PROGRESS_DISTANCE = 0.5;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.2;
    private static final double DEFAULT_STEP_HEIGHT = 1.0;
    private static final double DEFAULT_JUMP_STRENGTH = 0.42;
    /** Upward push under water: unaware mobs don't swim, so without it the golem walks the bottom. */
    private static final double SWIM_UP_VELOCITY = 0.08;

    private final CopperGolem golem;
    private final TripTracker trip = new TripTracker(COMPLETE_TOLERANCE);

    private @Nullable Location target;
    private double speed = 1.0;
    private @Nullable List<Location> points;
    private int nextPoint;
    private boolean needsPath;
    private boolean failed;
    private boolean arrived;
    private @Nullable Location arrivedAt;
    private long age;
    private long failedAt;
    private int ticksSinceSearch = SEARCH_COOLDOWN_TICKS;
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
            if (failed) {
                if (age - failedAt < RETRY_COOLDOWN_TICKS) {
                    return false;
                }
                startTrip();
                return !failed;
            }
            if (arrived && (arrivedAt == null || !isNear(arrivedAt, REACH))) {
                // Pushed away after arriving: walk back.
                startTrip();
            }
            return true;
        }
        this.target = target.clone();
        startTrip();
        return !failed;
    }

    /** Begins a fresh trip to {@link #target}, keeping the old path until a new one is found. */
    private void startTrip() {
        trip.reset();
        failed = false;
        arrived = false;
        arrivedAt = null;
        needsPath = true;
        if (!withinReach() && ticksSinceSearch >= SEARCH_COOLDOWN_TICKS) {
            search();
        }
    }

    @Override
    public void stop() {
        target = null;
        points = null;
        needsPath = false;
        failed = false;
        arrived = false;
        arrivedAt = null;
        progressMark = null;
        trip.reset();
        halt();
    }

    @Override
    public boolean isMoving() {
        return target != null && !failed && !arrived && !withinReach();
    }

    @Override
    public boolean failed() {
        return failed;
    }

    @Override
    public boolean isNear(Location target, double distance) {
        Location here = golem.getLocation();
        return sameWorld(here, target) && here.distanceSquared(target) <= distance * distance;
    }

    @Override
    public void tick() {
        age++;
        ticksSinceSearch++;
        Location target = this.target;
        if (target == null) {
            return;
        }
        if (!sameWorld(golem.getLocation(), target)) {
            stop();
            return;
        }
        if (failed || arrived) {
            return;
        }
        if (withinReach()) {
            points = null;
            needsPath = false;
            halt();
            return;
        }
        if (points == null) {
            needsPath = true;
        }
        if (needsPath && ticksSinceSearch >= SEARCH_COOLDOWN_TICKS) {
            search();
        }
        if (failed || arrived || points == null) {
            halt();
            return;
        }
        checkStuck();
        walk();
    }

    /** Asks the pathfinder for a fresh path to {@link #target} and lets the trip judge it. */
    private void search() {
        Location target = this.target;
        if (target == null || !canPlan()) {
            // Ground navigation refuses to plan mid-air; needsPath stays set, so retry next tick.
            return;
        }
        ticksSinceSearch = 0;
        needsPath = false;
        Location here = golem.getLocation();
        PathResult result = golem.getPathfinder().findPath(target, PATH_REACH_RANGE);
        List<Location> found = result == null ? List.of() : result.getPoints();
        TripTracker.Decision decision;
        if (result == null || found.isEmpty()) {
            decision = trip.onPath(false, false, 0, here.distance(target), false);
        } else {
            Location end = result.getFinalPoint();
            if (end == null) {
                end = found.get(found.size() - 1);
            }
            boolean endsHere = end.getBlockX() == here.getBlockX()
                    && end.getBlockY() == here.getBlockY()
                    && end.getBlockZ() == here.getBlockZ();
            decision = trip.onPath(true, result.canReachFinalPoint(), blockCenter(end).distance(target),
                    here.distance(target), endsHere);
        }
        switch (decision) {
            case WALK -> {
                points = List.copyOf(found);
                nextPoint = 0;
                progressMark = here;
                ticksWithoutProgress = 0;
            }
            case ARRIVED -> arrive();
            case UNREACHABLE -> fail();
            case SEARCH -> needsPath = true;
        }
    }

    /** Mirrors the server's ground-navigation check, without which findPath returns null. */
    private boolean canPlan() {
        return golem.isOnGround() || golem.isInWater() || golem.isInLava() || golem.isInsideVehicle();
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
            trip.onStuck();
            needsPath = true;
            progressMark = here;
            ticksWithoutProgress = 0;
        }
    }

    /** Moves one tick toward the next waypoint, skipping waypoints already stood on. */
    private void walk() {
        List<Location> points = this.points;
        Location target = this.target;
        if (points == null || target == null) {
            return;
        }
        Location here = golem.getLocation();
        Location waypoint;
        while (true) {
            if (nextPoint >= points.size()) {
                this.points = null;
                if (trip.onLegFinished() == TripTracker.Decision.ARRIVED) {
                    arrive();
                } else {
                    // A partial path ran out: plan the next leg from here.
                    needsPath = true;
                    halt();
                }
                return;
            }
            waypoint = blockCenter(points.get(nextPoint));
            if (!PathSteps.reached(here.getX(), here.getY(), here.getZ(),
                    waypoint.getX(), waypoint.getY(), waypoint.getZ())) {
                break;
            }
            trip.onWaypointReached(here.distance(target));
            nextPoint++;
        }

        double perTick = PathSteps.blocksPerTick(attribute(Attribute.MOVEMENT_SPEED, DEFAULT_MOVEMENT_SPEED), speed);
        PathSteps.Step step = PathSteps.step(here.getX(), here.getZ(), waypoint.getX(), waypoint.getZ(), perTick);
        double vy = golem.getVelocity().getY();
        if (golem.isOnGround() && PathSteps.needsJump(here.getY(), waypoint.getY(),
                attribute(Attribute.STEP_HEIGHT, DEFAULT_STEP_HEIGHT))) {
            vy = attribute(Attribute.JUMP_STRENGTH, DEFAULT_JUMP_STRENGTH);
        } else if (golem.isUnderWater() && waypoint.getY() >= here.getY() - 0.1) {
            vy = Math.max(vy, SWIM_UP_VELOCITY);
        }
        // Sent every tick on purpose: ground friction scales the velocity by 0.546 each tick, so
        // skipping a tick would halve the speed.
        golem.setVelocity(new Vector(step.dx(), vy, step.dz()));
        steering = true;
        if (step.length() > 1e-4) {
            golem.setRotation(PathSteps.yaw(step.dx(), step.dz()), 0f);
        }
    }

    private void arrive() {
        arrived = true;
        arrivedAt = golem.getLocation();
        points = null;
        needsPath = false;
        halt();
    }

    private void fail() {
        failed = true;
        failedAt = age;
        points = null;
        needsPath = false;
        halt();
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

    /** Path points are block corners (int node coordinates); this is the block's floor center. */
    private static Location blockCenter(Location point) {
        return new Location(point.getWorld(), point.getBlockX() + 0.5, point.getBlockY(), point.getBlockZ() + 0.5);
    }

    private static boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }
}
