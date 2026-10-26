package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;

/**
 * Shows why the golem is stuck and rechecks every few seconds.
 *
 * <p>TODO(LAB-22): implement. Stub: stays in BLOCKED.
 */
public final class BlockedHandler implements StateHandler {

    private final Services services;

    public BlockedHandler(Services services) {
        this.services = services;
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        return GolemState.BLOCKED;
    }
}
