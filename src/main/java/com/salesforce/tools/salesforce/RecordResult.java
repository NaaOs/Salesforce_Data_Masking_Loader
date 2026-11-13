package com.salesforce.tools.salesforce;

/**
 * 1 レコード分の Insert / Upsert 結果
 *
 * @param index   投入リスト内のインデックス（0 始まり）
 * @param success 成功したか
 * @param id      作成・更新されたレコード ID（失敗時 null）
 * @param created Upsert で新規作成された場合 true（Insert は常に true）
 * @param errors  失敗時のエラーメッセージ（成功時は空文字）
 */
public record RecordResult(int index, boolean success, String id, boolean created, String errors) {
}
