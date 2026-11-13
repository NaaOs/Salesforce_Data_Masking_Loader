package com.salesforce.tools.salesforce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Salesforce接続設定
 */
public class SalesforceConfig {
    private static final Logger logger = LoggerFactory.getLogger(SalesforceConfig.class);

    public static final String DEFAULT_LOGIN_URL = "https://login.salesforce.com/services/Soap/u/58.0";
    public static final int DEFAULT_BATCH_SIZE = 200;
    /** SOAP API の create/upsert 1 回あたりの上限件数 */
    public static final int MAX_BATCH_SIZE = 200;

    /** 環境変数: 設定するとプロパティファイルの値より優先される */
    public static final String ENV_USERNAME = "SF_USERNAME";
    public static final String ENV_PASSWORD = "SF_PASSWORD";
    public static final String ENV_SECURITY_TOKEN = "SF_SECURITY_TOKEN";

    private String username;
    private String password;
    private String securityToken;
    private String loginUrl;
    private int batchSize;

    public SalesforceConfig() {
        this.loginUrl = DEFAULT_LOGIN_URL;
        this.batchSize = DEFAULT_BATCH_SIZE;
    }

    /**
     * プロパティファイルと環境変数から接続設定を読み込む。
     * 認証情報（ユーザー名・パスワード・トークン）は環境変数が設定されていればそちらを優先する。
     */
    public static SalesforceConfig load(String propertiesPath) throws IOException {
        return load(propertiesPath, System.getenv());
    }

    static SalesforceConfig load(String propertiesPath, Map<String, String> env) throws IOException {
        Properties props = new Properties();
        if (propertiesPath != null && !propertiesPath.isBlank() && Files.exists(Path.of(propertiesPath))) {
            try (Reader reader = Files.newBufferedReader(Path.of(propertiesPath), StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } else if (!hasText(env.get(ENV_USERNAME)) || !hasText(env.get(ENV_PASSWORD))) {
            throw new IOException("Salesforce設定ファイルが見つかりません: " + propertiesPath
                    + "（または環境変数 " + ENV_USERNAME + " / " + ENV_PASSWORD + " を設定してください）");
        }

        SalesforceConfig config = new SalesforceConfig();
        config.setUsername(firstNonBlank(env.get(ENV_USERNAME), props.getProperty("sf.username")));
        config.setPassword(firstNonBlank(env.get(ENV_PASSWORD), props.getProperty("sf.password")));
        config.setSecurityToken(firstNonBlank(env.get(ENV_SECURITY_TOKEN), props.getProperty("sf.securityToken", "")));
        config.setLoginUrl(props.getProperty("sf.loginUrl", DEFAULT_LOGIN_URL).trim());

        String batchSizeStr = props.getProperty("sf.batchSize", String.valueOf(DEFAULT_BATCH_SIZE)).trim();
        int batchSize;
        try {
            batchSize = Integer.parseInt(batchSizeStr);
        } catch (NumberFormatException e) {
            throw new IOException("sf.batchSize が数値ではありません: " + batchSizeStr, e);
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IOException("sf.batchSize は 1〜" + MAX_BATCH_SIZE + " で指定してください: " + batchSize);
        }
        config.setBatchSize(batchSize);

        if (!hasText(config.getUsername()) || !hasText(config.getPassword())) {
            throw new IOException("ユーザー名またはパスワードが設定されていません（sf.username / sf.password または環境変数）");
        }
        if (hasText(props.getProperty("sf.password"))) {
            logger.warn("パスワードがプロパティファイルに平文で保存されています。環境変数 {} の利用を推奨します", ENV_PASSWORD);
        }
        return config;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public void setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * パスワードとセキュリティトークンを結合
     */
    public String getPasswordWithToken() {
        if (securityToken != null && !securityToken.isEmpty()) {
            return password + securityToken;
        }
        return password;
    }

    /** 認証情報を含めない文字列表現 */
    @Override
    public String toString() {
        return "SalesforceConfig{username=" + username + ", loginUrl=" + loginUrl + ", batchSize=" + batchSize + "}";
    }
}
