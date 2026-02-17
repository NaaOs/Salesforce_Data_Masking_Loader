package com.salesforce.tools;

import com.salesforce.tools.config.ConfigLoader;
import com.salesforce.tools.csv.CsvProcessor;
import com.salesforce.tools.model.MaskingConfig;
import com.salesforce.tools.model.ObjectConfig;
import com.salesforce.tools.salesforce.SalesforceConfig;
import com.salesforce.tools.salesforce.SalesforceConnection;
import com.sforce.ws.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * データマスキングローダー メインクラス
 * 
 * 使い方:
 * java -jar data-masking-loader.jar <処理モード> <オブジェクト名> <CSVファイルパス>
 * 
 * 処理モード:
 *   mask-only    : CSVをマスキングして出力のみ（Salesforceへの投入なし)
 *   mask-insert  : CSVをマスキングしてSalesforceにInsert
 *   mask-upsert  : CSVをマスキングしてSalesforceにUpsert
 */
public class DataMaskingLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataMaskingLoader.class);
    
    private static final String CONFIG_FILE = "masking-config.yml";
    private static final String SF_CONFIG_FILE = "salesforce.properties";

    public static void main(String[] args) {
        logger.info("===== Data Masking Loader 起動 =====");

        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        String mode = args[0];
        String objectName = args[1];
        String inputCsvPath = args[2];

        try {
            // マスキング設定読み込み
            MaskingConfig maskingConfig = ConfigLoader.loadConfig(CONFIG_FILE);
            if (!ConfigLoader.validateConfig(maskingConfig)) {
                logger.error("設定ファイルの検証に失敗しました");
                System.exit(1);
            }

            // オブジェクト設定取得
            ObjectConfig objectConfig = maskingConfig.getObjectConfig(objectName);
            if (objectConfig == null) {
                logger.error("オブジェクト {} の設定が見つかりません", objectName);
                System.exit(1);
            }

            // CSV処理
            CsvProcessor csvProcessor = new CsvProcessor();
            List<Map<String, Object>> maskedRecords = 
                csvProcessor.readAndMaskCsv(inputCsvPath, objectConfig);

            if (maskedRecords.isEmpty()) {
                logger.warn("処理対象のレコードがありません");
                System.exit(0);
            }

            // 処理モード別実行
            switch (mode.toLowerCase()) {
                case "mask-only":
                    executeMaskOnly(csvProcessor, maskedRecords, objectName);
                    break;
                
                case "mask-insert":
                    executeMaskInsert(maskedRecords, objectName);
                    break;
                
                case "mask-upsert":
                    String externalIdField = args.length > 3 ? args[3] : "Id";
                    executeMaskUpsert(maskedRecords, objectName, externalIdField);
                    break;
                
                default:
                    logger.error("不明な処理モード: {}", mode);
                    printUsage();
                    System.exit(1);
            }

            logger.info("===== 処理完了 =====");

        } catch (Exception e) {
            logger.error("処理中にエラーが発生しました", e);
            System.exit(1);
        }
    }

    /**
     * マスキングのみ実行（CSV出力）
     */
    private static void executeMaskOnly(CsvProcessor csvProcessor, 
                                       List<Map<String, Object>> maskedRecords,
                                       String objectName) throws IOException {
        String outputPath = objectName + "_masked_" + System.currentTimeMillis() + ".csv";
        csvProcessor.writeMaskedCsv(outputPath, maskedRecords);
        logger.info("マスキング済みCSVファイル: {}", outputPath);
    }

    /**
     * マスキング + Insert実行
     */
    private static void executeMaskInsert(List<Map<String, Object>> maskedRecords,
                                         String objectName) throws IOException, ConnectionException {
        SalesforceConfig sfConfig = loadSalesforceConfig();
        SalesforceConnection sfConnection = new SalesforceConnection(sfConfig);
        
        try {
            sfConnection.connect();
            sfConnection.insertRecords(objectName, maskedRecords);
        } finally {
            sfConnection.disconnect();
        }
    }

    /**
     * マスキング + Upsert実行
     */
    private static void executeMaskUpsert(List<Map<String, Object>> maskedRecords,
                                         String objectName,
                                         String externalIdField) throws IOException, ConnectionException {
        SalesforceConfig sfConfig = loadSalesforceConfig();
        SalesforceConnection sfConnection = new SalesforceConnection(sfConfig);
        
        try {
            sfConnection.connect();
            sfConnection.upsertRecords(objectName, maskedRecords, externalIdField);
        } finally {
            sfConnection.disconnect();
        }
    }

    /**
     * Salesforce接続設定を読み込む
     */
    private static SalesforceConfig loadSalesforceConfig() throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(SF_CONFIG_FILE)) {
            props.load(fis);
        }

        SalesforceConfig config = new SalesforceConfig();
        config.setUsername(props.getProperty("sf.username"));
        config.setPassword(props.getProperty("sf.password"));
        config.setSecurityToken(props.getProperty("sf.securityToken", ""));
        config.setLoginUrl(props.getProperty("sf.loginUrl", 
                          "https://login.salesforce.com/services/Soap/u/58.0"));
        
        String batchSizeStr = props.getProperty("sf.batchSize", "200");
        config.setBatchSize(Integer.parseInt(batchSizeStr));

        return config;
    }

    /**
     * 使い方を表示
     */
    private static void printUsage() {
        System.out.println("\n使い方:");
        System.out.println("  java -jar data-masking-loader.jar <処理モード> <オブジェクト名> <CSVファイルパス> [外部ID項目]");
        System.out.println("\n処理モード:");
        System.out.println("  mask-only    - CSVをマスキングして出力のみ（Salesforceへの投入なし）");
        System.out.println("  mask-insert  - CSVをマスキングしてSalesforceにInsert");
        System.out.println("  mask-upsert  - CSVをマスキングしてSalesforceにUpsert（外部ID項目指定が必要）");
        System.out.println("\n例:");
        System.out.println("  java -jar data-masking-loader.jar mask-only Account accounts.csv");
        System.out.println("  java -jar data-masking-loader.jar mask-insert Contact contacts.csv");
        System.out.println("  java -jar data-masking-loader.jar mask-upsert Account accounts.csv External_Id__c");
        System.out.println();
    }
}
