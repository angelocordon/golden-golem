package dev.angelocordon.goldengolem.work;

import dev.angelocordon.goldengolem.util.BlockPos;

/**
 * Finds the nearest matching block in a work area, a few hundred positions per tick so a big
 * area never causes a lag spike. One scanner per golem.
 *
 * <p>TODO(LAB-18): implement. Stub: always reports the area as empty.
 */
public final class AreaScanner {

    /** The outcome of one {@link #step}. */
    public sealed interface Result permits Found, Scanning, Exhausted {
    }

    /** A matching block, nearest to the center among those not blacklisted. */
    public record Found(BlockPos pos) implements Result {
    }

    /** Still looking; call {@link #step} again next tick. */
    public record Scanning() implements Result {
    }

    /** Checked the whole area and found nothing. */
    public record Exhausted() implements Result {
    }

    private final int radius;
    private final int verticalRange;
    private final int blocksPerTick;

    public AreaScanner(int radius, int verticalRange, int blocksPerTick) {
        this.radius = radius;
        this.verticalRange = verticalRange;
        this.blocksPerTick = blocksPerTick;
    }

    /** Starts a fresh scan around {@code center}. Keeps the blacklist. */
    public void reset(BlockPos center) {
    }

    /** Checks up to {@code blocksPerTick} positions. {@code nowTick} is used for blacklist expiry. */
    public Result step(BlockProbe probe, long nowTick) {
        return new Exhausted();
    }

    /** Skips {@code pos} until {@code untilTick} (e.g. unreachable, unsafe, or claim-protected). */
    public void blacklist(BlockPos pos, long untilTick) {
    }
}
