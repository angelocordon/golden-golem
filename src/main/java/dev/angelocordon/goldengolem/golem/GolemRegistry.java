package dev.angelocordon.goldengolem.golem;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Tracks every golden golem currently in a loaded chunk. Paper fires the add/remove events for
 * spawns, deaths and chunk loads/unloads alike, so these two handlers cover everything.
 */
public final class GolemRegistry implements Listener {

    private final Logger logger;
    private final Map<UUID, GoldenGolem> golems = new LinkedHashMap<>();
    private final List<Consumer<GoldenGolem>> loadHooks = new ArrayList<>();

    public GolemRegistry(Logger logger) {
        this.logger = logger;
    }

    /**
     * Runs {@code hook} for every golem as it enters the world, including ones already loaded
     * when the hook is added. Use it for per-entity setup that vanilla doesn't persist.
     */
    public void onGolemLoaded(Consumer<GoldenGolem> hook) {
        loadHooks.add(hook);
        for (GoldenGolem golem : all()) {
            runHook(hook, golem);
        }
    }

    /** Picks up golems that were loaded before the plugin enabled (e.g. after /reload). */
    public void scanLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            for (CopperGolem entity : world.getEntitiesByClass(CopperGolem.class)) {
                add(entity);
            }
        }
    }

    /** A snapshot, safe to iterate while golems are added or removed. */
    public Collection<GoldenGolem> all() {
        return List.copyOf(golems.values());
    }

    public Optional<GoldenGolem> get(UUID id) {
        return Optional.ofNullable(golems.get(id));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        add(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        golems.remove(event.getEntity().getUniqueId());
    }

    private void add(Entity entity) {
        GoldenGolem.of(entity).ifPresent(golem -> {
            if (golems.putIfAbsent(golem.id(), golem) == null) {
                for (Consumer<GoldenGolem> hook : loadHooks) {
                    runHook(hook, golem);
                }
            }
        });
    }

    private void runHook(Consumer<GoldenGolem> hook, GoldenGolem golem) {
        try {
            hook.accept(golem);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Golem load hook failed for " + golem.id(), e);
        }
    }
}
