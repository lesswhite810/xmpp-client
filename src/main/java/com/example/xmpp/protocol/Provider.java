package com.example.xmpp.protocol;

import com.example.xmpp.exception.XmppParseException;
import com.example.xmpp.util.XmlStringBuilder;

import javax.xml.stream.XMLEventReader;

/**
 * XML 解析和序列化提供者接口。
 *
 * <p>用于处理 XML 元素的解析和序列化，支持自定义 XML 处理逻辑。
 * parse 方法抛出 XmppParseException 封装底层 XML 解析错误，
 * 子类实现应使用 AbstractProvider 简化异常处理。</p>
 *
 * @param <T> 处理的对象类型
 * @since 2026-02-09
 */
public interface Provider<T> {

    /**
     * 从 XML 解析对象。
     *
     * @param reader XMLEventReader，指向 XML 流的当前位置，不能为 null
     * @return 解析后的对象实例，可能为 null（取决于具体实现）
     * @throws XmppParseException 如果 XML 格式错误、元素缺失、验证失败或 IO 错误
     */
    T parse(XMLEventReader reader) throws XmppParseException;

    /**
     * 将对象序列化为 XML。
     *
     * @param object 要序列化的对象，可能为 null
     * @param xml    XmlStringBuilder，用于构建 XML 输出，不能为 null
     * @throws NullPointerException 如果 xml 参数为 null
     */
    void serialize(T object, XmlStringBuilder xml);

    /**
     * 获取处理的元素名称。
     *
     * @return 元素本地名称，不能为 null
     */
    String getElementName();

    /**
     * 获取处理的命名空间。
     *
     * @return 命名空间 URI，可能为 null
     */
    String getNamespace();
}