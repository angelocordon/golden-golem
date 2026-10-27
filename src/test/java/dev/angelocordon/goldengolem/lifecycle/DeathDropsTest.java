package dev.angelocordon.goldengolem.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeathDropsTest {

    @Test
    void dropsToolTargetThenStorageInOrder() {
        List<String> storage = Arrays.asList("cobblestone x64", "coal x3");
        assertEquals(List.of("pickaxe", "stone", "cobblestone x64", "coal x3"),
                DeathDrops.assemble("pickaxe", "stone", storage));
    }

    @Test
    void skipsEmptyStorageSlots() {
        List<String> storage = Arrays.asList(null, "dirt", null, null, "gravel");
        assertEquals(List.of("shovel", "dirt", "gravel"), DeathDrops.assemble("shovel", null, storage));
    }

    @Test
    void dropsNothingWhenCarryingNothing() {
        List<String> storage = Arrays.asList(new String[15]);
        assertEquals(List.of(), DeathDrops.assemble(null, null, storage));
    }

    @Test
    void dropsTargetWithoutTool() {
        assertEquals(List.of("oak log"), DeathDrops.assemble(null, "oak log", List.of()));
    }

    @Test
    void keepsEveryStorageStack() {
        List<String> storage = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o");
        assertEquals(15, DeathDrops.assemble(null, null, storage).size());
    }
}
