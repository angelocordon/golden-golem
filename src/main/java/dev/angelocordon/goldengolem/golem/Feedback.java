package dev.angelocordon.goldengolem.golem;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Tells a golem's owner what it's doing. Plain action-bar text for now.
 *
 * <p>TODO(LAB-22): head shake, "no" sound, only message an owner who's nearby.
 */
public class Feedback {

    /** Called when a golem becomes blocked. */
    public void blocked(GoldenGolem golem, BlockedReason reason) {
        tell(golem, "Golden Golem " + reason.message());
    }

    /** Sends a short status line to the owner, if online. */
    public void tell(GoldenGolem golem, String message) {
        Player owner = onlineOwner(golem);
        if (owner != null) {
            owner.sendActionBar(Component.text(message, NamedTextColor.GOLD));
        }
    }

    protected @Nullable Player onlineOwner(GoldenGolem golem) {
        UUID owner = golem.owner();
        return owner == null ? null : Bukkit.getPlayer(owner);
    }
}
