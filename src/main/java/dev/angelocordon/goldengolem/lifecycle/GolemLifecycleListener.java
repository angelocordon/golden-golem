package dev.angelocordon.goldengolem.lifecycle;

import dev.angelocordon.goldengolem.Services;
import org.bukkit.event.Listener;

/**
 * Death drops, gold-ingot healing and not fighting back.
 *
 * <p>TODO(LAB-24): implement.
 */
public final class GolemLifecycleListener implements Listener {

    private final Services services;

    public GolemLifecycleListener(Services services) {
        this.services = services;
    }
}
