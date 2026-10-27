package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.work.Deposit;
import dev.angelocordon.goldengolem.work.HarvestRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Stands still, tells the owner why it's stuck, and rechecks every {@link #RECHECK_TICKS} ticks.
 * The decisions live in {@link BlockedRecheck}; this class only gathers the facts.
 */
public final class BlockedHandler implements StateHandler {

    static final int RECHECK_TICKS = 60;

    private final Services services;
    /** Ticks left until each blocked golem's next recheck. Pruned in onExit and on each recheck. */
    private final Map<UUID, Integer> untilRecheck = new HashMap<>();
    /**
     * Golems leaving for an UNREACHABLE retry. Their problem isn't known to be fixed, so their
     * announcement isn't reset, or a golem that keeps failing would say "no" every 3 seconds.
     */
    private final Set<UUID> retrying = new HashSet<>();

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
        if (!retrying.remove(golem.id())) {
            // Fixed, or a player stepped in: if it blocks again for the same reason, say so.
            services.feedback().cleared(golem);
        }
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
        retrying.removeIf(id -> services.registry().get(id).isEmpty());
        return recheck(golem);
    }

    private GolemState recheck(GoldenGolem golem) {
        BlockedReason reason = golem.blockedReason();
        BlockedRecheck.Facts facts = facts(golem);
        GolemState next = BlockedRecheck.decide(reason, facts);
        if (next != GolemState.BLOCKED) {
            if (reason == BlockedReason.UNREACHABLE) {
                retrying.add(golem.id());
            }
            return next;
        }
        BlockedReason current = BlockedRecheck.currentReason(reason, facts);
        if (current != null && current != reason) {
            StateHandler.block(golem, current);
            services.feedback().blocked(golem, current);
        }
        return GolemState.BLOCKED;
    }

    private BlockedRecheck.Facts facts(GoldenGolem golem) {
        ItemStack tool = golem.tool();
        Material target = golem.target();
        boolean hasUsableTool = BlockedRecheck.usableTool(tool, services.config());
        boolean toolFits = tool != null && target != null && HarvestRule.toolCanHarvest(tool, target);

        Inventory chest = chestInventory(golem.chest());
        boolean chestHasRoom = chest != null && hasRoom(chest, golem.storage());

        return new BlockedRecheck.Facts(hasUsableTool, toolFits, chest != null, chestHasRoom,
                golem.workCenter() != null);
    }

    /**
     * True if a deposit would move at least one item: an empty slot, or room in a partial stack.
     * Uses the same {@link Deposit} merge as DEPOSITING, so the two can't disagree and bounce.
     */
    private static boolean hasRoom(Inventory chest, @Nullable ItemStack[] storage) {
        if (chest.firstEmpty() != -1) {
            return true;
        }
        List<@Nullable ItemStack> carried = nonEmpty(storage);
        Deposit.Result<ItemStack> trial = Deposit.into(
                nonEmpty(chest.getStorageContents()), carried, Deposit.itemStacks(chest.getMaxStackSize()));
        return totalAmount(trial.leftovers()) < totalAmount(carried);
    }

    private static List<@Nullable ItemStack> nonEmpty(@Nullable ItemStack[] slots) {
        List<@Nullable ItemStack> list = new ArrayList<>(slots.length);
        for (ItemStack slot : slots) {
            list.add(slot == null || slot.isEmpty() ? null : slot);
        }
        return list;
    }

    private static int totalAmount(List<@Nullable ItemStack> slots) {
        int total = 0;
        for (ItemStack slot : slots) {
            if (slot != null) {
                total += slot.getAmount();
            }
        }
        return total;
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
