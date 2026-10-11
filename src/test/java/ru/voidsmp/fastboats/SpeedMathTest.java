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
        assertEquals(0.8, SpeedMath.limitForMultiplier(2), 1e-9);
        assertEquals(2, SpeedMath.limitForMultiplier(5), 1e-9);
        assertEquals(8, SpeedMath.limitForMultiplier(20), 1e-9);
    }

    @Test void eightBlocksPerSecondIsNotReplacedWithSmallServerVelocity() {
        assertEquals(0.48, SpeedMath.acceleratedSpeed(8.0 / 20, 5, 2), 1e-9);
    }

    @Test void twentyDoesNotInstantlyJumpToMaximum() {
        assertEquals(0.48, SpeedMath.acceleratedSpeed(0.4, 20, 8), 1e-9);
    }

    @Test void oneAndLowCapsDoNotBrake() {
        assertEquals(0.4, SpeedMath.acceleratedSpeed(0.4, 1, 2));
        assertEquals(0.4, SpeedMath.acceleratedSpeed(0.4, 5, 0.1));
    }

    @Test void stationaryAndTeleportSizedSamplesDoNotGetBoosted() {
        assertEquals(0, SpeedMath.acceleratedSpeed(0, 20, 8));
        assertEquals(0, SpeedMath.acceleratedSpeed(17, 20, 8));
        assertEquals(0, SpeedMath.acceleratedSpeed(Double.NaN, 20, 8));
    }

    @Test void everyImpulseIsBoundedAndEventuallyReachesTargetInIdealModel() {
        for (int multiplier = 2; multiplier <= 20; multiplier++) {
            double target = SpeedMath.limitForMultiplier(multiplier);
            double speed = 0.4;
            for (int i = 0; i < 1000; i++) {
                double next = SpeedMath.acceleratedSpeed(speed, multiplier, target);
                assertTrue(next >= speed);
                assertTrue(next - speed <= 0.08 + 1e-9);
                assertTrue(next <= target);
                speed = next;
            }
            assertEquals(target, speed, 1e-9);
        }
    }
}
