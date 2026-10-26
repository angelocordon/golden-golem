package dev.angelocordon.goldengolem.golem;

/**
 * Behavior for one {@link GolemState}. The controller calls {@link #tick} every server tick for
 * each golem in that state and moves the golem to whatever state it returns.
 *
 * <p>Handlers are shared by all golems, so keep per-golem data in the golem's PDC (through
 * {@link GoldenGolem}) or in a map keyed by golem id that you prune in {@link #onExit}.
 */
public interface StateHandler {

    /** Runs once when a golem enters this state. */
    default void onEnter(GoldenGolem golem) {
    }

    /** Runs once when a golem leaves this state, before the next state's {@link #onEnter}. */
    default void onExit(GoldenGolem golem) {
    }

    /** Does one tick of work and returns the next state (or this one to stay). */
    GolemState tick(GoldenGolem golem);

    /** Sets the reason and returns BLOCKED: {@code return StateHandler.block(golem, NO_TOOL);} */
    static GolemState block(GoldenGolem golem, BlockedReason reason) {
        golem.setBlockedReason(reason);
        return GolemState.BLOCKED;
    }
}
