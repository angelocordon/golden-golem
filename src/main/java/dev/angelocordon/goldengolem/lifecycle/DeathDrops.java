package dev.angelocordon.goldengolem.lifecycle;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Assembles what a dying golem drops: its tool, one of its target block, then every storage
 * stack, skipping empty slots. Generic so the order and null handling are unit-testable without
 * creating {@code ItemStack}s, which needs a server.
 */
public final class DeathDrops {

    private DeathDrops() {
    }

    public static <T> List<T> assemble(@Nullable T tool, @Nullable T target, List<? extends @Nullable T> storage) {
        List<T> drops = new ArrayList<>();
        if (tool != null) {
            drops.add(tool);
        }
        if (target != null) {
            drops.add(target);
        }
        for (T item : storage) {
            if (item != null) {
                drops.add(item);
            }
        }
        return drops;
    }
}
