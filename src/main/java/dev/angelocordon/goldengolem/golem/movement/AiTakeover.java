package dev.angelocordon.goldengolem.golem.movement;

import dev.angelocordon.goldengolem.golem.GoldenGolem;
import org.bukkit.entity.CopperGolem;

/**
 * Switches off the vanilla copper golem AI on golden golems. The copper golem runs on brain AI,
 * which the Paper API can't edit, so instead the golem is made unaware: its brain, navigation and
 * move control stop ticking (no item sorting, no copper-button pressing, no wandering) while
 * physics keeps running. {@link PathMover} then does all the walking.
 *
 * <p>Two more vanilla behaviors are switched off here, because nothing else would: the copper
 * golem's pathfinder plans through closed wooden doors (only the brain opens them, and it's off),
 * and weathering runs outside the AI step, eventually turning a fully oxidized golem into a statue
 * and deleting it with its items.
 *
 * <p>Registered as a registry load hook, so it runs for every golden golem as it enters the
 * world. That matters for doors: the copper golem constructor turns door opening back on at every
 * load. Awareness and waxing are saved with the entity; reapplying keeps them right.
 */
public final class AiTakeover {

    private AiTakeover() {
    }

    public static void apply(GoldenGolem golem) {
        CopperGolem entity = golem.entity();
        entity.setAware(false);
        entity.getPathfinder().setCanOpenDoors(false);
        entity.setOxidizing(CopperGolem.Oxidizing.waxed());
    }
}
