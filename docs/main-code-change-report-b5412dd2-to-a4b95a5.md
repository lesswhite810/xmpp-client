# `b5412dd2` 到 `a4b95a5` 主代码变更文档

## 1. 文档说明

本文档描述提交区间 `b5412dd2..a4b95a5` 中 `src/main/java` 主代码的实现变更。

文档目标：

- 归档本阶段主代码调整内容
- 说明每类变更的设计意图和实现结果
- 为后续维护、回归分析和发布说明提供依据

覆盖范围：

- 主代码文件
- 连接生命周期
- 状态机
- 重连与保活
- 管理命令
- TLS / SASL
- 配置与支撑工具类

不覆盖：

- 测试代码逐项说明
- 发布流程和部署说明

---

## 2. 提交范围

本次统计包含以下提交：

- `875535b` `fix: harden xmpp connection lifecycle`
- `f95e74f` `fix: harden xmpp error propagation and lifecycle tests`
- `ca8b498` `fix: preserve xmpp reconnection retries`
- `d9c640b` `refactor: remove system service config adapter`
- `a4b95a5` `fix: harden xmpp lifecycle and reduce over-splitting`

---

## 3. 变更总览

本阶段主代码变更可归纳为五个主题：

1. **连接生命周期增强**
   - 修复连接建立、断开、异常关闭、重连中的资源与状态竞争

2. **错误传播与异步发送增强**
   - 修复发送失败后只能等待超时的问题
   - 让状态机内部关键写操作具备失败感知能力

3. **重连与保活行为收紧**
   - 修复重连任务调度和重置语义
   - 修复 Ping 生命周期与重连复用问题

4. **配置与非核心代码清理**
   - 删除 `SystemService` 配置适配
   - 删除示例代码

5. **结构收口**
   - 对前期修复中引入的部分辅助方法做减法回收
   - 降低过度拆分倾向

---

## 4. 文件范围

本区间主代码涉及以下文件：

- [AbstractXmppConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/AbstractXmppConnection.java)
- [XmppConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/XmppConnection.java)
- [XmppTcpConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/XmppTcpConnection.java)
- [SystemService.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/config/SystemService.java) 已删除
- [XmppClientConfig.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/config/XmppClientConfig.java)
- [XmppConfigKeys.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/config/XmppConfigKeys.java) 已删除
- [MultiConnectionAdminExample.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/example/MultiConnectionAdminExample.java) 已删除
- [ProtocolErrorHandlingExample.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/example/ProtocolErrorHandlingExample.java) 已删除
- [AdminManager.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/logic/AdminManager.java)
- [PingManager.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/logic/PingManager.java)
- [ReconnectionManager.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/logic/ReconnectionManager.java)
- [SaslNegotiator.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/mechanism/SaslNegotiator.java)
- [DnsResolver.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/DnsResolver.java)
- [SslUtils.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/SslUtils.java)
- [XmppNettyHandler.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/XmppNettyHandler.java)
- [StateContext.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/state/StateContext.java)
- [XmppHandlerState.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/state/XmppHandlerState.java)
- [ConnectionUtils.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/util/ConnectionUtils.java)
- [NettyUtils.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/util/NettyUtils.java)

---

## 5. 连接生命周期变更

重点文件：

- [XmppTcpConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/XmppTcpConnection.java)
- [XmppNettyHandler.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/XmppNettyHandler.java)
- [StateContext.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/state/StateContext.java)

### 5.1 `connectAsync()` 生命周期重构

`XmppTcpConnection.connectAsync()` 由原先的“直接初始化后连接”改为分阶段处理：

- 先检查当前 `connectionReadyFuture` 是否可复用
- 清理失败后残留连接资源
- 重置本轮连接生命周期标记
- 解析连接目标
- 初始化 Netty 资源与管理器
- 发起连接
- 在失败时统一回滚

带来的效果：

- 已连接或正在连接时重复调用 `connectAsync()` 不会重复建连
- 失败后重试前会先清理旧生命周期资源
- 空目标、初始化异常、全部目标失败都会走统一失败路径

### 5.2 当前通道归属控制

新增了当前活动通道判断机制，核心方法包括：

- `isCurrentChannel(...)`
- `bindActiveChannel(...)`
- `failConnection(Channel, Exception)`
- `handleChannelInactive(Channel)`

作用：

- 旧通道的迟到事件不会结束当前生命周期
- stale channel 的异常、关闭、stream error、TLS 握手回调都会被忽略

这是本阶段最关键的稳定性增强之一。

### 5.3 手动关闭与异常关闭语义分离

新增字段：

- `disconnectRequested`

现在关闭分为两类：

