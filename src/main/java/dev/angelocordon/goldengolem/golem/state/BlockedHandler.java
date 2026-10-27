package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.work.HarvestRule;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Stands still, tells the owner why it's stuck, and rechecks every {@link #RECHECK_TICKS} ticks.
 * The decision lives in {@link BlockedRecheck}; this class only gathers the facts.
 */
public final class BlockedHandler implements StateHandler {

    static final int RECHECK_TICKS = 60;

    private final Services services;
    /** Ticks left until each blocked golem's next recheck. Pruned in onExit and on each recheck. */
    private final Map<UUID, Integer> untilRecheck = new HashMap<>();

    public BlockedHandler(Services services) {
        this.services = services;
    }

    @Override
    public void onEnter(GoldenGolem golem) {
        services.movers().of(golem).stop();
        untilRecheck.put(golem.id(), RECHECK_TICKS);
        BlockedReason reason = golem.blockedReason();
        if (reason != null) {
            services.feedback().blocked(golem, reason);
        }
    }

    @Override
    public void onExit(GoldenGolem golem) {
        untilRecheck.remove(golem.id());
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        // A golem loaded already BLOCKED (after a restart) never ran onEnter, so start its timer here.
        int left = untilRecheck.getOrDefault(golem.id(), RECHECK_TICKS) - 1;
        if (left > 0) {
            untilRecheck.put(golem.id(), left);
            return GolemState.BLOCKED;
        }
        untilRecheck.put(golem.id(), RECHECK_TICKS);
        // onExit doesn't run for golems that unload or die while blocked, so drop those here.
        untilRecheck.keySet().removeIf(id -> services.registry().get(id).isEmpty());
        return recheck(golem);
    }

    private GolemState recheck(GoldenGolem golem) {
        ItemStack tool = golem.tool();
        Material target = golem.target();
        boolean hasTool = tool != null && !tool.isEmpty();
        boolean toolFits = hasTool && target != null && toolFits(tool, target);

        Inventory chest = chestInventory(golem.chest());
        boolean chestHasRoom = chest != null && chest.firstEmpty() != -1;

        return BlockedRecheck.decide(golem.blockedReason(), hasTool, toolFits, chest != null, chestHasRoom,
                golem.workCenter() != null);
    }

    /**
     * The right kind of tool ({@link HarvestRule#toolFits}) and a high enough tier, so a wooden
     * pickaxe on diamond ore stays CANT_HARVEST instead of bouncing back to WORKING.
     */
    private static boolean toolFits(ItemStack tool, Material target) {
        return HarvestRule.toolFits(tool.getType(), target)
                && target.isBlock()
                && target.createBlockData().isPreferredTool(tool);
    }

    /**
     * The linked golden chest's inventory, or null if there's no link, the chest's chunk isn't
     * loaded (we never load chunks for a recheck), or it isn't a golden chest any more.
     */
    private @Nullable Inventory chestInventory(@Nullable BlockLocation link) {
        if (link == null) {
            return null;
        }
        World world = link.loadedWorld();
        if (world == null || !world.isChunkLoaded(link.pos().x() >> 4, link.pos().z() >> 4)) {
            return null;
        }
        Block block = world.getBlockAt(link.pos().x(), link.pos().y(), link.pos().z());
        if (!services.chests().isGoldenChest(block)) {
            return null;
        }
        return services.chests().inventoryOf(block);
    }
}
