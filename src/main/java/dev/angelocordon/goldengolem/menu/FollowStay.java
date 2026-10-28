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
 * The Follow/Stay toggle, shared by the menu item and sneak + hit or right-click. Reads tool and target
 * from the golem's saved data, so callers write an open menu's pending edits first and refresh
 * it after (transition hooks may move items). Main thread only.
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
        // After the transition: leaving the previous state must not clear the new leader.
        golem.setLeader(actor.getUniqueId());
        tell(golem, actor, "Golden Golem is following you", NamedTextColor.GOLD);
    }

    /** Stay at the golem's current block: the new work center, unless refused as TOO_FAR. */
    void stay(GoldenGolem golem, @Nullable Player actor) {
        apply(golem, BlockLocation.of(golem.entity().getLocation()), actor);
    }

    /**
     * Re-runs the Stay decision at the existing work center, after the tool or target changed
     * on a waiting golem. Does nothing without a work center.
     */
    void recheck(GoldenGolem golem, @Nullable Player actor) {
        BlockLocation center = golem.workCenter();
        if (center != null) {
            apply(golem, center, actor);
        }
    }

    private void apply(GoldenGolem golem, BlockLocation center, @Nullable Player actor) {
        ItemStack tool = golem.tool();
        Material target = golem.target();
        boolean fits = tool != null && target != null && HarvestRule.toolFits(tool.getType(), target);
        BlockLocation chest = golem.chest();
        int maxDistance = services.config().maxChestDistance();
        StayDecision decision = StayDecision.decide(tool != null, fits, chest != null,
                distance(center, chest), maxDistance);

        if (decision == StayDecision.TOO_FAR) {
            // Refused: keep the old work center.
            golem.setBlockedReason(BlockedReason.TOO_FAR);
            services.controller().transition(golem, GolemState.BLOCKED);
            tell(golem, actor, "Golden Golem " + BlockedReason.TOO_FAR.message() + " (max " + maxDistance
                    + " blocks)", NamedTextColor.RED);
            return;
        }
        golem.setWorkCenter(center);
        if (decision == StayDecision.WORK) {
            services.controller().transition(golem, GolemState.WORKING);
            tell(golem, actor, "Golden Golem is working here", NamedTextColor.GOLD);
        } else {
            if (golem.state() != GolemState.IDLE) {
                services.controller().transition(golem, GolemState.IDLE);
            }
            tell(golem, actor, idleHint(tool, target), NamedTextColor.GOLD);
        }
    }

    private static String idleHint(@Nullable ItemStack tool, @Nullable Material target) {
        if (tool == null) {
            return "Golden Golem is waiting here; it starts once it has a tool and a target block";
        }
        if (target == null) {
            return "Golden Golem is waiting here; it starts once it has a target block";
        }
        return "Golden Golem is waiting here; its tool can't gather that target";
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

    /** Tells the player who acted, or the owner (action bar) when nothing in-game acted. */
    private void tell(GoldenGolem golem, @Nullable Player actor, String message, NamedTextColor color) {
        if (actor != null) {
            actor.sendActionBar(Component.text(message, color));
        } else {
            services.feedback().tell(golem, message);
        }
    }
}
