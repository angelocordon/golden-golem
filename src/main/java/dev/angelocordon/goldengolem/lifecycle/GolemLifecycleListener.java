package dev.angelocordon.goldengolem.lifecycle;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Death drops, gold-ingot healing and not fighting back.
 *
 * <p>Healing is limited to the owner and players with {@link #ADMIN_PERMISSION}, the same people
 * who can open the golem's menu, so strangers can't spend gold on (or grief around) someone
 * else's golem. When hurt the golem does nothing special yet; fleeing is out of scope for 0.1.0.
 */
public final class GolemLifecycleListener implements Listener {

    static final String ADMIN_PERMISSION = "goldengolem.admin";

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
        dying.put(golem.id(), new Carried(tool, target, storage, drops));
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

    /**
     * Gold ingot on a hurt golem heals it. Runs before the menu listener (HIGH, ignoreCancelled)
     * and cancels the event so the menu doesn't open. At full health it does nothing and the menu
     * opens as usual.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        Optional<GoldenGolem> found = GoldenGolem.of(event.getRightClicked());
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (found.isEmpty() || hand.getType() != Material.GOLD_INGOT) {
            return;
        }
        GoldenGolem golem = found.get();
        if (!golem.isOwnedBy(player.getUniqueId()) && !player.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        if (!services.lifecycle().heal(golem.entity())) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack rest = hand.clone();
            rest.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(rest.getAmount() > 0 ? rest : null);
        }
        healEffects(golem.entity());
    }

    /** Vanilla copper golems don't attack, but a golden golem must never target anything. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && GoldenGolem.isGoldenGolem(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** One of the target block as an item, or null if there's no target or it has no item form. */
    private static @Nullable ItemStack targetItem(@Nullable Material target) {
        if (target == null || target.isAir() || !target.isItem()) {
            return null;
        }
        return new ItemStack(target);
    }

    private static void healEffects(CopperGolem entity) {
        Location at = entity.getLocation().add(0, entity.getHeight() / 2, 0);
        entity.getWorld().playSound(entity, Sound.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.NEUTRAL, 1f, 1.2f);
        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.3, 0.4, 0.3);
    }
}
