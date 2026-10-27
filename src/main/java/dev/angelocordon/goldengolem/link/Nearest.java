package dev.angelocordon.goldengolem.link;

import dev.angelocordon.goldengolem.util.BlockPos;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Picks the candidate closest to a point. Pure, so it is unit-tested. */
public final class Nearest {

    private Nearest() {
    }

    /**
     * The candidate whose position is closest to {@code origin}, or empty if there are none. On a
     * tie the earlier candidate wins, so the choice is stable for a given scan order.
     */
    public static <T> Optional<T> of(BlockPos origin, Iterable<T> candidates, Function<T, BlockPos> position) {
        @Nullable T best = null;
        long bestDistance = Long.MAX_VALUE;
        for (T candidate : candidates) {
            long distance = origin.distanceSquared(position.apply(candidate));
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
