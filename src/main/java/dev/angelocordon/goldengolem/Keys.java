package dev.angelocordon.goldengolem;

import java.util.Objects;
import org.bukkit.NamespacedKey;

/**
 * Every persistent-data key the plugin writes. The namespace matches the plugin name, so data
 * stays readable if keys are ever rebuilt from the plugin instance.
 */
public final class Keys {

    public static final String NAMESPACE = "goldengolem";

    // On the golem entity.
    public static final NamespacedKey GOLEM = key("golem");
    public static final NamespacedKey OWNER = key("owner");
    public static final NamespacedKey STATE = key("state");
    public static final NamespacedKey BLOCKED_REASON = key("blocked_reason");
    public static final NamespacedKey CHEST = key("chest");
    public static final NamespacedKey WORK_CENTER = key("work_center");
    public static final NamespacedKey TOOL = key("tool");
    public static final NamespacedKey TARGET = key("target");
    public static final NamespacedKey STORAGE = key("storage");

    // On golden chest items and placed golden chest blocks.
    public static final NamespacedKey GOLDEN_CHEST = key("golden_chest");

    private Keys() {
    }

    private static NamespacedKey key(String name) {
        return Objects.requireNonNull(NamespacedKey.fromString(NAMESPACE + ":" + name));
    }
}
