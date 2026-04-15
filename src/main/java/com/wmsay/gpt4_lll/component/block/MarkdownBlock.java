package com.wmsay.gpt4_lll.component.block;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;

import java.util.List;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Markdown 内容块。
 * 内部使用 SafeHtmlPane + flexmark 渲染，支持流式追加。
 * <p>
 * 使用 SafeHtmlPane 替代原始 JEditorPane + HTMLEditorKit，
 * 避免 macOS 上 CoreText 原生字体渲染导致的窗口冻结。
 */
public class MarkdownBlock implements ContentBlock {

    private final SafeHtmlPane htmlPane;
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final StringBuilder contentBuilder = new StringBuilder();

    private final ConcurrentLinkedQueue<String> pendingContent = new ConcurrentLinkedQueue<>();
    private final Timer updateTimer;
    private static final int COALESCE_DELAY_MS = 80;

    private String lastRenderedHtml = "";
    private Runnable onContentChanged;

    public MarkdownBlock() {
        MutableDataSet options = new MutableDataSet();
        List<Extension> extensions = List.of(TablesExtension.create());
        options.set(Parser.EXTENSIONS, extensions);
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();

        htmlPane = new SafeHtmlPane();
        htmlPane.setHtmlContent(wrapHtml(""));

        updateTimer = new Timer(COALESCE_DELAY_MS, e -> flushPendingContent());
        updateTimer.setRepeats(false);

        // 窗口切走时暂停渲染 timer，切回时 flush 积压内容
        htmlPane.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (htmlPane.isShowing() && !pendingContent.isEmpty()) {
                    flushPendingContent();
                }
            }
        });
    }

    @Override
    public BlockType getType() {
        return BlockType.MARKDOWN;
    }

    @Override
    public JComponent getComponent() {
        return htmlPane;
    }

    @Override
    public boolean isAppendable() {
        return true;
    }

    @Override
    public void appendContent(String delta) {
        pendingContent.add(delta);
        if (!htmlPane.isShowing()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!updateTimer.isRunning()) {
                updateTimer.restart();
            }
        });
    }

    /**
     * 一次性设置完整内容（用于加载历史消息，非流式场景）。
     */
    public void setContent(String fullText) {
        updateTimer.stop();
        pendingContent.clear();
        contentBuilder.setLength(0);
        contentBuilder.append(fullText);
        lastRenderedHtml = "";
        htmlPane.invalidateContentCache();
        updateText();
    }

    public String getContentText() {
        return contentBuilder.toString();
    }

    public void setOnContentChanged(Runnable callback) {
        this.onContentChanged = callback;
    }

    public void dispose() {
        updateTimer.stop();
        pendingContent.clear();
    }

    private void flushPendingContent() {
        String item;
        boolean drained = false;
        while ((item = pendingContent.poll()) != null) {
            contentBuilder.append(item);
            drained = true;
        }
        if (!drained) {
            return;
        }
        updateText();
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    private void updateText() {
        String html = renderer.render(parser.parse(contentBuilder.toString()));
        if (html.equals(lastRenderedHtml)) {
            return;
        }
        lastRenderedHtml = html;
        htmlPane.setHtmlContent(wrapHtml(html));
    }

    private static String wrapHtml(String bodyContent) {
        Color fg = UIManager.getColor("Label.foreground");
        Color bg = UIManager.getColor("Panel.background");
        Color border = UIManager.getColor("Component.borderColor");
        if (fg == null) fg = Color.DARK_GRAY;
        if (bg == null) bg = Color.WHITE;
        if (border == null) border = Color.GRAY;

        Color thBg = blend(bg, fg, 0.12f);

        String fgHex = toHex(fg);
        String thBgHex = toHex(thBg);
        String borderHex = toHex(border);

        return "<html><head><style>"
                + "body { width: 100%; margin: 2px 0; padding: 0 4px; color: " + fgHex + "; }"
                + "table { width: 100%; margin: 6px 0; }"
                + "th, td { border: 1px solid " + borderHex + "; padding: 4px 8px; text-align: left; }"
                + "th { background-color: " + thBgHex + "; font-weight: bold; }"
                + "ul, ol { padding-left: 24px; margin: 4px 0; }"
                + "li { margin: 2px 0; }"
                + "blockquote { margin: 6px 0 6px 12px; padding-left: 10px; border-left: 3px solid #ccc; }"
                + "p { margin: 4px 0; }"
                + "pre { margin: 6px 0; overflow-x: auto; }"
                + "</style></head>"
                + "<body>" + bodyContent + "</body></html>";
    }

    private static Color blend(Color c1, Color c2, float ratio) {
        float r = c1.getRed() + (c2.getRed() - c1.getRed()) * ratio;
        float g = c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio;
        float b = c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio;
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
