package com.salesforce.tools.service;

import com.salesforce.tools.csv.CsvProcessor;
import com.salesforce.tools.model.MaskingConfig;
import com.salesforce.tools.model.ObjectConfig;
import com.salesforce.tools.salesforce.RecordResult;
import com.salesforce.tools.salesforce.SalesforceClient;
import com.salesforce.tools.salesforce.SalesforceConfig;
import com.salesforce.tools.salesforce.SalesforceConnection;
import com.sforce.ws.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * マスキング処理の本体。CLI と GUI の共通処理。
 *
 * <p>CSV をチャンク単位で読み込み・マスキングし、そのまま CSV 出力または Salesforce へ投入するため、
 * 全レコードをメモリに保持しない。Salesforce 投入で失敗したレコードはエラー CSV に出力する。</p>
 */
public class MaskingService {
    private static final Logger logger = LoggerFactory.getLogger(MaskingService.class);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    /** mask-only 時のチャンクサイズ */
    static final int CSV_CHUNK_SIZE = 1000;
    /** Salesforce 投入時、1 チャンクあたりのバッチ数 */
    static final int BATCHES_PER_CHUNK = 10;

    static final String ERROR_COL_ROW = "RowNumber";
    static final String ERROR_COL_MESSAGE = "Error";

    /** 進捗通知 */
    @FunctionalInterface
    public interface ProgressListener {
        /**
         * @param fraction 0.0〜1.0
         * @param message  状況メッセージ
         */
        void onProgress(double fraction, String message);

        ProgressListener NONE = (f, m) -> { };
    }

    private final MaskingConfig maskingConfig;
    private final Supplier<CsvProcessor> csvProcessorFactory;
    private final Function<SalesforceConfig, SalesforceClient> clientFactory;

    public MaskingService(MaskingConfig maskingConfig) {
        this(maskingConfig, CsvProcessor::new, SalesforceConnection::new);
    }

    public MaskingService(MaskingConfig maskingConfig, Supplier<CsvProcessor> csvProcessorFactory,
                          Function<SalesforceConfig, SalesforceClient> clientFactory) {
        this.maskingConfig = maskingConfig;
        this.csvProcessorFactory = csvProcessorFactory;
        this.clientFactory = clientFactory;
    }

    /**
     * 処理を実行する
     *
     * @throws ProcessingException Salesforce との通信エラー等で途中終了した場合（途中までの結果を保持）
     * @throws Exception           設定・入力ファイルの不備など、処理開始前のエラー
     */
    public ProcessResult execute(ProcessRequest request, ProgressListener listener) throws Exception {
        ProgressListener progress = listener != null ? listener : ProgressListener.NONE;

        ObjectConfig objectConfig = maskingConfig.getObjectConfig(request.objectName());
        if (objectConfig == null) {
            throw new IllegalArgumentException("オブジェクト " + request.objectName() + " の設定が見つかりません");
        }
        if (!Files.isRegularFile(Path.of(request.inputCsvPath()))) {
            throw new IOException("入力CSVファイルが見つかりません: " + request.inputCsvPath());
        }
        Files.createDirectories(request.outputDir());

        CsvProcessor csvProcessor = csvProcessorFactory.get();
        progress.onProgress(0.0, "CSVファイルの件数を確認中...");
        int total = csvProcessor.countRecords(request.inputCsvPath());
        if (total == 0) {
            progress.onProgress(1.0, "処理対象のレコードがありません");
            return new ProcessResult(request.mode(), 0, 0, 0, null, null);
        }
        progress.onProgress(0.05, total + " 件のレコードを処理します");

        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        if (request.mode() == ProcessMode.MASK_ONLY) {
            return executeMaskOnly(request, objectConfig, csvProcessor, total, timestamp, progress);
        }
        return executeLoad(request, objectConfig, csvProcessor, total, timestamp, progress);
    }

