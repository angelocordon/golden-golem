package dev.angelocordon.goldengolem.lifecycle;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Death drops and not fighting back. Healing with a gold ingot is part of the shared control
 * input in {@code GolemMenuListener}. When hurt the golem does nothing special yet; fleeing is
 * out of scope for 0.1.0.
 */
public final class GolemLifecycleListener implements Listener {

    private final Services services;
    /** What each dying golem had, kept from NORMAL to MONITOR in case the death is cancelled. */
    private final Map<UUID, Carried> dying = new HashMap<>();

    private record Carried(@Nullable ItemStack tool, @Nullable Material target, @Nullable ItemStack[] storage,
            List<ItemStack> drops) {
    }

    public GolemLifecycleListener(Services services) {
        this.services = services;
        // Golems built before stats existed (or by an older version) get them the first time they load.
        services.registry().onGolemLoaded(golem -> {
            if (!services.lifecycle().hasStats(golem.entity())) {
                services.lifecycle().applyStats(golem.entity());
            }
        });
    }

    /**
     * Moves everything the golem carries into the death drops and clears it from the golem, so it
     * can't be handed out twice. Runs at NORMAL, after the menu saves an open menu back to the
     * golem at LOWEST, and reads the golem's data at that point.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        Optional<GoldenGolem> found = GoldenGolem.of(event.getEntity());
        if (found.isEmpty()) {
            return;
        }
        GoldenGolem golem = found.get();
        ItemStack tool = golem.tool();
        Material target = golem.target();
        @Nullable ItemStack[] storage = golem.storage();
        List<ItemStack> drops = DeathDrops.assemble(tool, targetItem(target), Arrays.asList(storage));

        // Vanilla copper golem drops are kept: none of them come from the golem's PDC, so nothing
        // here duplicates them.
        event.getDrops().addAll(drops);
        golem.setTool(null);
        golem.setTarget(null);
        golem.setStorage(new ItemStack[GoldenGolem.STORAGE_SIZE]);
        // Copies, so a plugin that edits the drop stacks and then cancels the death can't change
        // what the golem gets back.
        dying.put(golem.id(), new Carried(copy(tool), target, copyAll(storage), drops));
    }

    /**
     * If another plugin cancelled the death (the golem lives on), puts the items back on the golem
     * and takes them out of the drops, which won't be spawned anyway. Otherwise nothing to do.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathResult(EntityDeathEvent event) {
        Carried carried = dying.remove(event.getEntity().getUniqueId());
        if (carried == null || !event.isCancelled()) {
            return;
        }
        GoldenGolem.of(event.getEntity()).ifPresent(golem -> {
            golem.setTool(carried.tool());
            golem.setTarget(carried.target());
            golem.setStorage(carried.storage());
        });
        event.getDrops().removeIf(drop -> carried.drops().stream().anyMatch(added -> added == drop));
    }

    /** Vanilla copper golems don't attack, but a golden golem must never target anything. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && GoldenGolem.isGoldenGolem(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private static @Nullable ItemStack copy(@Nullable ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static @Nullable ItemStack[] copyAll(@Nullable ItemStack[] items) {
        @Nullable ItemStack[] copies = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copies[i] = copy(items[i]);
        }
        return copies;
    }

    /** One of the target block as an item, or null if there's no target or it has no item form. */
    private static @Nullable ItemStack targetItem(@Nullable Material target) {
        if (target == null || target.isAir() || !target.isItem()) {
            return null;
        }
        return new ItemStack(target);
    }
}
