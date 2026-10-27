package dev.angelocordon.goldengolem.link;

import dev.angelocordon.goldengolem.chest.GoldenChests;
import dev.angelocordon.goldengolem.golem.Feedback;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemRegistry;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.util.BlockPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chest;
import org.jspecify.annotations.Nullable;

/**
 * Links golems to golden chests, respecting each chest's capacity.
 *
 * <p>A double chest counts as one chest: links to either half count against it, and new links are
 * stored against its canonical half (see {@link ChestHalves}). Only golems in loaded chunks are
 * counted, since unloaded ones aren't in the registry. Main thread only.
 */
public final class ChestLinker {

    /** How far from a newly built golem to look for a golden chest. */
    static final int AUTO_LINK_RADIUS = 4;

    private final GolemRegistry registry;
    private final GoldenChests chests;
    private final Feedback feedback;

    public ChestLinker(GolemRegistry registry, GoldenChests chests, Feedback feedback) {
        this.registry = registry;
        this.chests = chests;
        this.feedback = feedback;
    }

    /** Links a newly built golem to the nearest golden chest with room, if any is close. */
    public void autoLink(GoldenGolem golem) {
        Block chest = nearestChestWithRoom(golem);
        if (chest == null) {
            feedback.tell(golem, "Golden Golem has no golden chest nearby. Right-click one while it follows you.");
            return;
        }
        link(golem, chest);
    }

    /**
     * Links {@code golem} to the golden chest {@code chest} (either half) if it has room, and tells
     * the owner either way. Returns true only if a new link was made.
     */
    public boolean relink(GoldenGolem golem, Block chest) {
        if (isLinkedTo(golem, chest)) {
            feedback.tell(golem, "Golden Golem is already linked to this chest");
            return false;
        }
        if (freeCapacity(chest, golem) <= 0) {
            feedback.tell(golem, "This golden chest has no room for another golem");
            return false;
        }
        link(golem, chest);
        return true;
    }

    /** True if {@code golem} is linked to either half of {@code chest}. */
    boolean isLinkedTo(GoldenGolem golem, Block chest) {
        BlockLocation link = golem.chest();
        return link != null && halves(chest).contains(link);
    }

    /** The canonical half of a chest: its partner for a RIGHT half of a double chest, else itself. */
    Block canonical(Block chest) {
        if (chest.getBlockData() instanceof Chest data) {
            BlockPos canonical = ChestHalves.canonical(BlockPos.of(chest), data.getFacing(), data.getType());
            if (!canonical.equals(BlockPos.of(chest))) {
                Block partner = partnerOf(chest);
                if (partner != null) {
                    return partner;
                }
            }
        }
        return chest;
    }

    /**
     * The other half of a golden double chest, or null if it is single, the partner's chunk isn't
     * loaded, or the block there isn't the matching golden half.
     */
    @Nullable Block partnerOf(Block chest) {
        if (!(chest.getBlockData() instanceof Chest data)) {
            return null;
        }
        // Check the partner's chunk first: at a chunk border the lookup would otherwise load it.
        BlockPos pos = ChestHalves.partner(BlockPos.of(chest), data.getFacing(), data.getType());
        if (pos == null || !isChunkLoaded(chest.getWorld(), pos)) {
            return null;
        }
        Block partner = chests.partnerOf(chest);
        return partner != null && chests.isGoldenChest(partner) ? partner : null;
    }

    /** Free golem slots on {@code chest} (either half), not counting {@code except} if linked. */
    int freeCapacity(Block chest, @Nullable GoldenGolem except) {
        return ChestCapacity.free(chests.golemCapacity(chest), linkedCount(chest, except));
    }

    private int linkedCount(Block chest, @Nullable GoldenGolem except) {
        Set<BlockLocation> halves = halves(chest);
        int count = 0;
        for (GoldenGolem golem : registry.all()) {
            BlockLocation link = golem.chest();
            // Set.of rejects contains(null), so check the link first.
            if (link != null && golem.isValid() && !golem.equals(except) && halves.contains(link)) {
                count++;
            }
        }
        return count;
    }

    private Set<BlockLocation> halves(Block chest) {
        Block partner = partnerOf(chest);
        return partner == null
                ? Set.of(BlockLocation.of(chest))
                : Set.of(BlockLocation.of(chest), BlockLocation.of(partner));
    }

    private void link(GoldenGolem golem, Block chest) {
        golem.setChest(BlockLocation.of(canonical(chest)));
        feedback.tell(golem, "Golden Golem linked to this golden chest");
    }

    /**
     * Scans the cube around the golem once (it's built rarely, so 9x9x9 blocks is fine) and returns
     * the nearest golden chest half with room. Unloaded chunks are skipped so the scan never loads
     * them.
     */
    private @Nullable Block nearestChestWithRoom(GoldenGolem golem) {
        World world = golem.entity().getWorld();
        BlockPos center = BlockPos.of(golem.entity().getLocation());
        Map<BlockPos, Integer> freeByChest = new HashMap<>();
        List<Block> candidates = new ArrayList<>();
        for (int dx = -AUTO_LINK_RADIUS; dx <= AUTO_LINK_RADIUS; dx++) {
            for (int dz = -AUTO_LINK_RADIUS; dz <= AUTO_LINK_RADIUS; dz++) {
                BlockPos column = center.offset(dx, 0, dz);
                if (!isChunkLoaded(world, column)) {
                    continue;
                }
                for (int dy = -AUTO_LINK_RADIUS; dy <= AUTO_LINK_RADIUS; dy++) {
                    Block block = world.getBlockAt(column.x(), column.y() + dy, column.z());
                    if (block.getType() != Material.CHEST || !chests.isGoldenChest(block)) {
                        continue;
                    }
                    // Both halves of a double chest share one free count; compute it once.
                    int free = freeByChest.computeIfAbsent(BlockPos.of(canonical(block)),
                            key -> freeCapacity(block, golem));
                    if (free > 0) {
                        candidates.add(block);
                    }
                }
            }
        }
        Optional<Block> nearest = Nearest.of(center, candidates, BlockPos::of);
        return nearest.orElse(null);
    }

    private static boolean isChunkLoaded(World world, BlockPos pos) {
        return world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4);
    }
}
