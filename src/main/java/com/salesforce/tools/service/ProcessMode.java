package com.salesforce.tools.service;

import java.util.Locale;

/**
 * 処理モード
 */
public enum ProcessMode {
    /** CSV をマスキングして出力のみ */
    MASK_ONLY("mask-only", "マスキングのみ"),
    /** マスキングして Salesforce に Insert */
    MASK_INSERT("mask-insert", "Insert"),
    /** マスキングして Salesforce に Upsert */
    MASK_UPSERT("mask-upsert", "Upsert");

    private final String code;
    private final String label;

    ProcessMode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public boolean requiresSalesforce() {
        return this != MASK_ONLY;
    }

    /**
     * モード文字列（大文字小文字区別なし）から変換
     *
     * @throws IllegalArgumentException 不明なモードの場合
     */
    public static ProcessMode fromCode(String code) {
        if (code != null) {
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            for (ProcessMode mode : values()) {
                if (mode.code.equals(normalized)) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("不明な処理モード: " + code);
    }

    /** 画面表示用（例: "mask-only (マスキングのみ)"） */
    @Override
    public String toString() {
        return code + " (" + label + ")";
    }
}
