package dev.angelocordon.goldengolem.work;

import dev.angelocordon.goldengolem.GolemConfig;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The Bukkit side of a golem breaking one block: crack overlay, the owner's
 * {@link BlockBreakEvent}, drops, the break effect and tool wear. The timing and the decisions
 * live in pure classes ({@link BreakTime}, {@link HazardCheck}); this only touches the world.
 * Main thread only.
 */
public final class BlockBreaker {

    /** Players this close to the block see the golem's crack overlay. */
    static final double CRACK_VIEW_DISTANCE = 32.0;

    private BlockBreaker() {
    }

    /** Ticks to break {@code block} with {@code tool}, or {@link BreakTime#NEVER}. */
    public static int ticksToBreak(Block block, ItemStack tool) {
        float perTick = BreakTime.progressPerTick(block.getDestroySpeed(tool, true),
                block.getType().getHardness(), block.isPreferredTool(tool));
        return BreakTime.ticksToBreak(perTick);
    }

    /**
     * Shows the crack overlay at {@code progress} (0 exclusive to 1) to nearby players, as the
     * golem's own damage so it doesn't clash with a player mining the same block.
     */
    public static void showCracks(Block block, float progress, Entity source) {
        sendDamage(block, Math.max(0.01f, Math.min(1f, progress)), source, CRACK_VIEW_DISTANCE);
    }

    /**
     * Removes the overlay. Progress 0 is the API's reset; sent to the whole world's players so
     * someone who walked away mid-break doesn't keep a stale crack.
     */
    public static void clearCracks(Block block, Entity source) {
        sendDamage(block, 0f, source, Double.POSITIVE_INFINITY);
    }

    private static void sendDamage(Block block, float progress, Entity source, double maxDistance) {
        Location location = block.getLocation();
        double maxSquared = maxDistance * maxDistance;
        for (Player player : block.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= maxSquared) {
                player.sendBlockDamage(location, progress, source);
            }
        }
    }

    /**
     * Asks protection plugins whether {@code owner} may break the block, by firing a
     * {@link BlockBreakEvent} as them. The event never drops items: the golem collects the drops
     * itself.
     */
    public static boolean ownerMayBreak(Block block, Player owner) {
        BlockBreakEvent event = new BlockBreakEvent(block, owner);
        event.setDropItems(false);
        event.setExpToDrop(0);
        return event.callEvent();
    }

    /**
     * Breaks the block as if mined with {@code tool} by {@code breaker} and returns its drops
     * (Fortune and Silk Touch apply) instead of spawning them. Plays the vanilla break particles
     * and sound. A waterlogged block leaves its water behind, as in vanilla.
     */
    public static List<ItemStack> breakBlock(Block block, ItemStack tool, Entity breaker) {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : block.getDrops(tool, breaker)) {
            if (!drop.isEmpty()) {
                drops.add(drop);
            }
        }
        BlockData data = block.getBlockData();
        boolean water = data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
        block.getWorld().playEffect(block.getLocation(), Effect.DESTROY_BLOCK_WITH_SOUND, data);
        block.setType(water ? Material.WATER : Material.AIR, true);
        return drops;
    }

    /**
     * Wears the tool by one use, with Unbreaking applied by the server. Returns the new tool;
     * empty only if it broke, which {@link #isLow} checks before every break rule out.
     */
    public static ItemStack wearTool(ItemStack tool, LivingEntity golem) {
        return tool.damage(1, golem);
    }

    /** True if the tool is worn out per the config ({@link Durability#isLow}). */
    public static boolean isLow(ItemStack tool, GolemConfig config) {
        Integer maxDamage = tool.getData(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage == null) {
            return false;
        }
        int damage = tool.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        return Durability.isLow(maxDamage, damage, config.lowDurabilityPercent(), config.lowDurabilityUses(),
                tool.hasData(DataComponentTypes.UNBREAKABLE));
    }
}
