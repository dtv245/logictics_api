package com.company.logicstic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Driver-data encryption configuration; values are supplied by the runtime secret manager. */
@ConfigurationProperties(prefix = "app.driver-profile.pii")
public class DriverPiiProperties {
    private String activeKeyId;
    /** Semicolon-delimited key ring entries in the form key-id:base64-key. */
    private String encryptionKeys;
    /** Stable, independently managed Base64 key used only for license-number HMAC indexes. */
    private String indexKey;

    public String getActiveKeyId() { return activeKeyId; }
    public void setActiveKeyId(String activeKeyId) { this.activeKeyId = activeKeyId; }
    public String getEncryptionKeys() { return encryptionKeys; }
    public void setEncryptionKeys(String encryptionKeys) { this.encryptionKeys = encryptionKeys; }
    public String getIndexKey() { return indexKey; }
    public void setIndexKey(String indexKey) { this.indexKey = indexKey; }
}
