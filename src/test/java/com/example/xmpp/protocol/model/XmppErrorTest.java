package com.example.xmpp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link XmppError} 单元测试。
 */
class XmppErrorTest {

    @Test
    @DisplayName("Condition.fromString 传入 null 时应返回 undefined_condition")
    void testConditionFromStringWithNull() {
        assertEquals(XmppError.Condition.UNDEFINED_CONDITION, XmppError.Condition.fromString(null));
    }
}
