package com.salesforce.tools.csv;

import com.salesforce.tools.model.FieldConfig;
import com.salesforce.tools.model.MaskingType;
import com.salesforce.tools.model.ObjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvProcessorTest {

    @TempDir
    Path dir;

    private final CsvProcessor processor = new CsvProcessor();

    private static ObjectConfig accountConfig() {
        FieldConfig phone = new FieldConfig();
        phone.setName("Phone");
        phone.setMaskingType(MaskingType.PHONE);
        ObjectConfig oc = new ObjectConfig();
        oc.setName("Account");
        oc.setFields(List.of(phone));
        return oc;
    }

    private Path write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void masksConfiguredColumnsAndKeepsOthers() throws Exception {
        Path in = write("in.csv", "Name,Phone,City\n山田商事,03-1111-2222,東京\n");
        List<Map<String, Object>> records = processor.readAndMaskCsv(in.toString(), accountConfig());

        assertEquals(1, records.size());
        Map<String, Object> r = records.get(0);
        assertEquals("山田商事", r.get("Name"));
        assertEquals("東京", r.get("City"));
        assertNotEquals("03-1111-2222", r.get("Phone"));
        assertTrue(r.get("Phone").toString().matches("090-\\d{4}-\\d{4}"));
    }

    @Test
    void stripsUtf8BomFromFirstHeader() throws Exception {
        Path in = write("bom.csv", "﻿Name,Phone\nA,1\n");
        Map<String, Object> r = processor.readAndMaskCsv(in.toString(), accountConfig()).get(0);
        assertTrue(r.containsKey("Name"));
        assertEquals("A", r.get("Name"));
    }

    @Test
    void outputPreservesInputColumnOrder() throws Exception {
        String header = "Zeta,Alpha,Phone,Mid,Beta";
        Path in = write("order.csv", header + "\n1,2,3,4,5\n");
        Path out = dir.resolve("out.csv");

        processor.writeMaskedCsv(out.toString(), processor.readAndMaskCsv(in.toString(), accountConfig()));

        List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        assertEquals("\"Zeta\",\"Alpha\",\"Phone\",\"Mid\",\"Beta\"", lines.get(0));
        assertTrue(lines.get(1).startsWith("\"1\",\"2\","));
    }

    @Test
    void japaneseRoundTripsAsUtf8() throws Exception {
        Path in = write("jp.csv", "Name,Phone\n株式会社テスト,1\n");
        Path out = dir.resolve("jp_out.csv");
        processor.writeMaskedCsv(out.toString(), processor.readAndMaskCsv(in.toString(), accountConfig()));
        assertTrue(Files.readString(out, StandardCharsets.UTF_8).contains("株式会社テスト"));
    }

    @Test
    void processesInChunksWithRowNumbers() throws Exception {
        StringBuilder sb = new StringBuilder("Name,Phone\n");
        for (int i = 1; i <= 7; i++) {
            sb.append("N").append(i).append(",x\n");
        }
        Path in = write("chunks.csv", sb.toString());

        List<Integer> firstRows = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        int count = processor.processInChunks(in.toString(), accountConfig(), 3, (headers, chunk, first) -> {
            assertEquals(List.of("Name", "Phone"), headers);
            firstRows.add(first);
            sizes.add(chunk.size());
            assertEquals("N" + first, chunk.get(0).get("Name"));
        });

        assertEquals(7, count);
        assertEquals(List.of(1, 4, 7), firstRows);
        assertEquals(List.of(3, 3, 1), sizes);
    }

    @Test
    void countRecordsHandlesQuotedNewlines() throws Exception {
        Path in = write("quoted.csv", "Name,Phone\n\"a\nb\",1\nc,2\n");
        assertEquals(2, processor.countRecords(in.toString()));
    }

    @Test
    void missingTrailingColumnsBecomeEmpty() throws Exception {
        Path in = write("short.csv", "Name,City,Phone\nA\n");
        Map<String, Object> r = processor.readAndMaskCsv(in.toString(), accountConfig()).get(0);
        assertEquals("", r.get("City"));
    }

    @Test
    void emptyFileYieldsNoRecords() throws Exception {
        Path in = write("empty.csv", "");
        assertTrue(processor.readAndMaskCsv(in.toString(), accountConfig()).isEmpty());
        assertEquals(0, processor.countRecords(in.toString()));
    }
}
