package dev.angelocordon.goldengolem.golem.movement;

import dev.angelocordon.goldengolem.golem.GoldenGolem;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bukkit.entity.CopperGolem;

/** One {@link Mover} per loaded golem, created on first use. */
public final class Movers {

    private final Function<CopperGolem, Mover> factory;
    private final Map<UUID, Mover> movers = new HashMap<>();
    private final Map<UUID, CopperGolem> entities = new HashMap<>();

    public Movers(Function<CopperGolem, Mover> factory) {
        this.factory = factory;
    }

    public Mover of(GoldenGolem golem) {
        // A cross-world teleport replaces the entity object but keeps its UUID: rebind then.
        if (entities.get(golem.id()) != golem.entity()) {
            entities.put(golem.id(), golem.entity());
            movers.put(golem.id(), factory.apply(golem.entity()));
        }
        return movers.get(golem.id());
    }

    /** Ticks the movers of {@code live} golems and drops movers for golems that are gone. */
    public void tick(Collection<GoldenGolem> live) {
        Set<UUID> ids = live.stream().map(GoldenGolem::id).collect(Collectors.toSet());
        movers.keySet().retainAll(ids);
        entities.keySet().retainAll(ids);
        for (GoldenGolem golem : live) {
            Mover mover = movers.get(golem.id());
            if (mover != null) {
                mover.tick();
            }
        }
    }
}
