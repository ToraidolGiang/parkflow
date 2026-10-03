package com.parkflow.backend.forecast;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForecastMathTest {
    @Test void medianHandlesEvenOddSingletonAndDoesNotMutateInput() {
        double[] values = {1000, 1, 3, 2};
        assertEquals(2.5, ForecastMath.median(values));
        assertArrayEquals(new double[]{1000, 1, 3, 2}, values);
        assertEquals(3, ForecastMath.median(new double[]{1000, 1, 3}));
        assertEquals(7, ForecastMath.median(new double[]{7}));
    }

    @Test void emptyAndNonFiniteInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ForecastMath.median(new double[0]));
        assertThrows(IllegalArgumentException.class, () -> ForecastMath.median(new double[]{Double.NaN}));
        assertThrows(IllegalArgumentException.class, () -> ForecastMath.clampK(Double.POSITIVE_INFINITY));
    }

    @Test void kIsClampedAtBothEnds() {
        assertEquals(0.7, ForecastMath.clampK(-1));
        assertEquals(1.5, ForecastMath.clampK(9));
        assertEquals(1.073, ForecastMath.clampK(1.073));
        assertEquals(0.7, ForecastMath.clampK(0.7));
        assertEquals(1.5, ForecastMath.clampK(1.5));
    }

    @Test void slotsIncludeBothBoundariesAndRejectOutsideWindow() {
        assertEquals(0, ForecastMath.slotIndex("06:00"));
        assertEquals(28, ForecastMath.slotIndex("13:00"));
        assertEquals(5, ForecastMath.slotIndex("07:20"));
        assertThrows(IllegalArgumentException.class, () -> ForecastMath.slotIndex("05:59"));
        assertThrows(IllegalArgumentException.class, () -> ForecastMath.slotIndex("13:01"));
        assertThrows(IllegalArgumentException.class, () -> ForecastMath.slotIndex("6:00"));
        assertThrows(java.time.DateTimeException.class, () -> ForecastMath.slotIndex("25:00"));
    }

    @Test void interpolationPreservesEndpointsAndFractionalPosition() {
        double[] counts = new double[29];
        counts[0] = 20;
        counts[5] = 424;
        counts[6] = 442;
        counts[28] = 100;
        assertEquals(20, ForecastMath.interpolate(counts, LocalTime.of(6, 0)));
        assertEquals(100, ForecastMath.interpolate(counts, LocalTime.of(13, 0)));
        assertEquals(424, ForecastMath.interpolate(counts, LocalTime.of(7, 15)));
        assertEquals(430, ForecastMath.interpolate(counts, LocalTime.of(7, 20)), 1e-9);
        assertEquals(433, ForecastMath.interpolate(counts, LocalTime.of(7, 22, 30)), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> ForecastMath.interpolate(new double[0], LocalTime.of(6, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> ForecastMath.interpolate(counts, LocalTime.of(13, 0, 1)));
    }
}
