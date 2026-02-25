# Netty XMPP 客户端

基于 **Netty 4.1** 和 **Woodstox (StAX)** 构建的高性能、异步 Java 21 XMPP 客户端库。

## 功能特性

- **异步 I/O**：使用 Netty 事件循环实现非阻塞网络通信。
- **现代 XML 处理**：使用 Woodstox (StAX) 进行 XML 解析，XmlStringBuilder 进行手动序列化。
- **稳健的连接**：
  - 支持 DNS SRV 记录查询。
  - 支持直接指定主机/端口连接。
  - 自动重连机制（可配置）。
- **安全性**：
  - 支持 StartTLS（必需/尽可能/禁用）。
  - 支持 Direct TLS（端口 5223，直接建立 SSL/TLS 隧道）。
  - SASL 认证支持（SCRAM-SHA-1、SCRAM-SHA-256、SCRAM-SHA-512、PLAIN）。
  - 支持自定义 SSLContext 和 TrustManager。
- **协议支持**：
  - RFC 6120 (核心协议)。
  - 资源绑定 (Resource Binding)。
  - 状态管理 (Presence)。
  - 基于注册表的 Stanza 扩展支持。

## 环境要求

- Java 21 或更高版本。
- Maven 3.8+。

## 快速开始

### 1. 构建项目

```bash
mvn clean package
```

### 2. 基础用法

```java
import com.example.xmpp.config.XmppClientConfig;
import com.example.xmpp.XmppTcpConnection;
import com.example.xmpp.protocol.model.XmppPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Example {
    private static final Logger log = LoggerFactory.getLogger(Example.class);

    public static void main(String[] args) throws Exception {
        // 1. 创建配置
        XmppClientConfig config = new XmppClientConfig.Builder("example.com", "username", "password")
            .host("xmpp.example.com") // 可选：覆盖 DNS SRV 查找，直接指定主机
            .port(5222)
            .securityMode(XmppClientConfig.SecurityMode.ifpossible)
            .sendPresence(true) // 登录后自动发送 Presence
            .build();

        // 2. 初始化连接
        XmppTcpConnection connection = new XmppTcpConnection(config);

        // 3. 添加监听器（可选）
        connection.addConnectionListener(new ConnectionListener() {
            @Override
            public void onAuthenticated(boolean resumed) {
                log.info("Authentication successful!");

                // 发送消息
                XmppPackets.Message msg = new XmppPackets.Message("friend@example.com", "Hello from Netty!");
                connection.sendStanza(msg);
            }

            @Override
            public void onConnectionClosed() {
                log.info("Connection closed.");
            }
        });

        // 4. 连接
        connection.connect();

        // 保持应用程序运行...
    }
}
```

### 3. Direct TLS 模式

```java
// 使用 Direct TLS（无需 STARTTLS 协商，默认端口 5223）
XmppClientConfig config = new XmppClientConfig.Builder("example.com", "username", "password")
    .host("xmpp.example.com")
    // .port(5223)  // 可选：启用 Direct TLS 时默认使用 5223
    .usingDirectTLS(true)  // 启用 Direct TLS 模式，忽略 SecurityMode
    .sendPresence(true)
    .build();

XmppTcpConnection connection = new XmppTcpConnection(config);
connection.connect();
```

## 架构概览

- **Connection**: `XmppTcpConnection` 管理生命周期和 Netty 通道。
- **Handler**: `XmppNettyHandler` 处理 XMPP 状态机（打开流 -> 协商 TLS/Direct TLS -> 认证 -> 绑定资源 -> 会话激活）。
- **Codec**:
  - `XmppStreamDecoder`: 使用 Woodstox (StAX) 将 TCP 字节流解析为 Java 对象。
  - `XmlStringBuilder`: 手动序列化 Java 对象为 XML 字符串。
- **Config**: `XmppClientConfig` 使用构建者模式（Builder Pattern）提供类型安全的配置。
- **SSL**: `SslUtils` 统一管理 SSLContext 构建，支持 StartTLS 和 Direct TLS 两种模式。

## 关键设计模式

- **Stanza 扩展**: 扩展（如 `Bind`、`Ping`）在 `ProviderRegistry` 中注册，通过 XML 命名空间映射到提供者。
- **状态机**: 连接流程由 `XmppHandlerState` 枚举严格管理。
- **SASL 解耦**: 认证逻辑隔离在 `SaslNegotiator` 类中。
- **密封类**: `Stanza` 使用 Java 21 密封类，仅允许 `Iq`、`Message`、`Presence` 继承。

## 许可证

MIT License.
