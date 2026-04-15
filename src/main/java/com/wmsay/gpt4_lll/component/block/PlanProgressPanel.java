package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.PluginIcons;
import com.wmsay.gpt4_lll.component.theme.GlassPanel;
import com.wmsay.gpt4_lll.component.theme.GlassProgressBar;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.fc.events.PlanProgressListener;
import com.wmsay.gpt4_lll.fc.state.PlanStepInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 计划进度面板 — 嵌入对话流的 ContentBlock。
 * 实现 PlanProgressListener 接收 push 事件。
 * 仅依赖 PlanStepInfo DTO，与策略层完全解耦。
 *
 * Liquid Glass 重构：
 * - 外层 wrapper → GlassPanel(RADIUS_LARGE, BlurLevel.MEDIUM)
 * - 双层玻璃标题栏（更高透明度背景）+ 1px 半透明分隔线
 * - GlassProgressBar 替换 JProgressBar
 * - 步骤行悬停高亮 + 状态 tint（IN_PROGRESS→蓝, COMPLETED→绿）
 */
public class PlanProgressPanel implements ContentBlock, PlanProgressListener {

    private final GlassPanel wrapper;
    private final JLabel titleLabel;
    private final JPanel titleBar;
    private final JPanel contentPanel;
    private final JPanel stepListPanel;
    private final GlassProgressBar glassProgressBar;
    private final JLabel statusLabel;

    private boolean collapsed = false;
    private boolean visible = true;

    /** 当不可见时缓存最新状态，恢复可见时 flush */
    private volatile boolean pendingUpdate = false;
    private volatile List<PlanStepInfo> latestSteps = new ArrayList<>();
    private volatile int latestCurrentIndex = -1;
    private volatile boolean planCompleted = false;

    private Runnable onContentChanged;

    /** Hover highlight color for step rows */
    private static final Color HOVER_BG = new JBColor(
            new Color(255, 255, 255, 30), new Color(255, 255, 255, 15));

    /** Double-layer glass title bar background (higher transparency than wrapper) */
    private static final Color TITLE_BAR_BG = new JBColor(
            new Color(255, 255, 255, 50), new Color(255, 255, 255, 20));

    /** 1px separator line color */
    private static final Color SEPARATOR_COLOR = new JBColor(
            new Color(255, 255, 255, 60), new Color(255, 255, 255, 25));


