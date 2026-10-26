package dev.angelocordon.goldengolem.golem.state;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.StateHandler;

/** Stands still until a player gives it something to do through the menu. */
public final class IdleHandler implements StateHandler {

    private final Services services;

    public IdleHandler(Services services) {
        this.services = services;
    }

    @Override
    public void onEnter(GoldenGolem golem) {
        services.movers().of(golem).stop();
    }

    @Override
    public GolemState tick(GoldenGolem golem) {
        return GolemState.IDLE;
    }
}
