package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.PluginIcons;
import com.wmsay.gpt4_lll.component.SpinnerIconAnimator;
import com.wmsay.gpt4_lll.component.theme.GlassPanel;
import com.wmsay.gpt4_lll.component.theme.GlassVerticalLine;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.component.theme.SpringAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 步骤容器组件，实现 ContentBlock 接口。
 * 包含标题区域（折叠指示符 + 编号徽章 + 描述 + 状态图标）和可折叠的子内容区域。
 */
public class StepBlock implements ContentBlock {

    // ── Status enum ──────────────────────────────────────────────
    public enum StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

    // ── Color definitions (JBColor for light/dark theme) ─────────
    private static final JBColor ACCENT_COLOR = new JBColor(new Color(0x2196F3), new Color(0x42A5F5));
    private static final JBColor SUCCESS_COLOR = new JBColor(new Color(0x4CAF50), new Color(0x66BB6A));
    private static final JBColor ERROR_COLOR = new JBColor(new Color(0xF44336), new Color(0xEF5350));
    private static final JBColor PENDING_COLOR = JBColor.GRAY;
    private static final JBColor BADGE_BG_PENDING = new JBColor(new Color(0xBDBDBD), new Color(0x757575));

    private static final int MAX_SUMMARY_LENGTH = 80;

    // ── Immutable construction params ────────────────────────────
    private final int stepIndex;
    private final String description;

    // ── Mutable state ────────────────────────────────────────────
    private StepStatus status = StepStatus.PENDING;
    private String resultSummary = "";
    private boolean collapsed = false;
    private final List<ContentBlock> childBlocks = new ArrayList<>();

    // ── UI components ────────────────────────────────────────────
    private final GlassPanel wrapper;
    private final JPanel headerPanel;
    private final JLabel collapseIndicator;
    private final StepNumberBadge numberBadge;
    private final JLabel descriptionLabel;
    private final JLabel statusIconLabel;
    private final JLabel summaryLabel;
    private final JPanel contentPanel;
    private final JPanel childContainer;
    private final GlassVerticalLine verticalLine;

    // ── Spinner animation ────────────────────────────────────────
    private SpinnerIconAnimator spinnerAnimator;

    // ── Internal MarkdownBlock for streaming text ────────────────
    private MarkdownBlock internalMarkdownBlock;

    // ── Internal ThinkingBlock for reasoning content ─────────────
    private ThinkingBlock internalThinkingBlock;

    // ── Content changed callback ─────────────────────────────────
    private Runnable onContentChanged;

