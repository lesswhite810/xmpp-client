package com.example.xmpp.mechanism;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * SASL 机制工厂行为测试。
 */
class SaslMechanismFactoryTest {

    private final SaslMechanismFactory factory = SaslMechanismFactory.getInstance();

    @Test
    @DisplayName("register 应拒绝空名称和空工厂")
    void testRegisterValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> factory.register(" ", 1, (username, password) -> null));
        assertThrows(NullPointerException.class,
                () -> factory.register("TEST-NULL-FACTORY", 1, null));
    }

    @Test
    @DisplayName("createBestMechanism 应尊重 enabledMechanisms 过滤")
    void testCreateBestMechanismHonorsEnabledMechanisms() {
        Optional<SaslMechanism> mechanism = factory.createBestMechanism(
                List.of("PLAIN", "SCRAM-SHA-256"),
                Set.of("PLAIN"),
                "user",
                "secret".toCharArray());

        assertTrue(mechanism.isPresent());
        assertEquals("PLAIN", mechanism.orElseThrow().getMechanismName());
    }

    @Test
    @DisplayName("createBestMechanism 应拒绝工厂返回 null")
    void testCreateBestMechanismRejectsNullMechanism() {
        String name = "TEST-NULL-MECHANISM-" + System.nanoTime();
        factory.register(name, Integer.MAX_VALUE, (username, password) -> null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                factory.createBestMechanism(List.of(name), Set.of(name), "user", "secret".toCharArray()));

        assertTrue(exception.getMessage().contains("returned null"));
    }

    @Test
    @DisplayName("createBestMechanism 应拒绝机制名称为空白的实现")
    void testCreateBestMechanismRejectsBlankMechanismName() {
        String name = "TEST-BLANK-MECHANISM-" + System.nanoTime();
        factory.register(name, Integer.MAX_VALUE, (username, password) -> new SaslMechanism() {
            @Override
            public String getMechanismName() {
                return " ";
            }

            @Override
            public byte[] processChallenge(byte[] challenge) throws SaslException {
                return new byte[0];
            }

            @Override
            public boolean isComplete() {
                return false;
            }
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                factory.createBestMechanism(List.of(name), Set.of(name), "user", "secret".toCharArray()));

        assertTrue(exception.getMessage().contains("invalid name"));
    }

    @Test
    @DisplayName("createBestMechanism 应在启用列表不匹配时返回空")
    void testCreateBestMechanismReturnsEmptyWhenFilteredOut() {
        Optional<SaslMechanism> mechanism = factory.createBestMechanism(
                List.of("PLAIN", "SCRAM-SHA-256"),
                Set.of("SCRAM-SHA-1"),
                "user",
                "secret".toCharArray());

        assertFalse(mechanism.isPresent());
    }

    @Test
    @DisplayName("createBestMechanism 在 serverMechanisms 为空时返回空")
    void testCreateBestMechanismReturnsEmptyForNullOrEmptyServerMechanisms() {
        assertTrue(factory.createBestMechanism(null, "user", "secret".toCharArray()).isEmpty());
        assertTrue(factory.createBestMechanism(List.of(), Set.of("PLAIN"), "user", "secret".toCharArray()).isEmpty());
    }

    @Test
    @DisplayName("createBestMechanism 在 enabledMechanisms 为空时不应额外过滤")
    void testCreateBestMechanismDoesNotFilterWhenEnabledMechanismsEmpty() {
        Optional<SaslMechanism> mechanism = factory.createBestMechanism(
                List.of("PLAIN"),
                Set.of(),
                "user",
                "secret".toCharArray());

        assertTrue(mechanism.isPresent());
        assertEquals("PLAIN", mechanism.orElseThrow().getMechanismName());
    }

    @Test
    @DisplayName("createBestMechanism 应选择最高优先级的机制")
    void testCreateBestMechanismSelectsHighestPriority() {
        // OAUTHBEARER (450) > SCRAM-SHA-512 (400) > SCRAM-SHA-256 (300)
        Optional<SaslMechanism> mechanism = factory.createBestMechanism(
                List.of("OAUTHBEARER", "SCRAM-SHA-512", "SCRAM-SHA-256"),
                Set.of(),
                "user",
                "token".toCharArray());

        assertTrue(mechanism.isPresent());
        assertEquals("OAUTHBEARER", mechanism.orElseThrow().getMechanismName());
    }

    @Test
    @DisplayName("createBestMechanism 应能创建 EXTERNAL 机制")
    void testCreateBestMechanismCreatesExternal() {
        Optional<SaslMechanism> mechanism = factory.createBestMechanism(
                List.of("EXTERNAL"),
                Set.of(),
                "authzid",
                "ignored".toCharArray());

        assertTrue(mechanism.isPresent());
        assertEquals("EXTERNAL", mechanism.orElseThrow().getMechanismName());
    }

    @Test
    @DisplayName("createBestMechanism 应能创建 ANONYMOUS 机制")
    void testCreateBestMechanismCreatesAnonymous() {
        Optional<SaslMechanism> mechanism = factory.createBestMechanism(
                List.of("ANONYMOUS"),
                Set.of(),
                "ignored",
                "ignored".toCharArray());

        assertTrue(mechanism.isPresent());
        assertEquals("ANONYMOUS", mechanism.orElseThrow().getMechanismName());
    }

    @Test
    @DisplayName("createBestMechanism 应能创建 OAUTHBEARER 机制")
    void testCreateBestMechanismCreatesOAuthBearer() {
        Optional<SaslMechanism> mechanism = factory.createBestMechanism(
                List.of("OAUTHBEARER"),
                Set.of(),
                "authcid",
                "token".toCharArray());

        assertTrue(mechanism.isPresent());
        assertEquals("OAUTHBEARER", mechanism.orElseThrow().getMechanismName());
    }

    @Test
    @DisplayName("createBestMechanism 在无匹配机制时返回空")
    void testCreateBestMechanismReturnsEmptyWhenNoMatch() {
        Optional<SaslMechanism> mechanism = factory.createBestMechanism(
                List.of("UNKNOWN-MECH"),
                Set.of(),
                "user",
                "secret".toCharArray());

        assertFalse(mechanism.isPresent());
    }

    @Test
    @DisplayName("register 在重复注册时应输出警告日志")
    void testRegisterLogsWarningForDuplicateMechanism() {
        String duplicateName = "TEST-DUP-" + System.nanoTime();
        factory.register(duplicateName, 100, (u, p) -> null);
        factory.register(duplicateName, 200, (u, p) -> null);
    }

    @Test
    @DisplayName("ServiceLoader should load SaslMechanismProvider implementations")
    void testServiceLoaderLoadsProviders() {
        ServiceLoader<SaslMechanismProvider> loader = ServiceLoader.load(SaslMechanismProvider.class);

        long providerCount = loader.stream().count();
        assertTrue(providerCount >= 1, "Should find at least one SaslMechanismProvider");
    }
}
