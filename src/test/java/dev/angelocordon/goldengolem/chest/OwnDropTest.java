package dev.angelocordon.goldengolem.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class OwnDropTest {

    @Test
    void emptyChestBrokenInSurvivalPicksItsOnlyDrop() {
        assertEquals(0, OwnDrop.index(List.of(Material.CHEST), false));
    }

    @Test
    void picksLastDropAfterContents() {
        assertEquals(2, OwnDrop.index(List.of(Material.DIAMOND, Material.STONE, Material.CHEST), false));
    }

    @Test
    void goldenChestFullOfPlainChestsPicksOnlyTheBlocksOwnDrop() {
        List<Material> drops = new ArrayList<>(Collections.nCopies(27, Material.CHEST));
        drops.add(Material.CHEST);
        assertEquals(27, OwnDrop.index(drops, false));
    }

    @Test
    void creativeBreakHasNoOwnDropEvenWithChestsInContents() {
        assertEquals(-1, OwnDrop.index(List.of(Material.CHEST, Material.CHEST), true));
        assertEquals(-1, OwnDrop.index(List.of(Material.CHEST), true));
    }

    @Test
    void noDropsMeansNoOwnDrop() {
        assertEquals(-1, OwnDrop.index(List.of(), false));
    }

    @Test
    void lastDropThatIsNotAChestIsNotTheOwnDrop() {
        assertEquals(-1, OwnDrop.index(List.of(Material.CHEST, Material.DIAMOND), false));
    }
}
