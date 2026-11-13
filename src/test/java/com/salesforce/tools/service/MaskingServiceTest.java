package com.salesforce.tools.service;

import com.salesforce.tools.csv.CsvProcessor;
import com.salesforce.tools.model.FieldConfig;
import com.salesforce.tools.model.MaskingConfig;
import com.salesforce.tools.model.MaskingType;
import com.salesforce.tools.model.ObjectConfig;
import com.salesforce.tools.salesforce.RecordResult;
import com.salesforce.tools.salesforce.SalesforceClient;
import com.sforce.ws.ConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaskingServiceTest {

    @TempDir
    Path dir;

    private MaskingConfig config;
    private Path inputCsv;
    private Path sfProps;

    @BeforeEach
    void setUp() throws Exception {
        FieldConfig phone = new FieldConfig();
        phone.setName("Phone");
        phone.setMaskingType(MaskingType.PHONE);
        ObjectConfig account = new ObjectConfig();
        account.setName("Account");
        account.setFields(List.of(phone));
        config = new MaskingConfig();
        config.setObjects(List.of(account));

        StringBuilder sb = new StringBuilder("Name,Phone,Ext__c\n");
        for (int i = 1; i <= 5; i++) {
            sb.append("Name").append(i).append(",03-0000-000").append(i).append(",E").append(i).append('\n');
        }
        inputCsv = dir.resolve("in.csv");
        Files.writeString(inputCsv, sb.toString(), StandardCharsets.UTF_8);

        sfProps = dir.resolve("sf.properties");
        Files.writeString(sfProps, "sf.username=u\nsf.password=p\nsf.batchSize=2\n", StandardCharsets.UTF_8);
    }

    /** "Name2" と "Name4" の行を失敗させるフェイク */
    static class FakeClient implements SalesforceClient {
        final List<String> calls = new ArrayList<>();
        int received;
        boolean closed;
        Integer failConnectionAfterRecords;

        @Override
        public void connect() {
            calls.add("connect");
        }

        @Override
        public List<RecordResult> insertRecords(String objectName, List<Map<String, Object>> records)
                throws ConnectionException {
            calls.add("insert:" + records.size());
            return results(records);
        }

        @Override
        public List<RecordResult> upsertRecords(String objectName, List<Map<String, Object>> records, String extId)
                throws ConnectionException {
            calls.add("upsert:" + extId + ":" + records.size());
            return results(records);
        }

        private List<RecordResult> results(List<Map<String, Object>> records) throws ConnectionException {
            if (failConnectionAfterRecords != null && received >= failConnectionAfterRecords) {
                throw new ConnectionException("network down");
            }
            received += records.size();
            List<RecordResult> out = new ArrayList<>();
            for (int i = 0; i < records.size(); i++) {
                String name = (String) records.get(i).get("Name");
                boolean fail = name.equals("Name2") || name.equals("Name4");
                out.add(new RecordResult(i, !fail, fail ? null : "001" + i, true, fail ? "ERR " + name : ""));
            }
            return out;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void maskOnlyWritesMaskedCsvInInputOrder() throws Exception {
        List<Double> progress = new ArrayList<>();
        ProcessResult result = new MaskingService(config).execute(
                new ProcessRequest(ProcessMode.MASK_ONLY, "Account", inputCsv.toString(), null, null, dir),
                (f, m) -> progress.add(f));

        assertEquals(5, result.totalRecords());
        assertEquals(5, result.successCount());
        assertFalse(result.hasFailures());
        assertNotNull(result.outputFile());
        List<String> lines = Files.readAllLines(result.outputFile(), StandardCharsets.UTF_8);
        assertEquals(6, lines.size());
        assertEquals("\"Name\",\"Phone\",\"Ext__c\"", lines.get(0));
        assertTrue(lines.get(1).startsWith("\"Name1\",\"090-"));
        assertEquals(1.0, progress.get(progress.size() - 1));
    }

    @Test
    void insertReportsFailuresAndWritesErrorCsv() throws Exception {
        FakeClient fake = new FakeClient();
        MaskingService service = new MaskingService(config, CsvProcessor::new, c -> fake);

        ProcessResult result = service.execute(new ProcessRequest(ProcessMode.MASK_INSERT, "Account",
                inputCsv.toString(), null, sfProps.toString(), dir), null);

        assertEquals(5, result.totalRecords());
        assertEquals(3, result.successCount());
        assertEquals(2, result.failureCount());
        assertTrue(result.hasFailures());
        assertTrue(fake.closed);
        assertEquals("connect", fake.calls.get(0));

        List<String> errorLines = Files.readAllLines(result.errorFile(), StandardCharsets.UTF_8);
        assertEquals(3, errorLines.size());
        assertEquals("\"RowNumber\",\"Error\",\"Name\",\"Phone\",\"Ext__c\"", errorLines.get(0));
        assertTrue(errorLines.get(1).startsWith("\"2\",\"ERR Name2\",\"Name2\","), errorLines.get(1));
        assertTrue(errorLines.get(2).startsWith("\"4\",\"ERR Name4\",\"Name4\","), errorLines.get(2));
    }

    @Test
    void upsertPassesExternalIdAndNoErrorFileWhenAllSucceed() throws Exception {
        Files.writeString(inputCsv, "Name,Phone\nName1,1\nName3,3\n", StandardCharsets.UTF_8);
        FakeClient fake = new FakeClient();

        ProcessResult result = new MaskingService(config, CsvProcessor::new, c -> fake).execute(
                new ProcessRequest(ProcessMode.MASK_UPSERT, "Account", inputCsv.toString(), "Ext__c",
                        sfProps.toString(), dir), null);

        assertEquals(2, result.successCount());
        assertNull(result.errorFile());
        assertTrue(fake.calls.contains("upsert:Ext__c:2"));
    }

    @Test
    void connectionErrorRaisesProcessingExceptionWithPartialResult() throws Exception {
        // batchSize=2 → チャンク = 20 件。チャンク 1 を 2 件にするため batchSize=1 → チャンク 10 件
        Files.writeString(sfProps, "sf.username=u\nsf.password=p\nsf.batchSize=1\n", StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder("Name,Phone\n");
        for (int i = 1; i <= 25; i++) {
            sb.append("Row").append(i).append(",x\n");
        }
        Files.writeString(inputCsv, sb.toString(), StandardCharsets.UTF_8);
        FakeClient fake = new FakeClient();
        fake.failConnectionAfterRecords = 10;

        ProcessingException ex = assertThrows(ProcessingException.class, () ->
                new MaskingService(config, CsvProcessor::new, c -> fake).execute(
                        new ProcessRequest(ProcessMode.MASK_INSERT, "Account", inputCsv.toString(), null,
                                sfProps.toString(), dir), null));

        assertEquals(10, ex.getPartialResult().successCount());
        assertTrue(ex.getMessage().contains("11 行目以降は未処理"), ex.getMessage());
        assertTrue(fake.closed);
    }

    @Test
    void unknownObjectFails() {
        assertThrows(IllegalArgumentException.class, () -> new MaskingService(config).execute(
                new ProcessRequest(ProcessMode.MASK_ONLY, "Contact", inputCsv.toString(), null, null, dir), null));
    }

    @Test
    void missingInputFails() {
        assertThrows(java.io.IOException.class, () -> new MaskingService(config).execute(
                new ProcessRequest(ProcessMode.MASK_ONLY, "Account", dir.resolve("x.csv").toString(), null, null, dir),
                null));
    }

    @Test
    void upsertRequestRequiresExternalId() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProcessRequest(ProcessMode.MASK_UPSERT, "Account", "a.csv", " ", null, dir));
    }

    @Test
    void processModeParsing() {
        assertEquals(ProcessMode.MASK_UPSERT, ProcessMode.fromCode(" Mask-Upsert "));
        assertThrows(IllegalArgumentException.class, () -> ProcessMode.fromCode("bogus"));
        assertFalse(ProcessMode.MASK_ONLY.requiresSalesforce());
        assertEquals("mask-only (マスキングのみ)", ProcessMode.MASK_ONLY.toString());
    }
}
