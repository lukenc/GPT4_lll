package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * 工具使用进度块。
 * 在 FC 执行过程中实时展示：工具名称、参数、执行状态。
 * 执行中显示旋转动画，完成后显示 ✓ 或 ✗ 状态。
 * 参数区域支持展开/折叠。
 */
public class ToolUseBlock implements ContentBlock {

    private final JPanel wrapper;
    private final JLabel statusIcon;
    private final JLabel nameLabel;
    private final JPanel paramsPanel;
    private final JLabel durationLabel;
    private boolean paramsCollapsed = true;
    private JLabel toggleLabel;

    /** 旋转动画帧 */
    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private int spinnerIndex = 0;
    private final Timer spinnerTimer;

    private static final JBColor ACCENT_COLOR = new JBColor(
            new Color(0x2196F3), new Color(0x42A5F5));
    private static final JBColor SUCCESS_COLOR = new JBColor(
            new Color(0x4CAF50), new Color(0x66BB6A));
    private static final JBColor ERROR_COLOR = new JBColor(
            new Color(0xF44336), new Color(0xEF5350));
    private static final JBColor PARAM_FG = new JBColor(
            new Color(0x555555), new Color(0xAAAAAA));

    private Runnable onContentChanged;

    public ToolUseBlock(String toolName, Map<String, Object> params) {
        wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_COLOR),
                JBUI.Borders.empty(8, 10)
        ));
        wrapper.setBackground(new JBColor(new Color(0xF0F4FF), new Color(0x2A2D3E)));

        // --- 标题行：旋转图标 + 工具名 + 折叠切换 ---
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerPanel.setOpaque(false);

        statusIcon = new JLabel(SPINNER[0]);
        statusIcon.setFont(statusIcon.getFont().deriveFont(Font.PLAIN, 13f));
        statusIcon.setForeground(ACCENT_COLOR);
        headerPanel.add(statusIcon);

        nameLabel = new JLabel("🔧 " + toolName);
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

        wrapper.add(headerPanel, BorderLayout.NORTH);

        // --- 参数面板（默认折叠） ---
        paramsPanel = new JPanel();
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
        wrapper.add(paramsPanel, BorderLayout.CENTER);

        // --- 旋转动画定时器 ---
        spinnerTimer = new Timer(100, e -> {
            spinnerIndex = (spinnerIndex + 1) % SPINNER.length;
            statusIcon.setText(SPINNER[spinnerIndex]);
        });
        spinnerTimer.start();
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
     */
    public void markCompleted(boolean success, long durationMs) {
        SwingUtilities.invokeLater(() -> {
            spinnerTimer.stop();
            if (success) {
                statusIcon.setText("✓");
                statusIcon.setForeground(SUCCESS_COLOR);
            } else {
                statusIcon.setText("✗");
                statusIcon.setForeground(ERROR_COLOR);
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
        spinnerTimer.stop();
    }
}
