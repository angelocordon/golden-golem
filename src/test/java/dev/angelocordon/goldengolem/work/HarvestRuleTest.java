package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class HarvestRuleTest {

    private static final List<Material> TOOLS = List.of(
            Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL);

    private static void assertFits(Material tool, Material... targets) {
        for (Material target : targets) {
            assertTrue(HarvestRule.toolFits(tool, target), tool + " should fit " + target);
        }
    }

    private static void assertDoesNotFit(Material tool, Material... targets) {
        for (Material target : targets) {
            assertFalse(HarvestRule.toolFits(tool, target), tool + " should not fit " + target);
        }
    }

    @Test
    void axeFitsLogsWoodStemsAndHyphaeIncludingStripped() {
        assertFits(Material.IRON_AXE,
                Material.OAK_LOG, Material.SPRUCE_WOOD, Material.CHERRY_LOG,
                Material.MANGROVE_WOOD, Material.PALE_OAK_LOG,
                Material.STRIPPED_OAK_LOG, Material.STRIPPED_BIRCH_WOOD,
                Material.CRIMSON_STEM, Material.WARPED_HYPHAE,
                Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_HYPHAE,
                Material.BAMBOO_BLOCK, Material.STRIPPED_BAMBOO_BLOCK);
    }

    @Test
    void axeFitsEveryLogAndWoodVariant() {
        for (Material material : Material.values()) {
            String name = material.name();
            if (!name.startsWith("LEGACY_")
                    && (name.endsWith("_LOG") || name.endsWith("_WOOD")
                            || name.endsWith("_HYPHAE"))) {
                assertFits(Material.WOODEN_AXE, material);
            }
        }
    }

    @Test
    void axeDoesNotFitStoneOrNonWood() {
        assertDoesNotFit(Material.DIAMOND_AXE,
                Material.STONE, Material.DIRT, Material.OAK_PLANKS, Material.OAK_LEAVES,
                Material.PUMPKIN_STEM, Material.ATTACHED_MELON_STEM, Material.BIG_DRIPLEAF_STEM,
                Material.MUSHROOM_STEM, Material.IRON_ORE);
    }

    @Test
    void pickaxeFitsStoneLikeBlocks() {
        assertFits(Material.STONE_PICKAXE,
                Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE,
                Material.COBBLED_DEEPSLATE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
                Material.TUFF, Material.CALCITE, Material.BLACKSTONE, Material.BASALT,
                Material.NETHERRACK, Material.SANDSTONE, Material.END_STONE, Material.OBSIDIAN);
    }

    @Test
    void pickaxeFitsEveryOre() {
        assertFits(Material.IRON_PICKAXE,
                Material.COAL_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.NETHER_GOLD_ORE,
                Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS);
        for (Material material : Material.values()) {
            String name = material.name();
            if (!name.startsWith("LEGACY_") && name.endsWith("_ORE")) {
                assertFits(Material.NETHERITE_PICKAXE, material);
            }
        }
    }

    @Test
    void pickaxeDoesNotFitLogsOrDirt() {
        assertDoesNotFit(Material.DIAMOND_PICKAXE,
                Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.DIRT, Material.SAND,
                Material.GRAVEL);
    }

    @Test
    void shovelFitsSoftGround() {
        assertFits(Material.GOLDEN_SHOVEL,
                Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.GRASS_BLOCK,
                Material.PODZOL, Material.MYCELIUM, Material.SAND, Material.RED_SAND,
                Material.GRAVEL, Material.CLAY, Material.MUD, Material.SOUL_SAND,
                Material.SOUL_SOIL, Material.SNOW_BLOCK);
    }

    @Test
    void shovelDoesNotFitStoneOrLogs() {
        assertDoesNotFit(Material.DIAMOND_SHOVEL,
                Material.STONE, Material.SANDSTONE, Material.OAK_LOG, Material.IRON_ORE);
    }

    @Test
    void nonToolsFitNothing() {
        assertDoesNotFit(Material.DIAMOND_HOE, Material.STONE, Material.OAK_LOG, Material.DIRT);
        assertDoesNotFit(Material.STICK, Material.STONE, Material.OAK_LOG, Material.DIRT);
        assertDoesNotFit(Material.STONE, Material.STONE);
    }

    @Test
    void nonBlocksAndItemsFitNothing() {
        for (Material tool : TOOLS) {
            assertDoesNotFit(tool, Material.AIR, Material.DIAMOND, Material.OAK_PLANKS,
                    Material.STONE_BRICKS);
        }
    }

    @Test
    void legacyMaterialsNeverFit() {
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_")) {
                for (Material tool : TOOLS) {
                    assertDoesNotFit(tool, material);
                }
            }
        }
    }

    @Test
    void atMostOneToolKindFitsAnyBlock() {
        for (Material material : Material.values()) {
            int fits = 0;
            for (Material tool : TOOLS) {
                if (HarvestRule.toolFits(tool, material)) {
                    fits++;
                }
            }
            assertTrue(fits <= 1, material + " fits " + fits + " tool kinds");
        }
    }
}
