package dev.angelocordon.goldengolem.build;

import dev.angelocordon.goldengolem.Services;
import dev.angelocordon.goldengolem.golem.GoldenGolem;
import io.papermc.paper.event.player.PlayerNameEntityEvent;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.CopperGolem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Turns a carved pumpkin or jack o'lantern placed on a gold block into a Golden Golem.
 *
 * <p>The build runs one tick after the place event and re-checks the blocks first. That way a
 * plugin cancelling the placement at a later priority (which reverts the pumpkin) stops the
 * build, and we never change blocks while the server is still processing the placement.
 */
public final class GolemBuildListener implements Listener {

    private final Services services;

    public GolemBuildListener(Services services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block head = event.getBlockPlaced();
        if (!BuildPattern.matches(head.getType(), head.getRelative(BlockFace.DOWN).getType())) {
            return;
        }
        UUID owner = event.getPlayer().getUniqueId();
        services.plugin().getServer().getScheduler().runTask(services.plugin(), () -> build(head, owner));
    }

    /**
     * The gold name is the golem's look, so name tags don't apply. Cancelling keeps the tag in
     * the player's hand instead of consuming it for a rename that wouldn't stick.
     */
    @EventHandler(ignoreCancelled = true)
    public void onName(PlayerNameEntityEvent event) {
        if (GoldenGolem.isGoldenGolem(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void build(Block head, UUID owner) {
        Block body = head.getRelative(BlockFace.DOWN);
        if (!BuildPattern.matches(head.getType(), body.getType())) {
            return;
        }
        World world = body.getWorld();
        Location spawnAt = body.getLocation().add(0.5, 0, 0.5);
        BlockData headData = head.getBlockData();
        if (headData instanceof Directional directional) {
            // Face the way the pumpkin's face points, like vanilla golem builds.
            spawnAt.setDirection(directional.getFacing().getDirection());
        }

        // BUILD_COPPERGOLEM so protection and mob-cap plugins treat it as a player-built golem.
        // The PDC tag is already set when CreatureSpawnEvent fires, so plugins can tell it apart.
        CopperGolem entity = world.spawn(spawnAt, CopperGolem.class, CreatureSpawnEvent.SpawnReason.BUILD_COPPERGOLEM,
                golem -> {
                    GoldenGolem.create(golem, owner);
                    golem.setOxidizing(CopperGolem.Oxidizing.waxed());
                    GolemLook.applyName(golem);
                });
        if (!entity.isValid()) {
            // Another plugin cancelled the spawn; leave the blocks so nothing is lost.
            return;
        }

        head.setType(Material.AIR);
        body.setType(Material.AIR);
        effects(world, spawnAt);

        services.lifecycle().applyStats(entity);
        GoldenGolem.of(entity).ifPresent(services.linker()::autoLink);
    }

    private static void effects(World world, Location at) {
        world.playSound(at, Sound.ENTITY_COPPER_GOLEM_SPAWN, SoundCategory.NEUTRAL, 1f, 1f);
        world.playSound(at, Sound.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.NEUTRAL, 1f, 1.2f);
        world.spawnParticle(Particle.DUST, at.clone().add(0, 0.6, 0), 30, 0.4, 0.6, 0.4, GolemLook.GOLD_DUST);
        world.spawnParticle(Particle.WAX_ON, at.clone().add(0, 0.6, 0), 12, 0.4, 0.6, 0.4);
    }
}
