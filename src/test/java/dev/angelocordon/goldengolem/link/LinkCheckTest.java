package dev.angelocordon.goldengolem.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.angelocordon.goldengolem.link.LinkCheck.ChestInfo;
import dev.angelocordon.goldengolem.link.LinkCheck.Link;
import dev.angelocordon.goldengolem.link.LinkCheck.Result;
import dev.angelocordon.goldengolem.util.BlockLocation;
import dev.angelocordon.goldengolem.util.BlockPos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LinkCheckTest {

    private static final UUID WORLD = UUID.fromString("2f1c6a1e-5b7d-4e8a-9c21-7d4e5f6a8b90");
    private static final BlockLocation LEFT = at(0, 64, 0);
    private static final BlockLocation RIGHT = at(1, 64, 0);
    private static final BlockLocation OTHER = at(10, 64, 10);

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static BlockLocation at(int x, int y, int z) {
        return new BlockLocation(WORLD, new BlockPos(x, y, z));
    }

    /** Both halves of an intact double chest, canonical LEFT, capacity 2. */
    private static Map<BlockLocation, ChestInfo> intactDouble() {
        Map<BlockLocation, ChestInfo> golden = new HashMap<>();
        golden.put(LEFT, new ChestInfo(LEFT, 2));
        golden.put(RIGHT, new ChestInfo(LEFT, 2));
        return golden;
    }

    private static Map<BlockLocation, @Nullable BlockLocation> noBreaks() {
        return new HashMap<>();
    }

    @Test
    void intactChestsKeepTheirLinks() {
        Map<BlockLocation, ChestInfo> golden = intactDouble();
        golden.put(OTHER, new ChestInfo(OTHER, 1));

        Result result = LinkCheck.decide(
                List.of(new Link(FIRST, LEFT), new Link(SECOND, RIGHT), new Link(THIRD, OTHER)), golden, noBreaks());

        assertTrue(result.moved().isEmpty());
        assertTrue(result.missing().isEmpty());
        assertTrue(result.overCapacity().isEmpty());
    }

    @Test
    void wholeChestGoneUnlinksEveryGolemOnIt() {
        Map<BlockLocation, @Nullable BlockLocation> survivors = new HashMap<>();
        survivors.put(LEFT, RIGHT);
        survivors.put(RIGHT, LEFT);
        Map<BlockLocation, ChestInfo> golden = Map.of(OTHER, new ChestInfo(OTHER, 1));

        Result result = LinkCheck.decide(
                List.of(new Link(FIRST, LEFT), new Link(SECOND, RIGHT), new Link(THIRD, OTHER)), golden, survivors);

        assertEquals(List.of(FIRST, SECOND), result.missing());
        assertTrue(result.moved().isEmpty());
        assertTrue(result.overCapacity().isEmpty());
    }

    @Test
    void brokenSingleChestUnlinksItsGolem() {
        Map<BlockLocation, @Nullable BlockLocation> survivors = new HashMap<>();
        survivors.put(OTHER, null);

        Result result = LinkCheck.decide(List.of(new Link(FIRST, OTHER)), Map.of(), survivors);

        assertEquals(List.of(FIRST), result.missing());
    }

    @Test
    void breakingLeftHalfMovesFirstGolemToRightAndUnlinksSecond() {
        Map<BlockLocation, @Nullable BlockLocation> survivors = new HashMap<>();
        survivors.put(LEFT, RIGHT);
        Map<BlockLocation, ChestInfo> golden = Map.of(RIGHT, new ChestInfo(RIGHT, 1));

        Result result = LinkCheck.decide(List.of(new Link(FIRST, LEFT), new Link(SECOND, LEFT)), golden, survivors);

        assertEquals(Map.of(FIRST, RIGHT), result.moved());
        assertEquals(List.of(SECOND), result.overCapacity());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void breakingRightHalfKeepsFirstGolemOnLeftAndUnlinksSecond() {
        Map<BlockLocation, @Nullable BlockLocation> survivors = new HashMap<>();
        survivors.put(RIGHT, LEFT);
        Map<BlockLocation, ChestInfo> golden = Map.of(LEFT, new ChestInfo(LEFT, 1));

        Result result = LinkCheck.decide(List.of(new Link(FIRST, LEFT), new Link(SECOND, LEFT)), golden, survivors);

        assertTrue(result.moved().isEmpty());
        assertEquals(List.of(SECOND), result.overCapacity());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void golemLinkedToBrokenHalfMovesWhenSurvivorHasRoom() {
        Map<BlockLocation, @Nullable BlockLocation> survivors = new HashMap<>();
        survivors.put(RIGHT, LEFT);
        Map<BlockLocation, ChestInfo> golden = Map.of(LEFT, new ChestInfo(LEFT, 1));

        Result result = LinkCheck.decide(List.of(new Link(FIRST, RIGHT)), golden, survivors);

        assertEquals(Map.of(FIRST, LEFT), result.moved());
        assertTrue(result.overCapacity().isEmpty());
    }

    @Test
    void overCapacityKeepsTheFirstGolemsInRegistryOrder() {
        Result result = LinkCheck.decide(
                List.of(new Link(THIRD, RIGHT), new Link(FIRST, LEFT), new Link(SECOND, LEFT)),
                intactDouble(), noBreaks());

        assertEquals(List.of(SECOND), result.overCapacity());
    }

    @Test
    void movedGolemThatEndsUpOverCapacityIsNotAlsoMoved() {
        Map<BlockLocation, @Nullable BlockLocation> survivors = new HashMap<>();
        survivors.put(RIGHT, LEFT);
        Map<BlockLocation, ChestInfo> golden = Map.of(LEFT, new ChestInfo(LEFT, 1));

        Result result = LinkCheck.decide(List.of(new Link(FIRST, LEFT), new Link(SECOND, RIGHT)), golden, survivors);

        assertTrue(result.moved().isEmpty());
        assertEquals(List.of(SECOND), result.overCapacity());
    }

    @Test
    void recordedBreakIsIgnoredWhenTheChestIsStillThere() {
        Map<BlockLocation, @Nullable BlockLocation> survivors = new HashMap<>();
        survivors.put(LEFT, RIGHT);

        Result result = LinkCheck.decide(List.of(new Link(FIRST, LEFT)), intactDouble(), survivors);

        assertTrue(result.moved().isEmpty());
        assertTrue(result.missing().isEmpty());
    }
}