- **手动断开**
  - 标记 `disconnectRequested`
  - 结束 pending collector
  - 失败未完成的 ready future
  - 清理状态
  - 发布 `CLOSED`

- **异常断开**
  - 结束 pending collector
  - 清理状态
  - 发布 `ERROR`

效果：

- 自动重连只跟随异常关闭
- 手动 `disconnect()` 不会被视为错误恢复场景

### 5.4 远端关闭后的统一收尾

`channelInactive()` 现在会：

- 检查 stale channel
- 检查终态事件是否已发布
- 调用 `connection.handleChannelInactive(...)`
- 统一释放旧 `channel` 和 `workerGroup`

以前存在的风险是：

- 远端关闭后旧资源可能残留
- pending collector 只能等超时
- 新旧连接生命周期可能交错污染

这些在本阶段已被收口。

---

## 6. IQ 发送与 collector 变更

重点文件：

- [AbstractXmppConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/AbstractXmppConnection.java)
- [XmppTcpConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/XmppTcpConnection.java)
- [NettyUtils.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/util/NettyUtils.java)

### 6.1 `sendIqPacketAsync()` 统一发送失败语义

旧行为问题：

- collector 先创建
- 底层写失败后，上层通常只能等超时

新行为：

- 参数校验前置
- IQ 响应 collector 独立创建
- 通过 `dispatchStanza(...)` 获取底层发送结果
- 一旦发送失败，立即异常结束对应 Future
- 一旦连接关闭，pending collector 立即失败

### 6.2 引入 `dispatchStanza(...)`

在 [AbstractXmppConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/AbstractXmppConnection.java) 中新增抽象能力：

- `dispatchStanza(XmppStanza stanza)`

在 [XmppTcpConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/XmppTcpConnection.java) 中实现：

- 检查通道有效性
- 检查 pipeline context
- 调用 `XmppNettyHandler.sendStanza(...)`
- 将 `ChannelFuture` 转为 `CompletableFuture<Void>`

该改动使业务层首次具备“底层写结果感知能力”。

### 6.3 collector 清理策略增强

新增或增强的方法：

- `failPendingCollectors(...)`
- `cleanupCollectors()`
- `failCollector(...)`

变化：

- 连接异常关闭时，未完成 collector 会立即失败
- 已完成 collector 会被统一回收
- collector 不再轻易拖到超时或残留到下一轮连接

### 6.4 unsupported IQ 的标准错误响应

`AbstractXmppConnection.handleIqRequest(...)` 中，如果客户端没有找到匹配处理器：

- 已知命名空间但功能未实现：返回 `feature-not-implemented`
- 未知命名空间：返回 `service-unavailable`

这让客户端对入站 IQ GET/SET 的响应更符合协议预期。

---

## 7. Netty 处理器与状态机变更

重点文件：

- [XmppNettyHandler.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/XmppNettyHandler.java)
- [StateContext.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/state/StateContext.java)
- [XmppHandlerState.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/state/XmppHandlerState.java)

### 7.1 `XmppNettyHandler` 的 stale channel 防护

新增逻辑：

- stale channel 的 `channelInactive`
- stale channel 的 `exceptionCaught`
- stale channel 的入站报文
- stale channel 的 TLS 握手事件

都会被直接忽略。

这避免了旧通道上的迟到事件破坏当前连接状态。

### 7.2 `clearState()` 与 `StateContext.terminate()`

新增机制：

- `XmppNettyHandler.clearState()`
- `StateContext.terminate()`
- `StateContext.isTerminated()`

用途：

- 连接关闭时终止旧上下文
- 旧上下文后续异步回调不会继续推进状态机

适用场景：

- TLS 握手完成回调迟到
- SASL 写成功回调迟到
- initial presence 写成功回调迟到

### 7.3 状态推进改为“写成功后推进”

`XmppHandlerState` 中以下阶段都改为等待写成功：

- 初始 stream 打开
- STARTTLS 请求
- SASL auth stanza
- SASL 成功后的 stream 重开
- bind IQ 发送
- initial presence 发送

实现方式：

- `transitionAfterSuccessfulWrite(...)`
- `failOnWriteFailure(...)`

这解决了原来“写失败但状态已经推进”的问题。

### 7.4 TLS 与 SASL 回调路径收紧

TLS：

- 握手成功后，不直接推进状态
- 先重开 stream
- stream 重开成功后再切换 `AWAITING_FEATURES`

SASL：

- 成功后重新打开 stream
- 写失败立即 fail fast

### 7.5 初始 Presence 与会话 ready 时机修正

以前风险：

- bind 成功后可能先标记 ready，再发 initial presence
- 如果 presence 发送失败，会出现“ready 但实际上没完成期望流程”

现在：

