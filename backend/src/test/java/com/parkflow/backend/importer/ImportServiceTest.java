package com.parkflow.backend.importer;

import com.parkflow.backend.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ImportService service = new ImportService(jdbc,publisher,new ObjectMapper());
    private static final String HEADER = "ts_in,ts_out,gate_id,card_id,lot_id,vehicle_type\n";
    private static final List<ImportService.Mapping> MAPPING = List.of(
            new ImportService.Mapping("ts_in","time_in"), new ImportService.Mapping("ts_out","time_out"),
            new ImportService.Mapping("gate_id","gate_id"), new ImportService.Mapping("card_id","card_id"),
            new ImportService.Mapping("lot_id","lot_id"), new ImportService.Mapping("vehicle_type","vehicle_type"));
    private MockMultipartFile file(String csv) { return new MockMultipartFile("file","history.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8)); }
    private String upload(String csv) { return service.upload("swipe_log",file(csv)).path("importId").asText(); }
    private void database() {
        when(jdbc.queryForList("SELECT lot_id FROM lots",String.class)).thenReturn(List.of("A"));
        when(jdbc.queryForList(contains("SELECT gate FROM forecast_gates"),eq(String.class))).thenReturn(List.of("MAIN","G1"));
        when(jdbc.batchUpdate(anyString(),anyCollection(),eq(1000),any(ParameterizedPreparedStatementSetter.class)))
                .thenAnswer(call -> new int[][]{new int[((java.util.Collection<?>)call.getArgument(1)).size()]});
    }
    @Test void uploadPreviewsAndConfirmReportsInvalidRowsAndDuplicateEvents() {
        database();
        String id = upload(HEADER + "2026-09-21 07:00:00,2026-09-21 08:00:00,G1,card,A,Xe máy\n"
                + "bad,,G1,card,A,Xe máy\n"
                + "2026-09-21 09:00:00,2026-09-21 08:00:00,G1,card,A,Xe máy\n"
                + "2026-09-21 07:00:00,,G1,card,UNKNOWN,Xe máy\n"
                + "2026-09-21 07:00:00,,G1,card,A,spaceship\n"
                + "2026-09-21 07:00:00,,INVALID,card,A,Xe máy\n"
                + "too,few\n");
        var result = service.confirm(id,MAPPING);
        assertEquals(0,result.path("imported").asInt());
        assertEquals(2,result.path("skipped").asInt());
        assertEquals(6,result.path("rejected").asInt());
        assertEquals(3,result.path("errors").get(0).path("row").asInt());
        assertThrows(ApiException.class, () -> service.confirm(id,MAPPING));
        verifyNoInteractions(publisher);
    }
    @Test void missingRequiredMappingCanBeCorrectedWithoutUploadingAgain() {
        database();
        String id = upload(HEADER + "2026-09-21 07:00:00,,G1,card,A,Xe máy\n");
        assertThrows(ApiException.class, () -> service.confirm(id,List.of(new ImportService.Mapping("ts_in","time_in"))));
        assertDoesNotThrow(() -> service.confirm(id,MAPPING));
    }
    @Test void acceptedPairUsesHashedCardAndPublishesImportedCount() {
        database();
        when(jdbc.batchUpdate(anyString(),anyCollection(),eq(1000),any(ParameterizedPreparedStatementSetter.class)))
                .thenAnswer(call -> {
                    List<Object[]> rows = call.getArgument(1);
                    assertEquals(2,rows.size());
                    assertEquals("in",rows.get(0)[3]);
                    assertEquals("out",rows.get(1)[3]);
                    assertEquals(com.parkflow.backend.sim.ReplayLog.hash("raw-card"),rows.get(0)[5]);
                    assertEquals(rows.get(0)[5],rows.get(1)[5]);
                    assertNotEquals(rows.get(0)[0],rows.get(1)[0]);
                    return new int[][]{{1,1}};
                });
        String id = upload(HEADER + "2026-09-21 07:00:00,2026-09-21 08:00:00,G1,raw-card,A,Xe máy\n");
        assertEquals(2,service.confirm(id,MAPPING).path("imported").asInt());
        verify(publisher).publishEvent(new com.parkflow.backend.ingest.HistoryImporter.Imported(2));
    }
    @Test void oversizedUploadIsRejectedBeforeReadingContent() {
        var file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(ImportService.MAX_BYTES+1);
        assertThrows(ApiException.class, () -> service.upload("swipe_log",file));
        verifyNoInteractions(jdbc);
    }
    @Test void duplicateHeadersAndUnclosedQuotesAreRejected() {
        assertThrows(ApiException.class, () -> upload("a,a\n1,2\n"));
        assertThrows(ApiException.class, () -> upload("a,b\n\"unclosed,2\n"));
    }
    @Test void datesMustBeRealCalendarDates() {
        assertThrows(RuntimeException.class, () -> ImportService.parseTime("31/02/2026 07:00","Giờ"));
        assertEquals(java.time.Instant.parse("2026-09-22T00:05:00Z"),ImportService.parseTime("22/09/2026 07:05","Giờ"));
    }
}
