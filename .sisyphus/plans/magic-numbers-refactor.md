# 魔法数字优化工作计划

## 任务概述

将代码库中的硬编码魔法数字（Magic Numbers）提取为具名常量，提高代码可读性和可维护性。

## 识别的魔法数字

### 1. 端口相关
- `5222` - 标准 XMPP 端口（出现 10+ 次）
- `5223` - Direct TLS 端口（出现 5+ 次）

### 2. 超时时间
- `30000` - 连接超时（30秒）
- `60000` - 读取超时（60秒）
- `5000` - DNS 查询超时（5秒）
- `10000` - Ping/认证超时（10秒）

### 3. SASL 优先级
- `400` - SCRAM-SHA-512
- `300` - SCRAM-SHA-256
- `200` - SCRAM-SHA-1
- `100` - PLAIN

### 4. 哈希算法大小
- `20` - SHA-1 输出（字节）
- `32` - SHA-256 输出（字节）
- `64` - SHA-512 输出（字节）

### 5. 其他
- `10 * 1024 * 1024` - 最大 XML 帧大小（10MB）
- `2`, `60` - 重连延迟（基础/最大）

## 实施步骤

### TODO 1: 创建常量类
**文件**: `src/main/java/com/example/xmpp/XmppConstants.java`

创建集中式常量类，包含：
- 端口常量（DEFAULT_XMPP_PORT, DIRECT_TLS_PORT）
- 超时时间常量（毫秒和秒）
- SASL 优先级常量
- 哈希大小常量
- 缓冲区大小常量
- XML 命名空间常量

### TODO 2: 更新 XmppClientConfig
**文件**: `src/main/java/com/example/xmpp/config/XmppClientConfig.java`

替换：
- `private int port = 5222;` → 使用 `XmppConstants.DEFAULT_XMPP_PORT`
- `private int connectTimeout = 30000;` → 使用常量
- `private int readTimeout = 60000;` → 使用常量
- `private int reconnectionBaseDelay = 2;` → 使用常量
- `private int reconnectionMaxDelay = 60;` → 使用常量

### TODO 3: 更新 XmppTcpConnection
**文件**: `src/main/java/com/example/xmpp/XmppTcpConnection.java`

替换端口魔法数字：
- 两处 `5223` 和 `5222` 的选择逻辑 → 使用常量
- 注释中的端口说明同步更新

### TODO 4: 更新 SaslMechanismFactory
**文件**: `src/main/java/com/example/xmpp/sasl/SaslMechanismFactory.java`

替换优先级：
- `register("SCRAM-SHA-512", 400, ...)` → 使用 `PRIORITY_SCRAM_SHA512`
- 其他机制同理

### TODO 5: 更新 SCRAM 机制类
**文件**: 
- `ScramSha1SaslMechanism.java` (hashSize: 20)
- `ScramSha256SaslMechanism.java` (hashSize: 32)
- `ScramSha512SaslMechanism.java` (hashSize: 64)

替换 `hashSize()` 返回的硬编码值为常量。

### TODO 6: 更新其他文件
**文件**:
- `PingManager.java` - pingIntervalSeconds (60), orTimeout (10)
- `ReconnectionManager.java` - BASE_DELAY (2), MAX_DELAY (60)
- `XmppFramingDecoder.java` - MAX_FRAME_SIZE (10MB)
- `DnsResolver.java` - shutdown timeout (5秒)

### TODO 7: 验证
- 执行 `mvn clean compile` 确保无编译错误
- 执行 `mvn test` 确保测试通过
- 检查是否有遗漏的魔法数字

## 常量命名规范

采用以下命名约定：
- 端口: `DEFAULT_XMPP_PORT`, `DIRECT_TLS_PORT`
- 超时: `DEFAULT_CONNECT_TIMEOUT_MS`, `DNS_QUERY_TIMEOUT_SECONDS`
- 优先级: `PRIORITY_SCRAM_SHA512`
- 大小: `SHA1_HASH_SIZE_BYTES`, `MAX_XML_FRAME_SIZE_BYTES`

## 影响范围

- **新增文件**: 1 个（XmppConstants.java）
- **修改文件**: 约 10 个
- **风险等级**: 低（纯重构，无逻辑变更）

## 验收标准

- [ ] 所有识别的魔法数字都被提取为常量
- [ ] 代码编译通过
- [ ] 所有现有测试通过
- [ ] 新增常量有完整的中文 JavaDoc 注释
