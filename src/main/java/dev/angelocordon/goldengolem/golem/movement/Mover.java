package dev.angelocordon.goldengolem.golem.movement;

import org.bukkit.Location;

/**
 * Moves one golem. State handlers say where to go; the controller calls {@link #tick()} once
 * per server tick after all handlers ran, so a mover never needs its own scheduler task.
 */
public interface Mover {

    /**
     * Starts moving toward {@code target}, or keeps going if already heading there. Safe to call
     * every tick with the same target. Returns false if there is no path.
     */
    boolean moveTo(Location target, double speed);

    /** Stops and forgets the current target. */
    void stop();

    /** True while heading somewhere and not yet within {@code reach} of the target. */
    boolean isMoving();

    /**
     * True if the last attempt to reach the current target gave up: no path, or no path that gets
     * any closer, or stuck on the same path. {@link #isMoving()} is false meanwhile, and
     * {@link #moveTo} returns false for that target until it changes or a short cooldown passes.
     * Handlers use this to block with UNREACHABLE. False after {@link #stop()}.
     */
    boolean failed();

    /** True if the golem is within {@code distance} blocks of {@code target}. */
    boolean isNear(Location target, double distance);

    /** Advances movement by one tick. Called by the controller. */
    void tick();
}
