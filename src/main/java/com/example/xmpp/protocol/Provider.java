package com.example.xmpp.protocol;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.util.XmlStringBuilder;

import javax.xml.stream.XMLEventReader;

/**
 * XML 解析和序列化提供者接口。
 *
 * @param <T> 处理的对象类型
 * @since 2026-02-09
 */
public interface Provider<T> {

    /**
     * 从 XML 解析对象。
     *
     * @param reader XML 事件读取器
     * @return 解析后的对象
     * @throws XmppParseException XML 解析失败
     */
    T parse(XMLEventReader reader) throws XmppParseException;

    /**
     * 将对象序列化为 XML。
     *
     * @param object 要序列化的对象
     * @param xml XML 构建器
     */
    void serialize(T object, XmlStringBuilder xml);

    /**
     * 获取处理的元素名称。
     *
     * @return 元素名称
     */
    String getElementName();

    /**
     * 获取处理的命名空间。
     *
     * @return 命名空间
     */
    String getNamespace();
}
