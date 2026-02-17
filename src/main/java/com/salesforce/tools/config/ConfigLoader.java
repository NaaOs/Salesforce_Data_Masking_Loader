package com.salesforce.tools.config;

import com.salesforce.tools.model.MaskingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * YAML設定ファイルを読み込むクラス
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    /**
     * YAML設定ファイルを読み込む
     * 
     * @param configFilePath 設定ファイルのパス
     * @return マスキング設定オブジェクト
     * @throws FileNotFoundException ファイルが見つからない場合
     */
    public static MaskingConfig loadConfig(String configFilePath) throws FileNotFoundException {
        logger.info("設定ファイルを読み込み中: {}", configFilePath);
        
        InputStream inputStream = new FileInputStream(configFilePath);
        
        // SnakeYAMLのバージョンに応じた初期化
        Yaml yaml;
        try {
            // SnakeYAML 2.0以降の方法を試す
            Class<?> loaderOptionsClass = Class.forName("org.yaml.snakeyaml.LoaderOptions");
            Object loaderOptions = loaderOptionsClass.getDeclaredConstructor().newInstance();
            Constructor constructor = Constructor.class
                .getConstructor(Class.class, loaderOptionsClass)
                .newInstance(MaskingConfig.class, loaderOptions);
            yaml = new Yaml(constructor);
            logger.debug("SnakeYAML 2.0+ 方式で初期化");
        } catch (Exception e) {
            // SnakeYAML 1.x の方法にフォールバック
            try {
                Constructor constructor = Constructor.class
                    .getConstructor(Class.class)
                    .newInstance(MaskingConfig.class);
                yaml = new Yaml(constructor);
                logger.debug("SnakeYAML 1.x 方式で初期化");
            } catch (Exception ex) {
                // 最終手段：デフォルトコンストラクタ
                yaml = new Yaml();
                logger.debug("デフォルト方式で初期化");
            }
        }
        
        MaskingConfig config = yaml.load(inputStream);
        
        logger.info("設定ファイルの読み込み完了: {} オブジェクト", 
                    config.getObjects() != null ? config.getObjects().size() : 0);
        
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

        for (var objectConfig : config.getObjects()) {
            if (objectConfig.getName() == null || objectConfig.getName().isEmpty()) {
                logger.error("オブジェクト名が未定義です");
                return false;
            }
            
            if (objectConfig.getFields() == null || objectConfig.getFields().isEmpty()) {
                logger.warn("オブジェクト {} にマスキング項目が定義されていません", 
                           objectConfig.getName());
            }
            
            if (objectConfig.getFields() != null) {
                for (var fieldConfig : objectConfig.getFields()) {
                    if (fieldConfig.getName() == null || fieldConfig.getName().isEmpty()) {
                        logger.error("項目名が未定義です (オブジェクト: {})", 
                                   objectConfig.getName());
                        return false;
                    }
                    
                    if (fieldConfig.getMaskingType() == null) {
                        logger.error("マスキングタイプが未定義です (オブジェクト: {}, 項目: {})", 
                                   objectConfig.getName(), fieldConfig.getName());
                        return false;
                    }
                }
            }
        }

        logger.info("設定ファイルの妥当性チェック: OK");
        return true;
    }
}