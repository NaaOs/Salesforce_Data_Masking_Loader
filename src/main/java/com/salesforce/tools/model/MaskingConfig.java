package com.salesforce.tools.model;

import java.util.List;

/**
 * マスキング設定全体を表すクラス
 */
public class MaskingConfig {
    private List<ObjectConfig> objects;

    public List<ObjectConfig> getObjects() {
        return objects;
    }

    public void setObjects(List<ObjectConfig> objects) {
        this.objects = objects;
    }

    /**
     * 指定されたオブジェクト名の設定を取得
     */
    public ObjectConfig getObjectConfig(String objectName) {
        if (objects == null) {
            return null;
        }
        return objects.stream()
                .filter(obj -> obj.getName().equals(objectName))
                .findFirst()
                .orElse(null);
    }
}
