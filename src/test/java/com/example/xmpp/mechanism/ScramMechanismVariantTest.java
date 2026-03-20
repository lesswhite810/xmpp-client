package com.example.xmpp.mechanism;

import com.example.xmpp.util.XmppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCRAM 具体机制包装类测试。
 *
 * @since 2026-03-20
 */
class ScramMechanismVariantTest {

    @Test
    @DisplayName("SCRAM-SHA-1 应暴露正确的机制和算法配置")
    void testScramSha1Configuration() {
        ScramSha1SaslMechanism mechanism = new ScramSha1SaslMechanism("user", "pencil".toCharArray());

        assertEquals(XmppConstants.SASL_MECH_SCRAM_SHA1, mechanism.getMechanismName());
        assertEquals("HmacSHA1", mechanism.getHmacAlgorithm());
        assertEquals("SHA-1", mechanism.getDigestAlgorithm());
        assertEquals("PBKDF2WithHmacSHA1", mechanism.getPBKDF2Algorithm());
        assertEquals(XmppConstants.SHA1_HASH_SIZE_BYTES, mechanism.hashSize());
        assertTrue(mechanism.hasInitialResponse());
        assertFalse(mechanism.isComplete());
    }

    @Test
    @DisplayName("SCRAM-SHA-512 应暴露正确的机制和算法配置")
    void testScramSha512Configuration() {
        ScramSha512SaslMechanism mechanism = new ScramSha512SaslMechanism("user", "pencil".toCharArray());

        assertEquals(XmppConstants.SASL_MECH_SCRAM_SHA512, mechanism.getMechanismName());
        assertEquals("HmacSHA512", mechanism.getHmacAlgorithm());
        assertEquals("SHA-512", mechanism.getDigestAlgorithm());
        assertEquals("PBKDF2WithHmacSHA512", mechanism.getPBKDF2Algorithm());
        assertEquals(XmppConstants.SHA512_HASH_SIZE_BYTES, mechanism.hashSize());
        assertTrue(mechanism.hasInitialResponse());
        assertFalse(mechanism.isComplete());
    }
}
