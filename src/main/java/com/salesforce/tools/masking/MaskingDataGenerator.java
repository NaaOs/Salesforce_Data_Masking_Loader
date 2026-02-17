package com.salesforce.tools.masking;

import com.github.javafaker.Faker;
import com.salesforce.tools.model.FieldConfig;
import com.salesforce.tools.model.MaskingType;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;

/**
 * マスキングデータを生成するクラス
 */
public class MaskingDataGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MaskingDataGenerator.class);
    private final Faker faker;
    private final Random random;

    public MaskingDataGenerator() {
        this.faker = new Faker(Locale.JAPAN);
        this.random = new Random();
    }

    /**
     * 項目設定に基づいてマスキングデータを生成
     * 
     * @param fieldConfig 項目設定
     * @return マスキングされた値
     */
    public String generateMaskedValue(FieldConfig fieldConfig) {
        if (fieldConfig == null || fieldConfig.getMaskingType() == null) {
            return null;
        }

        MaskingType type = fieldConfig.getMaskingType();
        
        try {
            switch (type) {
                case RANDOM_STRING:
                    return generateRandomString(fieldConfig.getLength() != null ? 
                                               fieldConfig.getLength() : 20);
                
                case PHONE:
                    return generatePhone();
                
                case EMAIL:
                    return generateEmail();
                
                case FIRST_NAME:
                    return generateFirstName();
                
                case LAST_NAME:
                    return generateLastName();
                
                case ADDRESS:
                    return generateAddress();
                
                case COMPANY_NAME:
                    return generateCompanyName();
                
                case FIXED_VALUE:
                    return fieldConfig.getValue();
                
                case NUMBER:
                    return generateNumber(fieldConfig.getLength() != null ? 
                                        fieldConfig.getLength() : 10);
                
                case DATE:
                    return generateDate();
                
                default:
                    logger.warn("未対応のマスキングタイプ: {}", type);
                    return null;
            }
        } catch (Exception e) {
            logger.error("マスキングデータの生成に失敗: {}", type, e);
            return null;
        }
    }

    private String generateRandomString(int length) {
        return RandomStringUtils.randomAlphanumeric(length);
    }

    private String generatePhone() {
        // 日本の電話番号形式: 090-XXXX-XXXX
        return String.format("090-%04d-%04d", 
                           random.nextInt(10000), 
                           random.nextInt(10000));
    }

    private String generateEmail() {
        return faker.internet().emailAddress();
    }

    private String generateFirstName() {
        return faker.name().firstName();
    }

    private String generateLastName() {
        return faker.name().lastName();
    }

    private String generateAddress() {
        return faker.address().fullAddress();
    }

    private String generateCompanyName() {
        return faker.company().name();
    }

    private String generateNumber(int maxDigits) {
        int max = (int) Math.pow(10, maxDigits) - 1;
        return String.valueOf(random.nextInt(max));
    }

    private String generateDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(faker.date().birthday());
    }
}
