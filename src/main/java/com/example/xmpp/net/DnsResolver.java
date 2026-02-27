package com.example.xmpp.net;

import com.example.xmpp.util.XmppConstants;
import com.example.xmpp.exception.XmppDnsException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DNS 解析器。
 *
 * <p>使用 Netty DnsNameResolver 查询 XMPP 服务器 SRV 记录。</p>
 *
 * @since 2026-02-09
 */
@Slf4j
public class DnsResolver implements AutoCloseable {

    /** Netty 事件循环组 */
    @NonNull
    private final EventLoopGroup group;

    /** DNS 名称解析器 */
    private final DnsNameResolver resolver;

    /** 是否拥有 EventLoopGroup 所有权 */
    private final boolean ownsGroup;

    /**
     * 使用内部 EventLoopGroup 创建解析器。
     */
    public DnsResolver() {
        this(new NioEventLoopGroup(1), true);
    }

    /**
     * 使用外部 EventLoopGroup 创建解析器。
     *
     * @param group 外部 EventLoopGroup
     */
    public DnsResolver(@NonNull EventLoopGroup group) {
        this(group, false);
    }

    /**
     * 私有构造器。
     *
     * @param group     EventLoopGroup
     * @param ownsGroup 是否拥有所有权
     */
    private DnsResolver(@NonNull EventLoopGroup group, boolean ownsGroup) {
        this.group = group;
        this.ownsGroup = ownsGroup;

        EventLoop eventLoop = group.next();
        this.resolver = new DnsNameResolverBuilder(eventLoop)
                .channelType(NioDatagramChannel.class)
                .build();
    }

    /**
     * 解析 XMPP 服务的 SRV 记录。
     *
     * @param domain XMPP 域名
     *
     * @return 按优先级和权重排序的 SRV 记录列表
     *
     * @throws XmppDnsException DNS 查询失败
     */
    public List<SrvRecord> resolveXmppService(String domain) throws XmppDnsException {
        validateDomain(domain);

        String serviceName = buildServiceName(domain);
        List<SrvRecord> records = new ArrayList<>();

        try {
            AddressedEnvelope<DnsResponse, InetSocketAddress> envelope = executeDnsQuery(serviceName);
            try {
                records = parseSrvRecords(envelope.content(), domain);
            } finally {
                envelope.release();
            }
        } catch (InterruptedException e) {
            throw new XmppDnsException("DNS resolution interrupted for domain: " + domain, e);
        } catch (TimeoutException e) {
            throw new XmppDnsException("DNS resolution timeout for domain: " + domain, e);
        } catch (ExecutionException e) {
            throw handleDnsException(e, domain);
        }

        Collections.sort(records);
        return records;
    }

    /**
     * 验证域名参数。
     *
     * @param domain 域名
     *
     * @throws NullPointerException     如果域名为 null
     * @throws IllegalArgumentException 如果域名为空
     */
    private void validateDomain(String domain) {
        Objects.requireNonNull(domain, "Domain must not be null");
        if (domain.isBlank()) {
            throw new IllegalArgumentException("Domain must not be blank");
        }
    }

    /**
     * 构建 SRV 服务名称。
     *
     * @param domain 域名
     *
     * @return SRV 服务名称
     */
    private String buildServiceName(String domain) {
        return "_xmpp-client._tcp." + domain;
    }

