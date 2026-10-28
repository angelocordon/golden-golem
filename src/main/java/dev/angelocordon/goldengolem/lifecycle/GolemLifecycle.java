package dev.angelocordon.goldengolem.lifecycle;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Golem stats and healing. A golden golem is much tougher than a vanilla copper golem so a stray
 * zombie doesn't end a mining run. The base value is stored by vanilla with the entity, so it
 * only needs setting once per golem.
 */
public final class GolemLifecycle {

    public static final double MAX_HEALTH = 100;
    /** Health one gold ingot restores. */
    public static final double HEAL_AMOUNT = 25;

    /** Sets max health to {@link #MAX_HEALTH} and heals to full. Called by the build listener and on load. */
    public void applyStats(CopperGolem golem) {
        AttributeInstance maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.setBaseValue(MAX_HEALTH);
        golem.setHealth(maxHealth.getValue());
    }

    /** True if the golem already has golden golem stats, so a load doesn't reset its health. */
    public boolean hasStats(CopperGolem golem) {
        AttributeInstance maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth == null || maxHealth.getBaseValue() == MAX_HEALTH;
    }

    /** True if the golem is alive and below max health, i.e. {@link #heal} would do something. */
    public boolean needsHealing(CopperGolem golem) {
        AttributeInstance maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth != null && !golem.isDead() && Healing.needsHealing(golem.getHealth(), maxHealth.getValue());
    }

    /**
     * Heals the golem with the gold ingot in the player's main hand: one ingot is used (none in
     * creative) and the repair effects play. Does nothing, and returns false, if the golem is at
     * full health or the player isn't holding gold.
     */
    public boolean healWithIngot(Player player, CopperGolem golem) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.GOLD_INGOT || !heal(golem)) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack rest = hand.clone();
            rest.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(rest.getAmount() > 0 ? rest : null);
        }
        Location at = golem.getLocation().add(0, golem.getHeight() / 2, 0);
        golem.getWorld().playSound(golem, Sound.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.NEUTRAL, 1f, 1.2f);
        golem.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.3, 0.4, 0.3);
        return true;
    }

    /** Heals {@link #HEAL_AMOUNT}, capped at max health. Returns false (and does nothing) at full health. */
    public boolean heal(CopperGolem golem) {
        AttributeInstance maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null || golem.isDead()) {
            return false;
        }
        double max = maxHealth.getValue();
        double current = golem.getHealth();
        if (!Healing.needsHealing(current, max)) {
            return false;
        }
        golem.setHealth(Healing.healed(current, max, HEAL_AMOUNT));
        return true;
    }
}
