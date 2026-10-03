package com.parkflow.backend.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc CSV theo luồng (không nạp cả file vào bộ nhớ): dấu phân cách `,` `;` hoặc tab tự nhận từ dòng đầu, trường có ngoặc kép
 * (kể cả chứa dấu phân cách, xuống dòng, `""`), bỏ BOM, bỏ dòng trống hoàn toàn. Mỗi bản ghi kèm số dòng (1 = dòng tiêu đề).
 */
final class CsvReader {
    interface RowHandler {
        /** @return false để dừng đọc sớm. */
        boolean accept(int line, List<String> cells) throws IOException;
    }

    private CsvReader() {}

    static void read(InputStream stream, RowHandler handler) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)), 1 << 16)) {
            reader.mark(8192);
            char delimiter = detectDelimiter(reader);
            reader.reset();
            int first = reader.read();
            if (first != 0xFEFF && first != -1) { reader.reset(); }

            List<String> row = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean quoted = false, any = false;
            int line = 1, startLine = 1;
            int c;
            while ((c = reader.read()) != -1) {
                char ch = (char) c;
                if (quoted) {
                    if (ch == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') cell.append('"'); else { quoted = false; if (next != -1) reader.reset(); }
                    } else {
                        if (ch == '\n') line++;
                        cell.append(ch);
                    }
                    continue;
                }
                if (ch == '"' && cell.length() == 0) { quoted = true; any = true; }
                else if (ch == delimiter) { row.add(cell.toString()); cell.setLength(0); any = true; }
                else if (ch == '\n' || ch == '\r') {
                    if (ch == '\r') { reader.mark(1); int next = reader.read(); if (next != '\n' && next != -1) reader.reset(); }
                    if (any || cell.length() > 0) {
                        row.add(cell.toString());
                        if (!(row.size() == 1 && row.get(0).isBlank()) && !handler.accept(startLine, row)) return;
                    }
                    row = new ArrayList<>(); cell.setLength(0); any = false; line++; startLine = line;
                } else { cell.append(ch); any = true; }
            }
            if (quoted) throw new IOException("Dòng " + startLine + ": trường CSV chưa đóng ngoặc kép.");
            if (any || cell.length() > 0) {
                row.add(cell.toString());
                if (!(row.size() == 1 && row.get(0).isBlank())) handler.accept(startLine, row);
            }
        }
    }

    private static char detectDelimiter(BufferedReader reader) throws IOException {
        StringBuilder head = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1 && c != '\n' && head.length() < 4000) head.append((char) c);
        int commas = count(head, ','), semicolons = count(head, ';'), tabs = count(head, '\t');
        if (tabs > commas && tabs >= semicolons) return '\t';
        if (semicolons > commas) return ';';
        return ',';
    }

    private static int count(CharSequence text, char ch) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == ch) n++;
        return n;
    }
}