    /**
     * 执行 DNS 查询。
     *
     * @param serviceName SRV 服务名称
     *
     * @return DNS 响应信封
     *
     * @throws InterruptedException 如果线程被中断
     * @throws TimeoutException     如果查询超时
     * @throws ExecutionException   如果查询失败
     */
    private AddressedEnvelope<DnsResponse, InetSocketAddress> executeDnsQuery(String serviceName)
            throws InterruptedException, TimeoutException, ExecutionException {
        DnsQuestion question = new DefaultDnsQuestion(serviceName, DnsRecordType.SRV);
        return resolver.query(question).get(XmppConstants.DNS_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 解析 DNS 响应中的 SRV 记录。
     *
     * @param response DNS 响应
     * @param domain   域名
     *
     * @return SRV 记录列表
     *
     * @throws ExecutionException 如果 DNS 响应码表示错误
     */
    private List<SrvRecord> parseSrvRecords(DnsResponse response, String domain)
            throws ExecutionException {
        DnsResponseCode code = response.code();
        if (code == DnsResponseCode.NXDOMAIN) {
            log.debug("DNS NXDOMAIN for domain: {}", domain);
            return Collections.emptyList();
        }
        if (code != DnsResponseCode.NOERROR) {
            String errorMsg = "DNS query failed with response code: " + code;
            log.error(errorMsg);
            throw new ExecutionException(errorMsg, new IllegalStateException(errorMsg));
        }

        List<SrvRecord> records = new ArrayList<>();
        int count = response.count(DnsSection.ANSWER);

        for (int i = 0; i < count; i++) {
            DnsRecord record = response.recordAt(DnsSection.ANSWER, i);
            parseSrvRecord(record).ifPresent(records::add);
        }

        return records;
    }

    /**
     * 解析单个 SRV 记录。
     *
     * @param record DNS 记录
     *
     * @return 解析后的 SRV 记录（可选）
     */
    private Optional<SrvRecord> parseSrvRecord(DnsRecord record) {
        if (record.type() != DnsRecordType.SRV || !(record instanceof DnsRawRecord raw)) {
            return Optional.empty();
        }

        ByteBuf content = raw.content();
        content.markReaderIndex();

        try {
            int priority = content.readUnsignedShort();
            int weight = content.readUnsignedShort();
            int port = content.readUnsignedShort();
            String target = decodeDomainName(content);
            return Optional.of(new SrvRecord(target, port, priority, weight));
        } finally {
            content.resetReaderIndex();
        }
    }

    /**
     * 处理 DNS 查询异常。
     *
     * @param e      执行异常
     * @param domain 域名
     *
     * @return XmppDnsException 异常
     */
    private XmppDnsException handleDnsException(ExecutionException e, String domain) {
        Throwable cause = e.getCause();

        if (cause instanceof IllegalStateException &&
                cause.getMessage() != null &&
                cause.getMessage().startsWith("DNS query failed")) {
            return new XmppDnsException(cause.getMessage(), cause);
        }
        if (cause instanceof Exception exception) {
            return new XmppDnsException("DNS resolution failed for domain: " + domain, exception);
        }
        return new XmppDnsException("DNS resolution failed for domain: " + domain, e);
    }

    /**
     * 解码 DNS 域名（支持 DNS 压缩指针）。
     *
     * <p>实现 RFC 1035。</p>
     *
     * @param buf        当前缓冲区
     * @param fullPacket 完整数据包缓冲区
     *
     * @return 解码后的域名
     */
    private String decodeDomainName(ByteBuf buf, ByteBuf fullPacket) {
        StringBuilder sb = new StringBuilder();
        int maxIterations = 128;
        int iterations = 0;
        boolean jumped = false;

        while (buf.isReadable() && iterations++ < maxIterations) {
            int len = buf.readUnsignedByte();
            if (len == 0) {
                break;
            }

            if ((len & 0xC0) == 0xC0) {
                if (!buf.isReadable()) {
                    break;
                }
                int secondByte = buf.readUnsignedByte();
                int offset = ((len & 0x3F) << 8) | secondByte;

                jumped = true;

                if (offset >= fullPacket.writerIndex()) {
                    break;
                }
                buf = fullPacket.slice(offset, fullPacket.writerIndex() - offset);
                continue;
            }

            if (sb.length() > 0) {
                sb.append('.');
            }
            for (int i = 0; i < len && buf.isReadable(); i++) {
                sb.append((char) buf.readUnsignedByte());
            }
        }

        return sb.toString();
    }

    /**
     * 解码 DNS 域名（使用自身作为完整报文）。
     *
     * @param buf 缓冲区
     *
     * @return 解码后的域名
     */
    private String decodeDomainName(ByteBuf buf) {
        return decodeDomainName(buf, buf);
    }

    /**
     * 检查是否拥有 EventLoopGroup 所有权。
     *
     * @return 如果拥有所有权返回 true
     */
    public boolean ownsGroup() {
        return ownsGroup;
    }

    /**
     * 释放资源。
     *
     * @see AutoCloseable
     */
    @Override
    public void close() {
        try {
            if (resolver != null) {
                resolver.close();
            }
        } finally {
            if (ownsGroup) {
                try {
                    group.shutdownGracefully(0, XmppConstants.SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).sync();
                } catch (InterruptedException e) {
                    log.warn("Interrupted while shutting down DNS resolver EventLoopGroup");
                }
            } else {
                log.debug("DNS resolver closed (external EventLoopGroup not released)");
            }
        }
    }

    /**
     * 立即释放资源。
     */
    public void shutdownNow() {
        if (ownsGroup) {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            log.debug("DNS resolver EventLoopGroup shutdown immediately");
        }
    }
}
