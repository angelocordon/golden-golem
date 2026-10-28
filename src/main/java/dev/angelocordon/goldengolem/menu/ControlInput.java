package dev.angelocordon.goldengolem.menu;

/**
 * What a hit or right-click on a golden golem does. Pure, so it is unit-tested.
 *
 * <p>Healing wins over sneaking, as right-click always did: a hurt golem with a gold ingot in
 * hand is healed even while sneaking. At full health the ingot does nothing special.
 */
public enum ControlInput {
    OPEN,
    TOGGLE,
    HEAL;

    public static ControlInput decide(boolean sneaking, boolean holdingGoldIngot, boolean golemHurt) {
        if (holdingGoldIngot && golemHurt) {
            return HEAL;
        }
        return sneaking ? TOGGLE : OPEN;
    }
}
