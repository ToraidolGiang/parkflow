package com.parkflow.backend.importer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CsvReaderTest {
    @Test void readsBomSemicolonQuotedDelimitersEscapedQuotesAndMultiline() throws Exception {
        var rows = new ArrayList<List<String>>();
        var lines = new ArrayList<Integer>();
        CsvReader.read(new ByteArrayInputStream(("\uFEFFa;b\n\"hello;world\";\"line1\nline2\"\n\"say \"\"hi\"\"\";x\n").getBytes(StandardCharsets.UTF_8)),
                (line,cells) -> { rows.add(cells); lines.add(line); return true; });
        assertEquals(List.of("hello;world","line1\nline2"),rows.get(1));
        assertEquals("say \"hi\"",rows.get(2).get(0));
        assertEquals(List.of(1,2,4),lines);
    }
    @Test void rejectsInvalidUtf8() {
        assertThrows(java.io.IOException.class, () -> CsvReader.read(new ByteArrayInputStream(new byte[]{(byte)0xff}), (line,cells) -> true));
    }
}
