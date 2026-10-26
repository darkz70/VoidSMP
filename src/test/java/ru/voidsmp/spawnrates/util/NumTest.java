package ru.voidsmp.spawnrates.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NumTest {

    @Test
    void wholeMultipliersHaveNoDecimalTail() {
        assertEquals("1", Num.multiplier(1.0D));
        assertEquals("0", Num.multiplier(0.0D));
        assertEquals("10", Num.multiplier(10.0D));
    }

    @Test
    void fractionsKeepOnlySignificantDigits() {
        assertEquals("0.5", Num.multiplier(0.5D));
        assertEquals("0.25", Num.multiplier(0.25D));
        assertEquals("1.5", Num.multiplier(1.5D));
        assertEquals("0.333", Num.multiplier(1.0D / 3.0D));
    }

    @Test
    void sizesAndTimestampsAreReadable() {
        assertEquals("64×16×64", Num.size(64, 16, 64));
        assertEquals("—", Num.timestamp(0L));
        assertEquals(16, Num.timestamp(1_700_000_000L).length());
    }

    @Test
    void clampsIgnoreBrokenInput() {
        assertEquals(0.0D, Num.clamp(Double.NaN, 0.0D, 10.0D));
        assertEquals(10.0D, Num.clamp(99.0D, 0.0D, 10.0D));
        assertEquals(0.0D, Num.clamp(-1.0D, 0.0D, 10.0D));
        assertEquals(5, Num.clamp(5, 0, 10));
    }
}
