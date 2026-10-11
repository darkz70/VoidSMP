package ru.voidsmp.fastboats;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeedMathTest {
    @Test void rejectsInvalidSettings() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 5.01}) {
            assertFalse(SpeedMath.inRange(value, 1, 5));
        }
        assertTrue(SpeedMath.inRange(1, 1, 5));
        assertTrue(SpeedMath.inRange(5, 1, 5));
    }

    @Test void forwardAndReverseFollowBoatYaw() {
        assertArrayEquals(new double[] {0, 0.04}, SpeedMath.boost(0, 0, 0, 1, 2, 1.2), 1e-9);
        assertArrayEquals(new double[] {-0.04, 0}, SpeedMath.boost(0, 0, 90, 1, 2, 1.2), 1e-9);
        assertArrayEquals(new double[] {0, -0.04}, SpeedMath.boost(0, 0, 0, -1, 2, 1.2), 1e-9);
    }

    @Test void repeatedThrustCannotExceedLimit() {
        double[] velocity = {0, 0};
        for (int i = 0; i < 10000; i++) {
            velocity = SpeedMath.boost(velocity[0], velocity[1], 45, 1, 5, 1.2);
            assertTrue(Math.hypot(velocity[0], velocity[1]) <= 1.2 + 1e-9);
        }
    }

    @Test void multiplierAlsoLimitsTargetSpeed() {
        double[] velocity = SpeedMath.boost(3, 4, 0, 1, 2, 2);
        assertEquals(0.8, Math.hypot(velocity[0], velocity[1]), 1e-9);
    }
}
