package com.parkflow.backend.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Nạp lịch sử đóng gói khi khởi động một máy chủ mới, để dự báo có số liệu (đường nền cần các ngày cùng thứ).
 * Chạy NỀN sau khi ứng dụng đã lên (kiểm tra sức khỏe vẫn ổn trong lúc nạp, vài chục giây đến vài phút) và chỉ khi CSDL
 * chưa có sự kiện lịch sử nào, nên khởi động lại không nạp lại. Chạy sau bộ nạp cấu hình trường (bãi phải có trước).
 * Mặc định TẮT (kiểm thử không phải nạp hàng trăm nghìn dòng); bản chạy thật bật bằng HISTORY_AUTO_IMPORT=true.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "parkflow.history.auto-import", havingValue = "true")
public class HistoryBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final HistoryImporter importer;

    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(this::importIfEmpty, "history-import");
        worker.setDaemon(true);
        worker.start();
    }

    void importIfEmpty() {
        try {
            Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE historical", Long.class);
            if (existing != null && existing > 0) {
                log.info("Đã có {} sự kiện lịch sử, bỏ qua nạp lại", existing);
                return;
            }
            log.info("Chưa có lịch sử, bắt đầu nạp lịch sử đóng gói");
            importer.importPackaged();
        } catch (RuntimeException e) {
            log.error("Không nạp được lịch sử khi khởi động: {}", e.getMessage());
        }
    }
}
