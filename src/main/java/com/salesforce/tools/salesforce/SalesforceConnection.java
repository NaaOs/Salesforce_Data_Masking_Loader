package com.salesforce.tools.salesforce;

import com.sforce.soap.partner.*;
import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Salesforce接続およびデータ操作クラス
 */
public class SalesforceConnection {
    private static final Logger logger = LoggerFactory.getLogger(SalesforceConnection.class);
    private PartnerConnection connection;
    private SalesforceConfig config;

    public SalesforceConnection(SalesforceConfig config) {
        this.config = config;
    }

    /**
     * Salesforceに接続
     */
    public void connect() throws ConnectionException {
        logger.info("Salesforceに接続中...");
        
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
     * @param objectName オブジェクト名
     * @param records レコードのリスト（Map形式）
     * @param externalIdField 外部ID項目名
     * @return Upsert結果
     */
    public List<UpsertResult> upsertRecords(String objectName, 
                                           List<Map<String, Object>> records,
                                           String externalIdField) throws ConnectionException {
        if (records == null || records.isEmpty()) {
            logger.warn("Upsert対象のレコードがありません");
            return new ArrayList<>();
        }

        logger.info("{} 件のレコードを {} にUpsert開始", records.size(), objectName);

        List<UpsertResult> allResults = new ArrayList<>();
        int batchSize = config.getBatchSize();

        // バッチ処理
        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            List<Map<String, Object>> batch = records.subList(i, end);
            
            SObject[] sObjects = new SObject[batch.size()];
            for (int j = 0; j < batch.size(); j++) {
                sObjects[j] = createSObject(objectName, batch.get(j));
            }

            try {
                UpsertResult[] results = connection.upsert(externalIdField, sObjects);
                
                // 結果を集計
                int successCount = 0;
                int errorCount = 0;
                for (UpsertResult result : results) {
                    allResults.add(result);
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        errorCount++;
                        for (Error error : result.getErrors()) {
                            logger.error("Upsertエラー: {}", error.getMessage());
                        }
                    }
                }
                
                logger.info("バッチ {}-{}: 成功 {} 件, エラー {} 件", 
                           i + 1, end, successCount, errorCount);
                
            } catch (ConnectionException e) {
                logger.error("Upsert処理でエラー発生", e);
                throw e;
            }
        }

        return allResults;
    }

    /**
     * レコードを挿入
     */
    public List<SaveResult> insertRecords(String objectName, 
                                         List<Map<String, Object>> records) throws ConnectionException {
        if (records == null || records.isEmpty()) {
            logger.warn("Insert対象のレコードがありません");
            return new ArrayList<>();
        }

        logger.info("{} 件のレコードを {} にInsert開始", records.size(), objectName);

        List<SaveResult> allResults = new ArrayList<>();
        int batchSize = config.getBatchSize();

        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            List<Map<String, Object>> batch = records.subList(i, end);
            
            SObject[] sObjects = new SObject[batch.size()];
            for (int j = 0; j < batch.size(); j++) {
                sObjects[j] = createSObject(objectName, batch.get(j));
            }

            try {
                SaveResult[] results = connection.create(sObjects);
                
                int successCount = 0;
                int errorCount = 0;
                for (SaveResult result : results) {
                    allResults.add(result);
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        errorCount++;
                        for (Error error : result.getErrors()) {
                            logger.error("Insertエラー: {}", error.getMessage());
                        }
                    }
                }
                
                logger.info("バッチ {}-{}: 成功 {} 件, エラー {} 件", 
                           i + 1, end, successCount, errorCount);
                
            } catch (ConnectionException e) {
                logger.error("Insert処理でエラー発生", e);
                throw e;
            }
        }

        return allResults;
    }

    /**
     * MapからSObjectを作成
     */
    private SObject createSObject(String objectName, Map<String, Object> fieldMap) {
        SObject sObject = new SObject();
        sObject.setType(objectName);

        for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
            if (entry.getValue() != null) {
                sObject.setField(entry.getKey(), entry.getValue());
            }
        }

        return sObject;
    }

    /**
     * 接続を閉じる
     */
    public void disconnect() {
        if (connection != null) {
            logger.info("Salesforce接続を切断しました");
            connection = null;
        }
    }

    public PartnerConnection getConnection() {
        return connection;
    }
}
