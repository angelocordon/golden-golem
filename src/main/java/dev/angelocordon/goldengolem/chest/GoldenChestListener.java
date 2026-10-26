package dev.angelocordon.goldengolem.chest;

import dev.angelocordon.goldengolem.Services;
import org.bukkit.event.Listener;

/**
 * Keeps the golden-chest tag on place and break, and stops a golden and a plain chest merging.
 *
 * <p>TODO(LAB-13): implement.
 */
public final class GoldenChestListener implements Listener {

    private final Services services;

    public GoldenChestListener(Services services) {
        this.services = services;
    }
}
