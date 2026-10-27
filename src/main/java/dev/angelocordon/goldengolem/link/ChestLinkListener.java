package dev.angelocordon.goldengolem.link;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.util.BlockPos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jspecify.annotations.Nullable;

/**
 * Relinks a following golem when its owner right-clicks a golden chest, and blocks golems
 * whose chest is broken.
 *
 * <p>Breaks are handled by re-checking links one tick later instead of reading the event's block
 * list: the golden chest listener takes golden chests out of explosion lists and breaks them
 * itself, so at MONITOR they may already be gone from the list. Checking the world after the
 * fact catches every cause the same way.
 */
public final class ChestLinkListener implements Listener {

    /** How close a following golem must be to its owner to be relinked by a chest click. */
    static final double RELINK_RADIUS = 8;

    private final Services services;
    private boolean checkScheduled;

    public ChestLinkListener(Services services) {
        this.services = services;
    }

    /**
     * HIGH so protection plugins (usually LOW/NORMAL) get to deny the click first. Cancels the
     * chest opening only when a link was made, so a full chest still opens normally.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !services.chests().isGoldenChest(block)) {
            return;
        }
        Optional<GoldenGolem> golem = nearestFollowingGolem(event.getPlayer(), block);
        if (golem.isPresent() && services.linker().relink(golem.get(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (services.chests().isGoldenChest(event.getBlock())) {
            scheduleLinkCheck();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        scheduleLinkCheck();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        scheduleLinkCheck();
    }

    /**
     * Withers break blocks through this event. Not ignoreCancelled: the golden chest listener
     * cancels it and breaks the chest itself. The check reads the world, so a truly cancelled
     * event just finds every link intact.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getTo().isAir()) {
            scheduleLinkCheck();
        }
    }

    private Optional<GoldenGolem> nearestFollowingGolem(Player player, Block chest) {
        UUID playerId = player.getUniqueId();
        Location at = player.getLocation();
        List<GoldenGolem> following = new ArrayList<>();
        for (GoldenGolem golem : services.registry().all()) {
            Location golemAt = golem.entity().getLocation();
            if (golem.isValid()
                    && golem.state() == GolemState.FOLLOWING
                    && golem.isOwnedBy(playerId)
                    && golemAt.getWorld().equals(at.getWorld())
                    && golemAt.distanceSquared(at) <= RELINK_RADIUS * RELINK_RADIUS) {
                following.add(golem);
            }
        }
        return Nearest.of(BlockPos.of(chest), following, golem -> BlockPos.of(golem.entity().getLocation()));
    }

    /** Runs {@link #checkLinks} next tick, once, however many breaks happen this tick. */
    private void scheduleLinkCheck() {
        if (checkScheduled) {
            return;
        }
        checkScheduled = true;
        services.plugin().getServer().getScheduler().runTask(services.plugin(), this::checkLinks);
    }

    /**
     * Unlinks every loaded golem whose chest is no longer a golden chest, then any golems beyond a
     * chest's capacity (a double chest that lost a half only holds one). Links into unloaded chunks
     * are left alone: we can't see the chest without loading it.
     */
    private void checkLinks() {
        checkScheduled = false;
        ChestLinker linker = services.linker();
        Map<BlockLocation, List<GoldenGolem>> byChest = new LinkedHashMap<>();
        for (GoldenGolem golem : services.registry().all()) {
            BlockLocation link = golem.chest();
            if (link == null || !golem.isValid()) {
                continue;
            }
            Block block = loadedBlock(link);
            if (block == null) {
                continue;
            }
            if (!services.chests().isGoldenChest(block)) {
                unlink(golem);
                continue;
            }
            byChest.computeIfAbsent(BlockLocation.of(linker.canonical(block)), key -> new ArrayList<>()).add(golem);
        }
        for (Map.Entry<BlockLocation, List<GoldenGolem>> entry : byChest.entrySet()) {
            Block chest = entry.getKey().block();
            List<GoldenGolem> linked = entry.getValue();
            int capacity = chest == null ? 0 : services.chests().golemCapacity(chest);
            for (int i = capacity; i < linked.size(); i++) {
                unlink(linked.get(i));
            }
        }
    }

    private void unlink(GoldenGolem golem) {
        golem.setChest(null);
        GolemState state = golem.state();
        if (state == GolemState.WORKING || state == GolemState.RETURNING || state == GolemState.DEPOSITING) {
            golem.setBlockedReason(BlockedReason.CHEST_MISSING);
            services.controller().transition(golem, GolemState.BLOCKED);
        }
        services.feedback().tell(golem, "Golden Golem lost its golden chest");
    }

    private static @Nullable Block loadedBlock(BlockLocation location) {
        World world = location.loadedWorld();
        BlockPos pos = location.pos();
        if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return null;
        }
        return world.getBlockAt(pos.x(), pos.y(), pos.z());
    }
}
