package dev.angelocordon.goldengolem;

import org.bukkit.configuration.ConfigurationSection;

/** Settings from config.yml, clamped to safe ranges. Read once on enable. */
public record GolemConfig(
        int radius,
        int verticalRange,
        int scanBlocksPerTick,
        int maxChestDistance,
        double lowDurabilityPercent,
        int lowDurabilityUses) {

    public static final int MAX_RADIUS = 32;

    public static final GolemConfig DEFAULTS = new GolemConfig(16, 4, 256, 64, 5.0, 3);

    public GolemConfig {
        radius = clamp(radius, 1, MAX_RADIUS);
        verticalRange = clamp(verticalRange, 0, 16);
        scanBlocksPerTick = clamp(scanBlocksPerTick, 16, 4096);
        maxChestDistance = clamp(maxChestDistance, 1, 128);
        lowDurabilityPercent = Math.max(0.0, Math.min(50.0, lowDurabilityPercent));
        lowDurabilityUses = clamp(lowDurabilityUses, 0, 64);
    }

    public static GolemConfig from(ConfigurationSection config) {
        return new GolemConfig(
                config.getInt("work-area.radius", DEFAULTS.radius()),
                config.getInt("work-area.vertical-range", DEFAULTS.verticalRange()),
                config.getInt("work-area.scan-blocks-per-tick", DEFAULTS.scanBlocksPerTick()),
                config.getInt("max-chest-distance", DEFAULTS.maxChestDistance()),
                config.getDouble("tool.low-durability-percent", DEFAULTS.lowDurabilityPercent()),
                config.getInt("tool.low-durability-uses", DEFAULTS.lowDurabilityUses()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
