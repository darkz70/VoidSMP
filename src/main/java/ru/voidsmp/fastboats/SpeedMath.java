package ru.voidsmp.fastboats;

final class SpeedMath {
    private SpeedMath() {}

    static boolean inRange(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    static double limitForMultiplier(double multiplier) {
        if (!inRange(multiplier, 1, 5)) {
            throw new IllegalArgumentException("Multiplier must be between 1 and 5");
        }
        return 0.4 * multiplier;
    }

    /** Extra distance only: vanilla movement is left untouched. Ignore teleport-sized jumps. */
    static double extraDistance(double naturalDistance, double multiplier, double maxSpeed) {
        if (!Double.isFinite(naturalDistance) || naturalDistance < 0.001
                || naturalDistance > 1.0 || multiplier <= 1) return 0;
        return Math.max(0, Math.min(naturalDistance * (multiplier - 1), maxSpeed - naturalDistance));
    }
}
