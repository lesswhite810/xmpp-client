package com.example.xmpp.protocol.model.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link StreamError} 单元测试。
 */
class StreamErrorTest {

    @Test
    @DisplayName("Condition.fromString 传入 null 时应返回 UNDEFINED_CONDITION")
    void testConditionFromStringWithNull() {
        assertEquals(StreamError.Condition.UNDEFINED_CONDITION, StreamError.Condition.fromString(null));
    }
}
