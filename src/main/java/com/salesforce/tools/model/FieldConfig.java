package com.salesforce.tools.model;

/**
 * 項目ごとのマスキング設定
 */
public class FieldConfig {
    private String name;
    private MaskingType maskingType;
    private Integer length;
    private String value;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MaskingType getMaskingType() {
        return maskingType;
    }

    public void setMaskingType(MaskingType maskingType) {
        this.maskingType = maskingType;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
