package ru.voidsmp.fastboats;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeedMathTest {
    @Test void rejectsInvalidSettings() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 20.01}) {
            assertFalse(SpeedMath.inRange(value, 1, SpeedMath.MAX_MULTIPLIER));
            assertThrows(IllegalArgumentException.class, () -> SpeedMath.limitForMultiplier(value));
        }
        assertTrue(SpeedMath.inRange(1, 1, SpeedMath.MAX_MULTIPLIER));
        assertTrue(SpeedMath.inRange(20, 1, SpeedMath.MAX_MULTIPLIER));
    }

    @Test void commandLimitTracksMultiplier() {
        assertEquals(0.4, SpeedMath.limitForMultiplier(1), 1e-9);
        assertEquals(0.8, SpeedMath.limitForMultiplier(2), 1e-9);
        assertEquals(1.2, SpeedMath.limitForMultiplier(3), 1e-9);
        assertEquals(2.0, SpeedMath.limitForMultiplier(5), 1e-9);
        assertEquals(8.0, SpeedMath.limitForMultiplier(20), 1e-9);
        assertTrue(SpeedMath.inRange(SpeedMath.limitForMultiplier(20), 0.1, SpeedMath.MAX_SPEED));
        assertFalse(SpeedMath.inRange(8.01, 0.1, SpeedMath.MAX_SPEED));
    }

    @Test void eightBlocksPerSecondIsNotSlowedDownAtFive() {
        double natural = 8.0 / 20;
        double extra = SpeedMath.extraDistance(natural, 5, 2);
        assertEquals(1.6, extra, 1e-9);
        assertEquals(40, (natural + extra) * 20, 1e-9);
    }

    @Test void twentyUsesRaisedBudget() {
        double extra = SpeedMath.extraDistance(0.4, 20, SpeedMath.limitForMultiplier(20));
        assertEquals(7.6, extra, 1e-9);
        assertEquals(8.0, 0.4 + extra, 1e-9);
    }

    @Test void raisedBudgetIsRespected() {
        for (int multiplier = 1; multiplier <= 20; multiplier++) {
            double limit = SpeedMath.limitForMultiplier(multiplier);
            for (double natural = 0.01; natural <= 1; natural += 0.01) {
                double extra = SpeedMath.extraDistance(natural, multiplier, limit);
                assertTrue(extra >= 0);
                assertTrue(natural + extra <= Math.max(natural, limit) + 1e-9);
            }
        }
    }

    @Test void oneLeavesVanillaMovementAlone() {
        assertEquals(0, SpeedMath.extraDistance(0.4, 1, 2));
    }

    @Test void lowCapNeverBrakesVanillaMovement() {
        assertEquals(0, SpeedMath.extraDistance(0.4, 5, 0.1));
    }

    @Test void ignoresStationaryBoatsAndPositionJumps() {
        for (double distance : new double[] {0, 0.0001, -1, 10, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0, SpeedMath.extraDistance(distance, 5, 2));
        }
    }

    @Test void additiveDistanceRespectsBudget() {
        for (double natural = 0.01; natural <= 1; natural += 0.01) {
            double extra = SpeedMath.extraDistance(natural, 5, 1.2);
            assertTrue(extra >= 0);
            assertTrue(natural + extra <= 1.2 + 1e-9);
        }
    }
}
