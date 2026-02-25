# 项目上下文：Netty XMPP Client

## 项目概述
这是一个基于 **Netty 4.1** 和 **Jackson XML** 构建的高性能、异步 Java 21 XMPP 客户端库。

## 主要技术栈
- **语言**: Java 21
- **核心框架**: Netty 4.1 (异步 I/O)
- **XML 处理**: Jackson Dataformat XML (无 Schema 解析)
- **构建工具**: Maven 3.8+

## 核心功能
- **连接管理**: 支持 TCP 连接、DNS SRV 查找、自动重连。
- **安全性**: 支持 StartTLS、Direct TLS (端口 5223)、SASL 认证 (SCRAM-SHA-1, PLAIN, DIGEST-MD5, CRAM-MD5)。
- **协议支持**: RFC 6120 核心协议、资源绑定、Presence、Stanza 扩展。

## 目录结构
- `src/main/java`: 核心源代码
- `src/test/java`: 测试代码
- `pom.xml`: Maven 配置文件

## 关键组件
- `XmppTcpConnection`: 连接管理
- `XmppNettyHandler`: 状态机处理
- `XmppFramingDecoder`: TCP 拆包
- `XmppClientConfig`: 客户端配置

## 当前状态
- 项目处于开发阶段，具备基础连接和消息发送功能。
- 测试覆盖率待确认。
