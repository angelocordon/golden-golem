package dev.angelocordon.goldengolem.build;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.CopperGolem;

/**
 * Makes golden golems look gold without a resource pack: a gold name and the odd gold particle.
 *
 * <p>Runs on its own slow timer rather than the controller tick, since none of this needs to be
 * frame-perfect. The name is re-applied because a name tag or another plugin can change it.
 */
public final class GolemLook {

    static final Component NAME = Component.text("Golden Golem", NamedTextColor.GOLD);
    static final Particle.DustOptions GOLD_DUST = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.0f);

    private static final long PERIOD_TICKS = 10L;
    /** Each golem sparkles on roughly one run in this many, so the effect stays subtle and cheap. */
    private static final int SPARKLE_ONE_IN = 3;
    private static final int SPARKLE_COUNT = 3;

    private final Services services;

    public GolemLook(Services services) {
        this.services = services;
    }

    /** Called once on enable. */
    public void start() {
        services.plugin().getServer().getScheduler().runTaskTimer(services.plugin(), this::refresh, PERIOD_TICKS, PERIOD_TICKS);
    }

    /** Gives a golem the gold name. Called at spawn and kept up by the timer. */
    static void applyName(CopperGolem entity) {
        if (!NAME.equals(entity.customName())) {
            entity.customName(NAME);
        }
        if (!entity.isCustomNameVisible()) {
            entity.setCustomNameVisible(true);
        }
    }

    private void refresh() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (GoldenGolem golem : services.registry().all()) {
            if (!golem.isValid()) {
                continue;
            }
            CopperGolem entity = golem.entity();
            applyName(entity);
            if (random.nextInt(SPARKLE_ONE_IN) == 0) {
                double height = entity.getHeight();
                entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().add(0, height / 2, 0),
                        SPARKLE_COUNT, 0.3, height / 2, 0.3, GOLD_DUST);
            }
        }
    }
}
