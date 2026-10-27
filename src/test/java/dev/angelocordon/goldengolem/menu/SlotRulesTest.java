package dev.angelocordon.goldengolem.menu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.angelocordon.goldengolem.menu.SlotRules.ClickHandling;
import dev.angelocordon.goldengolem.menu.SlotRules.TargetCheck;
import java.util.List;
import java.util.function.BiPredicate;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

class SlotRulesTest {

    private static final int PLAYER_SLOT = MenuLayout.SIZE + 5;
    private static final int STORAGE = MenuLayout.storageSlot(3);
    private static final BiPredicate<Material, Material> ALWAYS_FITS = (tool, target) -> true;
    private static final BiPredicate<Material, Material> NEVER_FITS = (tool, target) -> false;

    // --- clicks ---

    @Test
    void storageSlotsBehaveLikeAChest() {
        assertEquals(ClickHandling.ALLOW, SlotRules.classify(STORAGE, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(ClickHandling.ALLOW, SlotRules.classify(STORAGE, ClickType.RIGHT, InventoryAction.PLACE_ONE));
        assertEquals(ClickHandling.ALLOW,
                SlotRules.classify(STORAGE, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(ClickHandling.ALLOW, SlotRules.classify(STORAGE, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP));
        assertEquals(ClickHandling.ALLOW, SlotRules.classify(STORAGE, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP));
    }

    @Test
    void doubleClickCollectIsDeniedEverywhere() {
        // It would pull matching panes or the target sample out of the menu.
        assertEquals(ClickHandling.DENY,
                SlotRules.classify(STORAGE, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR));
        assertEquals(ClickHandling.DENY,
                SlotRules.classify(PLAYER_SLOT, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR));
    }

    @Test
    void shiftClickFromPlayerInventoryIsHandledByTheMenu() {
        assertEquals(ClickHandling.MOVE_INTO_MENU,
                SlotRules.classify(PLAYER_SLOT, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
    }

    @Test
    void otherPlayerInventoryClicksAreVanilla() {
        assertEquals(ClickHandling.ALLOW, SlotRules.classify(PLAYER_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(ClickHandling.ALLOW, SlotRules.classify(-999, ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR));
    }

    @Test
    void plainAndShiftClicksOnControlSlotsUseTheOneItemRules() {
        assertEquals(ClickHandling.TOOL_SLOT,
                SlotRules.classify(MenuLayout.TOOL, ClickType.LEFT, InventoryAction.PLACE_ALL));
        assertEquals(ClickHandling.TOOL_SLOT,
                SlotRules.classify(MenuLayout.TOOL, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
        assertEquals(ClickHandling.TARGET_SLOT,
                SlotRules.classify(MenuLayout.TARGET, ClickType.RIGHT, InventoryAction.PLACE_ONE));
        assertEquals(ClickHandling.TARGET_SLOT,
                SlotRules.classify(MenuLayout.TARGET, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR));
    }

    @Test
    void numberKeysOffhandDropAndCreativeOnControlSlotsAreDenied() {
        for (int slot : new int[] {MenuLayout.TOOL, MenuLayout.TARGET, MenuLayout.TOGGLE}) {
            assertEquals(ClickHandling.DENY, SlotRules.classify(slot, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP));
            assertEquals(ClickHandling.DENY, SlotRules.classify(slot, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP));
            assertEquals(ClickHandling.DENY, SlotRules.classify(slot, ClickType.DROP, InventoryAction.DROP_ONE_SLOT));
            assertEquals(ClickHandling.DENY, SlotRules.classify(slot, ClickType.CREATIVE, InventoryAction.PLACE_ALL));
            assertEquals(ClickHandling.DENY, SlotRules.classify(slot, ClickType.MIDDLE, InventoryAction.CLONE_STACK));
        }
    }

    @Test
    void toggleItemFlipsOnPlainClickOnly() {
        assertEquals(ClickHandling.TOGGLE, SlotRules.classify(MenuLayout.TOGGLE, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(ClickHandling.TOGGLE, SlotRules.classify(MenuLayout.TOGGLE, ClickType.RIGHT, InventoryAction.PICKUP_HALF));
        assertEquals(ClickHandling.DENY,
                SlotRules.classify(MenuLayout.TOGGLE, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY));
    }

    @Test
    void statusAndPanesCanNeverBeTakenOrFilled() {
        for (int slot : new int[] {MenuLayout.STATUS, MenuLayout.TOOL_HINT, 2, 3, 20}) {
            assertEquals(ClickHandling.DENY, SlotRules.classify(slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
            assertEquals(ClickHandling.DENY, SlotRules.classify(slot, ClickType.LEFT, InventoryAction.PLACE_ALL));
            assertEquals(ClickHandling.DENY,
                    SlotRules.classify(slot, ClickType.LEFT, InventoryAction.PICKUP_ALL_INTO_BUNDLE));
        }
    }

    // --- drags ---

    @Test
    void dragOverStorageAndPlayerInventoryIsAllowed() {
        assertTrue(SlotRules.dragAllowed(List.of(STORAGE, MenuLayout.storageSlot(0), PLAYER_SLOT)));
    }

    @Test
    void dragTouchingAnyControlOrPaneSlotIsDenied() {
        assertFalse(SlotRules.dragAllowed(List.of(STORAGE, MenuLayout.TOOL)));
        assertFalse(SlotRules.dragAllowed(List.of(STORAGE, MenuLayout.TARGET)));
        assertFalse(SlotRules.dragAllowed(List.of(MenuLayout.TOOL_HINT)));
    }

    // --- tool and target ---

    @Test
    void toolSlotTakesPickaxesAxesAndShovelsOnly() {
        assertTrue(SlotRules.acceptsTool(Material.IRON_PICKAXE));
        assertTrue(SlotRules.acceptsTool(Material.NETHERITE_AXE));
        assertTrue(SlotRules.acceptsTool(Material.STONE_SHOVEL));
        assertFalse(SlotRules.acceptsTool(Material.IRON_HOE));
        assertFalse(SlotRules.acceptsTool(Material.STONE));
        assertFalse(SlotRules.acceptsTool(null));
    }

    @Test
    void targetSlotTakesAPlainBlockTheToolFits() {
        assertEquals(TargetCheck.OK,
                SlotRules.checkTarget(Material.IRON_PICKAXE, Material.STONE, true, true, ALWAYS_FITS));
    }

    @Test
    void targetSlotRejectsBlocksTheToolDoesNotFit() {
        assertEquals(TargetCheck.DOES_NOT_FIT,
                SlotRules.checkTarget(Material.IRON_PICKAXE, Material.OAK_LOG, true, true, NEVER_FITS));
    }

    @Test
    void targetSlotRejectsNonBlocks() {
        assertEquals(TargetCheck.DOES_NOT_FIT,
                SlotRules.checkTarget(Material.IRON_PICKAXE, Material.DIAMOND, false, true, ALWAYS_FITS));
    }

    @Test
    void targetSlotRejectsItemsWithCustomData() {
        // A renamed or custom block would lose its data, since only the Material is saved.
        assertEquals(TargetCheck.NOT_PLAIN,
                SlotRules.checkTarget(Material.IRON_PICKAXE, Material.STONE, true, false, ALWAYS_FITS));
    }

    @Test
    void targetSlotRejectsEverythingWithoutAValidTool() {
        assertEquals(TargetCheck.NO_TOOL, SlotRules.checkTarget(null, Material.STONE, true, true, ALWAYS_FITS));
        assertEquals(TargetCheck.NO_TOOL,
                SlotRules.checkTarget(Material.IRON_HOE, Material.STONE, true, true, ALWAYS_FITS));
    }

    @Test
    void targetSlotPassesToolAndTargetToTheFitRule() {
        BiPredicate<Material, Material> onlyPickaxeOnStone =
                (tool, target) -> tool == Material.IRON_PICKAXE && target == Material.STONE;
        assertEquals(TargetCheck.OK,
                SlotRules.checkTarget(Material.IRON_PICKAXE, Material.STONE, true, true, onlyPickaxeOnStone));
        assertEquals(TargetCheck.DOES_NOT_FIT,
                SlotRules.checkTarget(Material.IRON_AXE, Material.STONE, true, true, onlyPickaxeOnStone));
    }

    @Test
    void targetSlotUsesTheRealHarvestRule() {
        assertEquals(TargetCheck.OK, SlotRules.checkTarget(Material.IRON_PICKAXE, Material.STONE, true, true));
        assertEquals(TargetCheck.OK, SlotRules.checkTarget(Material.IRON_AXE, Material.OAK_LOG, true, true));
        assertEquals(TargetCheck.DOES_NOT_FIT,
                SlotRules.checkTarget(Material.IRON_AXE, Material.STONE, true, true));
    }

    // --- shift-click fill plan ---

    @Test
    void fillsMatchingStacksBeforeEmptySlots() {
        // slot 0 empty, slot 1 has 60 of the item, slot 2 a different item, slot 3 has 10
        int[] add = SlotRules.planFill(20, 64, new int[] {0, 60, -1, 10});
        assertArrayEquals(new int[] {0, 4, 0, 16}, add);
    }

    @Test
    void spillsIntoEmptySlotsInOrder() {
        int[] add = SlotRules.planFill(100, 64, new int[] {-1, 0, 0});
        assertArrayEquals(new int[] {0, 64, 36}, add);
    }

    @Test
    void movesOnlyWhatFits() {
        int[] add = SlotRules.planFill(10, 64, new int[] {-1, 60, 64});
        assertArrayEquals(new int[] {0, 4, 0}, add);
    }

    @Test
    void neverOverfillsAnOverfullStack() {
        int[] add = SlotRules.planFill(5, 16, new int[] {20, 0});
        assertArrayEquals(new int[] {0, 5}, add);
    }
}
