package dev.angelocordon.goldengolem.work;

import dev.angelocordon.goldengolem.GolemConfig;
import dev.angelocordon.goldengolem.util.BlockPos;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Finds the nearest matching block in a work area, a few hundred positions per tick so a big
 * area never causes a lag spike. One scanner per golem.
 *
 * <p>The area is a box around the center: up to {@code radius} blocks on X and Z (capped at
 * {@link GolemConfig#MAX_RADIUS}) and {@code verticalRange} blocks up and down. Positions are
 * visited nearest-first using an offset list sorted by squared distance, ties broken by dy, then
 * dx, then dz (all ascending), so the scan order is the same on every run. The offset list is
 * built once per (radius, verticalRange) and shared by every scanner with that shape.
 *
 * <p>After a {@link Found}, the next {@link #step} carries on with the position after it, so a
 * caller can blacklist a result it can't reach and keep scanning without starting over.
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

    /**
     * Defensive cap on the vertical range so offsets fit the packed sort key. Far above anything
     * {@link GolemConfig} allows.
     */
    static final int MAX_VERTICAL_RANGE = 256;

    /** Packed key layout: 10 bits each for dz, dx, dy (biased by 512), distance above that. */
    private static final int BIAS = 512;
    private static final int BITS = 10;
    private static final int MASK = (1 << BITS) - 1;

    /** Shared offset lists, keyed by radius and vertical range. */
    private static final Map<Long, int[]> OFFSET_CACHE = new ConcurrentHashMap<>();

    private static final Scanning SCANNING = new Scanning();
    private static final Exhausted EXHAUSTED = new Exhausted();

    private final int radius;
    private final int verticalRange;
    private final int blocksPerTick;
    /** Flat {dx, dy, dz} triples in scan order. Shared; never modified. */
    private final int[] offsets;
    private final Map<BlockPos, Long> blacklist = new HashMap<>();

    private @Nullable BlockPos center;
    /** Index of the next offset triple to check. */
    private int cursor;

    public AreaScanner(int radius, int verticalRange, int blocksPerTick) {
        this.radius = Math.max(0, Math.min(radius, GolemConfig.MAX_RADIUS));
        this.verticalRange = Math.max(0, Math.min(verticalRange, MAX_VERTICAL_RANGE));
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.offsets = offsetsFor(this.radius, this.verticalRange);
    }

    /** Starts a fresh scan around {@code center}. Keeps the blacklist. */
    public void reset(BlockPos center) {
        this.center = center;
        this.cursor = 0;
    }

    /**
     * Checks up to {@code blocksPerTick} positions. Blacklisted positions count toward the budget
     * but are not probed, so the work per call is strictly bounded. {@code nowTick} is used for
     * blacklist expiry. Without a prior {@link #reset} there is nothing to scan: returns
     * {@link Exhausted}.
     */
    public Result step(BlockProbe probe, long nowTick) {
        BlockPos origin = center;
        if (origin == null) {
            return EXHAUSTED;
        }
        pruneBlacklist(nowTick);
        int total = offsets.length / 3;
        int checked = 0;
        while (checked < blocksPerTick && cursor < total) {
            int i = cursor * 3;
            cursor++;
            checked++;
            int x = origin.x() + offsets[i];
            int y = origin.y() + offsets[i + 1];
            int z = origin.z() + offsets[i + 2];
            if (!blacklist.isEmpty() && blacklist.containsKey(new BlockPos(x, y, z))) {
                continue;
            }
            if (probe.matches(x, y, z)) {
                return new Found(new BlockPos(x, y, z));
            }
        }
        return cursor >= total ? EXHAUSTED : SCANNING;
    }

    /**
     * Skips {@code pos} until {@code untilTick} (e.g. unreachable, unsafe, or claim-protected).
     * The position is skipped while the current tick is before {@code untilTick}. Blacklisting a
     * position again replaces its expiry.
     */
    public void blacklist(BlockPos pos, long untilTick) {
        blacklist.put(pos, untilTick);
    }

    /** The radius actually scanned, after the cap. */
    public int radius() {
        return radius;
    }

    /** The vertical range actually scanned, after the cap. */
    public int verticalRange() {
        return verticalRange;
    }

    /** Number of positions in the area. */
    public int areaSize() {
        return offsets.length / 3;
    }

    /** Live blacklist entries; for tests. */
    int blacklistSize() {
        return blacklist.size();
    }

    private void pruneBlacklist(long nowTick) {
        if (!blacklist.isEmpty()) {
            blacklist.values().removeIf(until -> until <= nowTick);
        }
    }

    /** The shared, sorted offset list for this shape; built on first use. */
    static int[] offsetsFor(int radius, int verticalRange) {
        long key = ((long) radius << 32) | (verticalRange & 0xFFFFFFFFL);
        return OFFSET_CACHE.computeIfAbsent(key, k -> buildOffsets(radius, verticalRange));
    }

    /**
     * Sorts by packing (distance², dy, dx, dz) into one long per offset and sorting the primitive
     * array, which avoids boxing for the ~140k offsets of the largest area.
     */
    private static int[] buildOffsets(int radius, int verticalRange) {
        int side = 2 * radius + 1;
        int height = 2 * verticalRange + 1;
        long[] keys = new long[side * side * height];
        int n = 0;
        for (int dy = -verticalRange; dy <= verticalRange; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long distSq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    keys[n++] = (distSq << (3 * BITS))
                            | ((long) (dy + BIAS) << (2 * BITS))
                            | ((long) (dx + BIAS) << BITS)
                            | (dz + BIAS);
                }
            }
        }
        Arrays.sort(keys);
        int[] out = new int[n * 3];
        for (int i = 0; i < n; i++) {
            long k = keys[i];
            out[i * 3] = (int) ((k >> BITS) & MASK) - BIAS;
            out[i * 3 + 1] = (int) ((k >> (2 * BITS)) & MASK) - BIAS;
            out[i * 3 + 2] = (int) (k & MASK) - BIAS;
        }
        return out;
    }
}
