package com.wmsay.gpt4_lll.component.block;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.PluginIcons;
import com.wmsay.gpt4_lll.component.theme.GlassPanel;
import com.wmsay.gpt4_lll.component.theme.GlassVerticalLine;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.component.theme.SpringAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 思考过程内容块（对应 reasoning_content）。
 * 支持流式追加，完成后自动折叠，点击标题可展开/折叠。
 * <p>
 * 使用 SafeHtmlPane 替代原始 JEditorPane + HTMLEditorKit，
 * 避免 macOS 上 CoreText 原生字体渲染导致的窗口冻结。
 */
public class ThinkingBlock implements ContentBlock {

    /** Higher transparency background for ThinkingBlock (conveys hazy/thinking feel) */
    private static final JBColor THINKING_BG = new JBColor(
            new Color(255, 255, 255, 140),   // light: alpha≈0.55, more transparent than PRIMARY_BG
            new Color(30, 32, 48, 115));      // dark: alpha≈0.45, more transparent than PRIMARY_BG

    /** Title bar background: even higher transparency than wrapper */
    private static final JBColor TITLE_BAR_BG = new JBColor(
            new Color(245, 245, 245, 160),   // light: slightly more opaque than wrapper
            new Color(43, 43, 43, 135));      // dark: slightly more opaque than wrapper

    private final GlassPanel wrapper;
    private final JPanel titleBar;
    private final JLabel titleLabel;
    private final SafeHtmlPane htmlPane;
    private final JPanel contentPanel;
    private final GlassVerticalLine verticalLine;

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

        // ── Wrapper: GlassPanel with 12px corner radius, higher transparency ──
        wrapper = new GlassPanel(LiquidGlassTheme.RADIUS_MEDIUM);
        wrapper.setLayout(new BorderLayout());
        wrapper.setBgColor(THINKING_BG);
        wrapper.setBorder(JBUI.Borders.empty(4, 0));

        // ── Title bar: higher transparency background + 1px highlight at top ──
        titleBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Higher transparency background fill
                    g2.setColor(TITLE_BAR_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            LiquidGlassTheme.RADIUS_SMALL, LiquidGlassTheme.RADIUS_SMALL);
                    // highlight reflection strip at top (gradient fade)
                    Color hlColor = LiquidGlassTheme.HIGHLIGHT;
                    int fadeH = 4;
                    g2.setPaint(new GradientPaint(0, 0, hlColor,
                            0, fadeH, new Color(hlColor.getRed(), hlColor.getGreen(), hlColor.getBlue(), 0)));
                    g2.fillRect(LiquidGlassTheme.RADIUS_SMALL / 2, 0,
                            getWidth() - LiquidGlassTheme.RADIUS_SMALL, fadeH);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        titleBar.setOpaque(false);
        titleBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        titleLabel = new JLabel("▼ 思考过程 / Reasoning Process");
        titleLabel.setIcon(PluginIcons.THINKING);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.ITALIC, 11f));
        titleLabel.setForeground(JBColor.GRAY);
        titleLabel.setBorder(JBUI.Borders.empty(2, 4));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleCollapse();
            }
        });
        wrapper.add(titleBar, BorderLayout.NORTH);

        htmlPane = new SafeHtmlPane();
        htmlPane.setHtmlContent(wrapHtml(""));

        // ── Content panel with GlassVerticalLine (soft gray) ──
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        verticalLine = new GlassVerticalLine(JBColor.GRAY);
        contentPanel.add(verticalLine, BorderLayout.WEST);

        JPanel contentInner = new JPanel(new BorderLayout());
        contentInner.setOpaque(false);
        contentInner.setBorder(JBUI.Borders.empty(0, 8));
        contentInner.add(htmlPane, BorderLayout.CENTER);

        contentPanel.add(contentInner, BorderLayout.CENTER);
        wrapper.add(contentPanel, BorderLayout.CENTER);

        updateTimer = new Timer(COALESCE_DELAY_MS, e -> flushPendingContent());
        updateTimer.setRepeats(false);

        wrapper.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (wrapper.isShowing() && !pendingContent.isEmpty()) {
                    flushPendingContent();
                }
            }
        });
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
        if (!wrapper.isShowing()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!updateTimer.isRunning()) {
                updateTimer.restart();
            }
        });
    }

    /** 标记思考过程结束，自动折叠。 */
    public void markComplete() {
        markComplete(null);
    }

    /**
     * 标记思考过程结束，自动折叠。
     * 预留 SpringAnimator 弹性过渡接口。
     *
     * @param springConfig 弹簧动画配置，为 null 时立即折叠（无动画）
     */
    public void markComplete(LiquidGlassTheme.SpringConfig springConfig) {
        updateTimer.stop();
        flushPendingContent();
        completed = true;
        setCollapsed(true, springConfig);
    }

    /** 替换全部内容（用于将占位文本替换为真实思考内容）。 */
    public void replaceContent(String newContent) {
        pendingContent.clear();
        contentBuilder.setLength(0);
        contentBuilder.append(newContent);
        lastRenderedHtml = "";
        htmlPane.invalidateContentCache();
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
        setCollapsed(collapsed, null);
    }

    /**
     * 折叠/展开内容面板，可选 SpringConfig 驱动弹性过渡动画。
     *
     * @param collapsed    是否折叠
     * @param springConfig 弹簧动画配置，为 null 时立即切换（无动画）
     */
    private void setCollapsed(boolean collapsed, LiquidGlassTheme.SpringConfig springConfig) {
        this.collapsed = collapsed;
        titleLabel.setText(collapsed
                ? "▶ 思考过程 / Reasoning Process (点击展开)"
                : "▼ 思考过程 / Reasoning Process");

        if (springConfig != null) {
            int fromHeight = collapsed ? contentPanel.getHeight() : 0;
            int toHeight = collapsed ? 0 : contentPanel.getPreferredSize().height;
            if (!collapsed) {
                contentPanel.setVisible(true);
            }
            SpringAnimator animator = new SpringAnimator(springConfig);
            animator.bindToComponent(wrapper);
            animator.animateTo(fromHeight, toHeight, value -> {
                int h = Math.max(0, (int) Math.round(value));
                contentPanel.setPreferredSize(new Dimension(contentPanel.getWidth(), h));
                wrapper.revalidate();
                wrapper.repaint();
            }, () -> {
                contentPanel.setPreferredSize(null);
                if (collapsed) {
                    contentPanel.setVisible(false);
                }
                wrapper.revalidate();
                wrapper.repaint();
            });
        } else {
            contentPanel.setVisible(!collapsed);
            wrapper.revalidate();
        }
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
        return "<html><head><style>"
                + "body { width: 100%; margin: 2px 0; color: gray; font-style: italic; }"
                + "ul, ol { padding-left: 24px; margin: 4px 0; }"
                + "li { margin: 2px 0; }"
                + "blockquote { margin: 6px 0 6px 12px; padding-left: 10px; border-left: 3px solid #ccc; }"
                + "p { margin: 4px 0; }"
                + "</style></head>"
                + "<body>" + bodyContent + "</body></html>";
    }

    // ── Package-private accessors for testing ──
    GlassPanel getWrapper() { return wrapper; }
    GlassVerticalLine getVerticalLine() { return verticalLine; }
    JPanel getTitleBar() { return titleBar; }
    JPanel getContentPanel() { return contentPanel; }
}
