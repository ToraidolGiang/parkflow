package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class NumberGuardTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNode facts = mapper.readTree("{\"forecastPeaks\":[{\"lotId\":\"A\",\"pct\":113.04},{\"lotId\":\"B\",\"pct\":88.9}]}");

    @Test void percentagesPresentInTheFactsPassIncludingRoundedForms() {
        assertTrue(NumberGuard.onlyKnownPercentages("Bãi A đạt 113% và bãi B 88,9%.", facts));
        assertTrue(NumberGuard.onlyKnownPercentages("Bãi A đạt 113,0 % lúc 07:45.", facts));
        assertTrue(NumberGuard.onlyKnownPercentages("Bãi B gần 89%.", facts));
    }

    @Test void aMadeUpPercentageIsBlocked() {
        assertFalse(NumberGuard.onlyKnownPercentages("Bãi A sẽ đạt 135%.", facts));
        assertFalse(NumberGuard.onlyKnownPercentages("Bãi A 113% còn bãi B 95%.", facts));
    }

    @Test void designThresholdsAreAlwaysAllowed() {
        assertTrue(NumberGuard.onlyKnownPercentages("Vượt ngưỡng 100% và chưa tới 120%, trên mức cảnh báo 80%.", facts));
    }

    @Test void textWithoutPercentagesIsFine() {
        assertTrue(NumberGuard.onlyKnownPercentages("Chưa có dữ liệu.", facts));
        assertTrue(NumberGuard.onlyKnownPercentages(null, facts));
    }
}
