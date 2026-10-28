package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;
import dev.angelocordon.goldengolem.golem.movement.Mover;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the golem near its leader, the player who told it to follow (its owner for golems saved
 * before leaders existed). Never mines. Teleports to the leader when far away or in another
 * world, but only onto a safe spot; otherwise it waits. With the leader offline it stands still
 * and stays FOLLOWING, so it picks up again when the leader comes back.
 */
public final class FollowingHandler implements StateHandler {

    /** Walk toward the leader when farther than this. */
    static final double FOLLOW_DISTANCE = 3.0;
    /** Stop once this close. Lower than {@link #FOLLOW_DISTANCE} so it doesn't stutter at the edge. */
    static final double STOP_DISTANCE = 2.0;
    /** Teleport to the leader when farther than this. */
    static final double TELEPORT_DISTANCE = 24.0;
    /** About 0.25 blocks/tick for a copper golem, a bit faster than a walking player. */
    private static final double FOLLOW_SPEED = 1.7;

    /** What to do this tick, from the distance to the leader. */
    enum Action {
        TELEPORT,
        WALK,
        STOP,
        /** Between the stop and follow distances: keep doing whatever it was doing. */
        KEEP
    }

    private final Services services;

    public FollowingHandler(Services services) {
        this.services = services;
    }

    @Override
    public void onEnter(GoldenGolem golem) {
        services.movers().of(golem).stop();
    }

    @Override
    public void onExit(GoldenGolem golem) {
        golem.setLeader(null);
        services.movers().of(golem).stop();
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        Mover mover = services.movers().of(golem);
        Player leader = onlineLeader(golem);
        if (leader == null) {
            mover.stop();
            return GolemState.FOLLOWING;
        }
        Location here = golem.entity().getLocation();
        Location leaderLocation = leader.getLocation();
        if (!leader.getWorld().equals(here.getWorld())) {
            mover.stop();
            if (canTeleportTo(leader)) {
                golem.entity().teleport(leaderLocation);
            }
            return GolemState.FOLLOWING;
        }
        switch (decide(here.distanceSquared(leaderLocation))) {
            case TELEPORT -> {
                if (canTeleportTo(leader)) {
                    mover.stop();
                    golem.entity().teleport(leaderLocation);
                } else {
                    // Not a safe spot (flying, falling, lava...): walking after the leader beats
                    // dropping the golem into danger. It teleports once the leader is somewhere safe.
                    mover.moveTo(leaderLocation, FOLLOW_SPEED);
                }
            }
            case WALK -> mover.moveTo(leaderLocation, FOLLOW_SPEED);
            case STOP -> mover.stop();
            case KEEP -> {
                if (mover.isMoving()) {
                    mover.moveTo(leaderLocation, FOLLOW_SPEED);
                }
            }
        }
        return GolemState.FOLLOWING;
    }

    /** The action for a squared distance to the leader. Pure, so it is unit-tested. */
    static Action decide(double distanceSquared) {
        if (distanceSquared > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            return Action.TELEPORT;
        }
        if (distanceSquared > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
            return Action.WALK;
        }
        if (distanceSquared <= STOP_DISTANCE * STOP_DISTANCE) {
            return Action.STOP;
        }
        return Action.KEEP;
    }

    /**
     * Whether the leader's spot is safe to drop the golem onto: leader alive, standing on solid
     * ground, not flying, gliding or riding, and no lava or fire at their feet. Pure, so it is
     * unit-tested.
     */
    static boolean isSafeTeleport(boolean dead, boolean flying, boolean gliding, boolean inVehicle,
            boolean inLava, Material atFeet, boolean solidBelow) {
        boolean hazardAtFeet = atFeet == Material.LAVA || atFeet == Material.FIRE || atFeet == Material.SOUL_FIRE;
        return !dead && !flying && !gliding && !inVehicle && !inLava && !hazardAtFeet && solidBelow;
    }

    private static boolean canTeleportTo(Player leader) {
        Location feet = leader.getLocation();
        return isSafeTeleport(leader.isDead(), leader.isFlying(), leader.isGliding(), leader.isInsideVehicle(),
                leader.isInLava(), feet.getBlock().getType(), feet.clone().subtract(0, 0.5, 0).getBlock().isSolid());
    }

    private @Nullable Player onlineLeader(GoldenGolem golem) {
        UUID leaderId = golem.followTarget();
        if (leaderId == null) {
            return null;
        }
        Player leader = services.plugin().getServer().getPlayer(leaderId);
        return leader != null && leader.isOnline() ? leader : null;
    }
}
