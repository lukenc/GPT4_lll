package com.wmsay.gpt4_lll.component.block;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;

import java.util.List;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.io.StringReader;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Markdown 内容块。
 * 内部使用 JEditorPane + flexmark 渲染，支持流式追加，
 * 渲染逻辑从原 Gpt4lllTextArea 迁移而来。
 */
public class MarkdownBlock implements ContentBlock {

    private final JEditorPane htmlPane;
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

        htmlPane = new JEditorPane() {
            @Override
            public Dimension getPreferredSize() {
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    setSize(p.getWidth(), Short.MAX_VALUE);
                    Dimension d = super.getPreferredSize();
                    return new Dimension(p.getWidth(), d.height);
                }
                return super.getPreferredSize();
            }
        };
        htmlPane.setContentType("text/html");
        htmlPane.setEditable(false);
        htmlPane.setDoubleBuffered(true);
        htmlPane.setOpaque(false);
        htmlPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        htmlPane.setText(wrapHtml(""));

        updateTimer = new Timer(COALESCE_DELAY_MS, e -> flushPendingContent());
        updateTimer.setRepeats(false);
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

        String fullHtml = wrapHtml(html);

        try {
            Document doc = htmlPane.getDocument();
            doc.putProperty(Document.StreamDescriptionProperty, null);
            htmlPane.getEditorKit().read(new StringReader(fullHtml), doc, 0);
        } catch (Exception e) {
            htmlPane.setText(fullHtml);
        }

        htmlPane.revalidate();
        htmlPane.repaint();
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
                + "body { width: 100%; margin: 2px 0; color: " + fgHex + "; }"
                + "table { width: 100%; margin: 6px 0; }"
                + "th, td { border: 1px solid " + borderHex + "; padding: 4px 8px; text-align: left; }"
                + "th { background-color: " + thBgHex + "; font-weight: bold; }"
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
