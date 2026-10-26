package dev.angelocordon.goldengolem.build;

import dev.angelocordon.goldengolem.Services;
import org.bukkit.event.Listener;

/**
 * Turns a carved pumpkin placed on a gold block into a Golden Golem.
 *
 * <p>TODO(LAB-12): implement. After tagging, call {@code services.lifecycle().applyStats(...)}
 * and {@code services.linker().autoLink(...)}.
 */
public final class GolemBuildListener implements Listener {

    private final Services services;

    public GolemBuildListener(Services services) {
        this.services = services;
    }
}
