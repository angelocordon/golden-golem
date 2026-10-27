package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.GolemConfig;
import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.work.Deposit;
import dev.angelocordon.goldengolem.work.Durability;
import dev.angelocordon.goldengolem.work.HarvestRule;
import dev.angelocordon.goldengolem.work.HarvestRule.ToolKind;
import dev.angelocordon.goldengolem.work.ToolSwap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * At the chest: swaps a worn or missing tool (LAB-21), empties storage into the chest (LAB-20),
 * then picks the next state.
 *
 * <p>All item moves happen in the first tick, in one go, so a golem that dies or unloads while
 * standing at the chest can't lose or duplicate anything. The rest of the visit only waits
 * {@link #LID_TICKS} with the lid open so players can see it work. The lid's close is scheduled
 * when it opens, so it closes even if the golem never ticks again.
 */
public final class DepositingHandler implements StateHandler {

    /** How long the lid stays open, in ticks. */
    static final int LID_TICKS = 10;
    /** Farther than this from the chest's center (pushed away), walk back first. */
    private static final double REACH_DISTANCE = 3.0;

    /** What the item moves did, kept until the lid wait ends and the next state is decided. */
    private record Visit(int doneAt, boolean allDeposited, SwapResult swap) {
    }

    /** The next state, and the blocked reason when that state is BLOCKED. */
    record Outcome(GolemState state, @Nullable BlockedReason reason) {

        static Outcome to(GolemState state) {
            return new Outcome(state, null);
        }

        static Outcome blocked(BlockedReason reason) {
            return new Outcome(GolemState.BLOCKED, reason);
        }
    }

    /** What the tool swap did. */
    enum SwapResult { KEPT, SWAPPED, NO_TOOL, CHEST_FULL }

    private final Services services;
    private final Map<UUID, Visit> visits = new HashMap<>();

    public DepositingHandler(Services services) {
        this.services = services;
    }

    @Override
    public void onEnter(GoldenGolem golem) {
        services.movers().of(golem).stop();
        visits.remove(golem.id());
    }

    @Override
    public void onExit(GoldenGolem golem) {
        visits.remove(golem.id());
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        int now = services.plugin().getServer().getCurrentTick();
        Visit visit = visits.get(golem.id());
        if (visit != null) {
            if (now < visit.doneAt()) {
                return GolemState.DEPOSITING;
            }
            // Decided now, not when the items moved: the menu may have changed the tool, target or
            // work center during the wait.
            return apply(golem, decide(golem, visit));
        }

        Block chest = ReturningHandler.loadedGoldenChest(services, golem);
        if (chest == null) {
            return StateHandler.block(golem, BlockedReason.CHEST_MISSING);
        }
        if (!services.movers().of(golem).isNear(chest.getLocation().toCenterLocation(), REACH_DISTANCE)) {
            return GolemState.RETURNING;
        }
        Inventory inventory = services.chests().inventoryOf(chest);
        if (inventory == null) {
            return StateHandler.block(golem, BlockedReason.CHEST_MISSING);
        }

        openLid(chest);
        // Swap first: taking a tool out of a full chest frees a slot this deposit can use.
        SwapResult swap = swapTool(golem, inventory);
        boolean allDeposited = depositStorage(golem, inventory);
        visits.put(golem.id(), new Visit(now + LID_TICKS, allDeposited, swap));
        return GolemState.DEPOSITING;
    }

    private static GolemState apply(GoldenGolem golem, Outcome outcome) {
        BlockedReason reason = outcome.reason();
        return reason == null ? outcome.state() : StateHandler.block(golem, reason);
    }

    private static Outcome decide(GoldenGolem golem, Visit visit) {
        ItemStack tool = golem.tool();
        return next(visit.allDeposited(), visit.swap(), golem.workCenter() != null, golem.target(),
                tool == null ? null : tool.getType());
    }

    /** Moves storage into the chest; whatever doesn't fit stays in storage. True if all fit. */
    private boolean depositStorage(GoldenGolem golem, Inventory inventory) {
        @Nullable ItemStack[] storage = golem.storage();
        Deposit.Result<ItemStack> result = Deposit.into(
                chestSlots(inventory), Arrays.asList(storage), Deposit.itemStacks(inventory.getMaxStackSize()));
        // Both writes in the same tick: nothing can observe the items in two places or none.
        inventory.setStorageContents(result.chest().toArray(new ItemStack[0]));
        golem.setStorage(result.leftovers().toArray(new ItemStack[0]));
        return result.allDeposited();
    }

    /**
     * Swaps a worn or missing tool for the best acceptable one in the chest ({@link ToolSwap}).
     * Nothing is written unless the whole swap fits, which keeps item counts exact.
     */
    private SwapResult swapTool(GoldenGolem golem, Inventory inventory) {
        GolemConfig config = services.config();
        ItemStack worn = golem.tool();
        if (worn != null && !Durability.isLow(worn, config)) {
            return SwapResult.KEPT;
        }
        ToolKind wornKind = worn == null ? null : ToolKind.of(worn.getType()).orElse(null);
        Material target = golem.target();
        List<@Nullable ItemStack> slots = chestSlots(inventory);

        List<ToolSwap.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack item = slots.get(i);
            if (item != null && ToolSwap.acceptable(item.getType(), wornKind, target)) {
                candidates.add(new ToolSwap.Candidate(
                        i, item.getType(), Durability.usesLeft(item), Durability.isLow(item, config)));
            }
        }
        Optional<ToolSwap.Candidate> pick = ToolSwap.pick(candidates);
        if (pick.isEmpty()) {
            return SwapResult.NO_TOOL;
        }
        Optional<ToolSwap.Applied<ItemStack>> applied = ToolSwap.apply(
                slots, worn, pick.get().slot(), Deposit.itemStacks(inventory.getMaxStackSize()));
        if (applied.isEmpty()) {
            return SwapResult.CHEST_FULL;
        }
        inventory.setStorageContents(applied.get().chest().toArray(new ItemStack[0]));
        golem.setTool(applied.get().taken());
        return SwapResult.SWAPPED;
    }

    /**
     * The state after the visit, per the contract with WORKING: back to WORKING whenever there is
     * a work center and a tool that fits the target (WORKING itself notices an empty area), IDLE
     * without a work center or target, otherwise blocked with the reason it can't go on. Items
     * left over in storage win over everything. Pure, so it is unit-tested.
     */
    static Outcome next(boolean allDeposited, SwapResult swap, boolean hasWorkCenter,
            @Nullable Material target, @Nullable Material tool) {
        if (!allDeposited || swap == SwapResult.CHEST_FULL) {
            return Outcome.blocked(BlockedReason.CHEST_FULL);
        }
        if (!hasWorkCenter || target == null) {
            return Outcome.to(GolemState.IDLE);
        }
        if (swap == SwapResult.NO_TOOL || tool == null) {
            return Outcome.blocked(BlockedReason.NO_TOOL);
        }
        if (!HarvestRule.toolFits(tool, target)) {
            return Outcome.blocked(BlockedReason.CANT_HARVEST);
        }
        return Outcome.to(GolemState.WORKING);
    }

    /** The chest's slots with empty stacks as null, which is what {@link Deposit} expects. */
    private static List<@Nullable ItemStack> chestSlots(Inventory inventory) {
        @Nullable ItemStack[] contents = inventory.getStorageContents();
        List<@Nullable ItemStack> slots = new ArrayList<>(contents.length);
        for (ItemStack item : contents) {
            slots.add(item == null || item.isEmpty() ? null : item);
        }
        return slots;
    }

    /**
     * Opens the lid and schedules its close. The chest plays its own open and close sounds. The
     * close is a sync task, so it runs on the main thread whether the golem leaves, dies or
     * unloads meanwhile.
     */
    private void openLid(Block chest) {
        if (chest.getState(false) instanceof Lidded lid) {
            lid.open();
            BlockLocation location = BlockLocation.of(chest);
            services.plugin().getServer().getScheduler()
                    .runTaskLater(services.plugin(), () -> closeLid(location), LID_TICKS);
        }
    }

    /**
     * Closes the lid if the chest is still loaded. If its chunk unloaded, there's nothing to do:
     * the open flag isn't saved, so the lid is closed when the chunk loads again.
     */
    private static void closeLid(BlockLocation location) {
        World world = location.loadedWorld();
        if (world == null || !world.isChunkLoaded(location.pos().x() >> 4, location.pos().z() >> 4)) {
            return;
        }
        Block block = world.getBlockAt(location.pos().x(), location.pos().y(), location.pos().z());
        if (block.getState(false) instanceof Lidded lid) {
            lid.close();
        }
    }
}
