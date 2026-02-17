package com.salesforce.tools.model;

import java.util.List;

/**
 * Salesforceオブジェクトごとのマスキング設定
 */
public class ObjectConfig {
    private String name;
    private List<FieldConfig> fields;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<FieldConfig> getFields() {
        return fields;
    }

    public void setFields(List<FieldConfig> fields) {
        this.fields = fields;
    }

    /**
     * 指定された項目名の設定を取得
     */
    public FieldConfig getFieldConfig(String fieldName) {
        if (fields == null) {
            return null;
        }
        return fields.stream()
                .filter(field -> field.getName().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    /**
     * マスキング対象の項目名リストを取得
     */
    public List<String> getMaskingFieldNames() {
        if (fields == null) {
            return List.of();
        }
        return fields.stream()
                .map(FieldConfig::getName)
                .toList();
    }
}
