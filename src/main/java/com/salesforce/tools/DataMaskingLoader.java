package com.salesforce.tools;

import com.salesforce.tools.config.ConfigLoader;
import com.salesforce.tools.model.MaskingConfig;
import com.salesforce.tools.service.MaskingService;
import com.salesforce.tools.service.ProcessMode;
import com.salesforce.tools.service.ProcessRequest;
import com.salesforce.tools.service.ProcessResult;
import com.salesforce.tools.service.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * データマスキングローダー CLI メインクラス
 *
 * <pre>
 * java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar &lt;処理モード&gt; &lt;オブジェクト名&gt; &lt;CSVファイルパス&gt; [外部ID項目]
 * </pre>
 *
 * 終了コード: 0 = 正常終了 / 1 = エラー / 2 = 一部レコードの投入に失敗
 */
public class DataMaskingLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataMaskingLoader.class);

    static final String CONFIG_FILE = "masking-config.yml";
    static final String SF_CONFIG_FILE = "salesforce.properties";

    public static final int EXIT_OK = 0;
    public static final int EXIT_ERROR = 1;
    public static final int EXIT_PARTIAL_FAILURE = 2;

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * CLI 処理本体（テスト可能にするため終了コードを返す）
     */
    static int run(String[] args) {
        logger.info("===== Data Masking Loader 起動 =====");

        if (args.length < 3) {
            printUsage();
            return EXIT_ERROR;
        }

        ProcessMode mode;
        try {
            mode = ProcessMode.fromCode(args[0]);
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            printUsage();
            return EXIT_ERROR;
        }
        String objectName = args[1];
        String inputCsvPath = args[2];
        String externalIdField = args.length > 3 ? args[3] : "Id";

        try {
            MaskingConfig maskingConfig = ConfigLoader.loadConfig(CONFIG_FILE);
            if (!ConfigLoader.validateConfig(maskingConfig)) {
                logger.error("設定ファイルの検証に失敗しました");
                return EXIT_ERROR;
            }

            ProcessRequest request = new ProcessRequest(mode, objectName, inputCsvPath,
                    mode == ProcessMode.MASK_UPSERT ? externalIdField : null, SF_CONFIG_FILE, Path.of("."));
            ProcessResult result = new MaskingService(maskingConfig).execute(request, null);

            logger.info("===== 処理完了 =====\n{}", result.summary());
            if (result.hasFailures()) {
                logger.warn("{} 件のレコードの投入に失敗しました。エラーファイルを確認してください", result.failureCount());
                return EXIT_PARTIAL_FAILURE;
            }
            return EXIT_OK;

        } catch (ProcessingException e) {
            logger.error(e.getMessage(), e);
            if (e.getPartialResult() != null) {
                logger.error("中断までの結果:\n{}", e.getPartialResult().summary());
            }
            return EXIT_ERROR;
        } catch (Exception e) {
            logger.error("処理中にエラーが発生しました: {}", e.getMessage(), e);
            return EXIT_ERROR;
        }
    }

    private static void printUsage() {
        System.out.println("\n使い方:");
        System.out.println("  java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar <処理モード> <オブジェクト名> <CSVファイルパス> [外部ID項目]");
        System.out.println("  （引数なしで実行すると GUI が起動します）");
        System.out.println("\n処理モード:");
        System.out.println("  mask-only    - CSVをマスキングして出力のみ（Salesforceへの投入なし）");
        System.out.println("  mask-insert  - CSVをマスキングしてSalesforceにInsert");
        System.out.println("  mask-upsert  - CSVをマスキングしてSalesforceにUpsert（外部ID項目、省略時は Id）");
        System.out.println("\n終了コード:");
        System.out.println("  0: 正常終了 / 1: エラー / 2: 一部レコードの投入に失敗（エラーCSVを出力）");
        System.out.println("\n例:");
        System.out.println("  java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar mask-only Account accounts.csv");
        System.out.println("  java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar mask-insert Contact contacts.csv");
        System.out.println("  java -jar data-masking-loader-1.0.0-jar-with-dependencies.jar mask-upsert Account accounts.csv External_Id__c");
        System.out.println();
    }
}
