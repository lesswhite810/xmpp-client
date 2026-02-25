# AGENTS.md - XMPP 客户端编码指南

> 本文件帮助 AI 编码代理在此 Java/Maven 代码库中高效工作。
>
> **基础规范**: 遵循《阿里巴巴 Java 开发手册》

## 项目概述

- **语言**: Java 21 (sealed classes, pattern matching for instanceof, switch expressions)
- **构建工具**: Maven 3.8+
- **框架**: Netty 4.1.106 (异步网络)
- **XML 处理**: 手动序列化 `XmlStringBuilder` / Woodstox (StAX) 解析
- **工具库**: Lombok (样板代码), SLF4J + Log4j2 (日志)
- **测试框架**: JUnit 5 (Jupiter)

## Build Commands

```bash
mvn clean compile                    # 编译
mvn test                             # 运行所有测试
mvn test -Dtest=ClassNameTest        # 运行单个测试类
mvn test -Dtest=ClassNameTest#method # 运行单个测试方法
mvn clean package -DskipTests        # 打包（跳过测试）
```

## Code Style

### Formatting (阿里规范)
- 4 空格缩进，120 字符行宽
- K&R 大括号风格，方法间空一行
- 禁止通配符导入，禁止 `@SuppressWarnings`
- 左大括号前不换行，左大括号后换行

### Import Ordering (阿里规范)
```java
// 1. java.* / javax.*
import java.util.List;
import javax.net.ssl.SSLContext;

// 2. 第三方库 (io.netty, org.slf4j, lombok, org.apache.commons)
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

// 3. 项目导入
import com.example.xmpp.config.XmppClientConfig;
```

### Naming Conventions (阿里规范)
- **类**: UpperCamelCase (`XmppConnection`, `StanzaStreamParser`)
- **方法/变量**: lowerCamelCase (`sendStanza()`, `channel`)
- **常量**: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`, `DEFAULT_XMPP_PORT`)
- **包名**: 全小写 (`com.example.xmpp.protocol.model`)
- **测试类**: `ClassNameTest` (e.g., `XmlStanzaTest`)

### 日志规范 (IMPORTANT)

**使用 Lombok @Slf4j 注解：**

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyClass {
    
    public void doSomething() {
        log.debug("Processing stanza: {}", stanzaId);
        log.error("Connection failed: {}", reason, exception);
    }
}
```

**阿里规范 - 日志级别：**
- `TRACE`: 非常详细的程序运行信息
- `DEBUG`: 调试信息，生产环境关闭
- `INFO`: 重要的业务流程信息
- `WARN`: 警告信息，不影响程序运行
- `ERROR`: 错误信息，需要人工介入

### Code Comments (IMPORTANT - 阿里规范)

**非 private 类和 public 方法必须有 Javadoc 注释，描述与参数之间空一行：**

```java
/**
 * 基于 TCP 的 XMPP 连接实现。
 * 
 * <p>提供异步连接、认证、消息收发等功能。</p>
 *
 * @since 2026-02-09
 */
public class XmppTcpConnection {
    
    /**
     * 发送节。
     *
     * @param stanza 要发送的节
     * @throws IllegalArgumentException 如果 stanza 为 null
     */
    public void sendStanza(XmppStanza stanza) {
        // ...
    }
    
    /**
     * 获取连接状态。
     *
     * @return 连接是否活跃
     */
    public boolean isConnected() {
        // ...
    }
}
```

**注释规范要点：**
- 类/接口注释：描述功能，包含 `@since`、`@author`（如适用）
- 方法注释：描述与参数/返回值/异常之间空一行
- 行内注释：使用 `//`，与代码间空一格
- 代码分区：使用 `// --- Title ---` 格式

### Lombok Usage

```java
@Getter
@RequiredArgsConstructor
@Slf4j
public class XmppClientConfig {
    private final String xmppServiceDomain;
    private final String username;
}
```

常用注解：`@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`, `@Builder`

### Java 21 Features (USE THESE)

**Sealed Classes** - Stanza 层次结构：
```java
public abstract sealed class Stanza implements XmppStanza 
    permits Iq, Message, Presence { }
```

**Pattern Matching**：
```java
if (packet instanceof Iq iq) {
    handleIq(iq);
}
```

**Switch Expressions**：
```java
return switch (type.toLowerCase()) {
    case "get" -> Type.get;
    case "set" -> Type.set;
    default -> null;
};
```

