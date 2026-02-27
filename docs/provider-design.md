# Provider 架构设计

## 当前问题

`XmppStreamDecoder` 中 Provider 使用混乱：
- `parseElement` 顶层解析时尝试用 Provider，但 IQ/Message/Presence 实际用内置解析
- `parseIq` 子元素解析时又用 Provider
- 没有区分 `IqProvider` 和 `ExtensionElementProvider`

## Smack 的设计参考

Smack 将 Provider 分为四种类型：

```
ProviderManager
├── IQ Providers              → IqProvider<IQ>         返回完整 IQ 对象
├── Extension Providers       → ExtensionElementProvider  返回扩展元素
├── Stream Feature Providers  → StreamFeatureProvider
└── Nonza Providers           → NonzaProvider
```

### IqProvider
- 解析 IQ 子元素，返回**完整的 IQ 对象**
- 例如：`VersionIqProvider` 解析 `<iq><query xmlns="jabber:iq:version">...</query></iq>`
- IQ 对象包含 type、id、from、to 和子元素

### ExtensionElementProvider
- 解析扩展元素，返回 **ExtensionElement 对象**
- 例如：`PingProvider` 返回 `Ping` 对象
- 扩展元素可以附加到 IQ、Message、Presence

## 改进方案

### 1. Provider 类型分离

```java
/**
 * 扩展元素 Provider（解析 IQ/Message/Presence 的子元素）
 */
public interface ExtensionElementProvider<T extends ExtensionElement> extends Provider<T> {
}

/**
 * IQ Provider（解析完整 IQ，返回带有子元素的 IQ 对象）
 * 用于子元素本身就是 IQ 语义一部分的情况
 */
public interface IqProvider extends Provider<Iq> {
    // 解析时已包含 IQ 属性（type, id, from, to）
    // 返回带有子元素的完整 IQ
}
```

### 2. 解析流程改进

```
XmppStreamDecoder.parseElement()
├── 顶层元素判断
│   ├── "iq"      → parseIq()      → 内置解析 + ExtensionProvider 解析子元素
│   ├── "message" → parseMessage() → 内置解析 + ExtensionProvider 解析子元素
│   ├── "presence"→ parsePresence()→ 内置解析 + ExtensionProvider 解析子元素
│   └── 其他      → tryParseWithProvider() → 尝试用 Provider 解析
│
└── parseIq 内部
    ├── 解析 IQ 属性 (type, id, from, to)
    └── 循环解析子元素
        └── 使用 ExtensionElementProvider 解析子元素
```

### 3. 注册中心分离

```java
public final class ProviderRegistry {
    // 扩展元素 Provider（用于 IQ/Message/Presence 子元素）
    private final Map<String, ExtensionElementProvider<?>> extensionProviders;

    // 流级别 Provider（用于非 Stanza 元素，如 SASL、TLS）
    private final Map<String, Provider<?>> streamProviders;

    // 获取扩展元素 Provider
    public Optional<ExtensionElementProvider<?>> getExtensionProvider(String element, String namespace);

    // 获取流级别 Provider
    public Optional<Provider<?>> getStreamProvider(String element, String namespace);
}
```

### 4. 使用场景对比

| 场景 | Provider 类型 | 返回类型 | 示例 |
|------|--------------|----------|------|
| IQ 子元素 (ping) | ExtensionElementProvider | Ping | `<ping xmlns="urn:xmpp:ping"/>` |
| IQ 子元素 (bind) | ExtensionElementProvider | Bind | `<bind xmlns="urn:ietf:..."><jid>...</jid></bind>` |
| Message 扩展 | ExtensionElementProvider | Delay, ChatState | `<x xmlns="jabber:x:delay"/>` |
| SASL 元素 | StreamProvider | Auth, Success | `<auth mechanism="PLAIN">...</auth>` |
| TLS 元素 | StreamProvider | StartTls | `<starttls xmlns="..."/>` |

### 5. 代码示例

**扩展元素 Provider（当前已有）：**
```java
public class PingProvider implements ExtensionElementProvider<Ping> {
    @Override
    public Ping parse(XMLEventReader reader) {
        // 跳过结束标签
        XmppEventReader.getElementText(reader);
        return Ping.INSTANCE;
    }
}
```

**IQ 解析时使用：**
```java
private Iq parseIq(XMLEventReader reader, StartElement element) {
    Map<String, String> attrs = XmppEventReader.getAttributes(element);
    Iq.Builder builder = new Iq.Builder(attrs.get("type"));
    builder.id(attrs.get("id")).from(attrs.get("from")).to(attrs.get("to"));

    while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isStartElement()) {
            StartElement start = event.asStartElement();
            String name = start.getName().getLocalPart();
            String ns = start.getName().getNamespaceURI();

            // 使用 ExtensionElementProvider 解析子元素
            Optional<ExtensionElementProvider<?>> provider =
                ProviderRegistry.getInstance().getExtensionProvider(name, ns);
            if (provider.isPresent()) {
                ExtensionElement ext = provider.get().parse(reader);
                builder.childElement(ext);
            }
        }
    }
    return builder.build();
}
```

## 实施步骤

1. 创建 `ExtensionElementProvider` 接口
2. 修改 `ProviderRegistry` 分离扩展 Provider 和流 Provider
3. 修改现有 Provider 实现 `ExtensionElementProvider`
4. 修改 `XmppStreamDecoder` 清晰区分解析流程
5. 添加测试验证

## 是否需要 IqProvider？

**不需要**，原因：
- IQ 的属性（type, id, from, to）是通用的，内置解析即可
- 子元素用 `ExtensionElementProvider` 解析
- Smack 的 `IqProvider` 主要用于 IQ 子元素也是 IQ 类型的情况（较少见）

当前设计已经足够，只需：
1. 明确 Provider 的用途（解析扩展元素）
2. 清理解析流程中的混乱
