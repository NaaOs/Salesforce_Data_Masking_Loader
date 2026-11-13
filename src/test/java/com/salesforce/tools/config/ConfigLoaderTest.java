package com.salesforce.tools.config;

import com.salesforce.tools.model.MaskingConfig;
import com.salesforce.tools.model.MaskingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @TempDir
    Path dir;

    private MaskingConfig load(String yaml) throws IOException {
        Path p = dir.resolve("c.yml");
        Files.writeString(p, yaml, StandardCharsets.UTF_8);
        return ConfigLoader.loadConfig(p.toString());
    }

    @Test
    void loadsBundledSampleConfig() throws IOException {
        MaskingConfig config = ConfigLoader.loadConfig("masking-config.yml");
        assertTrue(ConfigLoader.validateConfig(config));
        assertEquals(MaskingType.PHONE,
                config.getObjectConfig("Account").getFieldConfig("Phone").getMaskingType());
    }

    @Test
    void readsUtf8Values() throws IOException {
        MaskingConfig c = load("""
                objects:
                  - name: Account
                    fields:
                      - name: Description
                        maskingType: FIXED_VALUE
                        value: マスク済み
                """);
        assertTrue(ConfigLoader.validateConfig(c));
        assertEquals("マスク済み", c.getObjectConfig("Account").getFieldConfig("Description").getValue());
    }

    @Test
    void missingFileThrows() {
        assertThrows(IOException.class, () -> ConfigLoader.loadConfig(dir.resolve("none.yml").toString()));
    }

    @Test
    void emptyConfigIsInvalid() throws IOException {
        assertFalse(ConfigLoader.validateConfig(load("")));
        assertFalse(ConfigLoader.validateConfig(load("objects: []")));
    }

    @Test
    void fixedValueWithoutValueIsInvalid() throws IOException {
        assertFalse(ConfigLoader.validateConfig(load("""
                objects:
                  - name: Account
                    fields:
                      - name: Name
                        maskingType: FIXED_VALUE
                """)));
    }

    @Test
    void numberLengthOutOfRangeIsInvalid() throws IOException {
        assertFalse(ConfigLoader.validateConfig(load("""
                objects:
                  - name: Account
                    fields:
                      - name: Num__c
                        maskingType: NUMBER
                        length: 19
                """)));
    }

    @Test
    void randomStringLengthZeroIsInvalid() throws IOException {
        assertFalse(ConfigLoader.validateConfig(load("""
                objects:
                  - name: Account
                    fields:
                      - name: Name
                        maskingType: RANDOM_STRING
                        length: 0
                """)));
    }

    @Test
    void duplicateObjectIsInvalid() throws IOException {
        assertFalse(ConfigLoader.validateConfig(load("""
                objects:
                  - name: Account
                    fields:
                      - name: Name
                        maskingType: PHONE
                  - name: Account
                    fields:
                      - name: Phone
                        maskingType: PHONE
                """)));
    }

    @Test
    void missingMaskingTypeIsInvalid() throws IOException {
        assertFalse(ConfigLoader.validateConfig(load("""
                objects:
                  - name: Account
                    fields:
                      - name: Name
                """)));
    }

    @Test
    void objectWithoutFieldsIsValid() throws IOException {
        assertTrue(ConfigLoader.validateConfig(load("""
                objects:
                  - name: Account
                """)));
    }
}