    public PlanProgressPanel(List<PlanStepInfo> initialSteps) {
        this.latestSteps = new ArrayList<>(initialSteps);

        // --- Outer wrapper: GlassPanel with large corner radius ---
        wrapper = new GlassPanel(LiquidGlassTheme.RADIUS_LARGE, GlassPanel.BlurLevel.MEDIUM);
        wrapper.setLayout(new BorderLayout());
        wrapper.setBorder(JBUI.Borders.empty(4, 0));

        // --- Title bar: double-layer glass overlay with separator ---
        titleBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Higher transparency background fill (double-layer glass)
                    g2.setColor(TITLE_BAR_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            LiquidGlassTheme.RADIUS_LARGE, LiquidGlassTheme.RADIUS_LARGE);
                    // 1px semi-transparent separator at bottom
                    g2.setColor(SEPARATOR_COLOR);
                    g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                } finally {
                    g2.dispose();
                }
            }
        };
        titleBar.setOpaque(false);

        titleLabel = new JLabel(buildTitleText(initialSteps.size()));
        titleLabel.setIcon(PluginIcons.PLAN);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(LiquidGlassTheme.ACCENT);
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        titleLabel.setBorder(JBUI.Borders.empty(4, 6));
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleCollapse();
            }
        });
        titleBar.add(titleLabel, BorderLayout.CENTER);
        wrapper.add(titleBar, BorderLayout.NORTH);

        // --- Content panel (collapsible) ---
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(JBUI.Borders.empty(4, 6));

        // Step list area
        stepListPanel = new JPanel();
        stepListPanel.setLayout(new BoxLayout(stepListPanel, BoxLayout.Y_AXIS));
        stepListPanel.setOpaque(false);
        contentPanel.add(stepListPanel);

        // Progress bar area — GlassProgressBar replaces JProgressBar
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setOpaque(false);
        progressPanel.setBorder(JBUI.Borders.empty(6, 0, 2, 0));

        glassProgressBar = new GlassProgressBar(LiquidGlassTheme.ACCENT);
        glassProgressBar.setProgress(0.0);
        progressPanel.add(glassProgressBar, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 10f));
        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setBorder(JBUI.Borders.empty(2, 0, 0, 0));
        progressPanel.add(statusLabel, BorderLayout.SOUTH);

        contentPanel.add(progressPanel);
        wrapper.add(contentPanel, BorderLayout.CENTER);

        // Build initial step rows
        rebuildStepRows(initialSteps);

        // Visibility awareness: pause UI updates when not visible,
        // flush latest state when becoming visible (ThinkingBlock pattern)
        wrapper.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (wrapper.isShowing()) {
                    visible = true;
                    if (pendingUpdate) {
                        pendingUpdate = false;
                        flushLatestState();
                    }
                } else {
                    visible = false;
                }
            }
        });
    }

    // ---- ContentBlock implementation ----

    @Override
    public BlockType getType() {
        return BlockType.PLAN_PROGRESS;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    // ---- PlanProgressListener callbacks (EDT-safe) ----

    @Override
    public void onPlanGenerated(List<PlanStepInfo> steps) {
        updateState(steps, -1, false);
    }

    @Override
    public void onStepStarted(int stepIndex, String description) {
        synchronized (this) {
            latestCurrentIndex = stepIndex;
            updateStepInList(stepIndex, PlanStepInfo.Status.IN_PROGRESS, null);
        }
        scheduleUIUpdate();
    }

    @Override
    public void onStepCompleted(int stepIndex, boolean success, String resultSummary) {
        synchronized (this) {
            PlanStepInfo.Status status = success ? PlanStepInfo.Status.COMPLETED : PlanStepInfo.Status.FAILED;
            updateStepInList(stepIndex, status, resultSummary);
        }
        scheduleUIUpdate();
    }

    @Override
    public void onPlanRevised(List<PlanStepInfo> revisedSteps) {
        updateState(revisedSteps, latestCurrentIndex, false);
    }

    @Override
    public void onPlanCompleted() {
        synchronized (this) {
            planCompleted = true;
        }
        scheduleUIUpdate();
    }

    @Override
    public void onPlanCleared() {
        updateState(new ArrayList<>(), -1, false);
    }

    // ---- Collapse/Expand ----

    public void toggleCollapse() {
        setCollapsed(!collapsed);
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    // ---- Dispose ----

    public void dispose() {
        latestSteps.clear();
        pendingUpdate = false;
    }

    public void setOnContentChanged(Runnable callback) {
        this.onContentChanged = callback;
    }

    // ---- Internal helpers ----

    private synchronized void updateState(List<PlanStepInfo> steps, int currentIndex, boolean completed) {
        this.latestSteps = new ArrayList<>(steps);
        this.latestCurrentIndex = currentIndex;
        this.planCompleted = completed;
        scheduleUIUpdate();
    }

    private void updateStepInList(int stepIndex, PlanStepInfo.Status status, String result) {
        if (stepIndex >= 0 && stepIndex < latestSteps.size()) {
            PlanStepInfo old = latestSteps.get(stepIndex);
            latestSteps.set(stepIndex, new PlanStepInfo(
                    old.getIndex(), old.getDescription(), status,
                    result != null ? result : old.getResult()));
        }
    }

    private void scheduleUIUpdate() {
        if (!visible) {
            pendingUpdate = true;
            return;
        }
        SwingUtilities.invokeLater(this::flushLatestState);
    }

    private void flushLatestState() {
        List<PlanStepInfo> steps;
        int currentIndex;
        boolean completed;
        synchronized (this) {
            steps = new ArrayList<>(latestSteps);
            currentIndex = latestCurrentIndex;
            completed = planCompleted;
        }

        rebuildStepRows(steps);
        updateGlassProgressBar(steps, completed);
        updateStatusLabel(steps, currentIndex, completed);
        titleLabel.setText(buildTitleText(steps.size()));

        wrapper.revalidate();
        wrapper.repaint();
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    private void rebuildStepRows(List<PlanStepInfo> steps) {
        stepListPanel.removeAll();
        for (PlanStepInfo step : steps) {
            stepListPanel.add(createStepRow(step));
            stepListPanel.add(Box.createVerticalStrut(2));
        }
    }

    private JPanel createStepRow(PlanStepInfo step) {
        JPanel row = new JPanel(new BorderLayout(4, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                // Paint status tint background
                Color tint = getStatusTint(step.getStatus());
                if (tint != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(tint);
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                                LiquidGlassTheme.RADIUS_SMALL, LiquidGlassTheme.RADIUS_SMALL);
                    } finally {
                        g2.dispose();
                    }
                }
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(JBUI.Borders.empty(1, 4));

        // Hover highlight effect
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(HOVER_BG);
                row.setOpaque(true);
                row.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setOpaque(false);
                row.repaint();
            }
        });

        // Left: status icon + number
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        leftPanel.setOpaque(false);

        Icon icon = getStatusIcon(step.getStatus());
        JLabel iconLabel = new JLabel(icon);
        leftPanel.add(iconLabel);

        JLabel numberLabel = new JLabel(String.valueOf(step.getIndex() + 1));
        numberLabel.setFont(numberLabel.getFont().deriveFont(Font.BOLD, 10f));
        numberLabel.setForeground(getStepNumberColor(step.getStatus()));
        numberLabel.setHorizontalAlignment(SwingConstants.CENTER);
        numberLabel.setPreferredSize(new Dimension(18, 18));
        numberLabel.setBorder(BorderFactory.createLineBorder(
                getStepNumberColor(step.getStatus()), 1, true));
        leftPanel.add(numberLabel);

        row.add(leftPanel, BorderLayout.WEST);

        // Center: description text
        JLabel descLabel = new JLabel(step.getDescription());
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 11f));
        descLabel.setForeground(getDescriptionColor(step.getStatus()));
        row.add(descLabel, BorderLayout.CENTER);

        return row;
    }

    /** Returns the status tint color for a step row, or null for no tint. */
    private Color getStatusTint(PlanStepInfo.Status status) {
        return switch (status) {
            case IN_PROGRESS -> LiquidGlassTheme.IN_PROGRESS_TINT;
            case COMPLETED -> LiquidGlassTheme.SUCCESS_TINT;
            case FAILED -> LiquidGlassTheme.ERROR_TINT;
            default -> null;
        };
    }

    private void updateGlassProgressBar(List<PlanStepInfo> steps, boolean completed) {
        if (steps.isEmpty()) {
            glassProgressBar.setProgress(0.0);
            return;
        }
        if (completed) {
            glassProgressBar.setProgress(1.0);
            glassProgressBar.setFillColor(LiquidGlassTheme.SUCCESS);
            return;
        }
        long completedCount = steps.stream()
                .filter(s -> s.getStatus() == PlanStepInfo.Status.COMPLETED)
                .count();
        double progress = (double) completedCount / steps.size();
        glassProgressBar.setProgress(progress);
        glassProgressBar.setFillColor(LiquidGlassTheme.ACCENT);
    }

    private void updateStatusLabel(List<PlanStepInfo> steps, int currentIndex, boolean completed) {
        if (completed) {
            statusLabel.setIcon(PluginIcons.STEP_COMPLETED);
            statusLabel.setText("All steps completed");
            statusLabel.setForeground(LiquidGlassTheme.SUCCESS);
            return;
        }
        if (currentIndex >= 0 && currentIndex < steps.size()) {
            PlanStepInfo current = steps.get(currentIndex);
            Icon statusIcon = getStatusIcon(current.getStatus());
            statusLabel.setIcon(statusIcon);
            statusLabel.setText("Step " + (currentIndex + 1) + ": " + current.getDescription());
            statusLabel.setForeground(JBColor.GRAY);
        } else {
            statusLabel.setIcon(null);
            statusLabel.setText(" ");
            statusLabel.setForeground(JBColor.GRAY);
        }
    }

    private void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        contentPanel.setVisible(!collapsed);
        int stepCount;
        synchronized (this) {
            stepCount = latestSteps.size();
        }
        titleLabel.setText(collapsed
                ? "▶ EXECUTION PLAN (" + stepCount + " STEPS) — click to expand"
                : buildTitleText(stepCount));
        wrapper.revalidate();
        wrapper.repaint();
    }

    private String buildTitleText(int stepCount) {
        return "▼ EXECUTION PLAN (" + stepCount + " STEPS)";
    }

    static Icon getStatusIcon(PlanStepInfo.Status status) {
        return switch (status) {
            case PENDING -> PluginIcons.STEP_PENDING;
            case IN_PROGRESS -> PluginIcons.STEP_IN_PROGRESS;
            case COMPLETED -> PluginIcons.STEP_COMPLETED;
            case FAILED -> PluginIcons.STEP_FAILED;
            case SKIPPED -> PluginIcons.STEP_SKIPPED;
        };
    }

    private Color getStepNumberColor(PlanStepInfo.Status status) {
        return switch (status) {
            case COMPLETED -> LiquidGlassTheme.SUCCESS;
            case FAILED -> LiquidGlassTheme.ERROR;
            case IN_PROGRESS -> LiquidGlassTheme.ACCENT;
            default -> JBColor.GRAY;
        };
    }

    private Color getDescriptionColor(PlanStepInfo.Status status) {
        return switch (status) {
            case COMPLETED -> LiquidGlassTheme.SUCCESS;
            case FAILED -> LiquidGlassTheme.ERROR;
            case IN_PROGRESS -> LiquidGlassTheme.ACCENT;
            case SKIPPED -> JBColor.GRAY;
            default -> JBColor.foreground();
        };
    }

    // ---- Package-private accessors for testing ----

    GlassPanel getWrapper() { return wrapper; }
    JPanel getTitleBar() { return titleBar; }
    JPanel getStepListPanel() { return stepListPanel; }
    GlassProgressBar getGlassProgressBar() { return glassProgressBar; }
    JLabel getStatusLabel() { return statusLabel; }
    JLabel getTitleLabel() { return titleLabel; }
    List<PlanStepInfo> getLatestSteps() { return new ArrayList<>(latestSteps); }
}
