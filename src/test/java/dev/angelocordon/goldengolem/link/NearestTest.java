package dev.angelocordon.goldengolem.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.angelocordon.goldengolem.util.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class NearestTest {

    private record Candidate(String name, BlockPos pos) {
    }

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    @Test
    void emptyWhenThereAreNoCandidates() {
        assertTrue(Nearest.of(ORIGIN, List.<Candidate>of(), Candidate::pos).isEmpty());
    }

    @Test
    void picksTheClosestCandidate() {
        List<Candidate> candidates = List.of(
                new Candidate("far", new BlockPos(4, 64, 4)),
                new Candidate("near", new BlockPos(1, 65, 0)),
                new Candidate("middle", new BlockPos(0, 64, -3)));

        assertEquals("near", Nearest.of(ORIGIN, candidates, Candidate::pos).orElseThrow().name());
    }

    @Test
    void countsVerticalDistance() {
        List<Candidate> candidates = List.of(
                new Candidate("below", new BlockPos(0, 60, 0)),
                new Candidate("beside", new BlockPos(2, 64, 0)));

        assertEquals("beside", Nearest.of(ORIGIN, candidates, Candidate::pos).orElseThrow().name());
    }

    @Test
    void tieGoesToTheEarlierCandidate() {
        List<Candidate> candidates = List.of(
                new Candidate("first", new BlockPos(2, 64, 0)),
                new Candidate("second", new BlockPos(-2, 64, 0)));

        assertEquals("first", Nearest.of(ORIGIN, candidates, Candidate::pos).orElseThrow().name());
    }
}
