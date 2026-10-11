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

    // Add thrust rather than multiplying last tick's velocity exponentially.
    static double[] boost(double x, double z, double yaw, int direction,
                          double multiplier, double maxSpeed) {
        double thrust = 0.04 * (multiplier - 1.0) * direction;
        double radians = Math.toRadians(yaw);
        x -= Math.sin(radians) * thrust;
        z += Math.cos(radians) * thrust;
        double limit = Math.min(0.4 * multiplier, maxSpeed);
        double speed = Math.hypot(x, z);
        if (speed > limit) {
            x = x / speed * limit;
            z = z / speed * limit;
        }
        return new double[] {x, z};
    }
}
