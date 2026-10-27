package dev.angelocordon.goldengolem.work;

import dev.angelocordon.goldengolem.work.HarvestRule.ToolKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * Picks the replacement tool from the chest when the golem's tool is worn out or missing, and
 * works out the chest after the swap. Pure: the caller reads each chest tool's type, uses left
 * and low flag off the item and passes them in.
 */
public final class ToolSwap {

    /**
     * A tool in the chest.
     *
     * @param slot the chest slot it is in
     * @param type its material
     * @param usesLeft max damage minus damage; {@link Integer#MAX_VALUE} for unbreakable tools
     * @param low whether {@link Durability#isLow} says it is worn out
     */
    public record Candidate(int slot, Material type, int usesLeft, boolean low) {
    }

    /**
     * The chest after a swap, and the one tool that came out of it for the golem.
     *
     * @param chest the chest's new slots, same size as before
     * @param taken a single tool (amount 1) taken from the picked slot
     */
    public record Applied<T>(List<@Nullable T> chest, T taken) {
    }

    /** Best first: higher tier, then more uses left, then the lower slot so the pick is stable. */
    private static final Comparator<Candidate> BEST_FIRST = Comparator
            .comparingInt((Candidate c) -> tierRank(c.type())).reversed()
            .thenComparing(Comparator.comparingInt((Candidate c) -> c.usesLeft()).reversed())
            .thenComparingInt(Candidate::slot);

    private ToolSwap() {
    }

    /**
     * The best candidate that isn't itself low, or empty if there is none. Callers filter the
     * kind first with {@link #acceptable}.
     */
    public static Optional<Candidate> pick(List<Candidate> candidates) {
        return candidates.stream().filter(c -> !c.low()).min(BEST_FIRST);
    }

    /**
     * Whether a chest tool may replace the golem's: the same {@link ToolKind} as the worn tool,
     * or, if the golem had no tool ({@code wornKind} null), any tool that
     * {@link HarvestRule#toolFits fits} the target. With neither a worn kind nor a target nothing
     * is acceptable, since there is no way to tell what the golem needs.
     */
    public static boolean acceptable(Material type, @Nullable ToolKind wornKind, @Nullable Material target) {
        Optional<ToolKind> kind = ToolKind.of(type);
        if (kind.isEmpty()) {
            return false;
        }
        if (wornKind != null) {
            return kind.get() == wornKind;
        }
        return target != null && HarvestRule.toolFits(type, target);
    }

    /**
     * Tool tier from the material name, higher is better. Mirrors vanilla mining tiers, with the
     * tiers that share a mining level ranked equal (uses left breaks the tie):
     *
     * <table>
     *   <caption>Tier ranks</caption>
     *   <tr><th>Prefix</th><th>Rank</th></tr>
     *   <tr><td>WOODEN_</td><td>0</td></tr>
     *   <tr><td>STONE_, COPPER_, GOLDEN_</td><td>1</td></tr>
     *   <tr><td>IRON_</td><td>2</td></tr>
     *   <tr><td>DIAMOND_</td><td>3</td></tr>
     *   <tr><td>NETHERITE_</td><td>4</td></tr>
     * </table>
     *
     * Anything else (a tool from a future update) ranks -1, below wood.
     */
    public static int tierRank(Material type) {
        String name = type.name();
        if (name.startsWith("WOODEN_")) {
            return 0;
        }
        if (name.startsWith("STONE_") || name.startsWith("COPPER_") || name.startsWith("GOLDEN_")) {
            return 1;
        }
        if (name.startsWith("IRON_")) {
            return 2;
        }
        if (name.startsWith("DIAMOND_")) {
            return 3;
        }
        if (name.startsWith("NETHERITE_")) {
            return 4;
        }
        return -1;
    }

    /**
     * Takes one tool out of chest {@code slot} and puts {@code worn} (if any) into the chest.
     * Taking first frees the slot when the stack held one tool, so the worn tool nearly always
     * fits; a stacked tool is split and the rest stays. Returns empty, meaning the caller writes
     * nothing, when the worn tool doesn't fit. Item totals are unchanged: the worn tool goes in,
     * the taken one comes out. Inputs are never mutated.
     *
     * @throws IllegalArgumentException if {@code slot} is empty
     */
    public static <T> Optional<Applied<T>> apply(
            List<? extends @Nullable T> chest, @Nullable T worn, int slot, Deposit.Stacks<T> stacks) {
        List<@Nullable T> slots = new ArrayList<>(chest);
        T stack = slots.get(slot);
        if (stack == null) {
            throw new IllegalArgumentException("chest slot " + slot + " is empty");
        }
        int amount = stacks.amount(stack);
        T taken = stacks.withAmount(stack, 1);
        slots.set(slot, amount > 1 ? stacks.withAmount(stack, amount - 1) : null);
        if (worn == null) {
            return Optional.of(new Applied<>(Collections.unmodifiableList(slots), taken));
        }
        Deposit.Result<T> stored = Deposit.into(slots, List.of(worn), stacks);
        if (!stored.allDeposited()) {
            return Optional.empty();
        }
        return Optional.of(new Applied<>(stored.chest(), taken));
    }
}
