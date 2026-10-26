package dev.angelocordon.goldengolem.menu;

import dev.angelocordon.goldengolem.Services;
import org.bukkit.event.Listener;

/**
 * Opens the equipment menu on right-click, handles Follow/Stay (menu item and sneak +
 * right-click), and enforces the menu's slot rules.
 *
 * <p>TODO(LAB-15, LAB-16): implement, along with a {@code GolemMenu} inventory holder in this
 * package. Change state through {@code services.controller().transition(...)}.
 */
public final class GolemMenuListener implements Listener {

    private final Services services;

    public GolemMenuListener(Services services) {
        this.services = services;
    }
}