    private ProcessResult executeMaskOnly(ProcessRequest request, ObjectConfig objectConfig, CsvProcessor csvProcessor,
                                          int total, String timestamp, ProgressListener progress) throws Exception {
        Path outputPath = request.outputDir().resolve(request.objectName() + "_masked_" + timestamp + ".csv");
        CsvProcessor.RecordWriter[] writer = new CsvProcessor.RecordWriter[1];
        int[] written = {0};
        try {
            int count = csvProcessor.processInChunks(request.inputCsvPath(), objectConfig, CSV_CHUNK_SIZE,
                    (headers, chunk, firstRow) -> {
                        if (writer[0] == null) {
                            writer[0] = csvProcessor.openWriter(outputPath.toString(), headers);
                        }
                        writer[0].write(chunk);
                        written[0] += chunk.size();
                        progress.onProgress(0.05 + 0.95 * written[0] / total,
                                written[0] + " / " + total + " 件をマスキング・出力しました");
                    });
            logger.info("マスキング済みCSVファイル: {}", outputPath);
            progress.onProgress(1.0, "処理完了");
            return new ProcessResult(request.mode(), count, count, 0, outputPath, null);
        } finally {
            if (writer[0] != null) {
                writer[0].close();
            }
        }
    }

    private ProcessResult executeLoad(ProcessRequest request, ObjectConfig objectConfig, CsvProcessor csvProcessor,
                                      int total, String timestamp, ProgressListener progress) throws Exception {
        SalesforceConfig sfConfig = SalesforceConfig.load(request.salesforceConfigPath());
        Path errorPath = request.outputDir().resolve(request.objectName() + "_errors_" + timestamp + ".csv");
        int chunkSize = sfConfig.getBatchSize() * BATCHES_PER_CHUNK;

        int[] processed = {0};
        int[] success = {0};
        int[] failure = {0};
        CsvProcessor.RecordWriter[] errorWriter = new CsvProcessor.RecordWriter[1];

        try (SalesforceClient client = clientFactory.apply(sfConfig)) {
            progress.onProgress(0.05, "Salesforceに接続中...");
            client.connect();
            progress.onProgress(0.08, (request.mode() == ProcessMode.MASK_INSERT ? "Insert" : "Upsert") + "実行中...");

            csvProcessor.processInChunks(request.inputCsvPath(), objectConfig, chunkSize, (headers, chunk, firstRow) -> {
                List<RecordResult> results;
                try {
                    results = request.mode() == ProcessMode.MASK_INSERT
                            ? client.insertRecords(request.objectName(), chunk)
                            : client.upsertRecords(request.objectName(), chunk, request.externalIdField());
                } catch (ConnectionException e) {
                    ProcessResult partial = new ProcessResult(request.mode(), processed[0], success[0], failure[0],
                            null, errorWriter[0] != null ? errorPath : null);
                    throw new ProcessingException(String.format(
                            "Salesforceとの通信でエラーが発生したため中断しました: %s%n"
                                    + "（%d 行目以降は未処理。それ以前の成功 %d 件は反映済み）",
                            e.getMessage(), firstRow, success[0]), partial, e);
                }

                for (RecordResult r : results) {
                    if (r.success()) {
                        success[0]++;
                        continue;
                    }
                    failure[0]++;
                    if (errorWriter[0] == null) {
                        List<String> errorHeaders = new ArrayList<>();
                        errorHeaders.add(ERROR_COL_ROW);
                        errorHeaders.add(ERROR_COL_MESSAGE);
                        errorHeaders.addAll(headers);
                        errorWriter[0] = csvProcessor.openWriter(errorPath.toString(), errorHeaders);
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(ERROR_COL_ROW, firstRow + r.index());
                    row.put(ERROR_COL_MESSAGE, r.errors());
                    row.putAll(chunk.get(r.index()));
                    errorWriter[0].write(row);
                }
                processed[0] += chunk.size();
                progress.onProgress(0.08 + 0.92 * processed[0] / total, String.format(
                        "%d / %d 件処理（成功 %d 件、失敗 %d 件）", processed[0], total, success[0], failure[0]));
            });
        } finally {
            if (errorWriter[0] != null) {
                errorWriter[0].close();
            }
        }

        ProcessResult result = new ProcessResult(request.mode(), processed[0], success[0], failure[0], null,
                failure[0] > 0 ? errorPath : null);
        if (result.hasFailures()) {
            logger.warn("一部のレコードの投入に失敗しました: {}", result.summary());
            progress.onProgress(1.0, "完了（失敗 " + failure[0] + " 件あり）");
        } else {
            logger.info("処理完了: {}", result.summary());
            progress.onProgress(1.0, "処理完了");
        }
        return result;
    }
}
