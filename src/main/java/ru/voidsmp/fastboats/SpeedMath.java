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

    static double acceleratedSpeed(double measuredSpeed, double multiplier) {
        if (!Double.isFinite(measuredSpeed) || measuredSpeed < 0.001) return 0;
        if (multiplier <= 1) return measuredSpeed;
        double target = targetForMultiplier(multiplier);
        if (measuredSpeed >= target) return measuredSpeed;
        // Bound acceleration, not the administrator's chosen target speed.
        double step = Math.min(0.08, 0.02 * (multiplier - 1));
        return Math.min(target, measuredSpeed + step);
    }

    // Bound the COST of checking blocks, not the velocity sent to the client.
    static double lookAheadDistance(double speed) {
        return Math.min(speed, 8) * 2;
    }
}
