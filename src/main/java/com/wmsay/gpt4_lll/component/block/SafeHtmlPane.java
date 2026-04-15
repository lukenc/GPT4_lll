package com.wmsay.gpt4_lll.component.block;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import java.awt.*;
import java.awt.im.InputMethodRequests;
import java.io.StringReader;

/**
 * macOS 安全的 HTML 渲染面板。
 * <p>
 * 标准 JEditorPane + HTMLEditorKit 在 macOS 上存在已知的原生层死锁问题：
 * HTMLEditorKit 的字体度量计算会触发 CoreText 原生调用，在窗口激活/失活
 * 切换时可能导致 sun.lwawt 与 CoreText 之间的死锁，表现为整个窗口
 * 不再响应输入事件（EDT 本身并未阻塞）。
 * <p>
 * 本类通过以下措施规避该问题：
 * <ol>
 *   <li>使用自定义 HTMLEditorKit，禁用异步加载</li>
 *   <li>在 Document 上禁用异步加载属性</li>
 *   <li>覆盖 getPreferredSize 添加缓存，减少字体度量调用频率</li>
 *   <li>设置 JEditorPane.W3C_LENGTH_UNITS = false 避免额外的字体计算</li>
 *   <li>使用 BasicTextUI 的文本抗锯齿而非原生渲染</li>
 *   <li>覆写 getInputMethodRequests() 返回 null，阻止 TSM 交互</li>
 * </ol>
 */
public class SafeHtmlPane extends JTextPane {

    private Dimension cachedPrefSize = null;
    private int cachedParentWidth = -1;
    private boolean contentDirty = false;

    public SafeHtmlPane() {
        super();
        // 使用自定义的安全 HTMLEditorKit
        SafeHTMLEditorKit kit = new SafeHTMLEditorKit();
        setEditorKit(kit);
        setContentType("text/html");

        setEditable(false);
        setDoubleBuffered(true);
        setOpaque(false);

        // 关键：禁用 W3C 长度单位，减少字体度量计算
        putClientProperty(JEditorPane.W3C_LENGTH_UNITS, Boolean.FALSE);
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        // 禁用焦点遍历键（Tab 等不会跳到此面板），但允许鼠标点击选中文本
        setFocusTraversalKeysEnabled(false);

        // 使用 Java2D 文本抗锯齿而非原生渲染
        putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        putClientProperty("JEditorPane.honorDisplayProperties", Boolean.TRUE);

        // 初始化空内容
        setText("<html><body></body></html>");
    }

    /**
     * 彻底阻止 TSM（Text Services Manager）交互。
     * 返回 null 表示此组件不支持输入法，macOS 不会尝试
     * 在窗口切换时恢复此组件的输入法上下文。
     */
    @Override
    public InputMethodRequests getInputMethodRequests() {
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        Container p = getParent();
        if (p != null && p.getWidth() > 0) {
            int pw = p.getWidth();
            if (cachedPrefSize != null && pw == cachedParentWidth && !contentDirty) {
                return cachedPrefSize;
            }
            setSize(pw, Short.MAX_VALUE);
            Dimension d = super.getPreferredSize();
            cachedPrefSize = new Dimension(pw, d.height);
            cachedParentWidth = pw;
            contentDirty = false;
            return cachedPrefSize;
        }
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        Container p = getParent();
        if (p != null && p.getWidth() > 0) {
            return new Dimension(p.getWidth(), pref.height);
        }
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    /**
     * 安全地设置 HTML 内容。使用 Document 级别替换避免闪烁，
     * 并在异常时降级到 setText。
     */
    public void setHtmlContent(String html) {
        contentDirty = true;
        cachedPrefSize = null;
        try {
            Document doc = getDocument();
            doc.putProperty(Document.StreamDescriptionProperty, null);
            doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
            getEditorKit().read(new StringReader(html), doc, 0);
        } catch (Exception e) {
            setText(html);
        }
        revalidate();
        repaint();
    }

    /**
     * 标记内容已变化，下次 getPreferredSize 时重新计算。
     */
    public void invalidateContentCache() {
        contentDirty = true;
        cachedPrefSize = null;
    }

    /**
     * 安全的 HTMLEditorKit 子类。
     * 禁用异步加载，使用简化的 StyleSheet 避免复杂的字体计算。
     */
    private static class SafeHTMLEditorKit extends HTMLEditorKit {

        private static final StyleSheet SHARED_STYLE_SHEET = new StyleSheet();

        static {
            SHARED_STYLE_SHEET.addRule("ul, ol { padding-left: 24px; margin: 4px 0; }");
            SHARED_STYLE_SHEET.addRule("li { margin: 2px 0; }");
            SHARED_STYLE_SHEET.addRule("blockquote { margin: 6px 0 6px 12px; padding-left: 10px; border-left: 3px solid #ccc; }");
            SHARED_STYLE_SHEET.addRule("p { margin: 4px 0; }");
            SHARED_STYLE_SHEET.addRule("pre { margin: 6px 0; }");
        }

        @Override
        public Document createDefaultDocument() {
            HTMLDocument doc = (HTMLDocument) super.createDefaultDocument();
            doc.setAsynchronousLoadPriority(-1);
            doc.setTokenThreshold(Integer.MAX_VALUE);
            doc.setBase(null);
            return doc;
        }

        @Override
        public StyleSheet getStyleSheet() {
            return SHARED_STYLE_SHEET;
        }
    }
}
