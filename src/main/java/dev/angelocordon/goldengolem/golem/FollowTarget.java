package dev.angelocordon.goldengolem.golem;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Who a following golem walks after. Pure, so it is unit-tested. Golems saved before leaders
 * existed have none, so they fall back to their owner.
 */
public final class FollowTarget {

    private FollowTarget() {
    }

    /** The leader if one is stored, else the owner (which may itself be null for tampered data). */
    public static @Nullable UUID choose(@Nullable UUID leader, @Nullable UUID owner) {
        return leader != null ? leader : owner;
    }

    /** True if {@code player} is the one a golem with this leader and owner follows. */
    public static boolean isLedBy(@Nullable UUID leader, @Nullable UUID owner, UUID player) {
        return player.equals(choose(leader, owner));
    }
}
