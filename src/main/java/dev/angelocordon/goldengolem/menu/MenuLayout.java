package dev.angelocordon.goldengolem.menu;

import dev.angelocordon.goldengolem.golem.GoldenGolem;

/**
 * Where everything sits in the three-row golem menu, laid out like a horse screen: equipment and
 * controls on the left, a 5x3 storage grid on the right.
 *
 * <pre>
 *   [tool  ][hint  ][pane][pane][s0 ][s1 ][s2 ][s3 ][s4 ]
 *   [target][hint  ][pane][pane][s5 ][s6 ][s7 ][s8 ][s9 ]
 *   [toggle][status][pane][pane][s10][s11][s12][s13][s14]
 * </pre>
 *
 * Pure, so the slot map is unit-tested.
 */
public final class MenuLayout {

    /** What a raw slot of the menu view is for. */
    public enum Role {
        TOOL, TARGET, STORAGE, TOGGLE, STATUS, FILLER,
        /** Not in the menu: the player's own inventory, or outside the window. */
        OUTSIDE
    }

    public static final int ROWS = 3;
    public static final int SIZE = ROWS * 9;

    public static final int TOOL = 0;
    public static final int TOOL_HINT = 1;
    public static final int TARGET = 9;
    public static final int TARGET_HINT = 10;
    public static final int TOGGLE = 18;
    public static final int STATUS = 19;

    private static final int STORAGE_FIRST_COLUMN = 4;
    private static final int STORAGE_COLUMNS = 5;

    static {
        if (STORAGE_COLUMNS * ROWS != GoldenGolem.STORAGE_SIZE) {
            throw new IllegalStateException("menu storage grid doesn't match GoldenGolem.STORAGE_SIZE");
        }
    }

    private MenuLayout() {
    }

    public static Role roleOf(int rawSlot) {
        if (rawSlot < 0 || rawSlot >= SIZE) {
            return Role.OUTSIDE;
        }
        return switch (rawSlot) {
            case TOOL -> Role.TOOL;
            case TARGET -> Role.TARGET;
            case TOGGLE -> Role.TOGGLE;
            case STATUS -> Role.STATUS;
            default -> storageIndex(rawSlot) >= 0 ? Role.STORAGE : Role.FILLER;
        };
    }

    /** The menu slot that shows storage index {@code index} (0 to STORAGE_SIZE - 1). */
    public static int storageSlot(int index) {
        if (index < 0 || index >= GoldenGolem.STORAGE_SIZE) {
            throw new IndexOutOfBoundsException("storage index " + index);
        }
        return (index / STORAGE_COLUMNS) * 9 + STORAGE_FIRST_COLUMN + index % STORAGE_COLUMNS;
    }

    /** The storage index shown in {@code rawSlot}, or -1 if it isn't a storage slot. */
    public static int storageIndex(int rawSlot) {
        if (rawSlot < 0 || rawSlot >= SIZE) {
            return -1;
        }
        int column = rawSlot % 9 - STORAGE_FIRST_COLUMN;
        if (column < 0 || column >= STORAGE_COLUMNS) {
            return -1;
        }
        return (rawSlot / 9) * STORAGE_COLUMNS + column;
    }
}
