package com.example.xmpp.util;

import java.util.UUID;

/**
 * XMPP 节 ID 生成器。
 *
 * @since 2026-02-09
 */
public final class StanzaIdGenerator {

    /**
     * 创建节 ID 生成器。
     */
    private StanzaIdGenerator() {
    }

    /**
     * 生成一个新的 UUID ID。
     *
     * @return UUID 字符串
     */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成带前缀的唯一 ID。
     *
     * @param prefix ID 前缀
     * @return 唯一 ID
     */
    public static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
