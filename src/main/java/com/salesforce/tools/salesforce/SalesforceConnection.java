package com.salesforce.tools.salesforce;

import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Salesforce接続およびデータ操作クラス
 */
public class SalesforceConnection implements SalesforceClient {
    private static final Logger logger = LoggerFactory.getLogger(SalesforceConnection.class);
    private PartnerConnection connection;
    private final SalesforceConfig config;

    public SalesforceConnection(SalesforceConfig config) {
        this.config = config;
    }

    /**
     * Salesforceに接続
     */
    @Override
    public void connect() throws ConnectionException {
        logger.info("Salesforceに接続中... ({})", config.getLoginUrl());

        ConnectorConfig connectorConfig = new ConnectorConfig();
        connectorConfig.setUsername(config.getUsername());
        connectorConfig.setPassword(config.getPasswordWithToken());
        connectorConfig.setAuthEndpoint(config.getLoginUrl());

        connection = Connector.newConnection(connectorConfig);
        logger.info("Salesforce接続成功: {}", connection.getUserInfo().getUserFullName());
    }

    /**
     * レコードをUpsert（挿入または更新）
     *
     * @return レコードごとの結果（入力と同じ順序）。レコード単位の失敗は例外にならず結果に含まれる
     * @throws ConnectionException 通信・認証エラー（以降のバッチは処理しない）
     */
    @Override
    public List<RecordResult> upsertRecords(String objectName, List<Map<String, Object>> records,
                                            String externalIdField) throws ConnectionException {
        requireConnected();
        return processInBatches("Upsert", objectName, records,
                sObjects -> Stream.of(connection.upsert(externalIdField, sObjects))
                        .map(r -> new RecordResult(-1, r.isSuccess(), r.getId(), r.isCreated(), joinErrors(r.getErrors())))
                        .toList());
    }

    /**
     * レコードを挿入
     *
     * @return レコードごとの結果（入力と同じ順序）
     * @throws ConnectionException 通信・認証エラー（以降のバッチは処理しない）
     */
    @Override
    public List<RecordResult> insertRecords(String objectName, List<Map<String, Object>> records)
            throws ConnectionException {
        requireConnected();
        return processInBatches("Insert", objectName, records,
                sObjects -> Stream.of(connection.create(sObjects))
                        .map(r -> new RecordResult(-1, r.isSuccess(), r.getId(), true, joinErrors(r.getErrors())))
                        .toList());
    }

    @FunctionalInterface
    private interface BatchCall {
        List<RecordResult> call(SObject[] sObjects) throws ConnectionException;
    }

    private List<RecordResult> processInBatches(String operation, String objectName,
                                                List<Map<String, Object>> records, BatchCall call)
            throws ConnectionException {
        if (records == null || records.isEmpty()) {
            logger.warn("{}対象のレコードがありません", operation);
            return new ArrayList<>();
        }
        logger.info("{} 件のレコードを {} に{}開始", records.size(), objectName, operation);

        List<RecordResult> allResults = new ArrayList<>(records.size());
        int batchSize = config.getBatchSize();
        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            SObject[] sObjects = records.subList(i, end).stream()
                    .map(r -> createSObject(objectName, r))
                    .toArray(SObject[]::new);

            List<RecordResult> results;
            try {
                results = call.call(sObjects);
            } catch (ConnectionException e) {
                logger.error("{}処理でエラー発生（{}件目以降は未処理）", operation, i + 1, e);
                throw e;
            }

            int successCount = 0;
            for (int j = 0; j < results.size(); j++) {
                RecordResult r = results.get(j);
                RecordResult indexed = new RecordResult(i + j, r.success(), r.id(), r.created(), r.errors());
                allResults.add(indexed);
                if (r.success()) {
                    successCount++;
                } else {
                    logger.error("{}エラー ({}件目): {}", operation, i + j + 1, r.errors());
                }
            }
            logger.info("バッチ {}-{}: 成功 {} 件, エラー {} 件", i + 1, end, successCount, results.size() - successCount);
        }
        return allResults;
    }

    /**
     * MapからSObjectを作成。null および空文字の項目は送信しない
     * （数値・日付項目の型エラーや、Upsert 時に既存値を意図せず変更することを防ぐ）。
     */
    static SObject createSObject(String objectName, Map<String, Object> fieldMap) {
        SObject sObject = new SObject();
        sObject.setType(objectName);
        for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
            Object value = entry.getValue();
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                continue;
            }
            sObject.setField(entry.getKey(), value);
        }
        return sObject;
    }

    static String joinErrors(Error[] errors) {
        if (errors == null || errors.length == 0) {
            return "";
        }
        return Stream.of(errors)
                .map(e -> {
                    String fields = e.getFields() != null && e.getFields().length > 0
                            ? " [" + String.join(",", e.getFields()) + "]" : "";
                    return e.getStatusCode() + ": " + e.getMessage() + fields;
                })
                .collect(Collectors.joining(" / "));
    }

    private void requireConnected() {
        if (connection == null) {
            throw new IllegalStateException("Salesforceに接続されていません。connect() を先に呼び出してください");
        }
    }

    /**
     * ログアウトして接続を閉じる
     */
    public void disconnect() {
        if (connection != null) {
            try {
                connection.logout();
                logger.info("Salesforceからログアウトしました");
            } catch (ConnectionException e) {
                logger.warn("ログアウトに失敗しました", e);
            } finally {
                connection = null;
            }
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    public PartnerConnection getConnection() {
        return connection;
    }
}
