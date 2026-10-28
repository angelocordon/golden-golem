package dev.angelocordon.goldengolem.link;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.util.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;

/**
 * Relinks a following golem when the player it follows right-clicks a golden chest, and blocks golems
 * whose chest is broken.
 *
 * <p>Breaks are handled in two steps. While the chest is still readable, each broken golden half
 * is recorded with its surviving golden partner (if any). One tick later, once the world has
 * settled, {@link #checkLinks} reads what is actually there and lets {@link LinkCheck} decide.
 * Reading the world afterwards, rather than trusting the event, matters because the golden chest
 * listener removes golden chests from explosion lists and breaks them itself at HIGHEST, and
 * cancels the wither's block change, so later handlers can't see those chests in the event.
 */
public final class ChestLinkListener implements Listener {

    /** How close a following golem must be to its leader to be relinked by a chest click. */
    static final double RELINK_RADIUS = 8;

    private final Services services;
    /** Broken golden halves since the last check, mapped to their surviving golden partner. */
    private final Map<BlockLocation, @Nullable BlockLocation> survivors = new HashMap<>();
    private boolean checkScheduled;

    public ChestLinkListener(Services services) {
        this.services = services;
        // Catches a chest that broke while its golem was unloaded, once both are loaded again.
        services.registry().onGolemLoaded(golem -> scheduleLinkCheck());
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
        Player player = event.getPlayer();
        if (block == null || isUsingItem(player) || !services.chests().isGoldenChest(block)) {
            return;
        }
        Optional<GoldenGolem> golem = nearestFollowingGolem(player, block);
        if (golem.isPresent() && services.linker().relink(golem.get(), block)) {
            event.setCancelled(true);
        }
    }

    /** The block is still there at MONITOR, so its partner can be read. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        recordIfGolden(event.getBlock());
    }

    /**
     * LOWEST: golden chests are still in the list here, before the golden chest listener takes
     * them out at HIGHEST. Not ignoreCancelled, and recording is safe even if the explosion is
     * cancelled later: the check only moves a link off a half that is really gone.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::recordIfGolden);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::recordIfGolden);
    }

    /**
     * Withers break blocks through this event. Any change away from a chest counts: a waterlogged
     * chest turns to water, not air. Filtering on the chest also skips falling-block spawns.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getTo() != Material.CHEST) {
            recordIfGolden(event.getBlock());
        }
    }

    private void recordIfGolden(Block block) {
        if (block.getType() != Material.CHEST || !services.chests().isGoldenChest(block)) {
            return;
        }
        Block partner = services.linker().partnerOf(block);
        survivors.put(BlockLocation.of(block), partner == null ? null : BlockLocation.of(partner));
        scheduleLinkCheck();
    }

    /** Sneaking with an item uses the item (e.g. places a block) instead of opening the chest. */
    private static boolean isUsingItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        return player.isSneaking()
                && !(inventory.getItemInMainHand().isEmpty() && inventory.getItemInOffHand().isEmpty());
    }

    private Optional<GoldenGolem> nearestFollowingGolem(Player player, Block chest) {
        UUID playerId = player.getUniqueId();
        Location at = player.getLocation();
        List<GoldenGolem> following = new ArrayList<>();
        for (GoldenGolem golem : services.registry().all()) {
            if (!golem.isValid() || golem.state() != GolemState.FOLLOWING || !golem.isLedBy(playerId)) {
                continue;
            }
            Location golemAt = golem.entity().getLocation();
            if (golemAt.getWorld().equals(at.getWorld())
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
     * Gathers every loaded golem's link and the golden chests involved, then applies
     * {@link LinkCheck#decide}. Links into unloaded chunks are left out: we can't see the chest
     * without loading it.
     */
    private void checkLinks() {
        checkScheduled = false;
        // HashMap, not Map.copyOf: a broken single chest maps to a null survivor.
        Map<BlockLocation, @Nullable BlockLocation> survivorsNow = new HashMap<>(survivors);
        survivors.clear();

        Map<UUID, GoldenGolem> golems = new HashMap<>();
        List<LinkCheck.Link> links = new ArrayList<>();
        Map<BlockLocation, LinkCheck.ChestInfo> golden = new HashMap<>();
        for (GoldenGolem golem : services.registry().all()) {
            BlockLocation link = golem.chest();
            if (link == null || !golem.isValid() || loadedBlock(link) == null) {
                continue;
            }
            golems.put(golem.id(), golem);
            links.add(new LinkCheck.Link(golem.id(), link));
            addIfGolden(golden, link);
            BlockLocation survivor = survivorsNow.get(link);
            if (survivor != null) {
                addIfGolden(golden, survivor);
            }
        }

        LinkCheck.Result result = LinkCheck.decide(links, golden, survivorsNow);
        result.moved().forEach((id, chest) -> golems.get(id).setChest(chest));
        for (UUID id : result.missing()) {
            unlink(golems.get(id), "Golden Golem lost its golden chest");
        }
        for (UUID id : result.overCapacity()) {
            unlink(golems.get(id), "Golden Golem's chest is full, so it was unlinked");
        }
    }

    private void addIfGolden(Map<BlockLocation, LinkCheck.ChestInfo> golden, BlockLocation location) {
        if (golden.containsKey(location)) {
            return;
        }
        Block block = loadedBlock(location);
        if (block != null && services.chests().isGoldenChest(block)) {
            BlockLocation canonical = BlockLocation.of(services.linker().canonical(block));
            golden.put(location, new LinkCheck.ChestInfo(canonical, services.chests().golemCapacity(block)));
        }
    }

    private void unlink(GoldenGolem golem, String message) {
        golem.setChest(null);
        GolemState state = golem.state();
        if (state == GolemState.WORKING || state == GolemState.RETURNING || state == GolemState.DEPOSITING) {
            golem.setBlockedReason(BlockedReason.CHEST_MISSING);
            services.controller().transition(golem, GolemState.BLOCKED);
        }
        services.feedback().tell(golem, message);
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
