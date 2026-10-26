package dev.angelocordon.goldengolem.build;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.CopperGolem;
import org.jspecify.annotations.Nullable;

/**
 * Makes golden golems look gold without a resource pack: a gold name and the odd gold particle.
 *
 * <p>The name is set at spawn and whenever a golem loads, never on a timer, so there are no
 * repeated metadata packets. Name tags are blocked in {@link GolemBuildListener}. Sparkles run on
 * their own slow timer rather than the controller tick, and only near players.
 */
public final class GolemLook {

    private static final String NAME_TEXT = "Golden Golem";
    static final Component NAME = Component.text(NAME_TEXT, NamedTextColor.GOLD);
    static final Particle.DustOptions GOLD_DUST = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.0f);

    private static final long PERIOD_TICKS = 10L;
    /** Each golem sparkles on roughly one run in this many, so the effect stays subtle and cheap. */
    private static final int SPARKLE_ONE_IN = 3;
    private static final int SPARKLE_COUNT = 3;
    /** Nobody further away than this would notice the particles, so don't send them. */
    private static final double SPARKLE_VIEW_RADIUS = 32;

    private final Services services;

    public GolemLook(Services services) {
        this.services = services;
    }

    /** Called once on enable. */
    public void start() {
        services.registry().onGolemLoaded(golem -> applyName(golem.entity()));
        services.plugin().getServer().getScheduler().runTaskTimer(services.plugin(), this::sparkle, PERIOD_TICKS, PERIOD_TICKS);
    }

    /** Gives a golem the gold name, only touching the entity if it isn't already right. */
    static void applyName(CopperGolem entity) {
        if (!hasGoldName(entity.customName())) {
            entity.customName(NAME);
        }
        if (!entity.isCustomNameVisible()) {
            entity.setCustomNameVisible(true);
        }
    }

    /**
     * Compares by content rather than {@code equals}, because a name loaded from disk can be a
     * structurally different Component that still renders the same.
     */
    private static boolean hasGoldName(@Nullable Component name) {
        if (!(name instanceof TextComponent text) || !text.children().isEmpty()) {
            return false;
        }
        TextColor color = text.color();
        return NAME_TEXT.equals(text.content()) && color != null && color.value() == NamedTextColor.GOLD.value();
    }

    private void sparkle() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (GoldenGolem golem : services.registry().all()) {
            if (!golem.isValid() || random.nextInt(SPARKLE_ONE_IN) != 0) {
                continue;
            }
            CopperGolem entity = golem.entity();
            Location at = entity.getLocation();
            if (at.getNearbyPlayers(SPARKLE_VIEW_RADIUS).isEmpty()) {
                continue;
            }
            double height = entity.getHeight();
            entity.getWorld().spawnParticle(Particle.DUST, at.add(0, height / 2, 0),
                    SPARKLE_COUNT, 0.3, height / 2, 0.3, GOLD_DUST);
        }
    }
}
