package dev.angelocordon.goldengolem.link;

import dev.angelocordon.goldengolem.util.BlockLocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Decides what happens to golem links after golden chests break: keep, move to the surviving half
 * of a double chest, or unlink. Pure, so it is unit-tested; {@link ChestLinkListener} gathers the
 * world state into plain values and applies the result.
 */
public final class LinkCheck {

    private LinkCheck() {
    }

    /** A golem and the chest block it is linked to. */
    public record Link(UUID golem, BlockLocation chest) {
    }

    /** A block that is a golden chest right now: its canonical half and its golem capacity. */
    public record ChestInfo(BlockLocation canonical, int capacity) {
    }

    /**
     * What to do with each link that changes. Golems not mentioned keep their link.
     *
     * @param moved golems to relink to the given block (the surviving half of their chest)
     * @param missing golems whose chest is gone
     * @param overCapacity golems whose chest still exists but has no room for them any more
     */
    public record Result(Map<UUID, BlockLocation> moved, List<UUID> missing, List<UUID> overCapacity) {
    }

    /**
     * Applies the rules to {@code links}, given in registry order.
     *
     * <ol>
     *   <li>A link to a block that is still a golden chest stays put.</li>
     *   <li>A link to a broken half moves to the survivor recorded in {@code survivors} if that is
     *       still golden. Otherwise the chest is missing.</li>
     *   <li>Per chest (grouped by canonical half, so both halves count as one), the first golems in
     *       registry order keep their link up to capacity; the rest are over capacity.</li>
     * </ol>
     *
     * <p>Rule 2 makes the outcome the same whichever half of a double chest broke.
     *
     * @param golden every relevant block that is a golden chest now; absent means not golden
     * @param survivors broken chest halves mapped to their golden partner at break time, or null
     */
    public static Result decide(
            List<Link> links,
            Map<BlockLocation, ChestInfo> golden,
            Map<BlockLocation, @Nullable BlockLocation> survivors) {
        Map<UUID, BlockLocation> moved = new LinkedHashMap<>();
        List<UUID> missing = new ArrayList<>();
        List<UUID> overCapacity = new ArrayList<>();
        Map<BlockLocation, List<UUID>> byChest = new LinkedHashMap<>();
        Map<BlockLocation, Integer> capacities = new LinkedHashMap<>();

        for (Link link : links) {
            @Nullable BlockLocation target = link.chest();
            if (!golden.containsKey(target)) {
                target = survivors.get(target);
            }
            @Nullable ChestInfo info = target == null ? null : golden.get(target);
            if (info == null) {
                missing.add(link.golem());
                continue;
            }
            if (!target.equals(link.chest())) {
                moved.put(link.golem(), target);
            }
            byChest.computeIfAbsent(info.canonical(), key -> new ArrayList<>()).add(link.golem());
            // Both halves of a double chest report the same capacity, so the first one seen will do.
            capacities.putIfAbsent(info.canonical(), info.capacity());
        }

        for (Map.Entry<BlockLocation, List<UUID>> entry : byChest.entrySet()) {
            int capacity = capacities.get(entry.getKey());
            List<UUID> group = entry.getValue();
            for (int i = Math.max(0, capacity); i < group.size(); i++) {
                UUID golem = group.get(i);
                moved.remove(golem);
                overCapacity.add(golem);
            }
        }
        return new Result(moved, missing, overCapacity);
    }
}
