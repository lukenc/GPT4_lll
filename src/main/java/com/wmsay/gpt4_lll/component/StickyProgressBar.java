package com.wmsay.gpt4_lll.component;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.block.PlanProgressPanel;
import com.wmsay.gpt4_lll.component.theme.GlassProgressBar;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.fc.events.PlanProgressListener;
import com.wmsay.gpt4_lll.fc.state.PlanStepInfo;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 粘性进度横条 — 当 PlanProgressPanel 滚出可视区时显示在顶部。
 * 实现 PlanProgressListener 同步接收进度事件。
 * Liquid Glass 风格：GlassPanel 半透明磨砂玻璃渲染 + GlassProgressBar + 底部阴影 + 顶部高光。
 */
public class StickyProgressBar extends JPanel implements PlanProgressListener {

    private final JLabel stepNumberLabel;   // 圆形序号
    private final JLabel stepDescLabel;     // 步骤描述
    private final JLabel percentLabel;      // "50% COMPLETE"
    private final GlassProgressBar glassProgressBar; // 底部玻璃质感进度条

    /** Bottom shadow height for Z-axis separation */
    private static final int SHADOW_LAYERS = 3;

    /** 关联的 PlanProgressPanel 引用，用于可见性检测 */
    private PlanProgressPanel trackedPanel;

    /** 是否有活跃计划 */
    private volatile boolean hasActivePlan = false;

    /** 当前步骤索引 (0-based) */
    private volatile int currentStepIndex = -1;
    /** 当前步骤描述 */
    private volatile String currentStepDescription = "";
    /** 总步骤数 */
    private volatile int totalSteps = 0;
    /** 已完成步骤数 */
    private volatile int completedSteps = 0;

    public StickyProgressBar() {
        setLayout(new BorderLayout());
        setOpaque(false);  // GlassPanel 风格：非不透明，由 paintComponent 绘制半透明背景
        setVisible(false);

        // --- Top row: [number] description ... percent ---
        JPanel topRow = new JPanel(new BorderLayout(6, 0));
        topRow.setOpaque(false);
        topRow.setBorder(JBUI.Borders.empty(4, 8, 2, 8));

        // Left side: step number (WEST) + description (CENTER, fills remaining space)
        JPanel leftPanel = new JPanel(new BorderLayout(4, 0));
        leftPanel.setOpaque(false);

        stepNumberLabel = new JLabel();
        stepNumberLabel.setFont(stepNumberLabel.getFont().deriveFont(Font.BOLD, 10f));
        stepNumberLabel.setForeground(JBColor.foreground());
        stepNumberLabel.setHorizontalAlignment(SwingConstants.CENTER);
        stepNumberLabel.setPreferredSize(new Dimension(18, 18));
        stepNumberLabel.setBorder(BorderFactory.createLineBorder(
                LiquidGlassTheme.ACCENT, 1, true));
        leftPanel.add(stepNumberLabel, BorderLayout.WEST);

        stepDescLabel = new JLabel();
        stepDescLabel.setFont(stepDescLabel.getFont().deriveFont(Font.PLAIN, 11f));
        stepDescLabel.setForeground(JBColor.foreground());
        leftPanel.add(stepDescLabel, BorderLayout.CENTER);

        topRow.add(leftPanel, BorderLayout.CENTER);

        // Right side: percent label
        percentLabel = new JLabel();
        percentLabel.setFont(percentLabel.getFont().deriveFont(Font.BOLD, 10f));
        percentLabel.setForeground(LiquidGlassTheme.ACCENT);
        topRow.add(percentLabel, BorderLayout.EAST);

        add(topRow, BorderLayout.CENTER);

        // --- Bottom: GlassProgressBar (replaces JProgressBar) ---
        glassProgressBar = new GlassProgressBar(LiquidGlassTheme.ACCENT);
        glassProgressBar.setPreferredSize(new Dimension(0, 6));
        glassProgressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        add(glassProgressBar, BorderLayout.SOUTH);

        // 确保有固定的首选高度（含底部阴影空间）
        setPreferredSize(new Dimension(0, 38));
    }

    // ---- GlassPanel-style rendering ----

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // 1. Semi-transparent frosted glass background fill
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(LiquidGlassTheme.PRIMARY_BG);
            g2.fillRect(0, 0, w, h);

            // 2. Top highlight reflection strip (gradient fade)
            Color hlColor = LiquidGlassTheme.HIGHLIGHT;
            int fadeH = 4;
            g2.setPaint(new GradientPaint(0, 0, hlColor,
                    0, fadeH, new Color(hlColor.getRed(), hlColor.getGreen(), hlColor.getBlue(), 0)));
            g2.fillRect(0, 0, w, fadeH);