    // ══════════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════════
    public StepBlock(int stepIndex, String description) {
        this.stepIndex = Math.max(0, stepIndex);
        this.description = description != null ? description : "";

        // ── Wrapper (GlassPanel with 12px corner radius) ─────────
        wrapper = new GlassPanel(LiquidGlassTheme.RADIUS_MEDIUM);
        wrapper.setLayout(new BorderLayout());
        wrapper.setBgColor(LiquidGlassTheme.PRIMARY_BG);

        // ── Header panel with higher transparency background ─────
        headerPanel = new JPanel(new BorderLayout(6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(LiquidGlassTheme.SECONDARY_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            LiquidGlassTheme.RADIUS_SMALL, LiquidGlassTheme.RADIUS_SMALL);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        headerPanel.setOpaque(false);
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerPanel.setBorder(JBUI.Borders.empty(4, 0));

        // Left side: collapse indicator + step number badge
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);

        collapseIndicator = new JLabel("▼");
        collapseIndicator.setFont(collapseIndicator.getFont().deriveFont(Font.PLAIN, 10f));
        collapseIndicator.setForeground(JBColor.GRAY);
        leftPanel.add(collapseIndicator);

        numberBadge = new StepNumberBadge(this.stepIndex + 1);
        numberBadge.setBadgeColor(BADGE_BG_PENDING);
        leftPanel.add(numberBadge);

        headerPanel.add(leftPanel, BorderLayout.WEST);

        // Center: description text + summary label (stacked vertically when summary visible)
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        descriptionLabel = new JLabel(this.description);
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(Font.BOLD, 12f));
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(descriptionLabel);

        summaryLabel = new JLabel();
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 10f));
        summaryLabel.setForeground(JBColor.GRAY);
        summaryLabel.setVisible(false);
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(summaryLabel);

        headerPanel.add(centerPanel, BorderLayout.CENTER);

        // Right side: status icon
        statusIconLabel = new JLabel();
        statusIconLabel.setIcon(PluginIcons.STEP_PENDING);
        headerPanel.add(statusIconLabel, BorderLayout.EAST);

        // Click on header toggles collapse
        headerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setCollapsed(!collapsed);
            }
        });

        wrapper.add(headerPanel, BorderLayout.NORTH);

        // ── Content panel with GlassVerticalLine (replaces MatteBorder) ──
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        verticalLine = new GlassVerticalLine(PENDING_COLOR);
        contentPanel.add(verticalLine, BorderLayout.WEST);

        JPanel contentInner = new JPanel(new BorderLayout());
        contentInner.setOpaque(false);
        contentInner.setBorder(JBUI.Borders.empty(4, 12, 4, 0));

        childContainer = new JPanel();
        childContainer.setLayout(new BoxLayout(childContainer, BoxLayout.Y_AXIS));
        childContainer.setOpaque(false);
        contentInner.add(childContainer, BorderLayout.CENTER);

        contentPanel.add(contentInner, BorderLayout.CENTER);

        wrapper.add(contentPanel, BorderLayout.CENTER);

        // Apply outer margin
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.empty(4, 0),
                JBUI.Borders.empty(8, 12)
        ));
    }

    // ══════════════════════════════════════════════════════════════
    // ContentBlock interface
    // ══════════════════════════════════════════════════════════════
    @Override
    public BlockType getType() {
        return BlockType.STEP;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    @Override
    public boolean isAppendable() {
        return true;
    }

    @Override
    public void appendContent(String delta) {
        // Route to active ThinkingBlock if present
        if (internalThinkingBlock != null) {
            internalThinkingBlock.appendContent(delta);
            return;
        }
        ensureInternalMarkdownBlock();
        internalMarkdownBlock.appendContent(delta);
    }

    // ══════════════════════════════════════════════════════════════
    // Child block management
    // ══════════════════════════════════════════════════════════════
    public void addChildBlock(ContentBlock block) {
        if (block == null) return;
        childBlocks.add(block);
        JComponent comp = block.getComponent();
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        childContainer.add(comp);
        childContainer.add(Box.createVerticalStrut(4));
        childContainer.revalidate();
        childContainer.repaint();
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    public List<ContentBlock> getChildBlocks() {
        return Collections.unmodifiableList(childBlocks);
    }

    // ══════════════════════════════════════════════════════════════
    // Status management
    // ══════════════════════════════════════════════════════════════
    public void markInProgress() {
        if (status == StepStatus.COMPLETED || status == StepStatus.FAILED) return;
        status = StepStatus.IN_PROGRESS;
        updateVisuals();
        // Start spinner animation
        spinnerAnimator = new SpinnerIconAnimator(PluginIcons.SPINNER, statusIconLabel);
        spinnerAnimator.start();
    }

    public void markCompleted(String resultSummary) {
        if (status == StepStatus.COMPLETED || status == StepStatus.FAILED) return;
        stopSpinner();
        status = StepStatus.COMPLETED;
        this.resultSummary = resultSummary != null ? resultSummary : "";
        updateVisuals();
        statusIconLabel.setIcon(PluginIcons.STEP_COMPLETED);
        setCollapsed(true);
    }

    public void markFailed(String errorSummary) {
        if (status == StepStatus.COMPLETED || status == StepStatus.FAILED) return;
        stopSpinner();
        status = StepStatus.FAILED;
        this.resultSummary = errorSummary != null ? errorSummary : "";
        updateVisuals();
        statusIconLabel.setIcon(PluginIcons.STEP_FAILED);
        setCollapsed(true);
    }

    public StepStatus getStatus() {
        return status;
    }

    // ══════════════════════════════════════════════════════════════
    // Collapse control
    // ══════════════════════════════════════════════════════════════
    public void setCollapsed(boolean collapsed) {
        setCollapsed(collapsed, null);
    }

    /**
     * 折叠/展开内容面板，可选 SpringConfig 驱动弹性过渡动画。
     *
     * @param collapsed    是否折叠
     * @param springConfig 弹簧动画配置，为 null 时立即切换（无动画）
     */
    public void setCollapsed(boolean collapsed, LiquidGlassTheme.SpringConfig springConfig) {
        this.collapsed = collapsed;
        collapseIndicator.setText(collapsed ? "▶" : "▼");
        updateSummaryVisibility();

        if (springConfig != null) {
            // Spring animation: animate contentPanel preferred height
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
                // Animation complete: finalize state
                contentPanel.setPreferredSize(null); // restore natural sizing
                if (collapsed) {
                    contentPanel.setVisible(false);
                }
                wrapper.revalidate();
                wrapper.repaint();
            });
        } else {
            contentPanel.setVisible(!collapsed);
            wrapper.revalidate();
            wrapper.repaint();
        }

        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    // ══════════════════════════════════════════════════════════════
    // Streaming text support
    // ══════════════════════════════════════════════════════════════
    public void flushContent() {
        if (internalMarkdownBlock != null) {
            // MarkdownBlock handles its own flushing via timer
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ThinkingBlock support (reasoning content inside step)
    // ══════════════════════════════════════════════════════════════

    /**
     * 在 StepBlock 内部创建 ThinkingBlock 并设为活跃，
     * 后续 appendContent 会路由到 ThinkingBlock。
     */
    public ThinkingBlock startThinking() {
        // Reset internal markdown block pointer so new text after thinking
        // creates a fresh MarkdownBlock
        internalMarkdownBlock = null;
        ThinkingBlock tb = new ThinkingBlock();
        tb.setOnContentChanged(onContentChanged);
        addChildBlock(tb);
        internalThinkingBlock = tb;
        return tb;
    }

    /**
     * 结束 StepBlock 内部的 ThinkingBlock，标记完成并折叠。
     */
    public void endThinking() {
        if (internalThinkingBlock != null) {
            internalThinkingBlock.markComplete();
            internalThinkingBlock = null;
        }
    }

    /**
     * 是否有活跃的 ThinkingBlock。
     */
    public boolean isThinking() {
        return internalThinkingBlock != null;
    }

    // ══════════════════════════════════════════════════════════════
    // Callback
    // ══════════════════════════════════════════════════════════════
    public void setOnContentChanged(Runnable callback) {
        this.onContentChanged = callback;
    }

    // ══════════════════════════════════════════════════════════════
    // Package-private accessors for testing
    // ══════════════════════════════════════════════════════════════
    JPanel getHeaderPanel() { return headerPanel; }
    JLabel getCollapseIndicator() { return collapseIndicator; }
    StepNumberBadge getNumberBadge() { return numberBadge; }
    JLabel getDescriptionLabel() { return descriptionLabel; }
    JLabel getStatusIconLabel() { return statusIconLabel; }
    JLabel getSummaryLabel() { return summaryLabel; }
    JPanel getContentPanel() { return contentPanel; }
    JPanel getChildContainer() { return childContainer; }
    GlassVerticalLine getVerticalLine() { return verticalLine; }
    GlassPanel getWrapper() { return wrapper; }
    int getStepIndex() { return stepIndex; }
    String getDescription() { return description; }
    String getResultSummary() { return resultSummary; }
    SpinnerIconAnimator getSpinnerAnimator() { return spinnerAnimator; }

    // ══════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════
    private void ensureInternalMarkdownBlock() {
        if (internalMarkdownBlock == null) {
            internalMarkdownBlock = new MarkdownBlock();
            internalMarkdownBlock.setOnContentChanged(onContentChanged);
            addChildBlock(internalMarkdownBlock);
        }
    }

    private void stopSpinner() {
        if (spinnerAnimator != null) {
            spinnerAnimator.stop();
            spinnerAnimator = null;
        }
    }

    private void updateVisuals() {
        switch (status) {
            case PENDING:
                numberBadge.setBadgeColor(BADGE_BG_PENDING);
                updateContentBorderColor(PENDING_COLOR);
                statusIconLabel.setIcon(PluginIcons.STEP_PENDING);
                break;
            case IN_PROGRESS:
                numberBadge.setBadgeColor(ACCENT_COLOR);
                updateContentBorderColor(ACCENT_COLOR);
                // statusIcon is handled by SpinnerIconAnimator
                break;
            case COMPLETED:
                numberBadge.setBadgeColor(SUCCESS_COLOR);
                updateContentBorderColor(SUCCESS_COLOR);
                break;
            case FAILED:
                numberBadge.setBadgeColor(ERROR_COLOR);
                updateContentBorderColor(ERROR_COLOR);
                break;
        }
        wrapper.revalidate();
        wrapper.repaint();
    }

    private void updateContentBorderColor(Color color) {
        verticalLine.setLineColor(color);
    }

    private void updateSummaryVisibility() {
        if (collapsed && !resultSummary.isEmpty()) {
            summaryLabel.setText(truncateSummary(resultSummary));
            summaryLabel.setVisible(true);
        } else {
            summaryLabel.setVisible(false);
        }
    }

    static String truncateSummary(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_SUMMARY_LENGTH) return text;
        return text.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
    }

    // ══════════════════════════════════════════════════════════════
    // Inner class: Step Number Badge (24x24 circle with number)
    // ══════════════════════════════════════════════════════════════
    static class StepNumberBadge extends JPanel {
        private static final int SIZE = 24;
        private final int number;
        private Color badgeColor;

        StepNumberBadge(int number) {
            this.number = number;
            this.badgeColor = BADGE_BG_PENDING;
            setPreferredSize(new Dimension(SIZE, SIZE));
            setMinimumSize(new Dimension(SIZE, SIZE));
            setMaximumSize(new Dimension(SIZE, SIZE));
            setOpaque(false);
        }

        void setBadgeColor(Color color) {
            this.badgeColor = color;
            repaint();
        }

        Color getBadgeColor() {
            return badgeColor;
        }

        int getNumber() {
            return number;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. Soft shadow (offset 1px down, semi-transparent)
            g2.setColor(new Color(0, 0, 0, 30));
            g2.fillOval(0, 1, SIZE, SIZE);

            // 2. Semi-transparent background fill
            Color semiTransparentBg = new Color(
                    badgeColor.getRed(), badgeColor.getGreen(), badgeColor.getBlue(), 200);
            g2.setColor(semiTransparentBg);
            g2.fillOval(0, 0, SIZE, SIZE);

            // 3. 1px highlight border (top half, glass reflection)
            g2.setColor(new Color(255, 255, 255, 80));
            g2.setStroke(new BasicStroke(1f));
            g2.drawArc(1, 1, SIZE - 2, SIZE - 2, 30, 120);

            // 4. Draw centered number text
            g2.setColor(Color.WHITE);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
            String text = String.valueOf(number);
            FontMetrics fm = g2.getFontMetrics();
            int textX = (SIZE - fm.stringWidth(text)) / 2;
            int textY = (SIZE - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, textX, textY);

            g2.dispose();
        }
    }

}
