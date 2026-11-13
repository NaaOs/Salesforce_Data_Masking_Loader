package com.salesforce.tools.salesforce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SalesforceConfigTest {

    @TempDir
    Path dir;

    private String props(String content) throws IOException {
        Path p = dir.resolve("sf.properties");
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p.toString();
    }

    @Test
    void loadsPropertiesWithDefaults() throws IOException {
        SalesforceConfig c = SalesforceConfig.load(props("sf.username=u\nsf.password=p\n"), Map.of());
        assertEquals("u", c.getUsername());
        assertEquals("p", c.getPasswordWithToken());
        assertEquals(SalesforceConfig.DEFAULT_LOGIN_URL, c.getLoginUrl());
        assertEquals(200, c.getBatchSize());
    }

    @Test
    void environmentOverridesCredentials() throws IOException {
        SalesforceConfig c = SalesforceConfig.load(props("sf.username=u\nsf.password=p\nsf.batchSize=50\n"),
                Map.of("SF_PASSWORD", "envpass", "SF_SECURITY_TOKEN", "TOK"));
        assertEquals("u", c.getUsername());
        assertEquals("envpassTOK", c.getPasswordWithToken());
        assertEquals(50, c.getBatchSize());
    }

    @Test
    void worksWithoutFileWhenEnvironmentProvidesCredentials() throws IOException {
        SalesforceConfig c = SalesforceConfig.load(dir.resolve("missing.properties").toString(),
                Map.of("SF_USERNAME", "eu", "SF_PASSWORD", "ep"));
        assertEquals("eu", c.getUsername());
    }

    @Test
    void missingFileAndEnvironmentFails() {
        assertThrows(IOException.class,
                () -> SalesforceConfig.load(dir.resolve("missing.properties").toString(), Map.of()));
    }

    @Test
    void invalidBatchSizeFails() throws IOException {
        String base = "sf.username=u\nsf.password=p\n";
        assertThrows(IOException.class, () -> SalesforceConfig.load(props(base + "sf.batchSize=abc\n"), Map.of()));
        assertThrows(IOException.class, () -> SalesforceConfig.load(props(base + "sf.batchSize=0\n"), Map.of()));
        assertThrows(IOException.class, () -> SalesforceConfig.load(props(base + "sf.batchSize=201\n"), Map.of()));
    }

    @Test
    void missingPasswordFails() throws IOException {
        String path = props("sf.username=u\n");
        assertThrows(IOException.class, () -> SalesforceConfig.load(path, Map.of()));
    }

    @Test
    void toStringDoesNotExposeSecrets() throws IOException {
        SalesforceConfig c = SalesforceConfig.load(props("sf.username=u\nsf.password=secret\nsf.securityToken=tok\n"),
                Map.of());
        assertFalse(c.toString().contains("secret"));
        assertFalse(c.toString().contains("tok"));
    }
}
