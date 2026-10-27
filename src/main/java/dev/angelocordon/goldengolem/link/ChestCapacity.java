package dev.angelocordon.goldengolem.link;

/** How many more golems a golden chest can take. Pure, so it is unit-tested. */
public final class ChestCapacity {

    private ChestCapacity() {
    }

    /**
     * Free golem slots for a chest that allows {@code capacity} golems and already has
     * {@code linkedCount}. Never negative: a chest that lost a half can be over capacity.
     */
    public static int free(int capacity, int linkedCount) {
        return Math.max(0, capacity - linkedCount);
    }
}
