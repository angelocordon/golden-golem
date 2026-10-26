package dev.angelocordon.goldengolem.golem.movement;

import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;

/**
 * Steers a golden golem along Paper pathfinder paths.
 *
 * <p>TODO(LAB-11): implement. Stub: never moves.
 */
public final class PathMover implements Mover {

    private final CopperGolem golem;

    public PathMover(CopperGolem golem) {
        this.golem = golem;
    }

    @Override
    public boolean moveTo(Location target, double speed) {
        return false;
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isMoving() {
        return false;
    }

    @Override
    public boolean isNear(Location target, double distance) {
        Location here = golem.getLocation();
        return here.getWorld().equals(target.getWorld()) && here.distanceSquared(target) <= distance * distance;
    }

    @Override
    public void tick() {
    }
}
