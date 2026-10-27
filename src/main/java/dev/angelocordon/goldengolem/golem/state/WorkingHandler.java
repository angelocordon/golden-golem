package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.GolemConfig;
import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;
import dev.angelocordon.goldengolem.golem.movement.Mover;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.util.BlockPos;
import dev.angelocordon.goldengolem.work.AreaScanner;
import dev.angelocordon.goldengolem.work.BlockBreaker;
import dev.angelocordon.goldengolem.work.BreakTime;
import dev.angelocordon.goldengolem.work.HarvestRule;
import dev.angelocordon.goldengolem.work.HazardCheck;
import dev.angelocordon.goldengolem.work.StorageMerge;
import dev.angelocordon.goldengolem.work.WorkDecision;
import dev.angelocordon.goldengolem.work.WorldProbes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Finds, walks to and breaks target blocks in the work area, one at a time:
 *
 * <ol>
 *   <li><b>Scan</b>: step the golem's {@link AreaScanner} once per tick until it finds a target.
 *   <li><b>Walk</b> next to it (within {@link #BREAK_REACH}). An unreachable target is
 *       blacklisted and the scan goes on.
 *   <li><b>Break</b> it over time at the vanilla player speed, with crack stages, after the
 *       hazard check. The owner's {@code BlockBreakEvent} decides whether protection allows it.
 *       Drops go into storage; the tool wears by one use.
 *   <li><b>Settle</b>, only if sand or gravel sat on the broken block: step back a block and
 *       wait for it to land, then scan on.
 * </ol>
 *
 * <p>Every tick first runs {@link WorkDecision#check}: storage full or tool worn goes RETURNING,
 * no tool or a tool that doesn't fit the target blocks, no work center goes IDLE. A pass that
 * finds nothing more goes RETURNING with loot, IDLE without.
 *
 * <p>Per-golem progress lives in {@link #work}, created on enter and pruned on exit. The tool is
 * read from the golem's data at most once a second and again at each decision point (target
 * found, break start, break end), because decoding it every tick for every golem is wasteful.
 */
public final class WorkingHandler implements StateHandler {

    /**
     * Walking stops once the feet are this close to the target block's center (the mover's own
     * reach), which puts the golem right next to a target at its level or below.
     */
    static final double WALK_REACH = 1.5;
    /**
     * Feet within this many blocks of the target block's center can break it. Looser than
     * {@link #WALK_REACH} so a target at head height, where the path ends a little farther away,
     * counts as reached, and so a small shove mid-break doesn't restart it.
     */
    static final double BREAK_REACH = 2.5;
    /**
     * How long an unreachable, unsafe or protected block is skipped: 2 minutes, longer than a
     * scan pass, as {@link AreaScanner} asks.
     */
    static final long BLACKLIST_TICKS = 2 * 60 * 20;
    /** Re-read the tool this often, to notice menu edits. */
    private static final int TOOL_REFRESH_TICKS = 20;
    /** Wait this long for sand or gravel to land after breaking the block under it. */
    private static final int SETTLE_TICKS = 20;
    /** Swing the arm this often while breaking, roughly a player's swing rate. */
    private static final int SWING_INTERVAL_TICKS = 5;
    /** Drop stale entries (golems that unloaded while working) this often. */
    private static final int PRUNE_INTERVAL_TICKS = 1200;
    private static final double WALK_SPEED = 1.0;

    private enum Phase { SCAN, WALK, BREAK, SETTLE }

    /** One golem's progress in this state. */
    private static final class Work {
        final AreaScanner scanner;
        @Nullable BlockLocation center;
        @Nullable Material targetType;
        Phase phase = Phase.SCAN;
        @Nullable BlockPos target;
        int breakTicks;
        int breakTotal;
        int crackStage;
        long settleUntil;
        @Nullable ItemStack tool;
        long toolDueAt;
        boolean toolFits;
        boolean toolLow;
        boolean storageFull;

        Work(GolemConfig config) {
            scanner = new AreaScanner(config.radius(), config.verticalRange(), config.scanBlocksPerTick());
        }
    }

    private final Services services;
    private final Map<UUID, Work> work = new HashMap<>();
    private long nextPrune;

    public WorkingHandler(Services services) {
        this.services = services;
    }

    @Override
    public void onEnter(GoldenGolem golem) {
        services.movers().of(golem).stop();
        Work w = new Work(services.config());
        work.put(golem.id(), w);
        BlockLocation center = golem.workCenter();
        if (center != null) {
            restart(golem, w, center, golem.target());
        }
    }

    @Override
    public void onExit(GoldenGolem golem) {
        Work w = work.remove(golem.id());
        if (w != null) {
            abortBreak(golem, w);
        }
        services.movers().of(golem).stop();
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        long now = services.plugin().getServer().getCurrentTick();
        pruneIfDue(now);
        // A golem loaded from disk already WORKING never got onEnter.
        Work w = work.computeIfAbsent(golem.id(), id -> new Work(services.config()));
        BlockLocation center = golem.workCenter();
        if (center != null) {
            Material targetType = golem.target();
            if (!center.equals(w.center) || targetType != w.targetType) {
                restart(golem, w, center, targetType);
            }
            if (now >= w.toolDueAt) {
                refreshTool(golem, w, now);
            }
        }
        WorkDecision decision = decide(center != null, w);
        if (decision != WorkDecision.CONTINUE || center == null) {
            return apply(golem, decision);
        }
        World world = golem.entity().getWorld();
        if (!world.getUID().equals(center.world())) {
            return StateHandler.block(golem, BlockedReason.UNREACHABLE);
        }
        return switch (w.phase) {
            case SCAN -> scan(golem, w, world, center, now);
            case WALK -> walk(golem, w, world, now);
            case BREAK -> breakTick(golem, w, world, now);
            case SETTLE -> settle(golem, w, now);
        };
    }

    /** New work center or target type: drop the current target and start a fresh pass. */
    private void restart(GoldenGolem golem, Work w, BlockLocation center, @Nullable Material targetType) {
        abortBreak(golem, w);
        w.center = center;
        w.targetType = targetType;
        w.scanner.reset(center.pos());
        w.target = null;
        w.phase = Phase.SCAN;
        w.toolDueAt = 0;
        w.storageFull = !StorageMerge.hasEmptySlot(golem.storage());
        services.movers().of(golem).stop();
    }

    private void refreshTool(GoldenGolem golem, Work w, long now) {
        ItemStack tool = golem.tool();
        Material targetType = w.targetType;
        w.tool = tool;
        w.toolDueAt = now + TOOL_REFRESH_TICKS;
        w.toolFits = tool != null && targetType != null && HarvestRule.toolFits(tool.getType(), targetType);
        w.toolLow = tool != null && BlockBreaker.isLow(tool, services.config());
    }

    private static WorkDecision decide(boolean hasWorkCenter, Work w) {
        return WorkDecision.check(hasWorkCenter, w.storageFull, w.tool != null, w.toolFits, w.toolLow);
    }

    private static GolemState apply(GoldenGolem golem, WorkDecision decision) {
        return switch (decision) {
            case CONTINUE -> GolemState.WORKING;
            case IDLE -> GolemState.IDLE;
            case RETURN -> GolemState.RETURNING;
            case NO_TOOL -> StateHandler.block(golem, BlockedReason.NO_TOOL);
            case CANT_HARVEST -> StateHandler.block(golem, BlockedReason.CANT_HARVEST);
        };
    }

    private GolemState scan(GoldenGolem golem, Work w, World world, BlockLocation center, long now) {
        Material targetType = w.targetType;
        ItemStack tool = w.tool;
        if (targetType == null || tool == null) {
            return GolemState.WORKING;
        }
        AreaScanner.Result result = w.scanner.step(WorldProbes.targets(world, targetType, tool), now);
        return switch (result) {
            case AreaScanner.Found found -> claim(golem, w, world, found.pos(), now);
            case AreaScanner.Scanning _ -> GolemState.WORKING;
            case AreaScanner.Exhausted _ -> {
                w.scanner.reset(center.pos());
                yield apply(golem, WorkDecision.onAreaExhausted(!StorageMerge.isEmpty(golem.storage())));
            }
        };
    }

    /** The scanner found a target: check what can be checked from here, then walk to it. */
    private GolemState claim(GoldenGolem golem, Work w, World world, BlockPos target, long now) {
        refreshTool(golem, w, now);
        WorkDecision decision = decide(true, w);
        if (decision != WorkDecision.CONTINUE) {
            return apply(golem, decision);
        }
        if (onlineOwner(golem) == null || HazardCheck.lavaNextTo(target, WorldProbes.kinds(world))) {
            blacklist(w, target, now);
            return GolemState.WORKING;
        }
        w.target = target;
        w.phase = Phase.WALK;
        return walk(golem, w, world, now);
    }

    private GolemState walk(GoldenGolem golem, Work w, World world, long now) {
        BlockPos target = w.target;
        Material targetType = w.targetType;
        if (target == null || targetType == null || !WorldProbes.isType(world, target, targetType)) {
            dropTarget(golem, w);
            return GolemState.WORKING;
        }
        Mover mover = services.movers().of(golem);
        Location goal = blockCenter(world, target);
        if (!mover.isNear(goal, WALK_REACH)) {
            boolean moving = mover.moveTo(goal, WALK_SPEED);
            if (!moving || mover.failed()) {
                blacklist(w, target, now);
                dropTarget(golem, w);
                return GolemState.WORKING;
            }
            if (mover.isMoving()) {
                return GolemState.WORKING;
            }
            // The trip ended as close as a path gets (e.g. the target is at head height): fine if
            // that is within reach, as unreachable as a failed trip if not.
            if (!mover.isNear(goal, BREAK_REACH)) {
                blacklist(w, target, now);
                dropTarget(golem, w);
                return GolemState.WORKING;
            }
        }
        mover.stop();
        return startBreak(golem, w, world, target, now);
    }

    /** In reach: final checks with the golem where it will stand, then start digging. */
    private GolemState startBreak(GoldenGolem golem, Work w, World world, BlockPos target, long now) {
        refreshTool(golem, w, now);
        WorkDecision decision = decide(true, w);
        ItemStack tool = w.tool;
        if (decision != WorkDecision.CONTINUE || tool == null) {
            return apply(golem, decision);
        }
        CopperGolem entity = golem.entity();
        Block block = world.getBlockAt(target.x(), target.y(), target.z());
        BlockPos feet = BlockPos.of(entity.getLocation());
        int ticks = BlockBreaker.ticksToBreak(block, tool);
        if (ticks == BreakTime.NEVER
                || !HarvestRule.canHarvest(block, tool)
                || !HazardCheck.isSafe(target, feet, WorldProbes.kinds(world))
                || onlineOwner(golem) == null) {
            blacklist(w, target, now);
            dropTarget(golem, w);
            return GolemState.WORKING;
        }
        w.breakTicks = 0;
        w.breakTotal = ticks;
        w.crackStage = 0;
        w.phase = Phase.BREAK;
        face(entity, target);
        return GolemState.WORKING;
    }

    private GolemState breakTick(GoldenGolem golem, Work w, World world, long now) {
        BlockPos target = w.target;
        Material targetType = w.targetType;
        if (target == null || targetType == null || !WorldProbes.isType(world, target, targetType)) {
            // Changed mid-break (someone mined or replaced it): forget it and scan on.
            abortBreak(golem, w);
            dropTarget(golem, w);
            return GolemState.WORKING;
        }
        CopperGolem entity = golem.entity();
        if (!services.movers().of(golem).isNear(blockCenter(world, target), BREAK_REACH)) {
            // Pushed away: walk back and start the break over, as a player would.
            abortBreak(golem, w);
            w.phase = Phase.WALK;
            return GolemState.WORKING;
        }
        Block block = world.getBlockAt(target.x(), target.y(), target.z());
        w.breakTicks++;
        if (w.breakTicks % SWING_INTERVAL_TICKS == 1) {
            entity.swingMainHand();
        }
        if (w.breakTicks < w.breakTotal) {
            float progress = (float) w.breakTicks / w.breakTotal;
            int stage = (int) (progress * 10);
            if (stage != w.crackStage) {
                w.crackStage = stage;
                BlockBreaker.showCracks(block, progress, entity);
            }
            return GolemState.WORKING;
        }
        return finishBreak(golem, w, world, block, target, now);
    }

    /**
     * Digging is done: recheck the tool (the menu may have swapped it), ask protection, then
     * break, store the drops and wear the tool.
     */
    private GolemState finishBreak(GoldenGolem golem, Work w, World world, Block block, BlockPos target,
            long now) {
        CopperGolem entity = golem.entity();
        abortBreak(golem, w);
        refreshTool(golem, w, now);
        WorkDecision decision = decide(true, w);
        ItemStack tool = w.tool;
        if (decision != WorkDecision.CONTINUE || tool == null) {
            return apply(golem, decision);
        }
        Player owner = onlineOwner(golem);
        if (!HarvestRule.canHarvest(block, tool) || owner == null || !BlockBreaker.ownerMayBreak(block, owner)) {
            blacklist(w, target, now);
            dropTarget(golem, w);
            return GolemState.WORKING;
        }
        // A listener may have changed the block while handling the event.
        if (block.getType() != w.targetType) {
            dropTarget(golem, w);
            return GolemState.WORKING;
        }
        boolean fallingAbove = HazardCheck.hasFallingAbove(target, WorldProbes.kinds(world));

        List<ItemStack> drops = BlockBreaker.breakBlock(block, tool, entity);
        @Nullable ItemStack[] storage = golem.storage();
        List<ItemStack> leftovers = StorageMerge.add(storage, drops);
        golem.setStorage(storage);
        for (ItemStack leftover : leftovers) {
            world.dropItem(entity.getLocation(), leftover);
        }
        ItemStack worn = BlockBreaker.wearTool(tool, entity);
        golem.setTool(worn.isEmpty() ? null : worn);
        refreshTool(golem, w, now);
        w.storageFull = !leftovers.isEmpty() || !StorageMerge.hasRoomForAll(storage, drops);

        w.target = null;
        if (fallingAbove) {
            startSettle(golem, w, world, target, now);
        } else {
            w.phase = Phase.SCAN;
        }
        return apply(golem, decide(true, w));
    }

    /** Steps one block clear of the hole so the falling block can't land on the golem. */
    private void startSettle(GoldenGolem golem, Work w, World world, BlockPos broken, long now) {
        BlockPos feet = BlockPos.of(golem.entity().getLocation());
        for (BlockPos spot : HazardCheck.stepBackSpots(broken, feet)) {
            if (!spot.equals(broken) && WorldProbes.isStandable(world, spot)) {
                services.movers().of(golem).moveTo(
                        new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5), WALK_SPEED);
                break;
            }
        }
        w.phase = Phase.SETTLE;
        w.settleUntil = now + SETTLE_TICKS;
    }

    private GolemState settle(GoldenGolem golem, Work w, long now) {
        if (now >= w.settleUntil) {
            services.movers().of(golem).stop();
            w.phase = Phase.SCAN;
        }
        return GolemState.WORKING;
    }

    /** Clears the crack overlay if a break was in progress. */
    private static void abortBreak(GoldenGolem golem, Work w) {
        BlockPos target = w.target;
        if (w.phase == Phase.BREAK && target != null) {
            World world = golem.entity().getWorld();
            if (world.isChunkLoaded(target.x() >> 4, target.z() >> 4)) {
                BlockBreaker.clearCracks(world.getBlockAt(target.x(), target.y(), target.z()), golem.entity());
            }
        }
        w.breakTicks = 0;
        w.crackStage = 0;
        if (w.phase == Phase.BREAK) {
            w.phase = Phase.WALK;
        }
    }

    /** Forgets the current target and goes back to stepping the same pass (no reset). */
    private void dropTarget(GoldenGolem golem, Work w) {
        w.target = null;
        w.phase = Phase.SCAN;
        services.movers().of(golem).stop();
    }

    private static void blacklist(Work w, BlockPos target, long now) {
        w.scanner.blacklist(target, now + BLACKLIST_TICKS);
    }

    /** Turns the golem toward the block it is digging; its AI is off, so nothing else will. */
    private static void face(CopperGolem entity, BlockPos target) {
        Location here = entity.getLocation();
        double dx = target.x() + 0.5 - here.getX();
        double dz = target.z() + 0.5 - here.getZ();
        if (dx * dx + dz * dz > 1e-6) {
            entity.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0f);
        }
    }

    private static Location blockCenter(World world, BlockPos pos) {
        return new Location(world, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }

    private @Nullable Player onlineOwner(GoldenGolem golem) {
        UUID ownerId = golem.owner();
        if (ownerId == null) {
            return null;
        }
        Player owner = services.plugin().getServer().getPlayer(ownerId);
        return owner != null && owner.isOnline() ? owner : null;
    }

    /** Golems that unload mid-work never get onExit; forget them once they're gone. */
    private void pruneIfDue(long now) {
        if (now < nextPrune) {
            return;
        }
        nextPrune = now + PRUNE_INTERVAL_TICKS;
        work.keySet().removeIf(id -> services.registry().get(id).isEmpty());
    }
}
