package com.example.xmpp.mechanism;

import javax.security.sasl.SaslException;

/**
 * Test provider for SaslMechanismProvider SPI.
 */
public final class TestSaslMechanismProvider implements SaslMechanismProvider {

    public TestSaslMechanismProvider() {
    }

    @Override
    public String getMechanismName() {
        return "TEST-PROVIDER";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public SaslMechanism create(String username, char[] password) {
        return new PlainSaslMechanism(username, password);
    }
}
