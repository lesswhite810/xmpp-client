# CLAUDE.md

> 本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

基于 **Netty 4.1** 构建的高性能、异步 Java 21 XMPP 客户端库。使用 Woodstox (StAX) 进行 XML 解析，通过 `XmlStringBuilder` 进行手动序列化。

## 构建命令

```bash
# 编译（带 -parameters 标志以支持参数名反射）
mvn clean compile

# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassNameTest

# 运行单个测试方法
mvn test -Dtest=ClassNameTest#methodName

# 打包（生成 JAR）
mvn clean package

# 跳过测试构建
mvn clean compile -DskipTests
```

## 代码注释

代码库包含中英文混合注释。添加新代码时，请保持与所编辑文件现有风格一致。

## 架构

### 包结构
```
com.example.xmpp
├── config/           # XmppClientConfig（构建者模式）
├── exception/        # 自定义异常（XmppException、XmppAuthException 等）
├── logic/            # 管理器（PingManager、ConnectionRequestManager）
├── net/              # Netty 处理器（XmppNettyHandler、XmppStreamDecoder、DnsResolver、SslUtils）
├── protocol/
│   ├── model/        # 密封类节（Iq、Message、Presence）及扩展
│   │   ├── extension/   # 节扩展（Bind、Ping、ConnectionRequest）
│   │   ├── sasl/        # SASL 协议元素（Auth、Challenge、Success、Failure）
│   │   └── stream/      # 流元素（Features、StreamError、StartTls）
│   ├── provider/     # 扩展提供者（PingProvider、BindProvider）
│   └── ProviderRegistry.java  # 命名空间到提供者的映射
├── sasl/             # SASL 机制（SCRAM-SHA-*、PLAIN）
├── util/             # XmlStringBuilder、StanzaStreamParser、SecurityUtils
├── XmppTcpConnection.java     # 主连接类
├── XmppConnection.java        # 接口
└── AbstractXmppConnection.java
```

### 连接状态机（XmppNettyHandler）
```
CONNECTING → AWAITING_FEATURES → TLS_NEGOTIATING → AWAITING_FEATURES → SASL_AUTH
    → AWAITING_FEATURES → BINDING → SESSION_ACTIVE
```

关键状态：
- `AWAITING_FEATURES`：在多个阶段使用（TLS 前、TLS 后、SASL 后）
- `TLS_NEGOTIATING`：涵盖 STARTTLS 协商和 SSL 握手
- Direct TLS 模式：跳过初始流打开，先等待 SSL 握手

**双层状态架构：**
- `XmppStateMachine.State`（高层）：DISCONNECTED → CONNECTING → CONNECTED → AUTHENTICATING → AUTHENTICATED → SESSION_ACTIVE
- `StateEnum`（底层处理器）：如上所示的详细 Netty 处理器状态

### 关键模式
- **密封类层次结构**：`Stanza` 是密封类，仅允许 `Iq`、`Message`、`Presence` 继承
- **构建者模式**：用于 `XmppClientConfig` 和节的构建
- **提供者注册表**：将 XML 命名空间映射到提供者以进行扩展解析
- **监听器模式**：`ConnectionListener` 用于连接生命周期事件
- **自动 Ping 响应**：服务器 ping 请求（XEP-0199）在 `XmppNettyHandler` 中自动处理

## 重要约定

### 日志规范
使用 SLF4J 配合 Log4j2 后端。两种模式均可接受：
```java
// 推荐 - Lombok @Slf4j 注解
@Slf4j
public class MyClass {
    public void doSomething() {
        log.debug("处理中: {}", id);
    }
}

// 也可接受 - 传统 LoggerFactory
public class MyClass {
    private static final Logger log = LoggerFactory.getLogger(MyClass.class);
}
```

### XML 处理
使用 `XmlStringBuilder` 进行序列化（不使用 Jackson）：
```java
public void toXml(XmlStringBuilder xml) {
    xml.element("iq").attribute("type", type.name()).attribute("id", id);
    if (childElement != null) childElement.toXml(xml);
    xml.closeElement();
}
```

### Java 21 特性
- instanceof 模式匹配：`if (packet instanceof Iq iq) { handleIq(iq); }`
- 箭头语法 switch 表达式
- 节层次结构使用密封类

### 连接流程
1. 如果配置了 `hostAddress` → 直接连接
2. 否则如果配置了 `host` → 连接到指定主机
3. 否则 → DNS SRV 查询，尝试所有返回的服务器
4. 回退 → 连接到 `xmppServiceDomain:5222`

### TLS 模式
- **STARTTLS**（默认，端口 5222）：明文连接，通过协议协商升级为 TLS
- **Direct TLS**（端口 5223）：在 XMPP 流之前立即建立 SSL/TLS 隧道
  - 通过 `XmppClientConfig.Builder.usingDirectTLS(true)` 启用

## 测试覆盖

JaCoCo 强制要求 **80% 最低**行和分支覆盖率。测试使用 JUnit 5 + Mockito。

## 依赖项

| 依赖 | 版本 | 用途 |
|------|------|------|
| Netty | 4.1.106 | 异步网络 |
| Woodstox | 6.5.1 | StAX XML 解析 |
| stax2-api | 4.2.1 | StAX API |
| Lombok | 1.18.34 | 样板代码（scope: provided） |
| Log4j2 | 2.24.3 | 日志（通过 SLF4J 桥接） |
| JUnit Jupiter | 5.10.1 | 测试 |
| Mockito | 5.8.0 | Mock（测试范围） |

## 详细指南

参见 [AGENTS.md](AGENTS.md) 获取完整编码指南，包括：
- 代码格式标准（4 空格缩进，120 字符行宽限制）
- 导入顺序
- 命名规范
- 错误处理模式
- 并发模式
- 测试指南
