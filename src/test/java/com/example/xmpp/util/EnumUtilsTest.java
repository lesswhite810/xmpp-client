package com.example.xmpp.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnumUtils} 单元测试。
 */
class EnumUtilsTest {

    @Test
    void testFromStringOrDefaultThrowsWhenDefaultValueIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> EnumUtils.fromStringOrDefault(SampleEnum.class, "missing", null));

        assertEquals("defaultValue", exception.getMessage());
    }

    @Test
    void testFromStringReturnsEmptyWhenValueIsInvalid() {
        Optional<SampleEnum> result = EnumUtils.fromString(SampleEnum.class, "missing");

        assertTrue(result.isEmpty());
    }

    @Test
    void testFromStringThrowsWhenEnumClassIsNull() {
        assertThrows(NullPointerException.class, () -> EnumUtils.fromString(null, "alpha"));
    }

    private enum SampleEnum {
        ALPHA
    }
}
