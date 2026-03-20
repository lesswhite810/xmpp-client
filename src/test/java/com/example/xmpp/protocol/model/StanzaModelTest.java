package com.example.xmpp.protocol.model;

import com.example.xmpp.protocol.model.extension.Ping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stanza 模型类全面测试。
 */
class StanzaModelTest {

    @Test
    @DisplayName("Message、Iq、Presence 不应暴露 public 构造器")
    void testStanzaConstructorsAreNotPublic() {
        assertFalse(hasPublicConstructor(Message.class));
        assertFalse(hasPublicConstructor(Iq.class));
        assertFalse(hasPublicConstructor(Presence.class));
    }

    private boolean hasPublicConstructor(Class<?> type) {
        for (var constructor : type.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    @Nested
    @DisplayName("Iq 测试")
    class IqTests {

        @Test
        @DisplayName("Iq 默认类型应为 GET")
        void testIqDefaultType() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();
            assertEquals(Iq.Type.GET, iq.getType());
        }

        @Test
        @DisplayName("Iq 类型为 null 时默认为 GET")
        void testIqTypeNullDefaultsToGet() {
            Iq iq = new Iq.Builder((Iq.Type) null).build();
            assertEquals(Iq.Type.GET, iq.getType());
        }

        @Test
        @DisplayName("Iq 字符串类型构造器和设置器应处理有效值与空值")
        void testIqStringTypeBuilder() {
            Iq valid = new Iq.Builder("result").build();
            Iq invalid = new Iq.Builder("missing").build();
            Iq nullType = new Iq.Builder((String) null).build();
            Iq updated = new Iq.Builder(Iq.Type.GET).type("set").build();

            assertEquals(Iq.Type.RESULT, valid.getType());
            assertEquals(Iq.Type.GET, invalid.getType());
            assertEquals(Iq.Type.GET, nullType.getType());
            assertEquals(Iq.Type.SET, updated.getType());
        }

        @Test
        @DisplayName("Iq.createErrorResponse 应创建错误响应")
        void testCreateErrorResponse() {
            Ping ping = Ping.INSTANCE;
            Iq request = new Iq.Builder(Iq.Type.GET)
                    .id("req-1")
                    .from("user1@domain.com")
                    .to("user2@domain.com")
                    .childElement(ping)
                    .build();
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST).text("Bad request").build();

            Iq errorResponse = Iq.createErrorResponse(request, error);

            assertEquals(Iq.Type.ERROR, errorResponse.getType());
            assertEquals("req-1", errorResponse.getId());
            assertEquals("user1@domain.com", errorResponse.getTo());
            assertEquals("user2@domain.com", errorResponse.getFrom());
            assertSame(ping, errorResponse.getChildElement());
            assertNotNull(errorResponse.getError());
            assertTrue(errorResponse.toXml().contains("<ping xmlns=\"urn:xmpp:ping\"/>"));
        }

        @Test
        @DisplayName("Iq.createResultResponse 应创建结果响应")
        void testCreateResultResponse() {
            Iq request = new Iq.Builder(Iq.Type.GET).id("req-1").from("user1@domain.com").to("user2@domain.com").build();
            Ping ping = Ping.INSTANCE;

            Iq resultResponse = Iq.createResultResponse(request, ping);

            assertEquals(Iq.Type.RESULT, resultResponse.getType());
            assertEquals("req-1", resultResponse.getId());
            assertEquals("user1@domain.com", resultResponse.getTo());
            assertEquals("user2@domain.com", resultResponse.getFrom());
        }

        @Test
        @DisplayName("Iq.createResultResponse 应支持 null 子元素")
        void testCreateResultResponseWithNullChild() {
            Iq request = new Iq.Builder(Iq.Type.GET).id("req-1").build();

            Iq resultResponse = Iq.createResultResponse(request, null);

            assertEquals(Iq.Type.RESULT, resultResponse.getType());
            assertNull(resultResponse.getChildElement());
        }

        @Test
        @DisplayName("Iq.toXml 应生成完整 XML")
        void testIqToXmlFull() {
            Iq iq = new Iq.Builder(Iq.Type.SET)
                    .id("iq-1")
                    .from("user@domain.com")
                    .to("admin@domain.com")
                    .childElement(Ping.INSTANCE)
                    .build();

            String xml = iq.toXml();
            assertTrue(xml.contains("<iq type=\"set\" id=\"iq-1\""));
            assertTrue(xml.contains("from=\"user@domain.com\""));
            assertTrue(xml.contains("to=\"admin@domain.com\""));
            assertTrue(xml.contains("<ping xmlns=\"urn:xmpp:ping\"/>"));
            assertTrue(xml.contains("</iq>"));
        }

        @Test
        @DisplayName("Iq.toXml 应生成基本 XML")
        void testIqToXmlBasic() {
            Iq iq = new Iq.Builder(Iq.Type.GET).id("iq-1").build();

            String xml = iq.toXml();
            assertEquals("<iq type=\"get\" id=\"iq-1\"/>", xml);
        }

        @Test
        @DisplayName("Iq.getChildElementName 应返回子元素名称")
        void testGetChildElementName() {
            Iq iq = new Iq.Builder(Iq.Type.GET).childElement(Ping.INSTANCE).build();
            assertEquals("ping", iq.getChildElementName());
        }

        @Test
        @DisplayName("Iq.getChildElementName 无子元素时返回 null")
        void testGetChildElementNameNull() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();
            assertNull(iq.getChildElementName());
        }

