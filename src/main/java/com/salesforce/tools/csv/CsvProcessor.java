package com.salesforce.tools.csv;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.salesforce.tools.masking.MaskingDataGenerator;
import com.salesforce.tools.model.FieldConfig;
import com.salesforce.tools.model.ObjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSVファイルの読み込み・書き込み・マスキング処理。
 *
 * <p>文字コードは既定で UTF-8（先頭の BOM は自動で除去）。レコードは列順を保持する
 * {@link LinkedHashMap} で表現する。大量データに対応するため、{@link #processInChunks}
 * でチャンク単位にストリーミング処理できる。</p>
 */
public class CsvProcessor {
    private static final Logger logger = LoggerFactory.getLogger(CsvProcessor.class);
    private static final char BOM = '﻿';

    private final MaskingDataGenerator maskingGenerator;
    private final Charset charset;

    public CsvProcessor() {
        this(new MaskingDataGenerator(), StandardCharsets.UTF_8);
    }

    public CsvProcessor(MaskingDataGenerator maskingGenerator, Charset charset) {
        this.maskingGenerator = maskingGenerator;
        this.charset = charset;
    }

    /**
     * チャンク受け取り処理
     */
    @FunctionalInterface
    public interface ChunkHandler {
        /**
         * @param headers        CSV ヘッダー
         * @param chunk          マスキング済みレコード（列順保持）
         * @param firstRowNumber chunk 先頭レコードのデータ行番号（ヘッダーを除き 1 始まり）
         */
        void handle(List<String> headers, List<Map<String, Object>> chunk, int firstRowNumber) throws Exception;
    }

    /**
     * CSV のデータ行数（ヘッダーを除く）を数える。進捗表示用。
     */
    public int countRecords(String csvPath) throws IOException {
        int count = 0;
        try (CSVReader reader = openReader(csvPath)) {
            if (reader.readNext() == null) {
                return 0;
            }
            while (reader.readNext() != null) {
                count++;
            }
        } catch (CsvValidationException e) {
            throw new IOException("CSVフォーマットエラー: " + e.getMessage(), e);
        }
        return count;
    }

    /**
     * CSV を読み込みながらマスキングし、chunkSize 件ごとに handler へ渡す。
     *
     * @return 処理したレコード件数
     */
    public int processInChunks(String inputCsvPath, ObjectConfig objectConfig, int chunkSize,
                               ChunkHandler handler) throws Exception {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize は 1 以上を指定してください: " + chunkSize);
        }
        logger.info("CSVファイル読み込み開始: {}", inputCsvPath);

        try (CSVReader reader = openReader(inputCsvPath)) {
            String[] rawHeaders = reader.readNext();
            if (rawHeaders == null) {
                logger.error("CSVファイルが空です");
                return 0;
            }
            List<String> headers = normalizeHeaders(rawHeaders);
            logger.info("ヘッダー: {}", headers);

            // 列ごとのマスキング設定を事前に解決
            FieldConfig[] fieldConfigs = new FieldConfig[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                fieldConfigs[i] = objectConfig.getFieldConfig(headers.get(i));
            }

            List<Map<String, Object>> chunk = new ArrayList<>(chunkSize);
            int recordCount = 0;
            int chunkFirstRow = 1;
            String[] line;
            while ((line = reader.readNext()) != null) {
                recordCount++;
                Map<String, Object> record = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    if (fieldConfigs[i] != null) {
                        record.put(headers.get(i), maskingGenerator.generateMaskedValue(fieldConfigs[i]));
                    } else {
                        record.put(headers.get(i), i < line.length ? line[i] : "");
                    }
                }
                chunk.add(record);

                if (chunk.size() >= chunkSize) {
                    handler.handle(headers, chunk, chunkFirstRow);
                    chunkFirstRow = recordCount + 1;
                    chunk = new ArrayList<>(chunkSize);
                }
                if (recordCount % 1000 == 0) {
                    logger.info("{}件処理完了", recordCount);
                }
            }
            if (!chunk.isEmpty()) {
                handler.handle(headers, chunk, chunkFirstRow);
            }

            logger.info("CSV読み込み完了: {} 件のレコード", recordCount);
            return recordCount;
        } catch (CsvValidationException e) {
            logger.error("CSV解析エラー", e);
            throw new IOException("CSVフォーマットエラー: " + e.getMessage(), e);
        }
    }

    /**
     * CSVファイルを読み込んで全件マスキングする（小規模データ向け）
     */
    public List<Map<String, Object>> readAndMaskCsv(String inputCsvPath, ObjectConfig objectConfig) throws IOException {
        List<Map<String, Object>> all = new ArrayList<>();
        try {
            processInChunks(inputCsvPath, objectConfig, 1000, (headers, chunk, first) -> all.addAll(chunk));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
        return all;
    }

    /**
     * マスキング済みデータをCSVファイルに書き込み（列順は先頭レコードのキー順）
     */
    public void writeMaskedCsv(String outputCsvPath, List<Map<String, Object>> records) throws IOException {
        if (records == null || records.isEmpty()) {
            logger.warn("書き込むレコードがありません");
            return;
        }
        logger.info("マスキング済みCSVファイル出力開始: {}", outputCsvPath);
        try (RecordWriter writer = openWriter(outputCsvPath, new ArrayList<>(records.get(0).keySet()))) {
            writer.write(records);
        }
        logger.info("CSV出力完了: {} 件のレコード", records.size());
    }

    /**
     * ストリーミング書き込み用の CSV ライターを開く（ヘッダーを書き込み済みの状態で返す）
     */
    public RecordWriter openWriter(String outputCsvPath, List<String> headers) throws IOException {
        return new RecordWriter(Path.of(outputCsvPath), headers, charset);
    }

    private CSVReader openReader(String csvPath) throws IOException {
        Reader reader = Files.newBufferedReader(Path.of(csvPath), charset);
        return new CSVReader(reader);
    }

    static List<String> normalizeHeaders(String[] rawHeaders) {
        String[] headers = Arrays.copyOf(rawHeaders, rawHeaders.length);
        if (headers.length > 0 && headers[0] != null && !headers[0].isEmpty() && headers[0].charAt(0) == BOM) {
            headers[0] = headers[0].substring(1);
        }
        List<String> result = new ArrayList<>(headers.length);
        for (String h : headers) {
            result.add(h == null ? "" : h.trim());
        }
        return result;
    }

    /**
     * ヘッダー順にレコードを書き込む CSV ライター
     */
    public static class RecordWriter implements Closeable {
        private final CSVWriter writer;
        private final List<String> headers;

        RecordWriter(Path path, List<String> headers, Charset charset) throws IOException {
            this.headers = List.copyOf(headers);
            this.writer = new CSVWriter(Files.newBufferedWriter(path, charset));
            writer.writeNext(this.headers.toArray(new String[0]));
        }

        public void write(Map<String, Object> record) {
            String[] values = new String[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                Object value = record.get(headers.get(i));
                values[i] = value != null ? value.toString() : "";
            }
            writer.writeNext(values);
        }

        public void write(List<Map<String, Object>> records) {
            for (Map<String, Object> record : records) {
                write(record);
            }
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }
}
