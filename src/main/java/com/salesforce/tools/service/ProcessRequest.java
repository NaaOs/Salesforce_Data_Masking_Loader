package com.salesforce.tools.service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 処理要求
 *
 * @param mode                     処理モード
 * @param objectName               オブジェクト名
 * @param inputCsvPath             入力CSVパス
 * @param externalIdField          外部ID項目（Upsert 時のみ使用）
 * @param salesforceConfigPath     Salesforce 接続設定ファイル（mask-only では不要）
 * @param outputDir                出力 CSV（マスキング済み・エラー）の出力先ディレクトリ
 */
public record ProcessRequest(ProcessMode mode, String objectName, String inputCsvPath, String externalIdField,
                             String salesforceConfigPath, Path outputDir) {

    public ProcessRequest {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(objectName, "objectName");
        Objects.requireNonNull(inputCsvPath, "inputCsvPath");
        if (outputDir == null) {
            outputDir = Path.of(".");
        }
        if (mode == ProcessMode.MASK_UPSERT && (externalIdField == null || externalIdField.isBlank())) {
            throw new IllegalArgumentException("Upsertモードでは外部ID項目の指定が必須です");
        }
    }
}
