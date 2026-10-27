package dev.angelocordon.goldengolem.work;

import dev.angelocordon.goldengolem.GolemConfig;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * When a tool counts as worn out, so the golem goes home before it breaks. The arithmetic is pure;
 * {@link #isLow(ItemStack, GolemConfig)} and {@link #usesLeft(ItemStack)} read an item for it.
 *
 * <p>WORKING, DEPOSITING and the BLOCKED recheck must all use {@link #isLow(ItemStack,
 * GolemConfig)}: if any of them judged a tool differently, the golem would loop between states
 * (one sends it home for a new tool, the next keeps the old one and sends it back).
 */
public final class Durability {

    private Durability() {
    }

    /**
     * True if the uses left ({@code maxDamage - damage}) are at or below {@code minUses}, or below
     * {@code percent} percent of {@code maxDamage}. A tool with one use left is always low, even
     * with both thresholds at 0, because that use would break it. An item with
     * {@code maxDamage <= 0} has no durability and is never low.
     */
    public static boolean isLow(int maxDamage, int damage, double percent, int minUses) {
        return isLow(maxDamage, damage, percent, minUses, false);
    }

    /** As {@link #isLow(int, int, double, int)}, but an unbreakable tool is never low. */
    public static boolean isLow(
            int maxDamage, int damage, double percent, int minUses, boolean unbreakable) {
        if (unbreakable || maxDamage <= 0) {
            return false;
        }
        long usesLeft = (long) maxDamage - damage;
        // In vanilla a tool breaks when damage reaches maxDamage, so never let it get to its
        // last use.
        return usesLeft <= Math.max(1, minUses) || usesLeft < maxDamage * percent / 100.0;
    }

    /**
     * True if {@code tool} is worn out under the config thresholds, read from its data components
     * (max damage, damage, unbreakable). Null or empty is not low: the caller handles "no tool".
     */
    public static boolean isLow(@Nullable ItemStack tool, GolemConfig config) {
        if (tool == null || tool.isEmpty()) {
            return false;
        }
        return isLow(tool.getData(DataComponentTypes.MAX_DAMAGE),
                tool.getDataOrDefault(DataComponentTypes.DAMAGE, 0),
                tool.hasData(DataComponentTypes.UNBREAKABLE), config);
    }

    /**
     * {@link #isLow(ItemStack, GolemConfig)} on plain values, so it is unit-tested. No max damage
     * component means no durability, which is never low.
     */
    public static boolean isLow(@Nullable Integer maxDamage, int damage, boolean unbreakable, GolemConfig config) {
        return isLow(maxDamage == null ? 0 : maxDamage, damage,
                config.lowDurabilityPercent(), config.lowDurabilityUses(), unbreakable);
    }

    /** Uses before {@code tool} breaks, read from its data components. */
    public static int usesLeft(ItemStack tool) {
        return usesLeft(tool.getData(DataComponentTypes.MAX_DAMAGE),
                tool.getDataOrDefault(DataComponentTypes.DAMAGE, 0),
                tool.hasData(DataComponentTypes.UNBREAKABLE));
    }

    /** Max damage minus damage, or {@link Integer#MAX_VALUE} if it never breaks. */
    public static int usesLeft(@Nullable Integer maxDamage, int damage, boolean unbreakable) {
        if (maxDamage == null || unbreakable) {
            return Integer.MAX_VALUE;
        }
        return maxDamage - damage;
    }
}