### Types & Language Features
- `var` 用于类型明确的局部变量
- `Optional<T>` 用于可空返回值
- `CompletableFuture<T>` 用于异步操作
- `List.copyOf()` / `List.of()` 用于不可变集合

### XML Handling (CRITICAL)

**使用 XmlStringBuilder 序列化，不使用 Jackson：**

```java
@Override
public String toXml() {
    XmlStringBuilder xml = new XmlStringBuilder();
    xml.element("iq")
       .attribute("type", type.name())
       .attribute("id", id)
       .rightAngleBracket();
    if (childElement != null) {
        xml.append(childElement.toXml());
    }
    xml.closeElement("iq");
    return xml.toString();
}
```

**使用 StAX (Woodstox) 解析：** `StanzaStreamParser` 解析 XML 字符串为 Java 对象

### Error Handling (阿里规范)
- 使用 `com.example.xmpp.exception` 中的自定义异常
- 日志记录带上下文：`log.error("Context: {}", detail, exception)`
- 禁止静默吞掉异常
- 禁止使用 `e.printStackTrace()`，必须使用日志

### Concurrency
- 线程安全集合：`ConcurrentHashMap`, `CopyOnWriteArraySet`, `ConcurrentLinkedQueue`
- Netty `EventLoop` 用于通道操作
- `synchronized` 或 `ReentrantLock` 用于简单状态保护

## Architecture

### Package Structure
```
com.example.xmpp
├── config/           # 配置 (Builder pattern)
├── exception/        # 自定义异常
├── net/              # Netty handlers, DNS resolver, SSL utils
├── protocol/
│   ├── model/        # Sealed class stanzas (Iq, Message, Presence)
│   │   └── extension/  # XMPP 扩展 (Bind, Ping)
│   ├── provider/     # Extension providers
│   └── ProviderRegistry.java
├── sasl/             # SASL 认证机制
├── util/             # 工具类 (XmlStringBuilder, StanzaStreamParser)
└── XmppTcpConnection.java  # 主连接类
```

### Key Patterns

1. **Builder Pattern**: Config 和 Stanzas 广泛使用
2. **Sealed Class Hierarchy**: `Stanza` 只允许 `Iq`, `Message`, `Presence` 继承
3. **State Machine**: `XmppHandlerState` 枚举管理连接状态
4. **Provider/Registry Pattern**: XML 命名空间映射到 Java providers

## Testing

```java
@Test
public void testIqCreationAndSerialization() {
    // Given
    Iq iq = new Iq.Builder("get")
            .id("test-123")
            .from("user@example.com/resource")
            .build();

    // When
    String xmlString = iq.toXml();

    // Then
    assertNotNull(xmlString);
    assertTrue(xmlString.contains("<iq"));
}
```

- JUnit 5: `@Test`, `@BeforeEach`, `@DisplayName`
- Assertions: `assertEquals()`, `assertNotNull()`, `assertThrows()`
- Given-When-Then 结构

## 依赖项

| 依赖 | 版本 | 用途 |
|------|------|------|
| Netty | 4.1.106.Final | 异步网络 |
| Woodstox | 6.5.1 | XML 解析 (StAX) |
| Lombok | 1.18.34 | 样板代码简化 |
| Log4j2 | 2.24.3 | 日志实现 |
| JUnit Jupiter | 5.10.1 | 测试框架 |
| Mockito | 5.8.0 | Mock 测试 |

## Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| XML with Jackson | 使用 `XmlStringBuilder` 序列化，StAX 解析 |
| Netty ByteBuf 泄漏 | 如果未传递给下一个 handler，必须释放 ByteBuf |
| SSL Handshake 时序 | 通过 `userEventTriggered()` 处理，非 `channelRead()` |
| 可变集合 | 使用 `List.copyOf()` 进行防御性复制 |
| e.printStackTrace() | 禁止使用，必须用日志 `log.error("msg", e)` |

## 阿里规范要点

- 常量命名全大写，下划线分隔
- POJO 类属性使用包装类型（Integer 而非 int）
- 所有覆写方法必须加 `@Override`
- 集合判空使用 `CollectionUtils.isEmpty()`
- 禁止在循环中拼接字符串，使用 `StringBuilder`
- 禁止使用已废弃的 API
