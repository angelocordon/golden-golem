package dev.angelocordon.goldengolem.golem.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.angelocordon.goldengolem.golem.movement.TripTracker.Decision;
import org.junit.jupiter.api.Test;

class TripTrackerTest {

    private final TripTracker trip = new TripTracker(2.0);

    @Test
    void missingPathIsUnreachable() {
        assertEquals(Decision.UNREACHABLE, trip.onPath(false, false, 0, 10, false));
    }

    @Test
    void walksCompletePathAndArrivesWhenItEnds() {
        assertEquals(Decision.WALK, trip.onPath(true, true, 1.0, 10, false));
        assertEquals(Decision.ARRIVED, trip.onLegFinished());
    }

    @Test
    void arrivedWhenFullPathCompletesOutsideReach() {
        // Next to a block target passed as a corner: about 1.58 away, more than REACH (1.5).
        assertEquals(Decision.WALK, trip.onPath(true, true, 1.58, 10, false));
        assertEquals(Decision.ARRIVED, trip.onLegFinished());
    }

    @Test
    void completePathEndingAtGolemIsArrived() {
        assertEquals(Decision.ARRIVED, trip.onPath(true, true, 1.58, 1.6, true));
    }

    @Test
    void completePathEndingFarFromTargetIsTreatedAsPartial() {
        assertEquals(Decision.UNREACHABLE, trip.onPath(true, true, 4.0, 4.2, false));
    }

    @Test
    void chainsPartialPathsThatKeepGettingCloser() {
        assertEquals(Decision.WALK, trip.onPath(true, false, 30, 45, false));
        assertEquals(Decision.SEARCH, trip.onLegFinished());
        assertEquals(Decision.WALK, trip.onPath(true, false, 15, 30.4, false));
        assertEquals(Decision.SEARCH, trip.onLegFinished());
        assertEquals(Decision.WALK, trip.onPath(true, true, 1.0, 15.3, false));
        assertEquals(Decision.ARRIVED, trip.onLegFinished());
    }

    @Test
    void unreachableWhenLegDoesNotImproveBestDistance() {
        assertEquals(Decision.WALK, trip.onPath(true, false, 5, 20, false));
        assertEquals(Decision.SEARCH, trip.onLegFinished());
        assertEquals(Decision.UNREACHABLE, trip.onPath(true, false, 4.8, 5.3, false));
    }

    @Test
    void doesNotLoopOnOneNodePartialPath() {
        // Fenced-in owner: the search from the closest spot returns just the golem's own block.
        assertEquals(Decision.UNREACHABLE, trip.onPath(true, false, 3.0, 3.4, true));
    }

    @Test
    void partialPathThatDoesNotGetCloserThanGolemIsUnreachable() {
        assertEquals(Decision.UNREACHABLE, trip.onPath(true, false, 6.0, 6.2, false));
    }

    @Test
    void unreachableAfterRepeatedStuckOnSamePath() {
        // A closed door the pathfinder thinks is passable: every stuck search returns the same path.
        assertEquals(Decision.WALK, trip.onPath(true, true, 1.0, 10, false));
        trip.onWaypointReached(8.0);
        trip.onStuck();
        assertEquals(Decision.WALK, trip.onPath(true, true, 1.0, 7.8, false));
        trip.onWaypointReached(7.6);
        trip.onStuck();
        assertEquals(Decision.UNREACHABLE, trip.onPath(true, true, 1.0, 7.6, false));
    }

    @Test
    void stuckStrikesAreForgivenAfterRealProgress() {
        assertEquals(Decision.WALK, trip.onPath(true, true, 1.0, 20, false));
        trip.onWaypointReached(18.0);
        trip.onStuck();
        assertEquals(Decision.WALK, trip.onPath(true, true, 1.0, 18, false));
        trip.onWaypointReached(12.0);
        trip.onStuck();
        assertEquals(Decision.WALK, trip.onPath(true, true, 1.0, 12, false));
    }

    @Test
    void stuckSearchWithBetterPathKeepsWalking() {
        assertEquals(Decision.WALK, trip.onPath(true, false, 10, 20, false));
        trip.onStuck();
        assertEquals(Decision.WALK, trip.onPath(true, false, 4, 19, false));
        trip.onStuck();
        assertEquals(Decision.WALK, trip.onPath(true, false, 2, 18, false));
    }

    @Test
    void resetForgetsBestDistanceAndStrikes() {
        assertEquals(Decision.WALK, trip.onPath(true, false, 5, 20, false));
        trip.reset();

        assertEquals(Decision.WALK, trip.onPath(true, false, 5, 20, false));
        assertEquals(Decision.SEARCH, trip.onLegFinished());
    }
}
