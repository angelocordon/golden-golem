package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;

/**
 * Keeps the golem near its owner. Never mines.
 *
 * <p>TODO(LAB-11): implement. Stub: stays in FOLLOWING.
 */
public final class FollowingHandler implements StateHandler {

    private final Services services;

    public FollowingHandler(Services services) {
        this.services = services;
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        return GolemState.FOLLOWING;
    }
}