- 只有 initial presence 发送成功，才激活 session
- 如果配置关闭 `sendPresence`，则 bind 成功后直接激活 session

---

## 8. 自动重连与保活变更

重点文件：

- [ReconnectionManager.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/logic/ReconnectionManager.java)
- [PingManager.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/logic/PingManager.java)

### 8.1 重连周期语义修正

以前问题：

- `CONNECTED` 即重置重连计数
- 如果随后在 TLS、SASL、bind 阶段失败，退避会被错误打平

现在：

- `CONNECTED` 只表示 TCP 已建立
- `AUTHENTICATED` 才视为真正恢复
- 只有 `AUTHENTICATED` 才重置重连周期

### 8.2 不可恢复错误不再重连

不可恢复错误包括：

- `XmppAuthException`
- `XmppProtocolException`
- `XmppStanzaErrorException`

行为：

- 收到这些错误后立即停止当前重连周期
- 不再反复重试无意义错误

### 8.3 重连任务调度增强

增强点包括：

- 禁用后，已排队任务不会执行
- 禁用后，运行中的 `connect()` 即使成功也会立即关闭
- 达到最大重连次数后停止重连
- 已连接时，迟到的重连任务会跳过
- 避免并发排多个重连任务

### 8.4 Ping 生命周期修正

以前：

- 收到 `CLOSED/ERROR` 直接 `shutdown()`
- 这会取消订阅
- 同一连接对象重连成功后，ping 不再恢复

现在：

- `CLOSED/ERROR` 只 `stopKeepAlive()`
- 真正销毁时才 `shutdown()`
- `shutdown` 后不会再被重新启动

---

## 9. 管理命令变更

重点文件：

- [AdminManager.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/logic/AdminManager.java)

### 9.1 移除旧的手工 collector / filter 逻辑

旧实现特点：

- 自己构造 `StanzaFilter`
- 自己创建 `AsyncStanzaCollector`
- 依赖特殊的 `id + from` 匹配逻辑

新实现：

- 直接调用 `connection.sendIqPacketAsync(iq, timeout, unit)`

收益：

- 管理命令继承统一错误传播语义
- 底层发送失败时立即失败
- 不再保留 Openfire 特化匹配逻辑

### 9.2 单阶段与两阶段命令模板化

新增模板：

- `executeSinglePhaseCommand(...)`
- `executeTwoPhaseCommand(...)`
- `executeAccountCommand(...)`

影响命令：

- `addUser(...)`
- `deleteUser(...)`
- `changePassword(...)`
- `getUser(...)`
- `listUsers(domains)`

结果：

- 账户型命令复用统一的 JID 构造逻辑
- execute / submit 双阶段命令共用模板
- 代码重复减少，错误处理一致性增强

---

## 10. TLS / SASL 变更

重点文件：

- [SslUtils.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/SslUtils.java)
- [SaslNegotiator.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/mechanism/SaslNegotiator.java)
- [XmppHandlerState.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/state/XmppHandlerState.java)

### 10.1 TLS 主机名校验移除

`SslUtils` 中删除了按 `host/port` 创建 `SSLEngine` 的链路，统一改为：

- `sslContext.createSSLEngine()`

当前含义：

- 不启用 TLS 主机名匹配校验
- 仍保留证书链信任校验

### 10.2 SASL 对 TLS 成功状态判断更严格

`SaslNegotiator.isTlsEncrypted()` 现在要求：

- `SslHandler` 存在
- `handshakeFuture().isSuccess()`

修复效果：

- TLS 握手失败但 future 已结束时，不会再被误判为“加密连接”
- `PLAIN` 机制不会在错误的 TLS 状态下被放行

### 10.3 协议失败日志收紧

很多原来属于 `ERROR` 的场景已调整为 `WARN + DEBUG`：

- 服务端不支持必需 TLS
- SASL 机制缺失
- PLAIN 未走 TLS
- SSL 配置问题
- SASL failure
- bind failure

---

## 11. 配置与项目边界清理

重点文件：

- [XmppClientConfig.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/config/XmppClientConfig.java)
- [SystemService.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/config/SystemService.java) 已删除
- [XmppConfigKeys.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/config/XmppConfigKeys.java) 已删除

### 11.1 删除 `SystemService` 配置适配入口

已删除：

- `XmppClientConfig.fromSystemService(...)`
- `XmppClientConfig.fromSystemService(..., nodeIp)`
- 与之配套的一组系统配置解析辅助方法

影响：

- 当前 `xmpp-client` 项目不再负责外部系统配置装配
- 配置装配职责外移到接入方

### 11.2 删除示例代码

已删除：

