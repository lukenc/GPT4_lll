package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;

/**
 * Mermaid 图表内容块。
 * 流式阶段显示源代码（与 CodeBlock 一致），
 * 代码围栏结束后渲染为图表。
 *
 * 渲染策略：使用 mmdc CLI 生成 PNG，用 JLabel+ImageIcon 显示。
 * 交互设计：
 * - 图表模式：显示缩放后的图片 + 工具栏（放大 | 源码）
 * - 点击图片或「放大」按钮：弹出 lightbox 全尺寸预览
 * - 点击「源码」按钮：切换到源代码视图
 * - 源码模式：显示源代码 + 工具栏（图表）
 * - 点击「图表」按钮：切回图表视图
 */
public class MermaidBlock implements ContentBlock {

    private final JPanel wrapper;
    private final JTextArea codeArea;
    private final JScrollPane scrollPane;
    private final JLabel imageLabel;
    private final JTextArea statusLabel;
    private final JPanel contentPanel;
    private final JPanel toolbar;
    private final JButton zoomButton;
    private final JButton sourceButton;
    private final JButton diagramButton;
    private final StringBuilder contentBuilder = new StringBuilder();
    private boolean rendered = false;
    private boolean sourceVisible = false;
    private Runnable onContentChanged;
    private BufferedImage fullImage; // 保存原始全尺寸图片用于 lightbox

    private final Timer updateTimer;
    private static final int COALESCE_DELAY_MS = 80;
    private volatile boolean pendingUpdate = false;

