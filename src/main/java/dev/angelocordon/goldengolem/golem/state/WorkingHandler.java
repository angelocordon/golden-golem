package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;

/**
 * Finds, walks to and breaks target blocks in the work area.
 *
 * <p>TODO(LAB-19): implement. Stub: stays in WORKING.
 */
public final class WorkingHandler implements StateHandler {

    private final Services services;

    public WorkingHandler(Services services) {
        this.services = services;
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        return GolemState.WORKING;
    }
}
