package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.angelocordon.goldengolem.work.HarvestRule.ToolKind;
import java.util.Optional;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Also proves that plain Material constants and name() work without a server. */
class ToolKindTest {

    @Test
    void classifiesToolsByName() {
        assertEquals(Optional.of(ToolKind.PICKAXE), ToolKind.of(Material.DIAMOND_PICKAXE));
        assertEquals(Optional.of(ToolKind.AXE), ToolKind.of(Material.GOLDEN_AXE));
        assertEquals(Optional.of(ToolKind.SHOVEL), ToolKind.of(Material.WOODEN_SHOVEL));
    }

    @Test
    void rejectsNonTools() {
        assertEquals(Optional.empty(), ToolKind.of(Material.DIAMOND_HOE));
        assertEquals(Optional.empty(), ToolKind.of(Material.STONE));
        assertEquals(Optional.empty(), ToolKind.of(null));
    }
}
