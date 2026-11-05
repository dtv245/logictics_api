package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;

import com.company.logicstic.config.DriverPiiProperties;
import com.company.logicstic.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DriverPiiCipherTest {
    private static final String KEY_1 = Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
    private static final String KEY_2 = Base64.getEncoder().encodeToString("abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
    private static final String INDEX = Base64.getEncoder().encodeToString("index-key-0123456789012345678901".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptsAtRestAndUsesEmployeeBoundAssociatedData() {
        var cipher = cipher("k1:" + KEY_1, "k1");
        UUID employee = UUID.randomUUID();
        String stored = cipher.encryptLicenseNumber("ab 1234", employee, "vn");

        assertNotEquals("ab 1234", stored);
        assertFalse(stored.contains("1234"));
        assertEquals("ab 1234", cipher.decryptLicenseNumber(stored, employee, "VN"));
        assertThrows(ApiException.class, () -> cipher.decryptLicenseNumber(stored, UUID.randomUUID(), "VN"));
    }

    @Test
    void blindIndexNormalizesCaseAndWhitespaceButIsScopedToDriver() {
        var cipher = cipher("k1:" + KEY_1, "k1");
        UUID employee = UUID.randomUUID();
        assertEquals(cipher.licenseNumberHmac("ab 1234", employee, "vn"),
                cipher.licenseNumberHmac("AB1234", employee, "VN"));
        assertNotEquals(cipher.licenseNumberHmac("AB1234", employee, "VN"),
                cipher.licenseNumberHmac("AB1234", UUID.randomUUID(), "VN"));
    }

    @Test
    void sensitiveIdentifierIndexNormalizesFormattingAndSeparatesIdentifierTypes() {
        var cipher = cipher("k1:" + KEY_1, "k1");
        assertEquals(cipher.sensitiveValueHmac("123-456 789", "driver-national-id"),
                cipher.sensitiveValueHmac("123456789", "driver-national-id"));
        assertNotEquals(cipher.sensitiveValueHmac("123456789", "driver-national-id"),
                cipher.sensitiveValueHmac("123456789", "driver-personal-tax-code"));
    }

    @Test
    void keyRingAllowsEncryptionKeyRotationWithoutLosingOldReadKey() {
        UUID employee = UUID.randomUUID();
        var oldCipher = cipher("k1:" + KEY_1, "k1");
        String oldValue = oldCipher.encryptLicenseNumber("VN1234567", employee, "VN");
        var rotated = cipher("k1:" + KEY_1 + ";k2:" + KEY_2, "k2");
        String rotatedValue = rotated.encryptLicenseNumber(oldValue, employee, "VN");

        assertTrue(rotatedValue.startsWith("DLENC:v1:k2:"));
        assertEquals("VN1234567", rotated.decryptLicenseNumber(rotatedValue, employee, "VN"));
        assertEquals(oldCipher.licenseNumberHmac("VN1234567", employee, "VN"),
                rotated.licenseNumberHmac("VN1234567", employee, "VN"));
    }

    @Test
    void rejectsTamperedCiphertextWithoutReturningOriginalValue() {
        var cipher = cipher("k1:" + KEY_1, "k1");
        UUID employee = UUID.randomUUID();
        String value = cipher.encryptSensitiveValue("FIT", employee, "health-check-conclusion");
        int payloadStart = value.lastIndexOf(':') + 1;
        byte[] payload = Base64.getUrlDecoder().decode(value.substring(payloadStart));
        payload[payload.length - 1] ^= 0x01;
        String tampered = value.substring(0, payloadStart)
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        assertThrows(ApiException.class, () -> cipher.decryptSensitiveValue(tampered, employee, "health-check-conclusion"));
    }

    private static DriverPiiCipher cipher(String keyRing, String activeKey) {
        var properties = new DriverPiiProperties();
        properties.setActiveKeyId(activeKey);
        properties.setEncryptionKeys(keyRing);
        properties.setIndexKey(INDEX);
        return new DriverPiiCipher(properties);
    }
}
