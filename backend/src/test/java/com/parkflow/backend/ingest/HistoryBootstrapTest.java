package com.parkflow.backend.ingest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Nạp lịch sử khi khởi động: chỉ khi chưa có, và lỗi nạp không làm hỏng ứng dụng. Không cần Postgres. */
class HistoryBootstrapTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HistoryImporter importer = mock(HistoryImporter.class);
    private final HistoryBootstrap bootstrap = new HistoryBootstrap(jdbc, importer);

    @Test void importsWhenThereIsNoHistoryYet() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        bootstrap.importIfEmpty();
        verify(importer).importPackaged();
    }

    @Test void skipsWhenHistoryIsAlreadyThere() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(250_000L);
        bootstrap.importIfEmpty();
        verify(importer, never()).importPackaged();
    }

    @Test void aFailedImportIsLoggedAndDoesNotPropagate() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(importer.importPackaged()).thenThrow(new IllegalStateException("Lịch sử chứa bãi chưa được cấu hình"));
        bootstrap.importIfEmpty();   // không ném ra ngoài
        verify(importer).importPackaged();
    }
}
