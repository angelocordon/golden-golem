package dev.angelocordon.goldengolem.lifecycle;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.CopperGolem;

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