- [MultiConnectionAdminExample.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/example/MultiConnectionAdminExample.java)
- [ProtocolErrorHandlingExample.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/example/ProtocolErrorHandlingExample.java)

影响：

- 仓库主代码更聚焦于库实现
- 示例和外部接入逻辑不再作为主代码一部分维护

---

## 12. 工具与支撑模块变更

### 12.1 `NettyUtils`

新增：

- `writeAndFlushStringAsync(...)`

用途：

- 返回 `ChannelFuture`
- 支撑状态机和连接层做写结果驱动处理

### 12.2 `ConnectionUtils`

增强点：

- 中断时保留线程中断标记
- 非中断异常统一包装为 `XmppNetworkException`
- 网络失败日志降为 `WARN + DEBUG`

### 12.3 `DnsResolver`

本阶段仅做低风险收口：

- 查询中断时保留线程中断标记
- 非 `NOERROR` 结果从 `ERROR` 降到 `WARN`
- 关闭事件循环时若被中断，保留线程中断状态

---

## 13. 接口契约与文档对齐

重点文件：

- [XmppConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/XmppConnection.java)

主要调整：

- `disconnect()` 文档改为准确描述异步关闭行为
- `sendStanza()` 文档改为准确描述“不返回发送结果”
- `sendIqPacketAsync(Iq)` 文档明确调用方必须预置非空 ID
- 新增带超时参数的 `sendIqPacketAsync(Iq, long, TimeUnit)` 接口方法

这部分主要修复“接口说明与真实行为不一致”的问题。

---

## 14. 日志策略变更

本阶段对日志做了系统性收口。

主要原则：

- 可预期失败：`WARN`
- 细节堆栈：`DEBUG`
- 真正非预期运行时错误：`ERROR`

影响范围：

- `XmppNettyHandler`
- `XmppHandlerState`
- `ReconnectionManager`
- `SaslNegotiator`
- `ConnectionUtils`
- `DnsResolver`
- `XmppTcpConnection`

具体表现：

- 断链、EOF、I/O 异常不再普遍打 `ERROR`
- 最大重连次数终止不再记 `ERROR`
- 重复终态事件日志降到 `TRACE`
- stale channel / terminated state 的迟到事件只记 `DEBUG`

---

## 15. 结构性收口

在本阶段后半段，对前期修复引入的部分结构拆分做了减法回收，主要目标是：

- 避免单次调用的包装方法过多
- 保留真正有职责边界的方法
- 降低“为拆分而拆分”的痕迹

主要收口点：

- [AbstractXmppConnection.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/AbstractXmppConnection.java)
  - 删除一次性包装 `executeIqRequest(...)`
  - 内联部分 collector 分发路径

- [XmppHandlerState.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/net/state/XmppHandlerState.java)
  - 回收明显一次性包装方法

- [ReconnectionManager.java](/E:/Data/xmpp/src/main/java/com/example/xmpp/logic/ReconnectionManager.java)
  - 回收明显一次性包装方法

结论：

- 本轮结束时仍有结构化辅助方法，但整体已回到可接受范围
- 没有形成明显严重的过度设计

---

## 16. 变更结果总结

从 `b5412dd2` 到 `a4b95a5`，主代码实现层面完成了以下关键提升：

### 16.1 生命周期正确性提升

- 旧通道不会误结束当前生命周期
- 旧状态上下文不会推进当前状态机
- 连接对象复用更可靠
- 断开与异常关闭语义明确

### 16.2 错误传播更及时

- IQ 写失败可以立即失败
- 状态机关键写操作不会再静默失败后继续推进
- 管理命令不再绕过统一发送链路

### 16.3 重连策略更合理

- 只对可恢复错误重连
- 只有 `AUTHENTICATED` 才重置重连周期
- 禁用后的重连任务不会继续留下新会话

### 16.4 项目边界更清晰

- 删除 `SystemService` 适配层
- 删除示例代码
- 主代码聚焦核心 XMPP client 能力

### 16.5 日志更可用

- 可预期失败不再普遍记 `ERROR`
- 调试细节仍可通过 `DEBUG` 获取

---

## 17. 结论

本阶段变更不是单点修补，而是一次围绕连接生命周期稳定性展开的系统性收敛。其主要成果是：

- 让连接对象可重用
- 让旧生命周期不会污染新生命周期
- 让发送失败和协议失败更早、更准确地反馈
- 让重连、保活和管理命令都建立在一致的连接语义之上

从当前代码状态看，这一阶段已经把高风险生命周期问题基本收掉，后续继续演进时应优先遵循以下原则：

- 不再继续扩大结构拆分层级
- 优先做缺陷修复和重复逻辑消除
- 保持当前“错误传播及时、状态推进谨慎、日志分级克制”的设计方向

