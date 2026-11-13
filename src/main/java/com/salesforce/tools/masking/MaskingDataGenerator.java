package com.salesforce.tools.masking;

import com.github.javafaker.Faker;
import com.salesforce.tools.model.FieldConfig;
import com.salesforce.tools.model.MaskingType;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

/**
 * マスキングデータを生成するクラス
 */
public class MaskingDataGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MaskingDataGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static final int DEFAULT_STRING_LENGTH = 20;
    static final int DEFAULT_NUMBER_LENGTH = 10;

    private final Faker faker;
    private final Random random;

    public MaskingDataGenerator() {
        this(new Random());
    }

    /**
     * 乱数を指定して生成（テストで再現性を持たせる場合に使用）
     */
    public MaskingDataGenerator(Random random) {
        this.random = random;
        this.faker = new Faker(Locale.JAPAN, random);
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
                    return generateRandomString(lengthOrDefault(fieldConfig, DEFAULT_STRING_LENGTH));
                case PHONE:
                    return generatePhone();
                case EMAIL:
                    return faker.internet().emailAddress();
                case FIRST_NAME:
                    return faker.name().firstName();
                case LAST_NAME:
                    return faker.name().lastName();
                case ADDRESS:
                    return faker.address().fullAddress();
                case COMPANY_NAME:
                    return faker.company().name();
                case FIXED_VALUE:
                    return fieldConfig.getValue();
                case NUMBER:
                    return generateNumber(lengthOrDefault(fieldConfig, DEFAULT_NUMBER_LENGTH));
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

    private static int lengthOrDefault(FieldConfig fieldConfig, int defaultLength) {
        return fieldConfig.getLength() != null ? fieldConfig.getLength() : defaultLength;
    }

    private String generateRandomString(int length) {
        return RandomStringUtils.random(length, 0, 0, true, true, null, random);
    }

    private String generatePhone() {
        // 日本の携帯電話番号形式: 090-XXXX-XXXX
        return String.format("090-%04d-%04d", random.nextInt(10000), random.nextInt(10000));
    }

    /**
     * 最大 maxDigits 桁（0 〜 10^maxDigits - 1）の数値を生成。maxDigits は 1〜18。
     */
    private String generateNumber(int maxDigits) {
        long bound = 1;
        for (int i = 0; i < maxDigits; i++) {
            bound *= 10;
        }
        long value = Math.floorMod(random.nextLong(), bound);
        return String.valueOf(value);
    }

    private String generateDate() {
        return faker.date().birthday().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT);
    }
}
