package com.example.xmpp.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

/**
 * 枚举工具类。
 *
 * <p>提供枚举类型的通用解析方法。</p>
 *
 * @since 2026-02-28
 */
@Slf4j
public final class EnumUtils {

    private EnumUtils() {
    }

    /**
     * 从字符串解析枚举值（不区分大小写）。
     *
     * @param <T> 枚举类型
     * @param enumClass 枚举类
     * @param value 字符串值
     * @return 解析后的枚举值的 Optional，无效则返回 Optional.empty()
     */
    public static <T extends Enum<T>> Optional<T> fromString(Class<T> enumClass, String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumClass, value.toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse enum value '{}' for class {}: {}", value, enumClass.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从字符串解析枚举值，无效时返回默认值。
     *
     * @param <T> 枚举类型
     * @param enumClass 枚举类
     * @param value 字符串值
     * @param defaultValue 默认值
     * @return 解析后的枚举值，无效则返回默认值
     */
    public static <T extends Enum<T>> T fromStringOrDefault(Class<T> enumClass, String value, T defaultValue) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        return fromString(enumClass, value).orElse(defaultValue);
    }
}
