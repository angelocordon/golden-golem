package dev.angelocordon.goldengolem.work;

/**
 * When a tool counts as worn out, so the golem goes home before it breaks. Pure arithmetic so the
 * caller reads max damage, damage and the unbreakable flag off the item and passes them in.
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
}
