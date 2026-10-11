package ru.voidsmp.fastboats;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeedMathTest {
    @Test void rejectsInvalidSettings() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0, 0.99}) {
            assertFalse(SpeedMath.validMultiplier(value));
            assertThrows(IllegalArgumentException.class, () -> SpeedMath.targetForMultiplier(value));
        }
    }

    @Test void acceptsValuesAboveOldLimits() {
        for (double value : new double[] {1, 20.01, 50, 80, 100, 1000, Double.MAX_VALUE}) {
            assertTrue(SpeedMath.validMultiplier(value));
            assertTrue(Double.isFinite(SpeedMath.targetForMultiplier(value)));
        }
        assertEquals(32, SpeedMath.targetForMultiplier(80), 1e-9);
    }

    @Test void doesNotBrakeOrJumpInstantlyToTarget() {
        assertEquals(0.48, SpeedMath.acceleratedSpeed(0.4, 80), 1e-9);
        assertEquals(0.4, SpeedMath.acceleratedSpeed(0.4, 1));
        assertEquals(2, SpeedMath.acceleratedSpeed(2, 2));
    }

    @Test void oldEightAndSixteenBlockLimitsAreGone() {
        assertEquals(17.08, SpeedMath.acceleratedSpeed(17, 80), 1e-9);
        assertEquals(31.98, SpeedMath.acceleratedSpeed(31.9, 80), 1e-9);
        assertEquals(32, SpeedMath.acceleratedSpeed(31.99, 80), 1e-9);
    }

    @Test void invalidMeasurementsDoNotGetBoosted() {
        assertEquals(0, SpeedMath.acceleratedSpeed(0, 80));
        assertEquals(0, SpeedMath.acceleratedSpeed(Double.NaN, 80));
        assertEquals(0, SpeedMath.acceleratedSpeed(Double.POSITIVE_INFINITY, 80));
    }

    @Test void scanWorkRemainsBoundedWithoutClampingVelocity() {
        assertEquals(2, SpeedMath.lookAheadDistance(1));
        assertEquals(16, SpeedMath.lookAheadDistance(Double.MAX_VALUE));
        assertTrue(SpeedMath.acceleratedSpeed(30, 100) > 30);
    }
}
