package ru.voidsmp.spawnrates.util;

import java.util.Locale;

/** Мелкие форматтеры: множители и размеры в командах и подсказках должны выглядеть одинаково. */
public final class Num {

    private Num() {
    }

    /** 1.0 → «1», 0.5 → «0.5», 0.25 → «0.25» — без хвостов из нулей. */
    public static String multiplier(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9D) {
            return String.valueOf((long) Math.rint(value));
        }
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    public static String size(int x, int y, int z) {
        return x + "×" + y + "×" + z;
    }

    /** Метка времени в секундах → «2026-10-26 14:03». */
    public static String timestamp(long seconds) {
        if (seconds <= 0L) {
            return "—";
        }
        return java.time.LocalDateTime
                .ofInstant(java.time.Instant.ofEpochSecond(seconds), java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.min(Math.max(value, min), max);
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
