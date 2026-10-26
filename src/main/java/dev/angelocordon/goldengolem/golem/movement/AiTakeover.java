package dev.angelocordon.goldengolem.golem.movement;

import dev.angelocordon.goldengolem.golem.GoldenGolem;

/**
 * Switches off the vanilla copper golem AI on golden golems. The copper golem runs on brain AI,
 * which the Paper API can't edit, so instead the golem is made unaware: its brain, navigation and
 * move control stop ticking (no item sorting, no copper-button pressing, no wandering) while
 * physics keeps running. {@link PathMover} then does all the walking.
 *
 * <p>Registered as a registry load hook, so it runs for every golden golem as it enters the
 * world. Awareness is saved with the entity anyway; reapplying keeps it right if something else
 * flipped it.
 */
public final class AiTakeover {

    private AiTakeover() {
    }

    public static void apply(GoldenGolem golem) {
        golem.entity().setAware(false);
    }
}
