package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;

/**
 * Empties storage into the chest and swaps a worn tool (LAB-21).
 *
 * <p>TODO(LAB-20): implement. Stub: stays in DEPOSITING.
 */
public final class DepositingHandler implements StateHandler {

    private final Services services;

    public DepositingHandler(Services services) {
        this.services = services;
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        return GolemState.DEPOSITING;
    }
}
