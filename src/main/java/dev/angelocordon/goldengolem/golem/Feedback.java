package dev.angelocordon.goldengolem.golem;

import dev.angelocordon.goldengolem.golem.state.BlockedAnnouncements;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jspecify.annotations.Nullable;

/**
 * Tells a golem's owner what it's doing. When it gets stuck the golem shakes its head and says
 * "no" to anyone nearby, and its owner gets the reason on the action bar if they're close enough
 * to care. Repeats of the same reason are rate-limited by {@link BlockedAnnouncements}.
 */
public class Feedback {

    /** Owners further away than this aren't watching the golem, so they get no message. */
    static final double OWNER_RANGE = 48;
    /** The same reason is announced again only after this long (2 minutes). */
    static final long REPEAT_AFTER_TICKS = 20L * 120;
    private static final int SHAKE_TICKS = 20;
    private static final int SHAKES = 2;
    private static final float SHAKE_DEGREES = 35f;

    private final Plugin plugin;
    private final BlockedAnnouncements announcements = new BlockedAnnouncements(REPEAT_AFTER_TICKS);
    private final Map<UUID, BukkitRunnable> shakes = new HashMap<>();

    public Feedback(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Called when a golem becomes blocked. */
    public void blocked(GoldenGolem golem, BlockedReason reason) {
        if (!golem.isValid() || !announcements.shouldAnnounce(golem.id(), reason, Bukkit.getCurrentTick())) {
            return;
        }
        CopperGolem entity = golem.entity();
        entity.getWorld().playSound(entity, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1f, 1f);
        shakeHead(golem);
        Player owner = nearbyOwner(golem);
        if (owner != null) {
            owner.sendActionBar(Component.text("Golden Golem " + reason.message(), NamedTextColor.GOLD));
        }
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

    private @Nullable Player nearbyOwner(GoldenGolem golem) {
        Player owner = onlineOwner(golem);
        if (owner == null) {
            return null;
        }
        Location at = golem.entity().getLocation();
        Location ownerAt = owner.getLocation();
        boolean near = ownerAt.getWorld().equals(at.getWorld())
                && ownerAt.distanceSquared(at) <= OWNER_RANGE * OWNER_RANGE;
        return near ? owner : null;
    }

    /**
     * Wiggles the golem's yaw side to side for about a second, then puts it back. Stops early if
     * the golem dies, unloads or stops being blocked, so it never fights a moving golem's facing.
     */
    private void shakeHead(GoldenGolem golem) {
        BukkitRunnable previous = shakes.remove(golem.id());
        if (previous != null) {
            previous.cancel();
        }
        CopperGolem entity = golem.entity();
        float baseYaw = entity.getYaw();
        float pitch = entity.getPitch();
        BukkitRunnable shake = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                tick++;
                boolean stillBlocked = entity.isValid() && golem.state() == GolemState.BLOCKED;
                if (!stillBlocked || tick > SHAKE_TICKS) {
                    if (stillBlocked) {
                        entity.setRotation(baseYaw, pitch);
                    }
                    cancel();
                    shakes.remove(golem.id(), this);
                    return;
                }
                double phase = 2 * Math.PI * SHAKES * tick / SHAKE_TICKS;
                entity.setRotation(baseYaw + (float) (SHAKE_DEGREES * Math.sin(phase)), pitch);
            }
        };
        shakes.put(golem.id(), shake);
        shake.runTaskTimer(plugin, 1L, 1L);
    }
}
