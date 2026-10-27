package dev.angelocordon.goldengolem.lifecycle;

/** Gold-ingot healing math, kept pure for tests. */
public final class Healing {

    private Healing() {
    }

    /** True if there's anything to heal; at full health the ingot isn't used. */
    public static boolean needsHealing(double current, double max) {
        return current < max;
    }

    /** {@code current} plus {@code amount}, never above {@code max}. */
    public static double healed(double current, double max, double amount) {
        return Math.min(max, current + amount);
    }
}
