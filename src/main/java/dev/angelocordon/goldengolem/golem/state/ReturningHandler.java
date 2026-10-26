package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;

/**
 * Walks the golem back to its golden chest.
 *
 * <p>TODO(LAB-20): implement. Stub: stays in RETURNING.
 */
public final class ReturningHandler implements StateHandler {

    private final Services services;

    public ReturningHandler(Services services) {
        this.services = services;
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        return GolemState.RETURNING;
    }
}
