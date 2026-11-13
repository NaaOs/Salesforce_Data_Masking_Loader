package com.salesforce.tools.config;

import com.salesforce.tools.model.FieldConfig;
import com.salesforce.tools.model.MaskingConfig;
import com.salesforce.tools.model.MaskingType;
import com.salesforce.tools.model.ObjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * YAML設定ファイルを読み込むクラス
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    /** RANDOM_STRING の length 上限 */
    public static final int MAX_RANDOM_STRING_LENGTH = 255;
    /** NUMBER の length 上限（long で表現できる桁数） */
    public static final int MAX_NUMBER_LENGTH = 18;

    private ConfigLoader() {
    }

    /**
     * YAML設定ファイルを読み込む（UTF-8）
     *
     * @param configFilePath 設定ファイルのパス
     * @return マスキング設定オブジェクト（空ファイルの場合は null）
     * @throws IOException ファイルが見つからない・読めない場合
     */
    public static MaskingConfig loadConfig(String configFilePath) throws IOException {
        logger.info("設定ファイルを読み込み中: {}", configFilePath);

        Yaml yaml = new Yaml(new Constructor(MaskingConfig.class, new LoaderOptions()));
        MaskingConfig config;
        try (Reader reader = Files.newBufferedReader(Path.of(configFilePath), StandardCharsets.UTF_8)) {
            config = yaml.load(reader);
        }

        logger.info("設定ファイルの読み込み完了: {} オブジェクト",
                config != null && config.getObjects() != null ? config.getObjects().size() : 0);
        return config;
    }

    /**
     * 設定の妥当性をチェック
     *
     * @param config マスキング設定
     * @return 妥当な場合true
     */
    public static boolean validateConfig(MaskingConfig config) {
        if (config == null || config.getObjects() == null || config.getObjects().isEmpty()) {
            logger.error("設定ファイルにオブジェクト定義がありません");
            return false;
        }

        Set<String> objectNames = new HashSet<>();
        for (ObjectConfig objectConfig : config.getObjects()) {
            if (objectConfig.getName() == null || objectConfig.getName().isEmpty()) {
                logger.error("オブジェクト名が未定義です");
                return false;
            }
            if (!objectNames.add(objectConfig.getName())) {
                logger.error("オブジェクト {} が重複して定義されています", objectConfig.getName());
                return false;
            }

            if (objectConfig.getFields() == null || objectConfig.getFields().isEmpty()) {
                logger.warn("オブジェクト {} にマスキング項目が定義されていません", objectConfig.getName());
                continue;
            }

            for (FieldConfig fieldConfig : objectConfig.getFields()) {
                if (!validateField(objectConfig.getName(), fieldConfig)) {
                    return false;
                }
            }
        }

        logger.info("設定ファイルの妥当性チェック: OK");
        return true;
    }

    private static boolean validateField(String objectName, FieldConfig fieldConfig) {
        String fieldName = fieldConfig.getName();
        if (fieldName == null || fieldName.isEmpty()) {
            logger.error("項目名が未定義です (オブジェクト: {})", objectName);
            return false;
        }

        MaskingType type = fieldConfig.getMaskingType();
        if (type == null) {
            logger.error("マスキングタイプが未定義です (オブジェクト: {}, 項目: {})", objectName, fieldName);
            return false;
        }

        Integer length = fieldConfig.getLength();
        switch (type) {
            case FIXED_VALUE:
                if (fieldConfig.getValue() == null) {
                    logger.error("FIXED_VALUE には value の指定が必須です (オブジェクト: {}, 項目: {})",
                            objectName, fieldName);
                    return false;
                }
                break;
            case RANDOM_STRING:
                if (length != null && (length < 1 || length > MAX_RANDOM_STRING_LENGTH)) {
                    logger.error("RANDOM_STRING の length は 1〜{} で指定してください (オブジェクト: {}, 項目: {}, length: {})",
                            MAX_RANDOM_STRING_LENGTH, objectName, fieldName, length);
                    return false;
                }
                break;
            case NUMBER:
                if (length != null && (length < 1 || length > MAX_NUMBER_LENGTH)) {
                    logger.error("NUMBER の length は 1〜{} で指定してください (オブジェクト: {}, 項目: {}, length: {})",
                            MAX_NUMBER_LENGTH, objectName, fieldName, length);
                    return false;
                }
                break;
            default:
                if (length != null) {
                    logger.warn("{} では length は使用されません (オブジェクト: {}, 項目: {})",
                            type, objectName, fieldName);
                }
                break;
        }
        return true;
    }
}
