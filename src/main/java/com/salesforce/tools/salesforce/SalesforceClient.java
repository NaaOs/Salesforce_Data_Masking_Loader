package com.salesforce.tools.salesforce;

import com.sforce.ws.ConnectionException;

import java.util.List;
import java.util.Map;

/**
 * Salesforce へのデータ投入を抽象化したインタフェース（テストでの差し替え用）
 */
public interface SalesforceClient extends AutoCloseable {

    void connect() throws ConnectionException;

    List<RecordResult> insertRecords(String objectName, List<Map<String, Object>> records)
            throws ConnectionException;

    List<RecordResult> upsertRecords(String objectName, List<Map<String, Object>> records, String externalIdField)
            throws ConnectionException;

    /** ログアウトして接続を閉じる（例外は送出しない） */
    @Override
    void close();
}
