package dev.angelocordon.goldengolem.menu;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.BlockedReason;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.work.HarvestRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Follow/Stay toggle, shared by the menu item and sneak + right-click. Reads tool and target
 * from the golem's saved data, so callers sync an open menu first. Main thread only.
 */
final class FollowStay {

    private final Services services;

    FollowStay(Services services) {
        this.services = services;
    }

    /** Following golems Stay; every other state switches to Follow. */
    void toggle(GoldenGolem golem, Player actor) {
        if (golem.state() == GolemState.FOLLOWING) {
            stay(golem, actor);
        } else {
            follow(golem, actor);
        }
    }

    void follow(GoldenGolem golem, Player actor) {
        services.controller().transition(golem, GolemState.FOLLOWING);
        tell(actor, "Golden Golem is following its owner", NamedTextColor.GOLD);
    }

    /** Saves the golem's block as its work center, then works, idles or refuses (TOO_FAR). */
    void stay(GoldenGolem golem, Player actor) {
        BlockLocation center = BlockLocation.of(golem.entity().getLocation());
        golem.setWorkCenter(center);

        ItemStack tool = golem.tool();
        Material target = golem.target();
        boolean fits = tool != null && target != null && HarvestRule.toolFits(tool.getType(), target);
        BlockLocation chest = golem.chest();
        StayDecision decision = StayDecision.decide(tool != null, fits, chest != null,
                distance(center, chest), services.config().maxChestDistance());

        switch (decision) {
            case TOO_FAR -> {
                golem.setBlockedReason(BlockedReason.TOO_FAR);
                services.controller().transition(golem, GolemState.BLOCKED);
                tell(actor, "Golden Golem " + BlockedReason.TOO_FAR.message() + " (max "
                        + services.config().maxChestDistance() + " blocks)", NamedTextColor.RED);
            }
            case WORK -> {
                services.controller().transition(golem, GolemState.WORKING);
                tell(actor, "Golden Golem is working here", NamedTextColor.GOLD);
            }
            case IDLE -> {
                services.controller().transition(golem, GolemState.IDLE);
                tell(actor, idleHint(tool, target), NamedTextColor.GOLD);
            }
        }
    }

    private static String idleHint(@Nullable ItemStack tool, @Nullable Material target) {
        if (tool == null) {
            return "Golden Golem will wait here; give it a tool to start working";
        }
        if (target == null) {
            return "Golden Golem will wait here; give it a target block to start working";
        }
        return "Golden Golem will wait here; its tool can't gather that target";
    }

    /** Straight-line distance in blocks; infinite if the chest is in another world. */
    private static double distance(BlockLocation from, @Nullable BlockLocation chest) {
        if (chest == null) {
            return 0;
        }
        if (!chest.world().equals(from.world())) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(from.pos().distanceSquared(chest.pos()));
    }

    private static void tell(Player player, String message, NamedTextColor color) {
        player.sendActionBar(Component.text(message, color));
    }
}
