package dev.angelocordon.goldengolem.work;

/**
 * What a working golem does next, from plain facts about it. Pure, so the WORKING transitions
 * are unit-tested; the handler maps each value to a state (and blocked reason).
 */
public enum WorkDecision {
    /** Keep working. */
    CONTINUE,
    /** Go to IDLE: no work center, or the area has nothing left and storage is empty. */
    IDLE,
    /** Go to RETURNING: storage is full, the tool is worn, or the area is done and there is loot. */
    RETURN,
    /** BLOCKED with NO_TOOL. */
    NO_TOOL,
    /** BLOCKED with CANT_HARVEST: no target, or the tool doesn't fit it. */
    CANT_HARVEST;

    /**
     * Checked every tick before doing any work. A full storage goes home before the tool checks,
     * so a golem whose tool was taken still delivers what it carries.
     *
     * @param toolFitsTarget {@code HarvestRule.toolFits(tool, target)}; false without a target
     * @param toolLow {@code Durability.isLow} with the config thresholds
     */
    public static WorkDecision check(boolean hasWorkCenter, boolean storageFull, boolean hasTool,
            boolean toolFitsTarget, boolean toolLow) {
        if (!hasWorkCenter) {
            return IDLE;
        }
        if (storageFull) {
            return RETURN;
        }
        if (!hasTool) {
            return NO_TOOL;
        }
        if (!toolFitsTarget) {
            return CANT_HARVEST;
        }
        if (toolLow) {
            return RETURN;
        }
        return CONTINUE;
    }

    /** A full scan pass found nothing more: deliver what it has, or stand by where it is. */
    public static WorkDecision onAreaExhausted(boolean storageHasItems) {
        return storageHasItems ? RETURN : IDLE;
    }
}
