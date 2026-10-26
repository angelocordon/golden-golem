package dev.angelocordon.goldengolem;

import dev.angelocordon.goldengolem.chest.GoldenChests;
import dev.angelocordon.goldengolem.golem.Feedback;
import dev.angelocordon.goldengolem.golem.GolemController;
import dev.angelocordon.goldengolem.golem.GolemRegistry;
import dev.angelocordon.goldengolem.golem.movement.Movers;
import dev.angelocordon.goldengolem.lifecycle.GolemLifecycle;
import dev.angelocordon.goldengolem.link.ChestLinker;

/**
 * The shared objects every feature can use, built once in {@link GoldenGolemPlugin#onEnable}.
 * Features take this in their constructor instead of reaching for statics.
 */
public record Services(
        GoldenGolemPlugin plugin,
        GolemConfig config,
        GolemRegistry registry,
        GolemController controller,
        Movers movers,
        GoldenChests chests,
        ChestLinker linker,
        GolemLifecycle lifecycle,
        Feedback feedback) {
}
