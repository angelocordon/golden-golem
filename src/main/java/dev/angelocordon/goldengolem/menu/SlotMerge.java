package dev.angelocordon.goldengolem.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import org.jspecify.annotations.Nullable;

/**
 * Three-way, slot-by-slot merge of menu contents into golem data. {@code base} is what the menu
 * and the golem last agreed on, {@code view} is the menu now, {@code current} is the golem now.
 *
 * <p>A slot the player didn't touch keeps the golem's current value, so a change the golem made
 * meanwhile (a deposit, a mined drop) is never overwritten by a stale menu copy. Pure and
 * generic so it is unit-tested with plain values.
 */
public final class SlotMerge {

    /** The merged slots, and how many slots both sides changed differently. */
    public record Result<T>(List<@Nullable T> slots, int conflicts) {
    }

    private SlotMerge() {
    }

    /** One slot: the player's value if they changed it, otherwise the golem's. */
    public static <T> @Nullable T pick(@Nullable T base, @Nullable T view, @Nullable T current,
            BiPredicate<T, T> same) {
        return equal(view, base, same) ? current : view;
    }

    /** True if the player and the golem both changed this slot, to different values. */
    public static <T> boolean conflicts(@Nullable T base, @Nullable T view, @Nullable T current,
            BiPredicate<T, T> same) {
        return !equal(view, base, same) && !equal(current, base, same) && !equal(view, current, same);
    }

    public static <T> Result<T> merge(List<@Nullable T> base, List<@Nullable T> view, List<@Nullable T> current,
            BiPredicate<T, T> same) {
        if (base.size() != view.size() || base.size() != current.size()) {
            throw new IllegalArgumentException("slot lists differ in size");
        }
        List<@Nullable T> merged = new ArrayList<>(base.size());
        int conflicts = 0;
        for (int i = 0; i < base.size(); i++) {
            merged.add(pick(base.get(i), view.get(i), current.get(i), same));
            if (conflicts(base.get(i), view.get(i), current.get(i), same)) {
                conflicts++;
            }
        }
        return new Result<>(merged, conflicts);
    }

    /** Null-safe equality: two nulls are equal, a null never equals a value. */
    public static <T> boolean equal(@Nullable T a, @Nullable T b, BiPredicate<T, T> same) {
        if (a == null || b == null) {
            return a == b;
        }
        return same.test(a, b);
    }
}
