package com.company.logicstic.service;

import com.company.logicstic.config.DriverPiiProperties;
import com.company.logicstic.exception.ApiException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** AES-256-GCM encryption and a keyed blind index for driver license numbers. */
@Service
public class DriverPiiCipher {
    private static final String PREFIX = "DLENC:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DriverPiiProperties properties;

    public DriverPiiCipher(DriverPiiProperties properties) {
        this.properties = properties;
    }

    public boolean isEncryptedWithActiveKey(String value) {
        return value != null && value.startsWith(activePrefix());
    }

    public String activePrefix() {
        String keyId = properties.getActiveKeyId();
        if (!StringUtils.hasText(keyId) || !keyId.matches("[A-Za-z0-9._-]{1,32}")) {
            throw configurationUnavailable();
        }
        return PREFIX + keyId + ":";
    }

    public String encryptLicenseNumber(String plaintext, UUID employeeId, String country) {
        return encryptSensitiveValue(plaintext == null ? null : plaintext.trim(), employeeId,
                "license-number:" + normalizedCountry(country));
    }

    public String decryptLicenseNumber(String value, UUID employeeId, String country) {
        return decryptSensitiveValue(value, employeeId, "license-number:" + normalizedCountry(country));
    }

    public String encryptSensitiveValue(String plaintext, UUID employeeId, String fieldContext) {
        if (!StringUtils.hasText(plaintext)) throw new IllegalArgumentException("Sensitive value is required");
        String clearValue = plaintext;
        String activePrefix = activePrefix();
        if (plaintext.startsWith(PREFIX)) clearValue = decryptSensitiveValue(plaintext, employeeId, fieldContext);
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(activePrefix), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(employeeId, fieldContext));
            byte[] encrypted = cipher.doFinal(clearValue.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return activePrefix + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw configurationUnavailable();
        }
    }

    public String decryptSensitiveValue(String value, UUID employeeId, String fieldContext) {
        if (!StringUtils.hasText(value)) return value;
        if (!value.startsWith(PREFIX)) return value;
        try {
            int keyEnd = value.indexOf(':', PREFIX.length());
            if (keyEnd < 0) throw configurationUnavailable();
            String keyId = value.substring(PREFIX.length(), keyEnd);
            byte[] payload = Base64.getUrlDecoder().decode(value.substring(keyEnd + 1));
            if (payload.length <= IV_BYTES) throw configurationUnavailable();
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(PREFIX + keyId + ":"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(employeeId, fieldContext));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw configurationUnavailable();
        }
    }

    public String licenseNumberHmac(String value, UUID employeeId, String country) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException("Driver license number is required");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(indexKeyBytes(), "HmacSHA256"));
            String input = employeeId + ":" + normalizedCountry(country) + ":" + normalizeLicenseNumber(value);
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw configurationUnavailable();
        }
    }

    /** Stable tenant-wide blind index for sensitive identifiers with a UNIQUE constraint. */
    public String sensitiveValueHmac(String value, String fieldContext) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException("Sensitive identifier is required");
        if (!StringUtils.hasText(fieldContext) || !fieldContext.matches("[a-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("Sensitive identifier context is invalid");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(indexKeyBytes(), "HmacSHA256"));
            String normalized = value.trim().replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
            String input = "driver-pii-index:v1:" + fieldContext + ":" + normalized;
            return java.util.HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw configurationUnavailable();
        }
    }

    private byte[] indexKeyBytes() {
        if (!StringUtils.hasText(properties.getIndexKey())) throw configurationUnavailable();
        byte[] indexKey = Base64.getDecoder().decode(properties.getIndexKey());
        if (indexKey.length != 32) throw configurationUnavailable();
        return indexKey;
    }

    public boolean hasConfiguration() {
        return StringUtils.hasText(properties.getActiveKeyId())
                && StringUtils.hasText(properties.getEncryptionKeys())
                && StringUtils.hasText(properties.getIndexKey());
    }

    private SecretKeySpec encryptionKey(String prefix) {
        String keyId = prefix.substring(PREFIX.length(), prefix.length() - 1);
        String keyRing = properties.getEncryptionKeys();
        if (!StringUtils.hasText(keyRing)) throw configurationUnavailable();
        Map<String, byte[]> keys = new HashMap<>();
        for (String entry : keyRing.split(";")) {
            int separator = entry.indexOf(':');
            if (separator <= 0 || separator == entry.length() - 1) throw configurationUnavailable();
            String id = entry.substring(0, separator).trim();
            if (!id.matches("[A-Za-z0-9._-]{1,32}")) throw configurationUnavailable();
            byte[] bytes = Base64.getDecoder().decode(entry.substring(separator + 1).trim());
            if (bytes.length != 32) throw configurationUnavailable();
            keys.put(id, bytes);
        }
        String activeKeyId = properties.getActiveKeyId();
        byte[] activeKey = keys.get(activeKeyId);
        byte[] requestedKey = keys.get(keyId);
        if (activeKey == null || requestedKey == null) throw configurationUnavailable();
        return new SecretKeySpec(requestedKey, "AES");
    }

    private static byte[] aad(UUID employeeId, String fieldContext) {
        if (employeeId == null) throw new IllegalArgumentException("Driver employee id is required");
        if (!StringUtils.hasText(fieldContext)) throw new IllegalArgumentException("Sensitive field context is required");
        return ("driver-pii:v1:" + employeeId + ":" + fieldContext).getBytes(StandardCharsets.UTF_8);
    }

    private static String normalizedCountry(String country) {
        return StringUtils.hasText(country) ? country.trim().toUpperCase(Locale.ROOT) : "VN";
    }

    private static String normalizeLicenseNumber(String value) {
        return value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static ApiException configurationUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DRIVER_PII_KEY_UNAVAILABLE",
                "Sensitive driver data storage is temporarily unavailable");
    }
}
