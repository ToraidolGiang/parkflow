package com.parkflow.backend.importer;

import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.ingest.HistoryImporter;
import com.parkflow.backend.sim.ReplayLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Nhập dữ liệu từ file CSV: tải lên (đọc tiêu đề, vài dòng xem trước, đếm dòng) rồi xác nhận ánh xạ cột (nhập thật).
 * Log quẹt thẻ ghi vào `events` theo kiểu NẠP LỊCH SỬ (như HistoryImporter: không đụng phiên, số xe hiện tại hay nhịp tín hiệu),
 * cùng cách đặt mã sự kiện nên nhập lại cùng file hoặc file trùng lịch sử đóng gói đều không nhân đôi.
 * File chờ xác nhận nằm trong thư mục tạm, hết hạn sau 30 phút. Controller kiểm tra X-Api-Key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {
    static final long MAX_BYTES = 20L * 1024 * 1024;
    static final int MAX_ROWS = 500_000;
    static final int MAX_ERRORS = 50;
    static final int BATCH = 1000;
    static final Duration TTL = Duration.ofMinutes(30);
    static final int MAX_SESSIONS = 10;

    static final Map<String, Set<String>> FIELDS = Map.of(
            "swipe_log", Set.of("time_in", "time_out", "gate_id", "card_id", "lot_id", "vehicle_type"));
    static final Map<String, Set<String>> REQUIRED = Map.of(
            "swipe_log", Set.of("time_in", "card_id", "lot_id", "vehicle_type"));
    private static final Pattern HEX64 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d/M/uuuu H:mm[:ss]").withResolverStyle(java.time.format.ResolverStyle.STRICT);


    record Session(String id, String kind, Path file, String fileName, List<String> headers, int rowCount, Instant createdAt) {}
    public record Mapping(String column, String field) {}
    record RowError(int row, String message) {}

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    // ---- 1. tải lên ----

    public synchronized ObjectNode upload(String kind, MultipartFile file) {
        if (!FIELDS.containsKey(kind)) throw bad("Loại dữ liệu phải là swipe_log.");
        if (file == null || file.isEmpty()) throw bad("File trống.");
        if (file.getSize() > MAX_BYTES) throw tooLarge("File quá lớn (tối đa 20 MB).");
        purgeExpired();
        while (sessions.size() >= MAX_SESSIONS)
            sessions.values().stream().min(Comparator.comparing(Session::createdAt)).ifPresent(oldest -> drop(oldest.id()));
        Path path = null;
        try {
            path = Files.createTempFile("parkflow-import-", ".csv");
            try (var input = file.getInputStream(); var output = Files.newOutputStream(path)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                for (int read; (read = input.read(buffer)) != -1;) {
                    total += read;
                    if (total > MAX_BYTES) throw tooLarge("File quá lớn (tối đa 20 MB).");
                    output.write(buffer, 0, read);
                }
            }
            List<String> headers = new ArrayList<>();
            List<List<String>> sample = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            int[] rows = {0};
            try (InputStream in = Files.newInputStream(path)) {
                CsvReader.read(in, (line, cells) -> {
                    if (line == 1 || headers.isEmpty()) {
                        headers.addAll(cells.stream().map(String::trim).toList());
                        return true;
                    }
                    rows[0]++;
                    if (rows[0] > MAX_ROWS) return false;
                    if (sample.size() < 5) sample.add(cells);
                    if (cells.size() != headers.size() && errors.size() < 20)
                        errors.add(new RowError(line, "Dòng " + line + " có " + cells.size() + " cột, tiêu đề có " + headers.size() + " cột."));
                    return true;
                });
            }
            if (headers.isEmpty() || headers.stream().allMatch(String::isBlank)) throw bad("File CSV không có hàng tiêu đề.");
            if (headers.stream().anyMatch(String::isBlank)) throw bad("Tiêu đề có tên cột trống.");
            if (new HashSet<>(headers).size() != headers.size()) throw bad("Tiêu đề có cột trùng tên.");
            if (rows[0] > MAX_ROWS) throw tooLarge("File có quá nhiều dòng (tối đa " + MAX_ROWS + ").");
            if (rows[0] == 0) throw bad("File chỉ có hàng tiêu đề, chưa có dữ liệu.");
            Session session = new Session(UUID.randomUUID().toString(), kind, path, file.getOriginalFilename(), List.copyOf(headers), rows[0], Instant.now());
            sessions.put(session.id(), session);
            ObjectNode preview = mapper.createObjectNode();
            preview.put("importId", session.id()).put("kind", kind);
            ArrayNode h = preview.putArray("headers");
            headers.forEach(h::add);
            ArrayNode s = preview.putArray("sampleRows");
            for (var r : sample) { var row = s.addArray(); r.forEach(row::add); }
            preview.put("rowCount", rows[0]);
            ArrayNode e = preview.putArray("errors");
            errors.forEach(err -> e.addObject().put("row", err.row()).put("message", err.message()));
            path = null;   // đã giao cho phiên quản lý
            return preview;
        } catch (IOException e) {
            throw bad("Không đọc được file: " + e.getMessage());
        } finally {
            if (path != null) deleteQuietly(path);
        }
    }

    // ---- 2. xác nhận và nhập ----

    @Transactional
    public synchronized ObjectNode confirm(String importId, List<Mapping> mappings) {
        if (importId == null || importId.isBlank()) throw bad("Thiếu importId.");
        Session session = sessions.get(importId);
        if (session == null || session.createdAt().plus(TTL).isBefore(Instant.now())) {
            if (session != null) drop(importId);
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Phiên nhập này đã hết hạn hoặc không tồn tại. Tải file lên lại.");
        }
        Map<String, Integer> index = fieldIndex(session, mappings);
        jdbc.execute("SELECT pg_advisory_xact_lock(20261002)");
        Counter counter = new Counter();
        try (InputStream in = Files.newInputStream(session.file())) {
            importSwipes(in, session, index, counter);
        } catch (IOException e) {
            throw bad("Không đọc được file: " + e.getMessage());
        }
        if (counter.imported > 0) publisher.publishEvent(new HistoryImporter.Imported(counter.imported));
        drop(importId);
        log.info("Nhập {}: {} mới, {} trùng, {} dòng bị từ chối", session.kind(), counter.imported, counter.skipped, counter.rejected);
        ObjectNode result = mapper.createObjectNode();
        result.put("imported", counter.imported).put("skipped", counter.skipped).put("rejected", counter.rejected);
        ArrayNode errors = result.putArray("errors");
        counter.errors.forEach(err -> errors.addObject().put("row", err.row()).put("message", err.message()));
        return result;
    }

    private static final class Counter {
        int imported, skipped, rejected;
        final List<RowError> errors = new ArrayList<>();

        void reject(int line, String message) {
            rejected++;
            if (errors.size() < MAX_ERRORS) errors.add(new RowError(line, message));
        }
    }

    private Map<String, Integer> fieldIndex(Session session, List<Mapping> mappings) {
        if (mappings == null || mappings.isEmpty()) throw bad("Chưa có ánh xạ cột nào.");
        Map<String, Integer> index = new HashMap<>();
        Set<String> allowed = FIELDS.get(session.kind());
        for (Mapping mapping : mappings) {
            if (mapping == null || mapping.column() == null || mapping.field() == null) throw bad("Ánh xạ thiếu cột hoặc trường.");
            if ("ignore".equals(mapping.field())) continue;
            if (!allowed.contains(mapping.field())) throw bad("Trường '" + mapping.field() + "' không dùng cho loại " + session.kind() + ".");
            int column = session.headers().indexOf(mapping.column().trim());
            if (column < 0) throw bad("Cột '" + mapping.column() + "' không có trong file.");
            if (index.put(mapping.field(), column) != null) throw bad("Trường '" + mapping.field() + "' được ánh xạ cho nhiều cột.");
        }
        Set<String> missing = new LinkedHashSet<>(REQUIRED.get(session.kind()));
        missing.removeAll(index.keySet());
        if (!missing.isEmpty()) throw bad("Thiếu ánh xạ cho trường bắt buộc: " + String.join(", ", missing) + ".");
        return index;
    }

    private void importSwipes(InputStream in, Session session, Map<String, Integer> index, Counter counter) throws IOException {
        Map<String, String> lots = new HashMap<>();
        jdbc.queryForList("SELECT lot_id FROM lots", String.class).forEach(id -> lots.put(id.toUpperCase(java.util.Locale.ROOT), id));
        Set<String> gates = new HashSet<>(jdbc.queryForList("""
                SELECT gate FROM forecast_gates
                UNION SELECT g->>'code' FROM campus_config c, jsonb_array_elements(c.body->'gates') g
                WHERE g->>'code' IS NOT NULL
                """, String.class));
        List<Object[]> batch = new ArrayList<>(BATCH * 2);
        boolean[] header = {true};
        CsvReader.read(in, (line, cells) -> {
            if (header[0]) { header[0] = false; return true; }
            if (cells.size() != session.headers().size()) { counter.reject(line, "Số cột không khớp tiêu đề."); return true; }
            try {
                String inRaw = cell(cells, index, "time_in");
                Instant tsIn = parseTime(inRaw, "Giờ vào");
                String card = cell(cells, index, "card_id");
                if (card.isEmpty()) throw new RowProblem("Thiếu mã thẻ.");
                String lotRaw = cell(cells, index, "lot_id");
                String lot = lots.get(lotRaw.toUpperCase(java.util.Locale.ROOT));
                if (lot == null) throw new RowProblem("Bãi '" + lotRaw + "' không có trong cấu hình.");
                String gate = cell(cells, index, "gate_id");
                if (!gate.isEmpty() && !gates.contains(gate)) throw new RowProblem("Cổng '" + gate + "' không có trong cấu hình.");
                String vehicle = vehicle(cell(cells, index, "vehicle_type"));
                String outRaw = cell(cells, index, "time_out");
                Instant tsOut = outRaw.isEmpty() ? null : parseTime(outRaw, "Giờ ra");
                if (tsOut != null && tsOut.isBefore(tsIn)) throw new RowProblem("Giờ ra trước giờ vào.");
                String cardHash = HEX64.matcher(card).matches() ? card.toLowerCase() : ReplayLog.hash(card);
                batch.add(new Object[]{ReplayLog.id(inRaw, lot, card, "in"), Timestamp.from(tsIn), lot, "in", vehicle, cardHash});
                if (tsOut != null) batch.add(new Object[]{ReplayLog.id(outRaw, lot, card, "out"), Timestamp.from(tsOut), lot, "out", vehicle, cardHash});
            } catch (RowProblem problem) {
                counter.reject(line, "Dòng " + line + ": " + problem.getMessage());
            }
            if (batch.size() >= BATCH) flushEvents(batch, counter);
            return true;
        });
        flushEvents(batch, counter);
    }

    private void flushEvents(List<Object[]> batch, Counter counter) {
        if (batch.isEmpty()) return;
        int[][] results = jdbc.batchUpdate("""
                INSERT INTO events(event_id,ts,lot_id,direction,vehicle_type,card_hash,historical)
                VALUES (?,?,?,?,?,?,TRUE) ON CONFLICT(event_id) DO NOTHING
                """, batch, BATCH, (ps, row) -> {
            for (int i = 0; i < row.length; i++) ps.setObject(i + 1, row[i]);
        });
        int inserted = 0;
        for (int[] part : results) for (int n : part) inserted += Math.max(n, 0);
        counter.imported += inserted;
        counter.skipped += batch.size() - inserted;
        batch.clear();
    }

    // ---- phân tích giá trị ----

    private static final class RowProblem extends RuntimeException {
        RowProblem(String message) { super(message, null, false, false); }
    }

    private static String cell(List<String> cells, Map<String, Integer> index, String field) {
        Integer column = index.get(field);
        if (column == null || column >= cells.size()) return "";
        return cells.get(column).trim();
    }

    static Instant parseTime(String text, String label) {
        if (text == null || text.isBlank()) throw new RowProblem(label + " trống.");
        String s = text.trim();
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ignored) { /* thử dạng khác */ }
        try {
            return LocalDateTime.parse(s.replace(' ', 'T')).atZone(ParkFlowClock.ZONE).toInstant();
        } catch (DateTimeParseException ignored) { /* thử dạng khác */ }
        try {
            return LocalDateTime.parse(s, DMY).atZone(ParkFlowClock.ZONE).toInstant();
        } catch (DateTimeParseException ignored) { /* không đọc được */ }
        throw new RowProblem(label + " không đọc được: '" + s + "' (dạng đúng: 2026-09-22 07:05:00 hoặc 22/09/2026 07:05).");
    }

    static String vehicle(String text) {
        if (text == null || text.isBlank()) throw new RowProblem("Thiếu loại xe.");
        return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "xe máy", "xe may", "motorbike", "motorcycle", "moto" -> "motorbike";
            case "xe đạp", "xe dap", "bicycle", "bike" -> "bicycle";
            case "ô tô", "o to", "oto", "car" -> "car";
            default -> throw new RowProblem("Loại xe '" + text + "' không nhận ra (xe máy, xe đạp, ô tô).");
        };
    }

    // ---- dọn dẹp ----

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public synchronized void purgeExpired() {
        Instant limit = Instant.now().minus(TTL);
        sessions.values().stream().filter(s -> s.createdAt().isBefore(limit)).forEach(s -> drop(s.id()));
    }

    private void drop(String id) {
        Session session = sessions.remove(id);
        if (session != null) deleteQuietly(session.file());
    }

    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { /* thư mục tạm sẽ được hệ điều hành dọn */ }
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private static ApiException tooLarge(String message) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE", message);
    }
}
