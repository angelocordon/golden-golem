package dev.angelocordon.goldengolem.link;

import dev.angelocordon.goldengolem.Services;
import org.bukkit.event.Listener;

/**
 * Relinks a following golem when its owner right-clicks a golden chest, and blocks golems
 * whose chest is broken.
 *
 * <p>TODO(LAB-14): implement.
 */
public final class ChestLinkListener implements Listener {

    private final Services services;

    public ChestLinkListener(Services services) {
        this.services = services;
    }
}
