package com.example.xmpp.net.handler.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmppHandlerState 状态机测试。
 *
 * @since 2026-02-27
 */
class XmppHandlerStateTest {

    @Nested
    @DisplayName("状态转换验证测试")
    class StateTransitionTests {

        @Test
        @DisplayName("INITIAL 只能转换到 CONNECTING")
        void testInitialTransitions() {
            assertTrue(XmppHandlerState.INITIAL.canTransitionTo(XmppHandlerState.CONNECTING));
            assertFalse(XmppHandlerState.INITIAL.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertFalse(XmppHandlerState.INITIAL.canTransitionTo(XmppHandlerState.TLS_NEGOTIATING));
            assertFalse(XmppHandlerState.INITIAL.canTransitionTo(XmppHandlerState.SASL_AUTH));
            assertFalse(XmppHandlerState.INITIAL.canTransitionTo(XmppHandlerState.BINDING));
            assertFalse(XmppHandlerState.INITIAL.canTransitionTo(XmppHandlerState.SESSION_ACTIVE));
        }

        @Test
        @DisplayName("CONNECTING 可以转换到 AWAITING_FEATURES")
        void testConnectingTransitions() {
            assertTrue(XmppHandlerState.CONNECTING.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.CONNECTING.canTransitionTo(XmppHandlerState.CONNECTING));
            assertFalse(XmppHandlerState.CONNECTING.canTransitionTo(XmppHandlerState.TLS_NEGOTIATING));
            assertFalse(XmppHandlerState.CONNECTING.canTransitionTo(XmppHandlerState.SASL_AUTH));
        }

        @Test
        @DisplayName("AWAITING_FEATURES 可以转换到多个状态")
        void testAwaitingFeaturesTransitions() {
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.TLS_NEGOTIATING));
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.SASL_AUTH));
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.BINDING));
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.CONNECTING));
            assertFalse(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.SESSION_ACTIVE));
        }

        @Test
        @DisplayName("TLS_NEGOTIATING 可以转换到 AWAITING_FEATURES")
        void testTlsNegotiatingTransitions() {
            assertTrue(XmppHandlerState.TLS_NEGOTIATING.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.TLS_NEGOTIATING.canTransitionTo(XmppHandlerState.CONNECTING));
            assertFalse(XmppHandlerState.TLS_NEGOTIATING.canTransitionTo(XmppHandlerState.SASL_AUTH));
            assertFalse(XmppHandlerState.TLS_NEGOTIATING.canTransitionTo(XmppHandlerState.BINDING));
        }

        @Test
        @DisplayName("SASL_AUTH 可以转换到 AWAITING_FEATURES")
        void testSaslAuthTransitions() {
            assertTrue(XmppHandlerState.SASL_AUTH.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.SASL_AUTH.canTransitionTo(XmppHandlerState.CONNECTING));
            assertFalse(XmppHandlerState.SASL_AUTH.canTransitionTo(XmppHandlerState.TLS_NEGOTIATING));
            assertFalse(XmppHandlerState.SASL_AUTH.canTransitionTo(XmppHandlerState.BINDING));
        }

        @Test
        @DisplayName("BINDING 可以转换到 SESSION_ACTIVE")
        void testBindingTransitions() {
            assertTrue(XmppHandlerState.BINDING.canTransitionTo(XmppHandlerState.SESSION_ACTIVE));
            assertTrue(XmppHandlerState.BINDING.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.BINDING.canTransitionTo(XmppHandlerState.CONNECTING));
            assertFalse(XmppHandlerState.BINDING.canTransitionTo(XmppHandlerState.SASL_AUTH));
        }

        @Test
        @DisplayName("SESSION_ACTIVE 只能转换到 CONNECTING（重连）")
        void testSessionActiveTransitions() {
            assertTrue(XmppHandlerState.SESSION_ACTIVE.canTransitionTo(XmppHandlerState.CONNECTING));
            assertFalse(XmppHandlerState.SESSION_ACTIVE.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertFalse(XmppHandlerState.SESSION_ACTIVE.canTransitionTo(XmppHandlerState.TLS_NEGOTIATING));
            assertFalse(XmppHandlerState.SESSION_ACTIVE.canTransitionTo(XmppHandlerState.SASL_AUTH));
            assertFalse(XmppHandlerState.SESSION_ACTIVE.canTransitionTo(XmppHandlerState.BINDING));
        }
    }

    @Nested
    @DisplayName("validateTransition 测试")
    class ValidateTransitionTests {

        @Test
        @DisplayName("有效转换不应抛出异常")
        void testValidTransition() {
            assertDoesNotThrow(() -> XmppHandlerState.INITIAL.validateTransition(XmppHandlerState.CONNECTING));
            assertDoesNotThrow(() -> XmppHandlerState.CONNECTING.validateTransition(XmppHandlerState.AWAITING_FEATURES));
            assertDoesNotThrow(() -> XmppHandlerState.AWAITING_FEATURES.validateTransition(XmppHandlerState.TLS_NEGOTIATING));
            assertDoesNotThrow(() -> XmppHandlerState.BINDING.validateTransition(XmppHandlerState.SESSION_ACTIVE));
        }

        @Test
        @DisplayName("无效转换应抛出 IllegalStateException")
        void testInvalidTransition() {
            assertThrows(IllegalStateException.class,
                    () -> XmppHandlerState.INITIAL.validateTransition(XmppHandlerState.SESSION_ACTIVE));
            assertThrows(IllegalStateException.class,
                    () -> XmppHandlerState.SESSION_ACTIVE.validateTransition(XmppHandlerState.SASL_AUTH));
            assertThrows(IllegalStateException.class,
                    () -> XmppHandlerState.TLS_NEGOTIATING.validateTransition(XmppHandlerState.BINDING));
        }

        @Test
        @DisplayName("异常消息应包含源状态和目标状态")
        void testExceptionMessage() {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> XmppHandlerState.INITIAL.validateTransition(XmppHandlerState.SESSION_ACTIVE));

            String message = ex.getMessage();
            assertTrue(message.contains("INITIAL"));
            assertTrue(message.contains("SESSION_ACTIVE"));
        }
    }

    @Nested
    @DisplayName("getName 测试")
    class GetNameTests {

        @Test
        @DisplayName("getName 应返回枚举名称")
        void testGetName() {
            assertEquals("INITIAL", XmppHandlerState.INITIAL.getName());
            assertEquals("CONNECTING", XmppHandlerState.CONNECTING.getName());
            assertEquals("SESSION_ACTIVE", XmppHandlerState.SESSION_ACTIVE.getName());
        }
    }

    @Nested
    @DisplayName("isSessionActive 测试")
    class IsSessionActiveTests {

        @Test
        @DisplayName("只有 SESSION_ACTIVE 状态返回 true")
        void testIsSessionActive() {
            assertFalse(XmppHandlerState.INITIAL.isSessionActive());
            assertFalse(XmppHandlerState.CONNECTING.isSessionActive());
            assertFalse(XmppHandlerState.AWAITING_FEATURES.isSessionActive());
            assertFalse(XmppHandlerState.TLS_NEGOTIATING.isSessionActive());
            assertFalse(XmppHandlerState.SASL_AUTH.isSessionActive());
            assertFalse(XmppHandlerState.BINDING.isSessionActive());
            assertTrue(XmppHandlerState.SESSION_ACTIVE.isSessionActive());
        }
    }

    @Nested
    @DisplayName("完整连接流程测试")
    class ConnectionFlowTests {

        @Test
        @DisplayName("标准 STARTTLS 流程验证")
        void testStandardStartTlsFlow() {
            // INITIAL -> CONNECTING -> AWAITING_FEATURES -> TLS_NEGOTIATING -> AWAITING_FEATURES
            // -> SASL_AUTH -> AWAITING_FEATURES -> BINDING -> SESSION_ACTIVE

            // 验证每一步都可以转换
            assertTrue(XmppHandlerState.INITIAL.canTransitionTo(XmppHandlerState.CONNECTING));
            assertTrue(XmppHandlerState.CONNECTING.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.TLS_NEGOTIATING));
            assertTrue(XmppHandlerState.TLS_NEGOTIATING.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.SASL_AUTH));
            assertTrue(XmppHandlerState.SASL_AUTH.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.BINDING));
            assertTrue(XmppHandlerState.BINDING.canTransitionTo(XmppHandlerState.SESSION_ACTIVE));
        }

        @Test
        @DisplayName("Direct TLS 流程验证")
        void testDirectTlsFlow() {
            // Direct TLS 跳过 TLS_NEGOTIATING
            // INITIAL -> CONNECTING -> AWAITING_FEATURES -> SASL_AUTH -> AWAITING_FEATURES
            // -> BINDING -> SESSION_ACTIVE

            assertTrue(XmppHandlerState.INITIAL.canTransitionTo(XmppHandlerState.CONNECTING));
            assertTrue(XmppHandlerState.CONNECTING.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.SASL_AUTH));
            assertTrue(XmppHandlerState.SASL_AUTH.canTransitionTo(XmppHandlerState.AWAITING_FEATURES));
            assertTrue(XmppHandlerState.AWAITING_FEATURES.canTransitionTo(XmppHandlerState.BINDING));
            assertTrue(XmppHandlerState.BINDING.canTransitionTo(XmppHandlerState.SESSION_ACTIVE));
        }
    }
}
