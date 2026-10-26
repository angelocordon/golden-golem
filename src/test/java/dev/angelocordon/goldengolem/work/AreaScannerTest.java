package dev.angelocordon.goldengolem.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.angelocordon.goldengolem.GolemConfig;
import dev.angelocordon.goldengolem.util.BlockPos;
import dev.angelocordon.goldengolem.work.AreaScanner.Exhausted;
import dev.angelocordon.goldengolem.work.AreaScanner.Found;
import dev.angelocordon.goldengolem.work.AreaScanner.Result;
import dev.angelocordon.goldengolem.work.AreaScanner.Scanning;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AreaScannerTest {

    private static final BlockPos CENTER = new BlockPos(100, 64, -20);
    private static final int BIG_BUDGET = 1_000_000;

    /** A fake world: matches the given positions and records every probe in order. */
    private static final class FakeWorld implements BlockProbe {
        final Set<BlockPos> targets = new HashSet<>();
        final List<BlockPos> probed = new ArrayList<>();

        FakeWorld with(BlockPos... positions) {
            targets.addAll(List.of(positions));
            return this;
        }

        @Override
        public boolean matches(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            probed.add(pos);
            return targets.contains(pos);
        }
    }

    /** Steps until the scanner stops reporting {@link Scanning}. */
    private static Result drain(AreaScanner scanner, BlockProbe probe, long tick) {
        for (int i = 0; i < 1_000_000; i++) {
            Result result = scanner.step(probe, tick);
            if (!(result instanceof Scanning)) {
                return result;
            }
        }
        fail("scanner never finished");
        return null;
    }

    private static BlockPos rel(int dx, int dy, int dz) {
        return CENTER.offset(dx, dy, dz);
    }

    @Test
    void visitsEveryPositionOnceNearestFirst() {
        AreaScanner scanner = new AreaScanner(3, 2, BIG_BUDGET);
        FakeWorld world = new FakeWorld();
        scanner.reset(CENTER);

        assertInstanceOf(Exhausted.class, scanner.step(world, 0));

        assertEquals(7 * 7 * 5, world.probed.size());
        assertEquals(world.probed.size(), new HashSet<>(world.probed).size());
        assertEquals(CENTER, world.probed.getFirst());
        long previous = -1;
        for (BlockPos pos : world.probed) {
            long dist = pos.distanceSquared(CENTER);
            assertTrue(dist >= previous, "distance went down at " + pos);
            previous = dist;
        }
    }

    @Test
    void breaksDistanceTiesByDyThenDxThenDz() {
        AreaScanner scanner = new AreaScanner(1, 1, BIG_BUDGET);
        FakeWorld world = new FakeWorld();
        scanner.reset(CENTER);
        scanner.step(world, 0);

        List<BlockPos> distanceOne = world.probed.subList(1, 7);
        assertEquals(
                List.of(rel(0, -1, 0), rel(-1, 0, 0), rel(0, 0, -1), rel(0, 0, 1), rel(1, 0, 0),
                        rel(0, 1, 0)),
                distanceOne);
    }

    @Test
    void scanOrderIsTheSameEveryRun() {
        FakeWorld first = new FakeWorld();
        FakeWorld second = new FakeWorld();
        AreaScanner a = new AreaScanner(4, 2, BIG_BUDGET);
        AreaScanner b = new AreaScanner(4, 2, BIG_BUDGET);
        a.reset(CENTER);
        b.reset(CENTER);
        a.step(first, 0);
        b.step(second, 0);

        assertEquals(first.probed, second.probed);
    }

    @Test
    void findsTheNearestMatch() {
        AreaScanner scanner = new AreaScanner(8, 4, BIG_BUDGET);
        FakeWorld world = new FakeWorld().with(rel(5, 0, 0), rel(1, 1, 0), rel(-3, 2, 3));
        scanner.reset(CENTER);

        assertEquals(new Found(rel(1, 1, 0)), drain(scanner, world, 0));
    }

    @Test
    void probesExactlyTheBudgetPerStep() {
        AreaScanner scanner = new AreaScanner(4, 2, 10);
        FakeWorld world = new FakeWorld();
        scanner.reset(CENTER);

        assertInstanceOf(Scanning.class, scanner.step(world, 0));
        assertEquals(10, world.probed.size());
        assertInstanceOf(Scanning.class, scanner.step(world, 1));
        assertEquals(20, world.probed.size());
    }

    @Test
    void lastStepProbesTheRemainderThenReportsExhausted() {
        // 5 x 5 x 3 = 75 positions; with 10 per tick that is 7 full steps and one of 5.
        AreaScanner scanner = new AreaScanner(2, 1, 10);
        FakeWorld world = new FakeWorld();
        scanner.reset(CENTER);

        for (int i = 0; i < 7; i++) {
            assertInstanceOf(Scanning.class, scanner.step(world, i));
        }
        assertEquals(70, world.probed.size());
        assertInstanceOf(Exhausted.class, scanner.step(world, 7));
        assertEquals(75, world.probed.size());
    }

    @Test
    void stopsProbingAsSoonAsItFindsAMatch() {
        AreaScanner scanner = new AreaScanner(4, 2, 50);
        FakeWorld world = new FakeWorld().with(CENTER);
        scanner.reset(CENTER);

        assertEquals(new Found(CENTER), scanner.step(world, 0));
        assertEquals(1, world.probed.size());
    }

    @Test
    void resumesAfterFoundWithoutReset() {
        AreaScanner scanner = new AreaScanner(6, 2, 7);
        FakeWorld world = new FakeWorld().with(rel(1, 0, 0), rel(4, 0, 0));
        scanner.reset(CENTER);

        assertEquals(new Found(rel(1, 0, 0)), drain(scanner, world, 0));
        assertEquals(new Found(rel(4, 0, 0)), drain(scanner, world, 0));
        assertInstanceOf(Exhausted.class, drain(scanner, world, 0));
        assertEquals(world.probed.size(), new HashSet<>(world.probed).size(),
                "resuming must not re-probe positions");
    }

    @Test
    void resetStartsOverFromTheNearest() {
        AreaScanner scanner = new AreaScanner(6, 2, BIG_BUDGET);
        FakeWorld world = new FakeWorld().with(rel(1, 0, 0), rel(4, 0, 0));
        scanner.reset(CENTER);

        assertEquals(new Found(rel(1, 0, 0)), drain(scanner, world, 0));
        scanner.reset(CENTER);
        assertEquals(new Found(rel(1, 0, 0)), drain(scanner, world, 0));
    }

    @Test
    void resetToANewCenterScansAroundIt() {
        AreaScanner scanner = new AreaScanner(2, 0, BIG_BUDGET);
        BlockPos elsewhere = new BlockPos(0, 10, 0);
        FakeWorld world = new FakeWorld().with(elsewhere.offset(2, 0, 2));
        scanner.reset(CENTER);
        assertInstanceOf(Exhausted.class, drain(scanner, world, 0));

        scanner.reset(elsewhere);
        assertEquals(new Found(elsewhere.offset(2, 0, 2)), drain(scanner, world, 0));
    }

    @Test
    void blacklistedPositionIsSkippedAndNotProbed() {
        AreaScanner scanner = new AreaScanner(6, 2, BIG_BUDGET);
        FakeWorld world = new FakeWorld().with(rel(1, 0, 0), rel(4, 0, 0));
        scanner.blacklist(rel(1, 0, 0), 100);
        scanner.reset(CENTER);

        assertEquals(new Found(rel(4, 0, 0)), drain(scanner, world, 50));
        assertFalse(world.probed.contains(rel(1, 0, 0)));
    }

    @Test
    void blacklistAfterFoundThenContinueSkipsIt() {
        AreaScanner scanner = new AreaScanner(6, 2, BIG_BUDGET);
        FakeWorld world = new FakeWorld().with(rel(1, 0, 0), rel(4, 0, 0));
        scanner.reset(CENTER);

        Result first = drain(scanner, world, 0);
        assertEquals(new Found(rel(1, 0, 0)), first);
        scanner.blacklist(((Found) first).pos(), 200);
        scanner.reset(CENTER);
        assertEquals(new Found(rel(4, 0, 0)), drain(scanner, world, 10));
    }

    @Test
    void blacklistExpiresAtItsUntilTick() {
        AreaScanner scanner = new AreaScanner(6, 2, BIG_BUDGET);
        FakeWorld world = new FakeWorld().with(rel(1, 0, 0), rel(4, 0, 0));
        scanner.blacklist(rel(1, 0, 0), 100);

        scanner.reset(CENTER);
        assertEquals(new Found(rel(4, 0, 0)), drain(scanner, world, 99));
        scanner.reset(CENTER);
        assertEquals(new Found(rel(1, 0, 0)), drain(scanner, world, 100));
    }

    @Test
    void expiredBlacklistEntriesArePruned() {
        AreaScanner scanner = new AreaScanner(2, 0, BIG_BUDGET);
        FakeWorld world = new FakeWorld();
        scanner.blacklist(rel(1, 0, 0), 10);
        scanner.blacklist(rel(2, 0, 0), 20);
        scanner.reset(CENTER);
        assertEquals(2, scanner.blacklistSize());

        scanner.step(world, 15);
        assertEquals(1, scanner.blacklistSize());
        scanner.reset(CENTER);
        scanner.step(world, 20);
        assertEquals(0, scanner.blacklistSize());
    }

    @Test
    void blacklistSurvivesReset() {
        AreaScanner scanner = new AreaScanner(2, 0, BIG_BUDGET);
        FakeWorld world = new FakeWorld().with(rel(1, 0, 0));
        scanner.blacklist(rel(1, 0, 0), 100);
        scanner.reset(CENTER);
        scanner.reset(CENTER);

        assertInstanceOf(Exhausted.class, drain(scanner, world, 0));
    }

    @Test
    void blacklistedPositionsCountTowardTheBudget() {
        AreaScanner scanner = new AreaScanner(4, 2, 10);
        FakeWorld world = new FakeWorld();
        scanner.blacklist(CENTER, 100);
        scanner.reset(CENTER);

        scanner.step(world, 0);
        assertEquals(9, world.probed.size());
    }

    @Test
    void capsRadiusAtTheConfigMaximum() {
        AreaScanner scanner = new AreaScanner(100, 0, BIG_BUDGET);
        int max = GolemConfig.MAX_RADIUS;
        FakeWorld world = new FakeWorld().with(rel(max + 1, 0, 0), rel(0, 0, -(max + 1)));
        scanner.reset(CENTER);

        assertEquals(max, scanner.radius());
        assertInstanceOf(Exhausted.class, drain(scanner, world, 0));
        int side = 2 * max + 1;
        assertEquals(side * side, world.probed.size());
        assertTrue(world.probed.contains(rel(max, 0, max)));
        assertTrue(world.probed.contains(rel(-max, 0, -max)));
        for (BlockPos pos : world.probed) {
            assertTrue(Math.abs(pos.x() - CENTER.x()) <= max);
            assertTrue(Math.abs(pos.z() - CENTER.z()) <= max);
        }
    }

    @Test
    void scansVerticalRangeUpAndDownOnly() {
        AreaScanner scanner = new AreaScanner(3, 2, BIG_BUDGET);
        FakeWorld world = new FakeWorld().with(rel(0, 3, 0), rel(0, -3, 0));
        scanner.reset(CENTER);

        assertInstanceOf(Exhausted.class, drain(scanner, world, 0));
        Set<Integer> dys = new HashSet<>();
        for (BlockPos pos : world.probed) {
            dys.add(pos.y() - CENTER.y());
        }
        assertEquals(Set.of(-2, -1, 0, 1, 2), dys);

        FakeWorld edge = new FakeWorld().with(rel(0, -2, 0));
        scanner.reset(CENTER);
        assertEquals(new Found(rel(0, -2, 0)), drain(scanner, edge, 0));
    }

    @Test
    void zeroVerticalRangeScansOneLayer() {
        AreaScanner scanner = new AreaScanner(2, 0, BIG_BUDGET);
        FakeWorld world = new FakeWorld();
        scanner.reset(CENTER);
        scanner.step(world, 0);

        assertEquals(25, world.probed.size());
        assertTrue(world.probed.stream().allMatch(p -> p.y() == CENTER.y()));
    }

    @Test
    void reportsExhaustedOnAnEmptyAreaAndStaysExhausted() {
        AreaScanner scanner = new AreaScanner(5, 2, 64);
        FakeWorld world = new FakeWorld();
        scanner.reset(CENTER);

        assertInstanceOf(Exhausted.class, drain(scanner, world, 0));
        int probes = world.probed.size();
        assertEquals(scanner.areaSize(), probes);
        assertInstanceOf(Exhausted.class, scanner.step(world, 1));
        assertEquals(probes, world.probed.size());
    }

    @Test
    void stepBeforeResetIsExhaustedWithoutProbing() {
        AreaScanner scanner = new AreaScanner(5, 2, 64);
        FakeWorld world = new FakeWorld().with(CENTER);

        assertInstanceOf(Exhausted.class, scanner.step(world, 0));
        assertTrue(world.probed.isEmpty());
    }

    @Test
    void sharesOffsetsBetweenScannersWithTheSameShape() {
        assertSame(AreaScanner.offsetsFor(7, 3), AreaScanner.offsetsFor(7, 3));
        assertEquals(new AreaScanner(7, 3, 16).areaSize(), new AreaScanner(7, 3, 512).areaSize());
        assertEquals(15 * 15 * 7, new AreaScanner(7, 3, 16).areaSize());
    }

    @Test
    void clampsNonsenseArguments() {
        AreaScanner scanner = new AreaScanner(-5, -1, 0);
        FakeWorld world = new FakeWorld().with(CENTER);
        scanner.reset(CENTER);

        assertEquals(1, scanner.areaSize());
        assertEquals(new Found(CENTER), scanner.step(world, 0));
        assertInstanceOf(Exhausted.class, scanner.step(world, 0));
    }
}
