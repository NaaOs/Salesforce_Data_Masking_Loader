package com.salesforce.tools.service;

import java.nio.file.Path;

/**
 * 処理結果
 *
 * @param mode         処理モード
 * @param totalRecords 処理したレコード件数
 * @param successCount 成功件数（mask-only では出力件数）
 * @param failureCount 失敗件数
 * @param outputFile   マスキング済み CSV（mask-only 時のみ、それ以外は null）
 * @param errorFile    失敗レコード CSV（失敗がない場合は null）
 */
public record ProcessResult(ProcessMode mode, int totalRecords, int successCount, int failureCount,
                            Path outputFile, Path errorFile) {

    public boolean hasFailures() {
        return failureCount > 0;
    }

    /** 利用者向けの結果要約 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("処理件数: ").append(totalRecords)
                .append(" / 成功: ").append(successCount)
                .append(" / 失敗: ").append(failureCount);
        if (outputFile != null) {
            sb.append("\n出力ファイル: ").append(outputFile.toAbsolutePath().normalize());
        }
        if (errorFile != null) {
            sb.append("\nエラーファイル: ").append(errorFile.toAbsolutePath().normalize());
        }
        return sb.toString();
    }
}