        @Test
        @DisplayName("Iq Builder 默认构建应正常工作")
        void testIqBuilderDefaultConstruction() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();
            assertNotNull(iq);
            assertEquals(Iq.Type.GET, iq.getType());
        }

        @Test
        @DisplayName("Iq 类型 SET 应正确生成 XML")
        void testIqTypeSetToXml() {
            Iq iq = new Iq.Builder(Iq.Type.SET).id("iq-set").build();
            String xml = iq.toXml();
            assertTrue(xml.contains("type=\"set\""));
        }

        @Test
        @DisplayName("Iq 类型 RESULT 应正确生成 XML")
        void testIqTypeResultToXml() {
            Iq iq = new Iq.Builder(Iq.Type.RESULT).id("iq-result").build();
            String xml = iq.toXml();
            assertTrue(xml.contains("type=\"result\""));
        }

        @Test
        @DisplayName("Iq 类型 ERROR 应正确生成 XML")
        void testIqTypeErrorToXml() {
            Iq iq = new Iq.Builder(Iq.Type.ERROR).id("iq-error").build();
            String xml = iq.toXml();
            assertTrue(xml.contains("type=\"error\""));
        }

        @Test
        @DisplayName("Iq.toXml 无 id 属性时不应包含 id")
        void testIqToXmlNoId() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();
            String xml = iq.toXml();
            assertEquals("<iq type=\"get\"/>", xml);
            assertFalse(xml.contains("id=\""));
        }

        @Test
        @DisplayName("Iq.toXml 无 from 属性时不应包含 from")
        void testIqToXmlNoFrom() {
            Iq iq = new Iq.Builder(Iq.Type.GET).id("iq-1").build();
            String xml = iq.toXml();
            assertFalse(xml.contains("from=\""));
        }

        @Test
        @DisplayName("Iq.toXml 无 to 属性时不应包含 to")
        void testIqToXmlNoTo() {
            Iq iq = new Iq.Builder(Iq.Type.GET).id("iq-1").build();
            String xml = iq.toXml();
            assertFalse(xml.contains("to=\""));
        }

        @Test
        @DisplayName("Iq.toXml 有 error 时应包含错误元素")
        void testIqToXmlWithError() {
            XmppError error = new XmppError.Builder(XmppError.Condition.FORBIDDEN).build();
            Iq iq = new Iq.Builder(Iq.Type.ERROR)
                    .id("iq-error")
                    .error(error)
                    .build();
            String xml = iq.toXml();
            assertTrue(xml.contains("<error"));
            assertTrue(xml.contains("forbidden"));
        }

        @Test
        @DisplayName("Iq 扩展元素合并测试 - 只有 childElement")
        void testIqConsolidateChildElementOnly() {
            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .childElement(Ping.INSTANCE)
                    .build();

            assertNotNull(iq.getExtensions());
            assertEquals(1, iq.getExtensions().size());
        }

        @Test
        @DisplayName("Iq 扩展元素合并测试 - childElement + error")
        void testIqConsolidateChildElementAndError() {
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST).build();
            Iq iq = new Iq.Builder(Iq.Type.ERROR)
                    .childElement(Ping.INSTANCE)
                    .error(error)
                    .build();

            assertNotNull(iq.getExtensions());
            assertEquals(2, iq.getExtensions().size());
        }

        @Test
        @DisplayName("Iq.getError 应返回错误")
        void testIqGetError() {
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST).build();
            Iq iq = new Iq.Builder(Iq.Type.ERROR)
                    .error(error)
                    .build();

            assertNotNull(iq.getError());
            assertEquals(XmppError.Condition.BAD_REQUEST, iq.getError().getCondition());
        }

        @Test
        @DisplayName("Iq.getError 无错误时返回 null")
        void testIqGetErrorNull() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();
            assertNull(iq.getError());
        }

        @Test
        @DisplayName("Iq 类型辅助方法应按当前类型返回")
        void testIqTypeHelpers() {
            Iq getIq = new Iq.Builder(Iq.Type.GET).build();
            Iq setIq = new Iq.Builder(Iq.Type.SET).build();
            Iq resultIq = new Iq.Builder(Iq.Type.RESULT).build();
            Iq errorIq = new Iq.Builder(Iq.Type.ERROR).build();

            assertTrue(getIq.isGet());
            assertTrue(setIq.isSet());
            assertTrue(resultIq.isResult());
            assertTrue(errorIq.isError());
        }

        @Test
        @DisplayName("Iq.getChildElementNamespace 应返回子元素命名空间")
        void testGetChildElementNamespace() {
            Iq iq = new Iq.Builder(Iq.Type.GET).childElement(Ping.INSTANCE).build();

            assertEquals("urn:xmpp:ping", iq.getChildElementNamespace());
        }

        @Test
        @DisplayName("Iq.getChildElementNamespace 无子元素时返回 null")
        void testGetChildElementNamespaceNull() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            assertNull(iq.getChildElementNamespace());
        }
    }

    @Nested
    @DisplayName("Message 测试")
    class MessageTests {

        @Test
        @DisplayName("Message 默认类型应为 normal")
        void testMessageDefaultType() {
            Message msg = new Message.Builder(Message.Type.NORMAL).build();
            assertEquals(Message.Type.NORMAL, msg.getType());
        }

        @Test
        @DisplayName("Message 类型为 null 时默认为 normal")
        void testMessageTypeNullDefaultsToNormal() {
            Message msg = new Message.Builder((Message.Type) null).build();
            assertEquals(Message.Type.NORMAL, msg.getType());
        }

        @Test
        @DisplayName("Message.toXml 应生成完整 XML")
        void testMessageToXmlFull() {
            Message msg = new Message.Builder(Message.Type.CHAT)
                    .id("msg-1")
                    .from("user@domain.com")
                    .to("friend@domain.com")
                    .body("Hello!")
                    .subject("Greeting")
                    .build();

            String xml = msg.toXml();
            assertTrue(xml.contains("<message type=\"chat\" id=\"msg-1\""));
            assertTrue(xml.contains("from=\"user@domain.com\""));
            assertTrue(xml.contains("to=\"friend@domain.com\""));
            assertTrue(xml.contains("<body>Hello!</body>"));
            assertTrue(xml.contains("<subject>Greeting</subject>"));
            assertTrue(xml.contains("</message>"));
        }

        @Test
        @DisplayName("Message.toXml 应处理只有 body")
        void testMessageToXmlBodyOnly() {
            Message msg = new Message.Builder(Message.Type.NORMAL)
                    .body("Hello!")
                    .build();

            String xml = msg.toXml();
            assertTrue(xml.contains("<message"));
            assertTrue(xml.contains("<body>Hello!</body>"));
            assertFalse(xml.contains("<subject>"));
        }

        @Test
        @DisplayName("Message.toXml 应处理 thread")
        void testMessageToXmlWithThread() {
            Message msg = new Message.Builder(Message.Type.CHAT)
                    .body("Hello!")
                    .thread("thread-123")
                    .build();

            String xml = msg.toXml();
            assertTrue(xml.contains("<thread>thread-123</thread>"));
        }

        @Test
        @DisplayName("Message.toXml 应处理空 body")
        void testMessageToXmlEmptyBody() {
            Message msg = new Message.Builder(Message.Type.NORMAL).build();

            String xml = msg.toXml();
            assertTrue(xml.contains("<message"));
            assertFalse(xml.contains("<body>"));
        }

        @Test
        @DisplayName("Message.isChat 应返回 true 当类型为 CHAT")
        void testMessageIsChatTrue() {
            Message msg = new Message.Builder(Message.Type.CHAT).build();
            assertTrue(msg.isChat());
            assertFalse(msg.isGroupchat());
            assertFalse(msg.isHeadline());
            assertFalse(msg.isNormal());
            assertFalse(msg.isError());
        }

        @Test
        @DisplayName("Message.isGroupchat 应返回 true 当类型为 GROUPCHAT")
        void testMessageIsGroupchatTrue() {
            Message msg = new Message.Builder(Message.Type.GROUPCHAT).build();
            assertFalse(msg.isChat());
            assertTrue(msg.isGroupchat());
            assertFalse(msg.isHeadline());
            assertFalse(msg.isNormal());
            assertFalse(msg.isError());
        }

        @Test
        @DisplayName("Message.isHeadline 应返回 true 当类型为 HEADLINE")
        void testMessageIsHeadlineTrue() {
            Message msg = new Message.Builder(Message.Type.HEADLINE).build();
            assertFalse(msg.isChat());
            assertFalse(msg.isGroupchat());
            assertTrue(msg.isHeadline());
            assertFalse(msg.isNormal());
            assertFalse(msg.isError());
        }

        @Test
        @DisplayName("Message.isNormal 应返回 true 当类型为 NORMAL")
        void testMessageIsNormalTrue() {
            Message msg = new Message.Builder(Message.Type.NORMAL).build();
            assertFalse(msg.isChat());
            assertFalse(msg.isGroupchat());
            assertFalse(msg.isHeadline());
            assertTrue(msg.isNormal());
            assertFalse(msg.isError());
        }

        @Test
        @DisplayName("Message.isError 应返回 true 当类型为 ERROR")
        void testMessageIsErrorTrue() {
            Message msg = new Message.Builder(Message.Type.ERROR).build();
            assertFalse(msg.isChat());
            assertFalse(msg.isGroupchat());
            assertFalse(msg.isHeadline());
            assertFalse(msg.isNormal());
            assertTrue(msg.isError());
        }

        @Test
        @DisplayName("Message 字符串类型构造器和设置器应支持有效值和默认值")
        void testMessageStringTypeBuilder() {
            Message valid = new Message.Builder("headline").build();
            Message invalid = new Message.Builder("not-real").build();
            Message updated = new Message.Builder().type("groupchat").build();

            assertEquals(Message.Type.HEADLINE, valid.getType());
            assertEquals(Message.Type.NORMAL, invalid.getType());
            assertEquals(Message.Type.GROUPCHAT, updated.getType());
        }

        @Test
        @DisplayName("Message.Type.fromString 应处理命中和未命中")
        void testMessageTypeFromString() {
            assertEquals(Optional.of(Message.Type.CHAT), Message.Type.fromString("chat"));
            assertTrue(Message.Type.fromString("missing").isEmpty());
            assertTrue(Message.Type.fromString(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Presence 测试")
    class PresenceTests {

        @Test
        @DisplayName("Presence 默认类型应为 available")
        void testPresenceDefaultType() {
            Presence presence = new Presence.Builder(Presence.Type.AVAILABLE).build();
            assertEquals(Presence.Type.AVAILABLE, presence.getType());
        }

        @Test
        @DisplayName("Presence 显式传入 null 类型时应抛出异常")
        void testPresenceTypeNullRejected() {
            assertThrows(NullPointerException.class, () -> new Presence.Builder((Presence.Type) null));
        }

        @Test
        @DisplayName("Presence.toXml 应生成完整 XML")
        void testPresenceToXmlFull() {
            Presence presence = new Presence.Builder(Presence.Type.ERROR)
                    .id("pres-1")
                    .from("user@domain.com/resource")
                    .to("friend@domain.com")
                    .show("chat")
                    .status("Online")
                    .priority(5)
                    .build();

            String xml = presence.toXml();
            assertTrue(xml.contains("<presence type=\"error\" id=\"pres-1\""));
            assertTrue(xml.contains("from=\"user@domain.com/resource\""));
            assertTrue(xml.contains("to=\"friend@domain.com\""));
            assertTrue(xml.contains("<show>chat</show>"));
            assertTrue(xml.contains("<status>Online</status>"));
            assertTrue(xml.contains("<priority>5</priority>"));
            assertTrue(xml.contains("</presence>"));
        }

        @Test
        @DisplayName("Presence.toXml 应处理 available 类型（无 type 属性）")
        void testPresenceToXmlAvailableNoType() {
            Presence presence = new Presence.Builder(Presence.Type.AVAILABLE).build();

            String xml = presence.toXml();
            assertTrue(xml.contains("<presence"));
            assertFalse(xml.contains("type="));
        }

        @Test
        @DisplayName("Presence.toXml 应处理 unavailable 类型")
        void testPresenceToXmlUnavailable() {
            Presence presence = new Presence.Builder(Presence.Type.UNAVAILABLE).build();

            String xml = presence.toXml();
            assertTrue(xml.contains("type=\"unavailable\""));
        }

        @Test
        @DisplayName("Presence.toXml 应处理所有 show 值")
        void testPresenceToXmlShowValues() {
            String[] showValues = {"away", "chat", "dnd", "xa", "away", "chat"};
            for (String show : showValues) {
                Presence presence = new Presence.Builder(Presence.Type.AVAILABLE)
                        .show(show)
                        .build();

                String xml = presence.toXml();
                assertTrue(xml.contains("<show>" + show + "</show>"),
                        "Should contain show: " + show);
            }
        }

        @Test
        @DisplayName("Presence.toXml 应处理 null show")
        void testPresenceToXmlNullShow() {
            Presence presence = new Presence.Builder(Presence.Type.AVAILABLE).build();

            String xml = presence.toXml();
            assertFalse(xml.contains("<show>"));
        }

        @Test
        @DisplayName("Presence.toXml 应处理 null status")
        void testPresenceToXmlNullStatus() {
            Presence presence = new Presence.Builder(Presence.Type.AVAILABLE).build();

            String xml = presence.toXml();
            assertFalse(xml.contains("<status>"));
        }

        @Test
        @DisplayName("Presence.toXml 应处理负优先级")
        void testPresenceToXmlNegativePriority() {
            Presence presence = new Presence.Builder(Presence.Type.AVAILABLE)
                    .priority(-1)
                    .build();

            String xml = presence.toXml();
            assertTrue(xml.contains("<priority>-1</priority>"));
        }

        @Test
        @DisplayName("Presence.isAvailable 应返回 true 当类型为 AVAILABLE")
        void testPresenceIsAvailableTrue() {
            Presence presence = new Presence.Builder(Presence.Type.AVAILABLE).build();
            assertTrue(presence.isAvailable());
            assertFalse(presence.isUnavailable());
            assertFalse(presence.isError());
            assertFalse(presence.isSubscribe());
        }

        @Test
        @DisplayName("Presence.isUnavailable 应返回 true 当类型为 UNAVAILABLE")
        void testPresenceIsUnavailableTrue() {
            Presence presence = new Presence.Builder(Presence.Type.UNAVAILABLE).build();
            assertFalse(presence.isAvailable());
            assertTrue(presence.isUnavailable());
            assertFalse(presence.isError());
        }

        @Test
        @DisplayName("Presence.isError 应返回 true 当类型为 ERROR")
        void testPresenceIsErrorTrue() {
            Presence presence = new Presence.Builder(Presence.Type.ERROR).build();
            assertFalse(presence.isAvailable());
            assertTrue(presence.isError());
        }

        @Test
        @DisplayName("Presence.isSubscribe 应返回 true 当类型为 SUBSCRIBE")
        void testPresenceIsSubscribeTrue() {
            Presence presence = new Presence.Builder(Presence.Type.SUBSCRIBE).build();
            assertTrue(presence.isSubscribe());
            assertFalse(presence.isSubscribed());
            assertFalse(presence.isUnsubscribe());
        }

        @Test
        @DisplayName("Presence.isSubscribed 应返回 true 当类型为 SUBSCRIBED")
        void testPresenceIsSubscribedTrue() {
            Presence presence = new Presence.Builder(Presence.Type.SUBSCRIBED).build();
            assertFalse(presence.isSubscribe());
            assertTrue(presence.isSubscribed());
            assertFalse(presence.isUnsubscribe());
        }

        @Test
        @DisplayName("Presence.isUnsubscribe 应返回 true 当类型为 UNSUBSCRIBE")
        void testPresenceIsUnsubscribeTrue() {
            Presence presence = new Presence.Builder(Presence.Type.UNSUBSCRIBE).build();
            assertFalse(presence.isSubscribe());
            assertFalse(presence.isSubscribed());
            assertTrue(presence.isUnsubscribe());
        }

        @Test
        @DisplayName("Presence.isUnsubscribed 应返回 true 当类型为 UNSUBSCRIBED")
        void testPresenceIsUnsubscribedTrue() {
            Presence presence = new Presence.Builder(Presence.Type.UNSUBSCRIBED).build();
            assertFalse(presence.isUnsubscribe());
            assertTrue(presence.isUnsubscribed());
        }

        @Test
        @DisplayName("Presence 字符串类型构造器和设置器应拒绝无效值")
        void testPresenceStringTypeBuilder() {
            Presence valid = new Presence.Builder("subscribe").build();
            Presence updated = new Presence.Builder().type("unavailable").build();

            assertEquals(Presence.Type.SUBSCRIBE, valid.getType());
            assertEquals(Presence.Type.UNAVAILABLE, updated.getType());
            assertThrows(IllegalArgumentException.class, () -> new Presence.Builder("missing"));
            assertThrows(NullPointerException.class, () -> new Presence.Builder((String) null));
            assertThrows(IllegalArgumentException.class, () -> new Presence.Builder().type("missing"));
            assertThrows(NullPointerException.class, () -> new Presence.Builder().type((String) null));
            assertThrows(NullPointerException.class, () -> new Presence.Builder().type((Presence.Type) null));
        }

        @Test
        @DisplayName("Presence.getPresenceShow 应返回解析后的枚举")
        void testGetPresenceShow() {
            Presence valid = new Presence.Builder().show("chat").build();
            Presence invalid = new Presence.Builder().show("unknown").build();

            assertEquals(Optional.of(Presence.Show.CHAT), valid.getPresenceShow());
            assertTrue(invalid.getPresenceShow().isEmpty());
        }

        @Test
        @DisplayName("Presence.Type 和 Show 的 fromString 应处理未命中")
        void testPresenceEnumFromString() {
            assertEquals(Optional.of(Presence.Type.ERROR), Presence.Type.fromString("error"));
            assertTrue(Presence.Type.fromString("missing").isEmpty());
            assertTrue(Presence.Show.fromString("missing").isEmpty());
            assertEquals(Optional.of(Presence.Show.DND), Presence.Show.fromString("dnd"));
        }
    }

    @Nested
    @DisplayName("Stanza 扩展元素测试")
    class StanzaExtensionTests {

        @Test
        @DisplayName("Stanza.getExtension 应返回正确类型的扩展")
        void testGetExtensionByClass() {
            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .childElement(Ping.INSTANCE)
                    .build();

            Optional<Ping> ping = iq.getExtension(Ping.class);
            assertTrue(ping.isPresent());
            assertEquals("urn:xmpp:ping", ping.get().getNamespace());
        }

        @Test
        @DisplayName("Stanza.getExtension 不存在的类型应返回空")
        void testGetExtensionNotFound() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            Optional<Ping> ping = iq.getExtension(Ping.class);
            assertTrue(ping.isEmpty());
        }

        @Test
        @DisplayName("Stanza.getExtension 应支持命名空间查询")
        void testGetExtensionByNamespace() {
            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .childElement(Ping.INSTANCE)
                    .build();

            Optional<ExtensionElement> ext = iq.getExtension("urn:xmpp:ping");
            assertTrue(ext.isPresent());
        }

        @Test
        @DisplayName("Stanza.getExtension 命名空间不存在应返回空")
        void testGetExtensionNamespaceNotFound() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            Optional<ExtensionElement> ext = iq.getExtension("urn:xmpp:ping");
            assertTrue(ext.isEmpty());
        }

        @Test
        @DisplayName("Stanza.getExtension 传入 null 命名空间应抛出异常")
        void testGetExtensionNullNamespaceThrows() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            assertThrows(NullPointerException.class, () -> iq.getExtension((String) null));
        }

        @Test
        @DisplayName("Stanza.getExtension 应支持名称和命名空间查询")
        void testGetExtensionByNameAndNamespace() {
            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .childElement(Ping.INSTANCE)
                    .build();

            Optional<ExtensionElement> ext = iq.getExtension("ping", "urn:xmpp:ping");
            assertTrue(ext.isPresent());
        }

        @Test
        @DisplayName("Stanza.getExtensions 应返回所有扩展")
        void testGetExtensions() {
            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .childElement(Ping.INSTANCE)
                    .build();

            List<ExtensionElement> extensions = iq.getExtensions();
            assertNotNull(extensions);
            assertEquals(1, extensions.size());
        }

        @Test
        @DisplayName("Stanza 无扩展时 getExtensions 返回空列表")
        void testGetExtensionsEmpty() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            List<ExtensionElement> extensions = iq.getExtensions();
            assertNotNull(extensions);
            assertTrue(extensions.isEmpty());
        }

        @Test
        @DisplayName("Stanza.hasExtension 应返回 true 当存在对应类型")
        void testHasExtensionTrue() {
            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .childElement(Ping.INSTANCE)
                    .build();

            assertTrue(iq.hasExtension(Ping.class));
        }

        @Test
        @DisplayName("Stanza.hasExtension 应返回 false 当不存在对应类型")
        void testHasExtensionFalse() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            assertFalse(iq.hasExtension(Ping.class));
        }

        @Test
        @DisplayName("Stanza.getExtension 名称和命名空间不匹配应返回空")
        void testGetExtensionNameNamespaceNotFound() {
            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .childElement(Ping.INSTANCE)
                    .build();

            Optional<ExtensionElement> ext = iq.getExtension("wrong", "urn:xmpp:ping");
            assertTrue(ext.isEmpty());
        }

        @Test
        @DisplayName("Stanza.getExtension 名称匹配但命名空间不匹配应返回空")
        void testGetExtensionNameMatchNamespaceMismatch() {
            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .childElement(Ping.INSTANCE)
                    .build();

            Optional<ExtensionElement> ext = iq.getExtension("ping", "wrong:namespace");
            assertTrue(ext.isEmpty());
        }

        @Test
        @DisplayName("Stanza.getExtension 传入 null 名称应抛出异常")
        void testGetExtensionNullNameThrows() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            assertThrows(NullPointerException.class, () -> iq.getExtension(null, "ns"));
        }

        @Test
        @DisplayName("Stanza.getExtension 传入 null 命名空间应抛出异常")
        void testGetExtensionNullNamespaceInNameNsThrows() {
            Iq iq = new Iq.Builder(Iq.Type.GET).build();

            assertThrows(NullPointerException.class, () -> iq.getExtension("name", null));
        }

        @Test
        @DisplayName("Stanza 多个扩展元素应能正确获取")
        void testGetExtensionMultipleExtensions() {
            XmppError error = new XmppError.Builder(XmppError.Condition.BAD_REQUEST).build();
            Iq iq = new Iq.Builder(Iq.Type.ERROR)
                    .childElement(Ping.INSTANCE)
                    .error(error)
                    .build();

            Optional<Ping> ping = iq.getExtension(Ping.class);
            Optional<XmppError> err = iq.getExtension(XmppError.class);

            assertTrue(ping.isPresent());
            assertTrue(err.isPresent());
        }

        @Test
        @DisplayName("Stanza.Builder 应忽略 null 扩展并保持扩展列表不可变")
        void testStanzaBuilderIgnoresNullExtensionsAndCopiesInput() {
            List<ExtensionElement> source = new ArrayList<>();
            source.add(Ping.INSTANCE);

            Iq iq = new Iq.Builder(Iq.Type.GET)
                    .addExtension(null)
                    .addExtensions(null)
                    .addExtensions(source)
                    .build();

            source.add(new XmppError.Builder(XmppError.Condition.BAD_REQUEST).build());

            assertEquals(1, iq.getExtensions().size());
            assertSame(Ping.INSTANCE, iq.getExtensions().get(0));
            assertThrows(UnsupportedOperationException.class, () -> iq.getExtensions().add(Ping.INSTANCE));
        }
    }

    @Nested
    @DisplayName("Iq.Type 枚举测试")
    class IqTypeEnumTests {

        @Test
        @DisplayName("Iq.Type 枚举应有 4 个值")
        void testIqTypeCount() {
            assertEquals(4, Iq.Type.values().length);
        }

        @Test
        @DisplayName("Iq.Type.toString 应返回小写")
        void testIqTypeToString() {
            assertEquals("get", Iq.Type.GET.toString());
            assertEquals("set", Iq.Type.SET.toString());
            assertEquals("result", Iq.Type.RESULT.toString());
            assertEquals("error", Iq.Type.ERROR.toString());
        }

        @Test
        @DisplayName("Iq.Type.fromString 应处理有效值和非法值")
        void testIqTypeFromString() {
            assertEquals(Iq.Type.GET, Iq.Type.fromString("get"));
            assertEquals(Iq.Type.ERROR, Iq.Type.fromString("ERROR"));
            assertThrows(IllegalArgumentException.class, () -> Iq.Type.fromString(null));
            assertThrows(IllegalArgumentException.class, () -> Iq.Type.fromString("missing"));
        }
    }

    @Nested
    @DisplayName("Message.Type 枚举测试")
    class MessageTypeEnumTests {

        @Test
        @DisplayName("Message.Type 枚举应有 5 个值")
        void testMessageTypeCount() {
            assertEquals(5, Message.Type.values().length);
        }
    }

    @Nested
    @DisplayName("Presence.Type 枚举测试")
    class PresenceTypeEnumTests {

        @Test
        @DisplayName("Presence.Type 枚举应有 7 个值")
        void testPresenceTypeCount() {
            assertEquals(7, Presence.Type.values().length);
        }

        @Test
        @DisplayName("Presence.Show 枚举应有 4 个值")
        void testPresenceShowCount() {
            assertEquals(4, Presence.Show.values().length);
        }
    }
}