            // 3. Bottom drop shadow (Z-axis separation from scrolling content below)
            for (int i = 0; i < SHADOW_LAYERS; i++) {
                int alpha = Math.max(0, LiquidGlassTheme.SHADOW_COLOR.getAlpha() - i * 10);
                g2.setColor(new Color(0, 0, 0, alpha));
                g2.fillRect(0, h - SHADOW_LAYERS + i, w, 1);
            }
        } finally {
            g2.dispose();
        }
    }

    // ---- Public API ----

    /**
     * 设置跟踪的 PlanProgressPanel 引用。
     */
    public void setTrackedPanel(PlanProgressPanel panel) {
        this.trackedPanel = panel;
    }

    /**
     * 获取跟踪的 PlanProgressPanel 引用。
     */
    public PlanProgressPanel getTrackedPanel() {
        return trackedPanel;
    }

    /**
     * 由 AgentChatView 在滚动事件中调用。
     * panelVisible=true 表示 PlanProgressPanel 在可视区域内 → 隐藏横条。
     * panelVisible=false 且有活跃计划 → 显示横条。
     */
    public void updateVisibility(boolean panelVisible) {
        boolean shouldShow = !panelVisible && hasActivePlan;
        if (shouldShow != isVisible()) {
            setVisible(shouldShow);
            // 通知父 JLayeredPane 重新布局，确保 doLayout() 被调用以定位此组件
            Container parent = getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }
    }

    // ---- PlanProgressListener callbacks (EDT-safe) ----

    @Override
    public void onPlanGenerated(List<PlanStepInfo> steps) {
        SwingUtilities.invokeLater(() -> {
            hasActivePlan = true;
            totalSteps = steps.size();
            completedSteps = 0;
            currentStepIndex = -1;
            currentStepDescription = "";
            refreshDisplay();
        });
    }

    @Override
    public void onStepStarted(int stepIndex, String description) {
        SwingUtilities.invokeLater(() -> {
            currentStepIndex = stepIndex;
            currentStepDescription = description != null ? description : "";
            refreshDisplay();
        });
    }

    @Override
    public void onStepCompleted(int stepIndex, boolean success, String resultSummary) {
        SwingUtilities.invokeLater(() -> {
            if (success) {
                completedSteps = Math.min(completedSteps + 1, totalSteps);
            }
            refreshDisplay();
        });
    }

    @Override
    public void onPlanRevised(List<PlanStepInfo> revisedSteps) {
        SwingUtilities.invokeLater(() -> {
            totalSteps = revisedSteps.size();
            completedSteps = (int) revisedSteps.stream()
                    .filter(s -> s.getStatus() == PlanStepInfo.Status.COMPLETED)
                    .count();
            // Find current in-progress step
            currentStepIndex = -1;
            currentStepDescription = "";
            for (PlanStepInfo step : revisedSteps) {
                if (step.getStatus() == PlanStepInfo.Status.IN_PROGRESS) {
                    currentStepIndex = step.getIndex();
                    currentStepDescription = step.getDescription();
                    break;
                }
            }
            refreshDisplay();
        });
    }

    @Override
    public void onPlanCompleted() {
        SwingUtilities.invokeLater(() -> {
            hasActivePlan = false;
            setVisible(false);
        });
    }

    @Override
    public void onPlanCleared() {
        SwingUtilities.invokeLater(() -> {
            hasActivePlan = false;
            currentStepIndex = -1;
            currentStepDescription = "";
            totalSteps = 0;
            completedSteps = 0;
            setVisible(false);
        });
    }

    // ---- Internal ----

    private void refreshDisplay() {
        // Step number (1-based)
        if (currentStepIndex >= 0) {
            stepNumberLabel.setText(String.valueOf(currentStepIndex + 1));
        } else {
            stepNumberLabel.setText("-");
        }

        // Step description
        stepDescLabel.setText(currentStepDescription);

        // Percent
        int percent = totalSteps > 0 ? Math.min(100, completedSteps * 100 / totalSteps) : 0;
        percentLabel.setText(percent + "% COMPLETE");

        // Progress bar
        glassProgressBar.setProgress(percent / 100.0);

        revalidate();
        repaint();
        // 通知父 JLayeredPane 重新布局
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    // ---- Package-private accessors for testing ----

    JLabel getStepNumberLabel() { return stepNumberLabel; }
    JLabel getStepDescLabel() { return stepDescLabel; }
    JLabel getPercentLabel() { return percentLabel; }
    GlassProgressBar getGlassProgressBar() { return glassProgressBar; }
    boolean hasActivePlan() { return hasActivePlan; }
    int getCurrentStepIndex() { return currentStepIndex; }
    String getCurrentStepDescription() { return currentStepDescription; }
    int getTotalSteps() { return totalSteps; }
    int getCompletedSteps() { return completedSteps; }
}
