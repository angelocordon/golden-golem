package dev.angelocordon.goldengolem.golem;

import dev.angelocordon.goldengolem.golem.movement.Movers;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the state machine. Scheduled every tick on the main thread: ticks each golem's current
 * {@link StateHandler}, applies the state it returns, then ticks movement.
 */
public final class GolemController implements Runnable {

    private final Logger logger;
    private final GolemRegistry registry;
    private final Movers movers;
    private final Map<GolemState, StateHandler> handlers = new EnumMap<>(GolemState.class);
    private final Set<UUID> reportedFailures = new HashSet<>();

    public GolemController(Logger logger, GolemRegistry registry, Movers movers) {
        this.logger = logger;
        this.registry = registry;
        this.movers = movers;
    }

    public void register(GolemState state, StateHandler handler) {
        handlers.put(state, handler);
    }

    /** Fails fast on enable if a state has no handler, instead of on the first golem tick. */
    public void checkComplete() {
        for (GolemState state : GolemState.values()) {
            if (!handlers.containsKey(state)) {
                throw new IllegalStateException("No handler registered for " + state);
            }
        }
    }

    @Override
    public void run() {
        Collection<GoldenGolem> golems = registry.all();
        for (GoldenGolem golem : golems) {
            if (!golem.isValid()) {
                continue;
            }
            GolemState current = golem.state();
            try {
                GolemState next = handlers.get(current).tick(golem);
                if (next != current) {
                    transition(golem, next);
                }
            } catch (RuntimeException e) {
                // Log once per golem so one bad golem can't flood the console every tick.
                if (reportedFailures.add(golem.id())) {
                    logger.log(Level.SEVERE, "Golden golem " + golem.id() + " failed in " + current
                            + "; resetting it to IDLE", e);
                }
                if (current != GolemState.IDLE) {
                    golem.setBlockedReason(null);
                    golem.setState(GolemState.IDLE);
                }
            }
        }
        movers.tick(golems);
    }

    /**
     * Moves a golem to {@code next}, running the exit and enter hooks. Use this (not a state
     * setter) from menus and listeners, e.g. Follow/Stay. Leaving BLOCKED clears the reason.
     */
    public void transition(GoldenGolem golem, GolemState next) {
        GolemState current = golem.state();
        handlers.get(current).onExit(golem);
        if (current == GolemState.BLOCKED && next != GolemState.BLOCKED) {
            golem.setBlockedReason(null);
        }
        golem.setState(next);
        handlers.get(next).onEnter(golem);
    }
}
