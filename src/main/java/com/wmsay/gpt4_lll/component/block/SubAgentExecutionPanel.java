package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.PluginIcons;
import com.wmsay.gpt4_lll.component.SpinnerIconAnimator;
import com.wmsay.gpt4_lll.component.theme.GlassPanel;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.fc.events.SubAgentProgressListener;
import com.wmsay.gpt4_lll.fc.state.SubAgentProgressSnapshot.SubAgentPhase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 子 Agent 执行面板 — 嵌入 TurnPanel 的可折叠 ContentBlock。
 * 实现 SubAgentProgressListener 接收 push 事件。
 *
 * <ul>
 *   <li>默认折叠，显示 Skill 名称 + 当前状态摘要</li>
 *   <li>动态生成的 Skill 在名称旁显示特殊徽章</li>
 *   <li>展开后显示流式输出和状态变化时间线</li>
 *   <li>完成后显示执行结果摘要（Skill 名称、耗时、成功/失败）</li>
 * </ul>
 *
 * Liquid Glass 风格：GlassPanel 外层 + 双层玻璃标题栏 + 状态 tint。
 */
public class SubAgentExecutionPanel implements ContentBlock, SubAgentProgressListener {

    private final GlassPanel wrapper;
    private final JLabel titleLabel;
    private final JPanel titleBar;
    private final JPanel contentPanel;
    private final JPanel timelinePanel;
    private final JTextArea outputArea;
    private final JLabel resultLabel;

    private boolean collapsed = true; // 默认折叠
    private boolean visible = false;
    private volatile boolean pendingUpdate = false;

    // State fields (synchronized access)
    private volatile String skillName;
    private volatile boolean generated;
    private volatile SubAgentPhase phase = SubAgentPhase.MATCHING;
    private volatile long startTimeMs;
    private volatile long durationMs;
    private volatile boolean success;
    private final StringBuilder outputBuffer = new StringBuilder();
    private final List<TimelineEntry> timeline = new ArrayList<>();

    private Runnable onContentChanged;
    private SpinnerIconAnimator spinnerAnimator;

    /** Generated skill badge colors */
    private static final JBColor BADGE_BG = new JBColor(
            new Color(255, 152, 0, 40), new Color(255, 152, 0, 30));
    private static final JBColor BADGE_FG = new JBColor(
            new Color(255, 152, 0), new Color(255, 183, 77));

    /** Double-layer glass title bar background */
    private static final Color TITLE_BAR_BG = new JBColor(
            new Color(255, 255, 255, 50), new Color(255, 255, 255, 20));

    /** 1px separator line color */
    private static final Color SEPARATOR_COLOR = new JBColor(
            new Color(255, 255, 255, 60), new Color(255, 255, 255, 25));

    /** Timeline entry: phase name + timestamp */
    private record TimelineEntry(String phaseName, long timestampMs) {}

