package dev.angelocordon.goldengolem.golem;

/** The golem's states. See the state diagram in the design spec. */
public enum GolemState {
    /** Standing still: no work center, nothing left to mine, or just built. */
    IDLE,
    /** Following its owner. Never mines. */
    FOLLOWING,
    /** Finding, walking to and breaking target blocks in its work area. */
    WORKING,
    /** Walking back to its golden chest. */
    RETURNING,
    /** At the chest: emptying storage and swapping a worn tool. */
    DEPOSITING,
    /** Stuck; {@link GoldenGolem#blockedReason()} says why. Rechecks and resumes on its own. */
    BLOCKED
}