    public MermaidBlock() {
        wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    d.width = Math.min(d.width, p.getWidth());
                }
                return d;
            }

            @Override
            public Dimension getMaximumSize() {
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    return new Dimension(p.getWidth(), super.getPreferredSize().height);
                }
                return super.getMaximumSize();
            }
        };
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.empty(4, 0),
                BorderFactory.createLineBorder(JBColor.border(), 1, true)
        ));
        wrapper.setBackground(new JBColor(new Color(245, 245, 245), new Color(43, 43, 43)));

        // Language label
        JLabel languageLabel = new JLabel(" mermaid");
        languageLabel.setFont(languageLabel.getFont().deriveFont(Font.BOLD, 10f));
        languageLabel.setForeground(JBColor.GRAY);
        languageLabel.setBorder(JBUI.Borders.empty(2, 6, 0, 0));
        languageLabel.setOpaque(true);
        languageLabel.setBackground(wrapper.getBackground());
        wrapper.add(languageLabel, BorderLayout.NORTH);

        // Status area for loading/error messages (JTextArea so text is selectable/copyable)
        statusLabel = new JTextArea();
        statusLabel.setEditable(false);
        statusLabel.setLineWrap(true);
        statusLabel.setWrapStyleWord(true);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 12f));
        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setBackground(wrapper.getBackground());
        statusLabel.setBorder(JBUI.Borders.empty(4, 8));
        statusLabel.setVisible(false);

        // Code area for source code display
        codeArea = new JTextArea();
        codeArea.setEditable(false);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        codeArea.setLineWrap(false);
        codeArea.setTabSize(4);
        codeArea.setBackground(wrapper.getBackground());
        codeArea.setForeground(new JBColor(new Color(50, 50, 50), new Color(200, 200, 200)));
        codeArea.setBorder(JBUI.Borders.empty(4, 8, 4, 8));

        scrollPane = new JScrollPane(codeArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        // 禁用 macOS 平滑滚动动画，减少 MacScrollBarAnimationBehavior 协程
        scrollPane.putClientProperty("JScrollPane.smoothScrolling", Boolean.FALSE);
        // 禁用焦点遍历，避免 macOS 焦点系统在窗口切换时激活此组件
        scrollPane.setFocusable(false);
        codeArea.setFocusable(false);

        // 滚动事件转发（与 CodeBlock 一致）
        MouseWheelListener[] originalListeners = scrollPane.getMouseWheelListeners();
        for (MouseWheelListener mwl : originalListeners) {
            scrollPane.removeMouseWheelListener(mwl);
        }
        scrollPane.addMouseWheelListener(e -> {
            if (e.isShiftDown()) {
                for (MouseWheelListener mwl : originalListeners) {
                    mwl.mouseWheelMoved(e);
                }
            } else {
                Container ancestor = SwingUtilities.getAncestorOfClass(JScrollPane.class, scrollPane);
                if (ancestor != null) {
                    ancestor.dispatchEvent(SwingUtilities.convertMouseEvent(scrollPane, e, ancestor));
                }
            }
        });

        // Image label for mmdc PNG output — click to open lightbox
        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        imageLabel.setBorder(JBUI.Borders.empty(4, 8));
        imageLabel.setVisible(false);
        imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imageLabel.setToolTipText("点击放大 / Click to zoom");
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openLightbox();
            }
        });

        // Toolbar with action buttons
        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setOpaque(false);
        toolbar.setBorder(JBUI.Borders.empty(0, 6, 4, 0));
        toolbar.setVisible(false);

        zoomButton = createToolbarButton("\uD83D\uDD0D 放大 / Zoom");
        zoomButton.addActionListener(e -> openLightbox());

        sourceButton = createToolbarButton("</> 源码 / Source");
        sourceButton.addActionListener(e -> showSourceView());

        diagramButton = createToolbarButton("\uD83D\uDCCA 图表 / Diagram");
        diagramButton.addActionListener(e -> showDiagramView());
        diagramButton.setVisible(false);

        toolbar.add(zoomButton);
        toolbar.add(sourceButton);
        toolbar.add(diagramButton);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.add(statusLabel);
        contentPanel.add(scrollPane);
        contentPanel.add(imageLabel);
        contentPanel.add(toolbar);

        wrapper.add(contentPanel, BorderLayout.CENTER);

        updateTimer = new Timer(COALESCE_DELAY_MS, e -> flushPendingContent());
        updateTimer.setRepeats(false);

        // 窗口切走时暂停渲染 timer，切回时 flush 积压内容，避免 EDT 队列积压导致假死
        wrapper.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (wrapper.isShowing() && pendingUpdate) {
                    flushPendingContent();
                }
            }
        });
    }

    private JButton createToolbarButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 11f));
        btn.setMargin(JBUI.insets(2, 6));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusable(false);
        return btn;
    }

    // --- ContentBlock interface ---

    @Override
    public BlockType getType() { return BlockType.MERMAID; }

    @Override
    public JComponent getComponent() { return wrapper; }

    @Override
    public boolean isAppendable() { return !rendered; }

    @Override
    public void appendContent(String delta) {
        if (delta == null) return;
        contentBuilder.append(delta);
        pendingUpdate = true;
        // 不可见时跳过 timer 调度，内容留在 contentBuilder 中
        if (!wrapper.isShowing()) {
            return;
        }
        if (!updateTimer.isRunning()) {
            updateTimer.restart();
        }
    }

    // --- MermaidBlock specific methods ---

    public String getSourceCode() { return contentBuilder.toString(); }

    public void setOnContentChanged(Runnable callback) { this.onContentChanged = callback; }

    /** 触发渲染。使用 mmdc CLI 异步生成 PNG。 */
    public void triggerRender() {
        String source = contentBuilder.toString();

        statusLabel.setText("渲染中.../Rendering...");
        statusLabel.setVisible(true);
        toolbar.setVisible(false);
        wrapper.revalidate();
        wrapper.repaint();

        boolean darkTheme = !JBColor.isBright();

        MermaidRenderer.getInstance().renderWithMmdcAsync(source, darkTheme)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    if (result.isSuccess()) {
                        showImage(result.getImage());
                    } else {
                        showFallback(result.getErrorMessage());
                    }
                    rendered = true;
                    wrapper.revalidate();
                    wrapper.repaint();
                    if (onContentChanged != null) onContentChanged.run();
                }));
    }

    private void showImage(BufferedImage image) {
        this.fullImage = image;
        int panelWidth = wrapper.getParent() != null ? wrapper.getParent().getWidth() : 0;
        if (panelWidth > 0 && image.getWidth() > panelWidth) {
            double scale = (double) panelWidth / image.getWidth();
            int newHeight = (int) (image.getHeight() * scale);
            Image scaled = image.getScaledInstance(panelWidth, newHeight, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaled));
        } else {
            imageLabel.setIcon(new ImageIcon(image));
        }
        showDiagramView();
        statusLabel.setVisible(false);
    }

    private void showFallback(String errorMessage) {
        String displayMessage;
        if (errorMessage != null && (errorMessage.contains("Cannot run program") || errorMessage.contains("No such file"))) {
            displayMessage = "mmdc 未安装 / mmdc not found.\n"
                    + "请运行 npm install -g @mermaid-js/mermaid-cli 安装后重试。";
        } else {
            displayMessage = errorMessage;
        }
        scrollPane.setVisible(true);
        imageLabel.setVisible(false);
        toolbar.setVisible(false);
        statusLabel.setText(displayMessage);
        statusLabel.setForeground(new JBColor(new Color(180, 0, 0), new Color(255, 100, 100)));
        statusLabel.setVisible(true);
    }

    /** 切换到图表视图 */
    private void showDiagramView() {
        sourceVisible = false;
        scrollPane.setVisible(false);
        imageLabel.setVisible(true);
        toolbar.setVisible(true);
        zoomButton.setVisible(true);
        sourceButton.setVisible(true);
        diagramButton.setVisible(false);
        wrapper.revalidate();
        wrapper.repaint();
    }

    /** 切换到源码视图 */
    private void showSourceView() {
        sourceVisible = true;
        scrollPane.setVisible(true);
        imageLabel.setVisible(false);
        toolbar.setVisible(true);
        zoomButton.setVisible(false);
        sourceButton.setVisible(false);
        diagramButton.setVisible(true);
        wrapper.revalidate();
        wrapper.repaint();
    }

    /** 弹出 lightbox 全尺寸预览，支持缩放和拖拽 */
    private void openLightbox() {
        if (fullImage == null) return;
        Window window = SwingUtilities.getWindowAncestor(wrapper);
        JDialog dialog = new JDialog(
                window instanceof Frame ? (Frame) window : null,
                "Mermaid Diagram", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        ZoomableImagePanel imagePanel = new ZoomableImagePanel(fullImage);

        // 底部工具栏：缩放控制 + 百分比显示
        JLabel scaleLabel = new JLabel("100%");
        scaleLabel.setFont(scaleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        scaleLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JButton fitBtn = new JButton("适应窗口 / Fit");
        fitBtn.setFocusable(false);
        fitBtn.addActionListener(e -> imagePanel.fitToPanel());

        JButton actualBtn = new JButton("实际大小 / 1:1");
        actualBtn.setFocusable(false);
        actualBtn.addActionListener(e -> imagePanel.actualSize());

        JButton zoomInBtn = new JButton("+");
        zoomInBtn.setFocusable(false);
        zoomInBtn.addActionListener(e -> imagePanel.zoomIn());

        JButton zoomOutBtn = new JButton("−");
        zoomOutBtn.setFocusable(false);
        zoomOutBtn.addActionListener(e -> imagePanel.zoomOut());

        imagePanel.setOnScaleChanged(() ->
                scaleLabel.setText(String.format("%.0f%%", imagePanel.getScale() * 100)));

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        bottomBar.add(zoomOutBtn);
        bottomBar.add(scaleLabel);
        bottomBar.add(zoomInBtn);
        bottomBar.add(fitBtn);
        bottomBar.add(actualBtn);

        dialog.setLayout(new BorderLayout());
        dialog.add(imagePanel, BorderLayout.CENTER);
        dialog.add(bottomBar, BorderLayout.SOUTH);

        // 对话框大小：不超过屏幕 90%
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(fullImage.getWidth() + 40, (int) (screen.width * 0.9));
        int h = Math.min(fullImage.getHeight() + 100, (int) (screen.height * 0.9));
        dialog.setSize(w, h);
        dialog.setLocationRelativeTo(window);

        // 打开后自动适应窗口
        dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                SwingUtilities.invokeLater(imagePanel::fitToPanel);
            }
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                // 窗口大小变化时不自动 fit，让用户保持当前缩放
            }
        });

        dialog.setVisible(true);
    }

    /** @deprecated Use showSourceView/showDiagramView instead. Kept for test compatibility. */
    @Deprecated
    public void toggleSourceView() {
        if (sourceVisible) {
            showDiagramView();
        } else {
            showSourceView();
        }
    }

    public void setContentAndRender(String fullSource) {
        contentBuilder.setLength(0);
        contentBuilder.append(fullSource);
        codeArea.setText(fullSource);
        triggerRender();
    }

    public void dispose() {
        updateTimer.stop();
        onContentChanged = null;
        fullImage = null;
        imageLabel.setIcon(null);
    }

    // --- Internal helpers / test accessors ---

    boolean isRendered() { return rendered; }
    void setRendered(boolean rendered) { this.rendered = rendered; }
    boolean isSourceVisible() { return sourceVisible; }
    void setSourceVisible(boolean sourceVisible) { this.sourceVisible = sourceVisible; }
    JTextArea getStatusLabel() { return statusLabel; }
    JLabel getImageLabel() { return imageLabel; }
    JTextArea getCodeArea() { return codeArea; }
    JScrollPane getScrollPane() { return scrollPane; }

    private void flushPendingContent() {
        if (!pendingUpdate) return;
        pendingUpdate = false;
        codeArea.setText(contentBuilder.toString());
        wrapper.revalidate();
        wrapper.repaint();
        if (onContentChanged != null) onContentChanged.run();
    }
}
