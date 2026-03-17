package com.wmsay.gpt4_lll.component.block;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.StringReader;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 思考过程内容块（对应 reasoning_content）。
 * 支持流式追加，完成后自动折叠，点击标题可展开/折叠。
 */
public class ThinkingBlock implements ContentBlock {

    private final JPanel wrapper;
    private final JLabel titleLabel;
    private final JEditorPane htmlPane;
    private final JPanel contentPanel;

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final StringBuilder contentBuilder = new StringBuilder();

    private final ConcurrentLinkedQueue<String> pendingContent = new ConcurrentLinkedQueue<>();
    private final Timer updateTimer;
    private static final int COALESCE_DELAY_MS = 80;

    private String lastRenderedHtml = "";
    private boolean collapsed = false;
    private boolean completed = false;

    private Runnable onContentChanged;

    public ThinkingBlock() {
        MutableDataSet options = new MutableDataSet();
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();

        wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(JBUI.Borders.empty(4, 0));

        titleLabel = new JLabel("▼ 思考过程 / Reasoning Process");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.ITALIC, 11f));
        titleLabel.setForeground(JBColor.GRAY);
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        titleLabel.setBorder(JBUI.Borders.empty(2, 4));
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleCollapse();
            }
        });
        wrapper.add(titleLabel, BorderLayout.NORTH);

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

        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, JBColor.GRAY),
                JBUI.Borders.empty(0, 8)
        ));
        contentPanel.add(htmlPane, BorderLayout.CENTER);
        wrapper.add(contentPanel, BorderLayout.CENTER);

        updateTimer = new Timer(COALESCE_DELAY_MS, e -> flushPendingContent());
        updateTimer.setRepeats(false);
    }

    @Override
    public BlockType getType() {
        return BlockType.THINKING;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    @Override
    public boolean isAppendable() {
        return !completed;
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
     * 标记思考过程结束，自动折叠。
     */
    public void markComplete() {
        updateTimer.stop();
        flushPendingContent();
        completed = true;
        setCollapsed(true);
    }

    /**
     * 替换全部内容（用于将占位文本替换为真实思考内容）。
     */
    public void replaceContent(String newContent) {
        pendingContent.clear();
        contentBuilder.setLength(0);
        contentBuilder.append(newContent);
        lastRenderedHtml = "";
        updateText();
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    public void toggleCollapse() {
        setCollapsed(!collapsed);
    }

    public void setOnContentChanged(Runnable callback) {
        this.onContentChanged = callback;
    }

    public void dispose() {
        updateTimer.stop();
        pendingContent.clear();
    }

    private void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        contentPanel.setVisible(!collapsed);
        titleLabel.setText(collapsed
                ? "▶ 思考过程 / Reasoning Process (点击展开)"
                : "▼ 思考过程 / Reasoning Process");
        wrapper.revalidate();
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
        return "<html><head></head>"
                + "<body style='width: 100%; margin: 2px 0; "
                + "color: gray; font-style: italic;'>"
                + bodyContent
                + "</body></html>";
    }
}
