package ru.voidsmp.fastboats;

final class SpeedMath {
    private SpeedMath() {}

    static boolean validMultiplier(double value) {
        return Double.isFinite(value) && value >= 1;
    }

    static double targetForMultiplier(double multiplier) {
        if (!validMultiplier(multiplier)) {
            throw new IllegalArgumentException("Multiplier must be finite and at least 1");
        }
        return 0.4 * multiplier;
    }

    /**
     * Invert the two-tick water-drag model, rather than endlessly adding a fixed
     * impulse which friction consumes before the next update. This is a model,
     * not a guarantee of the observed client speed under network latency.
     */
    static double impulseSpeed(double measuredSpeed, double multiplier, boolean forward) {
        if (!Double.isFinite(measuredSpeed) || measuredSpeed < 0.001 || multiplier <= 1) return 0;
        double target = targetForMultiplier(multiplier);
        if (measuredSpeed > target) return 0; // do not brake existing vanilla motion
        double desiredAverage = Math.min(target, measuredSpeed + Math.min(0.08, 0.02 * (multiplier - 1)));
        double paddle = forward ? 0.04 : 0.005;
        // v1 = 0.9*v + paddle; v2 = 0.9*v1 + paddle.
        return Math.max(0, (desiredAverage - 1.45 * paddle) / 0.855);
    }

    // Bound the COST of checking blocks, not the velocity sent to the client.
    static double lookAheadDistance(double speed) {
        return Math.min(speed, 8) * 2;
    }
}
