package com.parkflow.backend.forecast;

import com.parkflow.backend.common.ParkFlowClock;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ForecastApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ParkFlowClock clock;
    String lot;
    OffsetDateTime now = OffsetDateTime.parse("2026-10-03T07:20:00+07:00");

    @BeforeEach void setup() {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim", now));
        lot = "FA" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO lots(lot_id,name,capacity,max_capacity,current_count) VALUES (?,?,10,12,1)", lot, lot);
    }

    void event(OffsetDateTime ts, boolean history) {
        jdbc.update("""
                INSERT INTO events(event_id,ts,lot_id,direction,vehicle_type,card_hash,historical)
                VALUES (?,?,?,'in','motorbike',?,?)
                """, UUID.randomUUID().toString(), ts, lot, UUID.randomUUID().toString(), history);
    }

    @Test void contractContainsRealActualsAndNullFuturePoints() throws Exception {
        event(now.minusWeeks(1).withHour(6).withMinute(0), true);
        event(now.withHour(6).withMinute(0), false);
        mvc.perform(get("/api/forecast").param("lotId",lot).param("date","2026-10-03"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lotId").value(lot))
            .andExpect(jsonPath("$.date").value("2026-10-03"))
            .andExpect(jsonPath("$.generatedAt").value("2026-10-03T07:20:00+07:00"))
            .andExpect(jsonPath("$.k").value(1))
            .andExpect(jsonPath("$.points",hasSize(29)))
            .andExpect(jsonPath("$.points[0].t").value("06:00"))
            .andExpect(jsonPath("$.points[0].actual").value(1))
            .andExpect(jsonPath("$.points[6].actual").value(nullValue()))
            .andExpect(jsonPath("$.points[28].t").value("13:00"))
            .andExpect(jsonPath("$.points[28].forecast").value(1))
            .andExpect(jsonPath("$.points[28].afterPlan").value(1))
            .andExpect(jsonPath("$.peak.t").value("06:00"))
            .andExpect(jsonPath("$.peak.count").value(1))
            .andExpect(jsonPath("$.peak.pct").value(10))
            .andExpect(jsonPath("$.thresholds.yellow").value(80))
            .andExpect(jsonPath("$.thresholds.over").value(100))
            .andExpect(jsonPath("$.thresholds.critical").value(120));
    }

    @Test void missingOrInvalidParametersReturnBadRequest() throws Exception {
        mvc.perform(get("/api/forecast").param("date","2026-10-03"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        mvc.perform(get("/api/forecast").param("lotId",lot).param("date","not-a-date"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        mvc.perform(get("/api/forecast").param("lotId"," ").param("date","2026-10-03"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test void unknownLotAndMissingHistoryHaveDistinctCodes() throws Exception {
        mvc.perform(get("/api/forecast").param("lotId","NO_SUCH_FORECAST").param("date","2026-10-03"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("UNKNOWN_LOT"));
        mvc.perform(get("/api/forecast").param("lotId",lot).param("date","2026-10-03"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NO_HISTORY"));
    }
}