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
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * At the chest: empties storage into it (LAB-20), swaps a worn or missing tool (LAB-21), then
 * picks the next state.
 *
 * <p>All item moves happen in the first tick, in one go, so a golem that dies or unloads while
 * standing at the chest can't lose or duplicate anything. The rest of the visit only keeps the
 * lid open for {@link #LID_TICKS} so players can see it work, then leaves.
 */
public final class DepositingHandler implements StateHandler {

    /** How long the lid stays open, in ticks. */
    static final int LID_TICKS = 10;
    /** Farther than this from the chest's center (pushed away), walk back first. */
    private static final double REACH_DISTANCE = 3.0;
    /** A lid whose golem never came back (unloaded mid-visit) is closed this long after its time. */
    private static final int STALE_LID_TICKS = 40;

    /** What the visit decided, applied once the lid has been open long enough. */
    private record Visit(BlockLocation chest, int closeAt, Outcome outcome) {
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
        closeLid(visits.remove(golem.id()));
    }

    @Override
    public void onExit(GoldenGolem golem) {
        closeLid(visits.remove(golem.id()));
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        int now = services.plugin().getServer().getCurrentTick();
        closeStaleLids(golem.id(), now);
        Visit visit = visits.get(golem.id());
        if (visit != null) {
            if (now < visit.closeAt()) {
                return GolemState.DEPOSITING;
            }
            // onExit closes the lid and forgets the visit.
            BlockedReason reason = visit.outcome().reason();
            return reason == null ? visit.outcome().state() : StateHandler.block(golem, reason);
        }

        Block chest = ReturningHandler.loadedGoldenChest(services, golem);
        if (chest == null) {
            return StateHandler.block(golem, BlockedReason.CHEST_MISSING);
        }
        Location center = chest.getLocation().toCenterLocation();
        if (!services.movers().of(golem).isNear(center, REACH_DISTANCE)) {
            return GolemState.RETURNING;
        }
        Inventory inventory = services.chests().inventoryOf(chest);
        if (inventory == null) {
            return StateHandler.block(golem, BlockedReason.CHEST_MISSING);
        }

        openLid(chest, center);
        boolean allDeposited = depositStorage(golem, inventory);
        SwapResult swap = swapTool(golem, inventory);
        ItemStack tool = golem.tool();
        Outcome outcome = next(allDeposited, swap, golem.workCenter() != null, golem.target(),
                tool == null ? null : tool.getType());
        visits.put(golem.id(), new Visit(BlockLocation.of(chest), now + LID_TICKS, outcome));
        return GolemState.DEPOSITING;
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
     * Swaps a worn or missing tool for the best acceptable one in the chest. The worn tool goes
     * into the chest only when a replacement comes out, so the golem keeps it otherwise. Nothing
     * is written unless the whole swap fits, which keeps item counts exact.
     */
    private SwapResult swapTool(GoldenGolem golem, Inventory inventory) {
        GolemConfig config = services.config();
        ItemStack worn = golem.tool();
        if (worn != null && !isLow(worn, config)) {
            return SwapResult.KEPT;
        }
        ToolKind wornKind = worn == null ? null : ToolKind.of(worn.getType()).orElse(null);
        Material target = golem.target();
        List<@Nullable ItemStack> slots = new ArrayList<>(chestSlots(inventory));

        List<ToolSwap.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack item = slots.get(i);
            if (item != null && ToolSwap.acceptable(item.getType(), wornKind, target)) {
                candidates.add(new ToolSwap.Candidate(i, item.getType(), usesLeft(item), isLow(item, config)));
            }
        }
        Optional<ToolSwap.Candidate> pick = ToolSwap.pick(candidates);
        if (pick.isEmpty()) {
            return SwapResult.NO_TOOL;
        }

        int slot = pick.get().slot();
        ItemStack stack = slots.get(slot);
        ItemStack taken = stack.asQuantity(1);
        slots.set(slot, stack.getAmount() > 1 ? stack.asQuantity(stack.getAmount() - 1) : null);
        if (worn != null) {
            Deposit.Result<ItemStack> stored = Deposit.into(
                    slots, List.of(worn), Deposit.itemStacks(inventory.getMaxStackSize()));
            if (!stored.allDeposited()) {
                return SwapResult.CHEST_FULL;
            }
            slots = stored.chest();
        }
        inventory.setStorageContents(slots.toArray(new ItemStack[0]));
        golem.setTool(taken);
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

    private static boolean isLow(ItemStack tool, GolemConfig config) {
        Integer maxDamage = tool.getData(DataComponentTypes.MAX_DAMAGE);
        return Durability.isLow(maxDamage == null ? 0 : maxDamage,
                tool.getDataOrDefault(DataComponentTypes.DAMAGE, 0),
                config.lowDurabilityPercent(), config.lowDurabilityUses(),
                tool.hasData(DataComponentTypes.UNBREAKABLE));
    }

    /** Uses before it breaks; {@link Integer#MAX_VALUE} if it never does. */
    private static int usesLeft(ItemStack tool) {
        Integer maxDamage = tool.getData(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage == null || tool.hasData(DataComponentTypes.UNBREAKABLE)) {
            return Integer.MAX_VALUE;
        }
        return maxDamage - tool.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
    }

    private void openLid(Block chest, Location center) {
        if (chest.getState(false) instanceof Lidded lid) {
            lid.open();
            chest.getWorld().playSound(center, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
        }
    }

    private void closeLid(@Nullable Visit visit) {
        if (visit == null) {
            return;
        }
        Block chest = loadedBlock(visit.chest());
        if (chest != null && chest.getState(false) instanceof Lidded lid) {
            lid.close();
            chest.getWorld().playSound(chest.getLocation().toCenterLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
        }
    }

    /**
     * Closes lids of golems that unloaded or died mid-visit (no onExit runs then), so a chest
     * isn't left open until the server restarts. The map only holds golems at a chest right now.
     */
    private void closeStaleLids(UUID current, int now) {
        Iterator<Map.Entry<UUID, Visit>> it = visits.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Visit> entry = it.next();
            if (!entry.getKey().equals(current) && now >= entry.getValue().closeAt() + STALE_LID_TICKS) {
                it.remove();
                closeLid(entry.getValue());
            }
        }
    }

    /** The block if its chunk is loaded, without loading it. */
    private static @Nullable Block loadedBlock(BlockLocation location) {
        World world = location.loadedWorld();
        if (world == null || !world.isChunkLoaded(location.pos().x() >> 4, location.pos().z() >> 4)) {
            return null;
        }
        return world.getBlockAt(location.pos().x(), location.pos().y(), location.pos().z());
    }
}
