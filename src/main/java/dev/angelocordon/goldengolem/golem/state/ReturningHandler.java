package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;
import dev.angelocordon.goldengolem.golem.movement.Mover;
import dev.angelocordon.goldengolem.util.BlockLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Walks the golem back to its golden chest, then hands over to DEPOSITING. Blocks with
 * CHEST_MISSING when there is no usable chest and UNREACHABLE when the mover gives up.
 */
public final class ReturningHandler implements StateHandler {

    /** Within this many blocks of the chest's center the golem can reach into it. */
    static final double ARRIVE_DISTANCE = 2.0;
    /** A normal walk; there is no hurry. */
    private static final double SPEED = 1.0;

    private final Services services;

    public ReturningHandler(Services services) {
        this.services = services;
    }

    @Override
    public void onEnter(GoldenGolem golem) {
        // Clears a failure left over from an earlier trip to the same spot, so it tries afresh.
        services.movers().of(golem).stop();
    }

    @Override
    public void onExit(GoldenGolem golem) {
        services.movers().of(golem).stop();
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        Block chest = loadedGoldenChest(services, golem);
        if (chest == null) {
            return StateHandler.block(golem, BlockedReason.CHEST_MISSING);
        }
        if (!chest.getWorld().equals(golem.entity().getWorld())) {
            return StateHandler.block(golem, BlockedReason.UNREACHABLE);
        }
        Location center = chest.getLocation().toCenterLocation();
        Mover mover = services.movers().of(golem);
        if (mover.isNear(center, ARRIVE_DISTANCE)) {
            return GolemState.DEPOSITING;
        }
        // Aiming at the chest block itself: the pathfinder accepts a solid target within a block,
        // so the trip ends on a free spot next to the chest without searching for one here.
        mover.moveTo(center, SPEED);
        if (mover.failed()) {
            return StateHandler.block(golem, BlockedReason.UNREACHABLE);
        }
        return GolemState.RETURNING;
    }

    /**
     * The golem's linked chest block if it is a golden chest in a loaded chunk, else null. Checks
     * the chunk first so a far-away chest is never loaded just to look at it. Shared with
     * {@link DepositingHandler}.
     */
    static @Nullable Block loadedGoldenChest(Services services, GoldenGolem golem) {
        BlockLocation link = golem.chest();
        if (link == null) {
            return null;
        }
        World world = link.loadedWorld();
        if (world == null || !world.isChunkLoaded(link.pos().x() >> 4, link.pos().z() >> 4)) {
            return null;
        }
        Block block = world.getBlockAt(link.pos().x(), link.pos().y(), link.pos().z());
        return services.chests().isGoldenChest(block) ? block : null;
    }
}
