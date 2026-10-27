package dev.angelocordon.goldengolem.work;

/**
 * How long a golem takes to break a block: the vanilla player formula
 * ({@code BlockBehaviour#getDestroyProgress}) without the player-only modifiers (haste, mining
 * fatigue, under water, not on ground). Pure, so it is unit-tested against vanilla break times.
 */
public final class BreakTime {

    /** {@link #ticksToBreak} for a block that can never be broken (bedrock and friends). */
    public static final int NEVER = -1;

    private BreakTime() {
    }

    /**
     * Break progress per tick, where 1.0 breaks the block: {@code destroySpeed / hardness / 30}
     * with a tool that can harvest the block, {@code / 100} without. Computed in float, as vanilla
     * does.
     *
     * @param destroySpeed the tool's speed on this block, Efficiency included
     *     ({@code Block#getDestroySpeed(tool, true)}); 1.0 for a hand or the wrong tool
     * @param hardness the block's hardness; negative means unbreakable, 0 breaks instantly
     * @param canHarvest whether the tool is correct for drops (vanilla's
     *     {@code hasCorrectToolForDrops})
     */
    public static float progressPerTick(float destroySpeed, float hardness, boolean canHarvest) {
        if (hardness < 0) {
            return 0f;
        }
        if (hardness == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return destroySpeed / hardness / (canHarvest ? 30 : 100);
    }

    /**
     * Ticks of digging until the block breaks: the first tick at which accumulated progress
     * reaches 1.0, so at least 1. {@link #NEVER} if there is no progress at all.
     */
    public static int ticksToBreak(float progressPerTick) {
        if (!(progressPerTick > 0)) {
            return NEVER;
        }
        if (progressPerTick >= 1f) {
            return 1;
        }
        return (int) Math.ceil(1.0 / progressPerTick);
    }
}