    public SubAgentExecutionPanel(String skillName, boolean generated) {
        this.skillName = skillName;
        this.generated = generated;
        this.startTimeMs = System.currentTimeMillis();

        // --- Outer wrapper: GlassPanel ---
        wrapper = new GlassPanel(LiquidGlassTheme.RADIUS_LARGE, GlassPanel.BlurLevel.MEDIUM);
        wrapper.setLayout(new BorderLayout());
        wrapper.setBorder(JBUI.Borders.empty(4, 0));

        // --- Title bar: double-layer glass ---
        titleBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(TITLE_BAR_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            LiquidGlassTheme.RADIUS_LARGE, LiquidGlassTheme.RADIUS_LARGE);
                    g2.setColor(SEPARATOR_COLOR);
                    g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                } finally {
                    g2.dispose();
                }
            }
        };
        titleBar.setOpaque(false);

        // Title label with skill name
        JPanel titleContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titleContent.setOpaque(false);

        titleLabel = new JLabel(buildCollapsedTitle());
        titleLabel.setIcon(PluginIcons.TOOL);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(LiquidGlassTheme.ACCENT);
        titleContent.add(titleLabel);

        // Generated badge
        if (generated) {
            JLabel badge = createGeneratedBadge();
            titleContent.add(badge);
        }

        titleContent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        titleContent.setBorder(JBUI.Borders.empty(4, 6));
        titleContent.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleCollapse();
            }
        });
        titleBar.add(titleContent, BorderLayout.CENTER);
        wrapper.add(titleBar, BorderLayout.NORTH);

        // --- Content panel (collapsible, default hidden) ---
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(JBUI.Borders.empty(4, 6));
        contentPanel.setVisible(false); // default collapsed

        // Timeline panel
        timelinePanel = new JPanel();
        timelinePanel.setLayout(new BoxLayout(timelinePanel, BoxLayout.Y_AXIS));
        timelinePanel.setOpaque(false);
        contentPanel.add(timelinePanel);

        // Streaming output area
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setOpaque(false);
        outputArea.setFont(outputArea.getFont().deriveFont(Font.PLAIN, 11f));
        outputArea.setForeground(LiquidGlassTheme.FOREGROUND);
        outputArea.setBorder(JBUI.Borders.empty(4, 0));

        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setPreferredSize(new Dimension(0, 120));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        contentPanel.add(scrollPane);

        // Result summary label (shown on completion)
        resultLabel = new JLabel(" ");
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.PLAIN, 10f));
        resultLabel.setForeground(JBColor.GRAY);
        resultLabel.setBorder(JBUI.Borders.empty(4, 0));
        resultLabel.setVisible(false);
        contentPanel.add(resultLabel);

        wrapper.add(contentPanel, BorderLayout.CENTER);

        // Add initial timeline entry
        addTimelineEntry("匹配 Skill");

        // Visibility awareness
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
        return BlockType.SUB_AGENT_EXECUTION;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    // ---- SubAgentProgressListener callbacks ----

    @Override
    public void onSubAgentStarting(String skillName, boolean generated) {
        synchronized (this) {
            this.skillName = skillName;
            this.generated = generated;
            this.phase = SubAgentPhase.CREATING;
            this.startTimeMs = System.currentTimeMillis();
        }
        addTimelineEntry("创建子 Agent");
        scheduleUIUpdate();
    }

    @Override
    public void onSubAgentTextDelta(String delta) {
        synchronized (outputBuffer) {
            outputBuffer.append(delta);
        }
        synchronized (this) {
            if (phase == SubAgentPhase.CREATING) {
                phase = SubAgentPhase.EXECUTING;
                addTimelineEntry("执行中");
            }
        }
        scheduleUIUpdate();
    }

    @Override
    public void onSubAgentCompleted(String skillName, boolean success, long durationMs) {
        synchronized (this) {
            this.phase = success ? SubAgentPhase.COMPLETED : SubAgentPhase.FAILED;
            this.success = success;
            this.durationMs = durationMs;
        }
        addTimelineEntry(success ? "完成" : "失败");
        scheduleUIUpdate();
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
        stopSpinner();
        synchronized (outputBuffer) {
            outputBuffer.setLength(0);
        }
        synchronized (timeline) {
            timeline.clear();
        }
        pendingUpdate = false;
    }

    public void setOnContentChanged(Runnable callback) {
        this.onContentChanged = callback;
    }

    // ---- Internal helpers ----

    private void addTimelineEntry(String phaseName) {
        synchronized (timeline) {
            timeline.add(new TimelineEntry(phaseName, System.currentTimeMillis()));
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
        // Update title
        SubAgentPhase currentPhase;
        boolean currentSuccess;
        long currentDuration;
        String currentSkillName;
        synchronized (this) {
            currentPhase = phase;
            currentSuccess = success;
            currentDuration = durationMs;
            currentSkillName = skillName;
        }

        titleLabel.setText(collapsed
                ? buildCollapsedTitle(currentSkillName, currentPhase, currentSuccess, currentDuration)
                : buildExpandedTitle(currentSkillName));

        // Update spinner for in-progress states
        if (currentPhase == SubAgentPhase.CREATING || currentPhase == SubAgentPhase.EXECUTING) {
            startSpinner();
        } else {
            stopSpinner();
            updateTitleIcon(currentPhase, currentSuccess);
        }

        // Update output area
        String output;
        synchronized (outputBuffer) {
            output = outputBuffer.toString();
        }
        if (!output.equals(outputArea.getText())) {
            outputArea.setText(output);
            outputArea.setCaretPosition(output.length());
        }

        // Update timeline
        rebuildTimeline();

        // Update result label on completion
        if (currentPhase == SubAgentPhase.COMPLETED || currentPhase == SubAgentPhase.FAILED) {
            resultLabel.setVisible(true);
            resultLabel.setIcon(currentSuccess ? PluginIcons.SUCCESS : PluginIcons.ERROR);
            resultLabel.setText(buildResultSummary(currentSkillName, currentSuccess, currentDuration));
            resultLabel.setForeground(currentSuccess ? LiquidGlassTheme.SUCCESS : LiquidGlassTheme.ERROR);
        }

        wrapper.revalidate();
        wrapper.repaint();
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    private void rebuildTimeline() {
        timelinePanel.removeAll();
        List<TimelineEntry> entries;
        synchronized (timeline) {
            entries = new ArrayList<>(timeline);
        }
        for (TimelineEntry entry : entries) {
            timelinePanel.add(createTimelineRow(entry));
            timelinePanel.add(Box.createVerticalStrut(2));
        }
    }

    private JPanel createTimelineRow(TimelineEntry entry) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setBorder(JBUI.Borders.empty(1, 4));

        // Dot indicator
        JLabel dot = new JLabel("●");
        dot.setFont(dot.getFont().deriveFont(Font.PLAIN, 8f));
        dot.setForeground(LiquidGlassTheme.ACCENT);
        row.add(dot, BorderLayout.WEST);

        // Phase name
        JLabel phaseLabel = new JLabel(entry.phaseName());
        phaseLabel.setFont(phaseLabel.getFont().deriveFont(Font.PLAIN, 11f));
        phaseLabel.setForeground(LiquidGlassTheme.FOREGROUND);
        row.add(phaseLabel, BorderLayout.CENTER);

        // Relative time
        long elapsed = entry.timestampMs() - startTimeMs;
        JLabel timeLabel = new JLabel("+" + elapsed + "ms");
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 9f));
        timeLabel.setForeground(JBColor.GRAY);
        row.add(timeLabel, BorderLayout.EAST);

        return row;
    }

    private JLabel createGeneratedBadge() {
        JLabel badge = new JLabel("Generated") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BADGE_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            LiquidGlassTheme.RADIUS_SMALL, LiquidGlassTheme.RADIUS_SMALL);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 9f));
        badge.setForeground(BADGE_FG);
        badge.setBorder(JBUI.Borders.empty(1, 4, 1, 4));
        badge.setOpaque(false);
        return badge;
    }

    private void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        contentPanel.setVisible(!collapsed);

        SubAgentPhase currentPhase;
        boolean currentSuccess;
        long currentDuration;
        String currentSkillName;
        synchronized (this) {
            currentPhase = phase;
            currentSuccess = success;
            currentDuration = durationMs;
            currentSkillName = skillName;
        }

        titleLabel.setText(collapsed
                ? buildCollapsedTitle(currentSkillName, currentPhase, currentSuccess, currentDuration)
                : buildExpandedTitle(currentSkillName));

        wrapper.revalidate();
        wrapper.repaint();
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    private String buildCollapsedTitle() {
        return buildCollapsedTitle(skillName, phase, success, durationMs);
    }

    private String buildCollapsedTitle(String name, SubAgentPhase phase, boolean success, long duration) {
        String statusText = switch (phase) {
            case MATCHING -> "匹配中...";
            case CREATING -> "创建中...";
            case EXECUTING -> "执行中...";
            case COMPLETED -> success ? "✓ 完成 (" + duration + "ms)" : "✗ 失败 (" + duration + "ms)";
            case FAILED -> "✗ 失败 (" + duration + "ms)";
        };
        return "▶ Sub-Agent: " + name + " — " + statusText;
    }

    private String buildExpandedTitle(String name) {
        return "▼ Sub-Agent: " + name;
    }

    private String buildResultSummary(String name, boolean success, long duration) {
        return "Skill: " + name + " | " + (success ? "成功" : "失败") + " | 耗时: " + duration + "ms";
    }

    private void updateTitleIcon(SubAgentPhase phase, boolean success) {
        switch (phase) {
            case COMPLETED -> titleLabel.setIcon(success ? PluginIcons.SUCCESS : PluginIcons.ERROR);
            case FAILED -> titleLabel.setIcon(PluginIcons.ERROR);
            default -> titleLabel.setIcon(PluginIcons.TOOL);
        }
    }

    private void startSpinner() {
        if (spinnerAnimator == null) {
            spinnerAnimator = new SpinnerIconAnimator(PluginIcons.SPINNER, titleLabel);
            spinnerAnimator.start();
        }
    }

    private void stopSpinner() {
        if (spinnerAnimator != null) {
            spinnerAnimator.stop();
            spinnerAnimator = null;
        }
    }

    // ---- Package-private accessors for testing ----

    GlassPanel getWrapper() { return wrapper; }
    JPanel getTitleBar() { return titleBar; }
    JPanel getContentPanel() { return contentPanel; }
    JPanel getTimelinePanel() { return timelinePanel; }
    JTextArea getOutputArea() { return outputArea; }
    JLabel getResultLabel() { return resultLabel; }
    JLabel getTitleLabel() { return titleLabel; }
    String getSkillName() { return skillName; }
    boolean isGenerated() { return generated; }
    SubAgentPhase getPhase() { return phase; }
    boolean isSuccess() { return success; }
    long getDurationMs() { return durationMs; }
}
