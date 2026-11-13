package com.salesforce.tools.masking;

import com.salesforce.tools.model.FieldConfig;
import com.salesforce.tools.model.MaskingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MaskingDataGeneratorTest {

    private final MaskingDataGenerator generator = new MaskingDataGenerator(new Random(42));

    private static FieldConfig field(MaskingType type, Integer length, String value) {
        FieldConfig f = new FieldConfig();
        f.setName("F");
        f.setMaskingType(type);
        f.setLength(length);
        f.setValue(value);
        return f;
    }

    @ParameterizedTest
    @EnumSource(value = MaskingType.class, names = "FIXED_VALUE", mode = EnumSource.Mode.EXCLUDE)
    void allTypesProduceNonBlankValue(MaskingType type) {
        String v = generator.generateMaskedValue(field(type, null, null));
        assertNotNull(v, type.name());
        assertFalse(v.isBlank(), type.name());
    }

    @Test
    void nullConfigReturnsNull() {
        assertNull(generator.generateMaskedValue(null));
        assertNull(generator.generateMaskedValue(field(null, null, null)));
    }

    @Test
    void randomStringUsesLengthAndIsAlphanumeric() {
        assertEquals(MaskingDataGenerator.DEFAULT_STRING_LENGTH,
                generator.generateMaskedValue(field(MaskingType.RANDOM_STRING, null, null)).length());
        String v = generator.generateMaskedValue(field(MaskingType.RANDOM_STRING, 7, null));
        assertEquals(7, v.length());
        assertTrue(v.matches("[A-Za-z0-9]+"), v);
    }

    @Test
    void phoneFormat() {
        for (int i = 0; i < 50; i++) {
            assertTrue(generator.generateMaskedValue(field(MaskingType.PHONE, null, null))
                    .matches("090-\\d{4}-\\d{4}"));
        }
    }

    @Test
    void fixedValueReturnsConfiguredValue() {
        assertEquals("MASKED", generator.generateMaskedValue(field(MaskingType.FIXED_VALUE, null, "MASKED")));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 18})
    void numberStaysWithinDigits(int digits) {
        for (int i = 0; i < 200; i++) {
            String v = generator.generateMaskedValue(field(MaskingType.NUMBER, digits, null));
            assertTrue(v.matches("\\d{1," + digits + "}"), v);
        }
    }

    @Test
    void numberWithLargeLengthUsesFullRange() {
        // 旧実装は int で計算していたため 10 桁以上で上限が Integer.MAX_VALUE になっていた
        boolean exceededInt = false;
        for (int i = 0; i < 200 && !exceededInt; i++) {
            long v = Long.parseLong(generator.generateMaskedValue(field(MaskingType.NUMBER, 15, null)));
            exceededInt = v > Integer.MAX_VALUE;
        }
        assertTrue(exceededInt);
    }

    @Test
    void dateIsIsoFormatted() {
        String v = generator.generateMaskedValue(field(MaskingType.DATE, null, null));
        assertDoesNotThrow(() -> LocalDate.parse(v));
    }

    @Test
    void sameSeedIsReproducible() {
        MaskingDataGenerator a = new MaskingDataGenerator(new Random(7));
        MaskingDataGenerator b = new MaskingDataGenerator(new Random(7));
        FieldConfig f = field(MaskingType.RANDOM_STRING, 12, null);
        assertEquals(a.generateMaskedValue(f), b.generateMaskedValue(f));
    }
}
