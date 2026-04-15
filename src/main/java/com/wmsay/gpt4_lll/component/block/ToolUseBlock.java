package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.PluginIcons;
import com.wmsay.gpt4_lll.component.SpinnerIconAnimator;
import com.wmsay.gpt4_lll.component.theme.GlassPanel;
import com.wmsay.gpt4_lll.component.theme.GlassVerticalLine;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * 工具使用进度块（Liquid Glass 风格）。
 * 在 FC 执行过程中实时展示：工具名称、参数、执行状态。
 * 执行中显示 SVG 旋转动画，完成后显示 SVG 成功/失败图标。
 * 参数区域支持展开/折叠，使用内嵌玻璃层效果。
 */
public class ToolUseBlock implements ContentBlock {

    private final GlassPanel wrapper;
    private final JLabel statusIcon;
    private final JLabel nameLabel;
    private final JPanel paramsPanel;
    private final JLabel durationLabel;
    private boolean paramsCollapsed = true;
    private JLabel toggleLabel;
    private final GlassVerticalLine verticalLine;

    private final SpinnerIconAnimator spinnerAnimator;

    private static final JBColor ACCENT_COLOR = new JBColor(
            new Color(0x2196F3), new Color(0x42A5F5));
    private static final JBColor SUCCESS_COLOR = new JBColor(
            new Color(0x4CAF50), new Color(0x66BB6A));
    private static final JBColor ERROR_COLOR = new JBColor(
            new Color(0xF44336), new Color(0xEF5350));
    private static final JBColor PARAM_FG = new JBColor(
            new Color(0x555555), new Color(0xAAAAAA));

    /** Inner glass layer background for paramsPanel (lower transparency than outer) */
    private static final JBColor PARAMS_INNER_BG = new JBColor(
            new Color(230, 235, 245, 120),  // light: lower alpha than PRIMARY_BG
            new Color(40, 42, 58, 100));     // dark: lower alpha than PRIMARY_BG

    private Runnable onContentChanged;

    public ToolUseBlock(String toolName, Map<String, Object> params) {
        // ── Wrapper: GlassPanel with 8px corner radius ──
        wrapper = new GlassPanel(LiquidGlassTheme.RADIUS_SMALL);
        wrapper.setLayout(new BorderLayout());
        wrapper.setBgColor(LiquidGlassTheme.PRIMARY_BG);

        // ── Left vertical line: GlassVerticalLine, color follows status ──
        verticalLine = new GlassVerticalLine(ACCENT_COLOR);

        // ── Inner content panel (verticalLine + main content) ──
        JPanel innerLayout = new JPanel(new BorderLayout());
        innerLayout.setOpaque(false);
        innerLayout.add(verticalLine, BorderLayout.WEST);

        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.setOpaque(false);
        mainContent.setBorder(JBUI.Borders.empty(8, 10));

        // --- 标题行：旋转图标 + 工具名 + 折叠切换 ---
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerPanel.setOpaque(false);

        statusIcon = new JLabel();
        statusIcon.setIcon(PluginIcons.SPINNER);
        statusIcon.setForeground(ACCENT_COLOR);
        headerPanel.add(statusIcon);

        nameLabel = new JLabel(toolName);
        nameLabel.setIcon(PluginIcons.TOOL);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        nameLabel.setForeground(ACCENT_COLOR);
        headerPanel.add(nameLabel);

        durationLabel = new JLabel();
        durationLabel.setFont(durationLabel.getFont().deriveFont(Font.PLAIN, 10f));
        durationLabel.setForeground(JBColor.GRAY);
        durationLabel.setVisible(false);
        headerPanel.add(durationLabel);

        // 参数折叠切换
        if (params != null && !params.isEmpty()) {
            toggleLabel = new JLabel("▶ params");
            toggleLabel.setFont(toggleLabel.getFont().deriveFont(Font.PLAIN, 10f));
            toggleLabel.setForeground(JBColor.GRAY);
            toggleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    paramsCollapsed = !paramsCollapsed;
                    paramsPanel.setVisible(!paramsCollapsed);
                    toggleLabel.setText(paramsCollapsed ? "▶ params" : "▼ params");
                    wrapper.revalidate();
                    wrapper.repaint();
                }
            });
            headerPanel.add(toggleLabel);
        }

        mainContent.add(headerPanel, BorderLayout.NORTH);

        // --- 参数面板（内嵌玻璃层，默认折叠） ---
        paramsPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(PARAMS_INNER_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            LiquidGlassTheme.RADIUS_SMALL, LiquidGlassTheme.RADIUS_SMALL);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        paramsPanel.setLayout(new BoxLayout(paramsPanel, BoxLayout.Y_AXIS));
        paramsPanel.setOpaque(false);
        paramsPanel.setBorder(JBUI.Borders.empty(6, 4, 6, 0));
        paramsPanel.setVisible(false);
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                JLabel paramLabel = new JLabel("  " + entry.getKey() + ": " + entry.getValue());
                paramLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                paramLabel.setForeground(PARAM_FG);
                paramsPanel.add(paramLabel);
            }
        }
        mainContent.add(paramsPanel, BorderLayout.CENTER);

        innerLayout.add(mainContent, BorderLayout.CENTER);
        wrapper.add(innerLayout, BorderLayout.CENTER);

        // Outer margin
        wrapper.setBorder(JBUI.Borders.empty(2, 0));

        // --- SpinnerIconAnimator 旋转动画 ---
        spinnerAnimator = new SpinnerIconAnimator(PluginIcons.SPINNER, statusIcon);
        spinnerAnimator.start();
    }

    @Override
    public BlockType getType() {
        return BlockType.TOOL_USE;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    /**
     * 标记工具执行完成。停止旋转动画，显示成功/失败图标和耗时。
     * 根据 success 参数渐变背景 tint：成功 → 绿色调，失败 → 红色调。
     * 竖线颜色跟随状态：成功 → SUCCESS，失败 → ERROR。
     */
    public void markCompleted(boolean success, long durationMs) {
        SwingUtilities.invokeLater(() -> {
            spinnerAnimator.stop();
            if (success) {
                statusIcon.setIcon(PluginIcons.SUCCESS);
                statusIcon.setText("");
                statusIcon.setForeground(SUCCESS_COLOR);
                verticalLine.setLineColor(SUCCESS_COLOR);
                wrapper.setBgColor(LiquidGlassTheme.SUCCESS_TINT);
            } else {
                statusIcon.setIcon(PluginIcons.ERROR);
                statusIcon.setText("");
                statusIcon.setForeground(ERROR_COLOR);
                verticalLine.setLineColor(ERROR_COLOR);
                wrapper.setBgColor(LiquidGlassTheme.ERROR_TINT);
            }
            durationLabel.setText("  " + durationMs + "ms");
            durationLabel.setVisible(true);
            wrapper.revalidate();
            wrapper.repaint();
            if (onContentChanged != null) {
                onContentChanged.run();
            }
        });
    }

    public void setOnContentChanged(Runnable callback) {
        this.onContentChanged = callback;
    }

    public void dispose() {
        spinnerAnimator.stop();
    }

    // ── Package-private accessors for testing ──
    GlassVerticalLine getVerticalLine() { return verticalLine; }
    GlassPanel getWrapper() { return wrapper; }
}
