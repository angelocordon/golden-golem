package dev.angelocordon.goldengolem.work;

/**
 * When a tool counts as worn out, so the golem goes home before it breaks.
 *
 * <p>TODO(LAB-18): implement. Stub: never low.
 */
public final class Durability {

    private Durability() {
    }

    /**
     * True if the uses left ({@code maxDamage - damage}) are at or below {@code minUses}, or below
     * {@code percent} percent of {@code maxDamage}.
     */
    public static boolean isLow(int maxDamage, int damage, double percent, int minUses) {
        return false;
    }
}
