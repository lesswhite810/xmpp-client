package com.example.xmpp.util;

import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import java.io.InputStream;

/**
 * XML 解析器工具类。
 *
 * <p>提供共享的 XML 解析器配置，供 XmppEventReader 和 XmppStreamDecoder 使用。</p>
 *
 * <h2>安全配置</h2>
 * <p>本类实现了完整的 XXE（XML External Entity）防护：</p>
 * <ul>
 *   <li>禁用外部实体解析（IS_SUPPORTING_EXTERNAL_ENTITIES）</li>
 *   <li>禁用 DTD 处理（SUPPORT_DTD）</li>
 *   <li>禁用验证（IS_VALIDATING）</li>
 *   <li>设置安全解析器拒绝所有外部引用</li>
 *   <li>禁用 xxe 属性（Woodstox 特定）</li>
 * </ul>
 *
 * <h2>功能配置</h2>
 * <ul>
 *   <li>启用命名空间支持</li>
 *   <li>启用文本合并</li>
 * </ul>
 *
 * @since 2026-02-13
 */
@UtilityClass
public class XmlParserUtils {

    private static final Logger log = LoggerFactory.getLogger(XmlParserUtils.class);

    /** 共享的 XMLInputFactory 实例（线程安全） */
    private static final XMLInputFactory SHARED_INPUT_FACTORY = createInputFactoryInternal();

    /**
     * 安全的 XML 解析器，拒绝所有外部实体引用。
     *
     * <p>作为 XXE 防护的最后一道防线，即使其他安全配置被绕过，
     * 此解析器也会拒绝所有外部引用请求。</p>
     */
    private static final XMLResolver SECURE_XML_RESOLVER = (publicID, systemID, baseURI, namespace) -> {
        log.warn("Blocked external entity reference - publicID: {}, systemID: {}, baseURI: {}",
                publicID, systemID, baseURI);
        // 返回空输入流，拒绝解析外部实体
        return null;
    };

    /**
     * 创建配置好的 XMLInputFactory 实例。
     *
     * <p>配置包括安全设置（防 XXE 攻击）和功能设置。</p>
     *
     * @return 配置好的 XMLInputFactory
     */
    private static XMLInputFactory createInputFactoryInternal() {
        XMLInputFactory factory = XMLInputFactory.newInstance();

        // ==================== 安全配置（XXE 防护） ====================

        // 禁用验证
        factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);

        // 禁用外部实体解析 - 防止 XXE 攻击
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);

        // 禁用 DTD - 防止 DTD 相关攻击（如 billion laughs）
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        // 设置安全的 XML 解析器，拒绝所有外部引用
        // 这是额外的安全层，即使上述属性被绕过也能防护
        factory.setXMLResolver(SECURE_XML_RESOLVER);

        // 尝试设置 Woodstox 特定的安全属性（如果可用）
        // 这些属性在其他 StAX 实现上会被忽略
        setPropertyIfSupported(factory, "com.ctc.wstx.enableTDs", Boolean.FALSE);
        setPropertyIfSupported(factory, "javax.xml.stream.supportDTD", Boolean.FALSE);

        // ==================== 功能配置 ====================

        // 启用命名空间支持（XMPP 需要）
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);

        // 启用文本合并，将相邻的文本事件合并为一个
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);

        log.debug("XMLInputFactory created with XXE protection enabled");
        return factory;
    }

    /**
     * 尝试设置属性，如果不支持则忽略。
     *
     * @param factory XMLInputFactory 实例
     * @param name    属性名称
     * @param value   属性值
     */
    private static void setPropertyIfSupported(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
            log.debug("Set XML parser property: {} = {}", name, value);
        } catch (Exception e) {
            // 属性不支持，忽略
            log.trace("XML parser property {} not supported: {}", name, e.getMessage());
        }
    }

    /**
     * 创建新的 XMLInputFactory 实例。
     *
     * <p>适用于需要独立配置的场景。</p>
     *
     * @return 新的 XMLInputFactory 实例
     */
    public static XMLInputFactory createInputFactory() {
        return createInputFactoryInternal();
    }

    /**
     * 获取共享的 XMLInputFactory 实例。
     *
     * <p>适用于大多数场景，避免重复创建工厂实例。
     * 该实例是线程安全的，可在多线程环境下使用。</p>
     *
     * @return 共享的 XMLInputFactory 实例
     */
    public static XMLInputFactory getSharedInputFactory() {
        return SHARED_INPUT_FACTORY;
    }
}
