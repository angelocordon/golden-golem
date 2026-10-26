package dev.angelocordon.goldengolem.link;

import dev.angelocordon.goldengolem.chest.GoldenChests;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemRegistry;

/**
 * Links golems to golden chests, respecting each chest's capacity.
 *
 * <p>TODO(LAB-14): implement.
 */
public final class ChestLinker {

    private final GolemRegistry registry;
    private final GoldenChests chests;

    public ChestLinker(GolemRegistry registry, GoldenChests chests) {
        this.registry = registry;
        this.chests = chests;
    }

    /** Links a newly built golem to the nearest golden chest with room, if any is close. */
    public void autoLink(GoldenGolem golem) {
    }
}
