package com.salesforce.tools.salesforce;

/**
 * Salesforce接続設定
 */
public class SalesforceConfig {
    private String username;
    private String password;
    private String securityToken;
    private String loginUrl;
    private int batchSize;
    
    public SalesforceConfig() {
        this.loginUrl = "https://login.salesforce.com/services/Soap/u/58.0";
        this.batchSize = 200; // デフォルトバッチサイズ
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
}
