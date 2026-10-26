package dev.angelocordon.goldengolem.build;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * The Golden Golem build pattern: a carved pumpkin or jack o'lantern on a block of gold. Kept
 * free of Bukkit glue so it can be unit-tested, and deliberately narrow so it never overlaps the
 * vanilla copper, iron or snow golem patterns.
 */
public final class BuildPattern {

    private BuildPattern() {
    }

    /** Whether {@code placed} on top of {@code below} builds a Golden Golem. */
    public static boolean matches(@Nullable Material placed, @Nullable Material below) {
        return isHead(placed) && below == Material.GOLD_BLOCK;
    }

    /** The blocks that act as a golem head. A plain (uncarved) pumpkin does not count, as in vanilla. */
    static boolean isHead(@Nullable Material material) {
        return material == Material.CARVED_PUMPKIN || material == Material.JACK_O_LANTERN;
    }
}
