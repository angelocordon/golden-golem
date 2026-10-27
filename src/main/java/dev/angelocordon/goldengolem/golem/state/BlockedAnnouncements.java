package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.golem.BlockedReason;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rate-limits "I'm blocked" feedback so a golem that keeps re-blocking (e.g. UNREACHABLE retries
 * every few seconds) doesn't spam its owner. A golem is announced when its reason differs from
 * the last one announced, or when the same reason comes back after {@code repeatAfterTicks}, so
 * a problem that recurs much later is still reported. Pure: the caller passes the current tick.
 */
public final class BlockedAnnouncements {

    private record Last(BlockedReason reason, long tick) {
    }

    private final long repeatAfterTicks;
    private final Map<UUID, Last> last = new HashMap<>();

    public BlockedAnnouncements(long repeatAfterTicks) {
        this.repeatAfterTicks = repeatAfterTicks;
    }

    /**
     * True if golem {@code id} blocked for {@code reason} at tick {@code now} should be announced.
     * Records the announcement when it returns true.
     */
    public boolean shouldAnnounce(UUID id, BlockedReason reason, long now) {
        // Entries older than the window no longer suppress anything, so dropping them is safe and
        // keeps the map from holding golems that died or unloaded.
        last.values().removeIf(entry -> now - entry.tick() >= repeatAfterTicks);
        Last previous = last.get(id);
        if (previous != null && previous.reason() == reason) {
            return false;
        }
        last.put(id, new Last(reason, now));
        return true;
    }

    /** How many golems are currently remembered. For tests. */
    int size() {
        return last.size();
    }
}
