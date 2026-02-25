package com.example.xmpp.net;

/**
 * DNS SRV 记录实体类。
 *
 * <p>用于表示 DNS SRV 记录解析结果，替代 dnsjava 的 SRVRecord，
 * 并适配 Netty DNS 解析器返回的结果。</p>
 *
 * <p>SRV 记录排序规则：</p>
 * <ul>
 *   <li>优先级（priority）：数值越小优先级越高</li>
 *   <li>权重（weight）：相同优先级时，数值越大被选中概率越高</li>
 * </ul>
 *
 * @param target   目标主机名
 * @param port     服务端口
 * @param priority 优先级（越小越优先）
 * @param weight   权重（相同优先级时，越大越可能被选中）
 *
 * @see DnsResolver
 * @since 2026-02-09
 */
public record SrvRecord(String target, int port, int priority, int weight) implements Comparable<SrvRecord> {

    /**
     * 比较两个 SRV 记录的优先级。
     *
     * <p>首先按优先级升序排列，优先级相同时按权重降序排列。</p>
     *
     * @param o 另一个 SRV 记录
     *
     * @return 比较结果
     */
    @Override
    public int compareTo(SrvRecord o) {
        // 优先级：越小越优先
        int p = Integer.compare(this.priority, o.priority);
        if (p != 0) {
            return p;
        }
        // 权重：越大越优先
        return Integer.compare(o.weight, this.weight);
    }
}
