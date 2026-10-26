package dev.angelocordon.goldengolem.build;

import dev.angelocordon.goldengolem.Services;

/**
 * Makes golden golems look gold without a resource pack: a gold name and the odd gold particle.
 *
 * <p>TODO(LAB-12): implement {@link #start()} (schedule your own repeating task).
 */
public final class GolemLook {

    private final Services services;

    public GolemLook(Services services) {
        this.services = services;
    }

    /** Called once on enable. */
    public void start() {
    }
}
