package com.salesforce.tools.model;

/**
 * マスキングのタイプを定義
 */
public enum MaskingType {
    RANDOM_STRING,      // ランダム文字列
    PHONE,             // 電話番号形式
    EMAIL,             // メールアドレス形式
    FIRST_NAME,        // ランダムな名
    LAST_NAME,         // ランダムな姓
    ADDRESS,           // ランダムな住所
    COMPANY_NAME,      // ランダムな会社名
    FIXED_VALUE,       // 固定値
    NUMBER,            // ランダムな数値
    DATE              // ランダムな日付
}
