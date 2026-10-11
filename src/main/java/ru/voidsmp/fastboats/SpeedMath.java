package ru.voidsmp.fastboats;

final class SpeedMath {
    static final double MAX_MULTIPLIER = 20;
    static final double MAX_SPEED = 8;

    private SpeedMath() {}

    static boolean inRange(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    static double limitForMultiplier(double multiplier) {
        if (!inRange(multiplier, 1, MAX_MULTIPLIER)) {
            throw new IllegalArgumentException("Multiplier must be between 1 and 20");
        }
        return 0.4 * multiplier;
    }

    /** One bounded impulse per two-tick sample; never replace real speed with stale velocity. */
    static double acceleratedSpeed(double measuredSpeed, double multiplier, double maxSpeed) {
        if (!Double.isFinite(measuredSpeed) || measuredSpeed < 0.001
                || measuredSpeed > MAX_SPEED * 2) return 0;
        if (multiplier <= 1) return measuredSpeed;
        double target = Math.min(limitForMultiplier(multiplier), maxSpeed);
        if (measuredSpeed >= target) return measuredSpeed;
        // Even x20 cannot cause an instantaneous jump from vanilla to maximum speed.
        double step = Math.min(0.08, 0.02 * (multiplier - 1));
        return Math.min(target, measuredSpeed + step);
    }
}
