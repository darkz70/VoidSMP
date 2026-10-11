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

    @Test void noImpulseWhenOffStationaryInvalidOrAlreadyAboveTarget() {
        assertEquals(0, SpeedMath.impulseSpeed(0.4, 1, true));
        assertEquals(0, SpeedMath.impulseSpeed(0, 80, true));
        assertEquals(0, SpeedMath.impulseSpeed(Double.NaN, 80, true));
        assertEquals(0, SpeedMath.impulseSpeed(Double.POSITIVE_INFINITY, 80, true));
        assertEquals(0, SpeedMath.impulseSpeed(2, 2, true));
    }

    @Test void highMultiplierDoesNotInstantlyJumpToTarget() {
        double impulse = SpeedMath.impulseSpeed(0.4, 1000, true);
        assertTrue(impulse > 0.4);
        assertTrue(impulse < 0.6);
        assertEquals(0.48, simulateTwoTicks(impulse, true), 1e-9);
    }

    @Test void compensatesWaterDragInsteadOfPlateauingAtVanillaSpeed() {
        for (boolean forward : new boolean[] {true, false}) {
            for (double multiplier : new double[] {2, 5, 20, 80}) {
                double measured = forward ? 0.4 : 0.05;
                double target = SpeedMath.targetForMultiplier(multiplier);
                for (int i = 0; i < 5000 && measured < target - 1e-9; i++) {
                    double impulse = SpeedMath.impulseSpeed(measured, multiplier, forward);
                    assertTrue(Double.isFinite(impulse));
                    double next = simulateTwoTicks(impulse, forward);
                    assertTrue(next >= measured - 1e-9);
                    assertTrue(next - measured <= 0.08 + 1e-9);
                    assertTrue(next <= target + 1e-9);
                    measured = next;
                }
                assertEquals(target, measured, 1e-9);
            }
        }
    }

    @Test void maintainsTargetInIdealModel() {
        assertEquals(0.8, simulateTwoTicks(SpeedMath.impulseSpeed(0.8, 2, true), true), 1e-9);
    }

    @Test void scanCostNotChosenSpeedIsBounded() {
        assertEquals(2, SpeedMath.lookAheadDistance(1));
        assertEquals(16, SpeedMath.lookAheadDistance(Double.MAX_VALUE));
        assertTrue(SpeedMath.impulseSpeed(30, 100, true) > 30);
    }

    private static double simulateTwoTicks(double velocity, boolean forward) {
        double paddle = forward ? 0.04 : 0.005;
        double first = velocity * 0.9 + paddle;
        double second = first * 0.9 + paddle;
        return (first + second) / 2;
    }
}
