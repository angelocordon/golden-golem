package dev.angelocordon.goldengolem.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.angelocordon.goldengolem.golem.GoldenGolem;
import dev.angelocordon.goldengolem.menu.MenuLayout.Role;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuLayoutTest {

    @Test
    void storageIndexesMapToDistinctStorageSlotsAndBack() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < GoldenGolem.STORAGE_SIZE; i++) {
            int slot = MenuLayout.storageSlot(i);
            slots.add(slot);
            assertEquals(Role.STORAGE, MenuLayout.roleOf(slot));
            assertEquals(i, MenuLayout.storageIndex(slot));
        }
        assertEquals(GoldenGolem.STORAGE_SIZE, slots.size());
    }

    @Test
    void storageIsAFiveByThreeGridOnTheRight() {
        assertEquals(4, MenuLayout.storageSlot(0));
        assertEquals(8, MenuLayout.storageSlot(4));
        assertEquals(13, MenuLayout.storageSlot(5));
        assertEquals(26, MenuLayout.storageSlot(14));
    }

    @Test
    void controlSlotsHaveTheirRoles() {
        assertEquals(Role.TOOL, MenuLayout.roleOf(MenuLayout.TOOL));
        assertEquals(Role.TARGET, MenuLayout.roleOf(MenuLayout.TARGET));
        assertEquals(Role.TOGGLE, MenuLayout.roleOf(MenuLayout.TOGGLE));
        assertEquals(Role.STATUS, MenuLayout.roleOf(MenuLayout.STATUS));
    }

    @Test
    void everyOtherMenuSlotIsFiller() {
        int filler = 0;
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            if (MenuLayout.roleOf(slot) == Role.FILLER) {
                filler++;
                assertEquals(-1, MenuLayout.storageIndex(slot));
            }
        }
        assertEquals(MenuLayout.SIZE - GoldenGolem.STORAGE_SIZE - 4, filler);
        assertEquals(Role.FILLER, MenuLayout.roleOf(MenuLayout.TOOL_HINT));
        assertEquals(Role.FILLER, MenuLayout.roleOf(MenuLayout.TARGET_HINT));
    }

    @Test
    void slotsPastTheMenuAreOutside() {
        assertEquals(Role.OUTSIDE, MenuLayout.roleOf(MenuLayout.SIZE));
        assertEquals(Role.OUTSIDE, MenuLayout.roleOf(MenuLayout.SIZE + 30));
        assertEquals(Role.OUTSIDE, MenuLayout.roleOf(-999));
        assertEquals(Role.OUTSIDE, MenuLayout.roleOf(-1));
        assertEquals(-1, MenuLayout.storageIndex(MenuLayout.SIZE + 4));
    }

    @Test
    void rejectsStorageIndexOutOfRange() {
        assertThrows(IndexOutOfBoundsException.class, () -> MenuLayout.storageSlot(GoldenGolem.STORAGE_SIZE));
        assertThrows(IndexOutOfBoundsException.class, () -> MenuLayout.storageSlot(-1));
    }
}
