package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具调用内容块。
 * 展示工具名称、参数，提供"允许执行"和"拒绝"按钮。
 * 通过 CompletableFuture 将用户审批结果传递给 AgentExecutor。
 * <p>
 * 视觉样式：左侧蓝色强调线 + 圆角边框 + 等宽参数显示。
 */
public class ToolCallBlock implements ContentBlock {

    private final JPanel wrapper;
    private final JButton approveBtn;
    private final JButton rejectBtn;
    private final JLabel statusLabel;
    private final CompletableFuture<Boolean> userDecision = new CompletableFuture<>();

    private final String toolName;
    private final String toolCallId;
    private final Map<String, Object> params;

    /** 参数面板，支持展开/折叠 */
    private final JPanel paramsPanel;
    private boolean paramsCollapsed = false;

    /** 左侧强调色：蓝色系表示工具调用 */
    private static final JBColor ACCENT_COLOR = new JBColor(
            new Color(0x2196F3), new Color(0x42A5F5));

    public ToolCallBlock(String toolCallId, String toolName, Map<String, Object> params) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.params = params;

        wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_COLOR),
                JBUI.Borders.empty(8, 10)
        ));
        wrapper.setBackground(new JBColor(new Color(0xF0F4FF), new Color(0x2A2D3E)));

        // --- 标题行：图标 + 工具名 ---
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerPanel.setOpaque(false);
        JLabel iconLabel = new JLabel("\uD83D\uDD27");
        JLabel nameLabel = new JLabel("Tool Call: " + toolName);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        nameLabel.setForeground(ACCENT_COLOR);
        headerPanel.add(iconLabel);
        headerPanel.add(nameLabel);

        // 参数折叠切换标签
        if (params != null && !params.isEmpty()) {
            JLabel toggleLabel = new JLabel("▼");
            toggleLabel.setFont(toggleLabel.getFont().deriveFont(Font.PLAIN, 10f));
            toggleLabel.setForeground(JBColor.GRAY);
            toggleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    paramsCollapsed = !paramsCollapsed;
                    paramsPanel.setVisible(!paramsCollapsed);
                    toggleLabel.setText(paramsCollapsed ? "▶" : "▼");
                    wrapper.revalidate();
                    wrapper.repaint();
                }
            });
            headerPanel.add(toggleLabel);
        }

        wrapper.add(headerPanel, BorderLayout.NORTH);

        // --- 参数面板：等宽字体显示 ---
        paramsPanel = new JPanel();
        paramsPanel.setLayout(new BoxLayout(paramsPanel, BoxLayout.Y_AXIS));
        paramsPanel.setOpaque(false);
        paramsPanel.setBorder(JBUI.Borders.empty(6, 4, 6, 0));
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                JLabel paramLabel = new JLabel("  " + entry.getKey() + ": " + entry.getValue());
                paramLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                paramLabel.setForeground(new JBColor(new Color(0x555555), new Color(0xAAAAAA)));
                paramsPanel.add(paramLabel);
            }
        }
        wrapper.add(paramsPanel, BorderLayout.CENTER);

        // --- 按钮面板 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttonPanel.setOpaque(false);
        approveBtn = new JButton("\u2713 允许执行 / Approve");
        rejectBtn = new JButton("\u2717 拒绝 / Reject");
        statusLabel = new JLabel();
        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setVisible(false);

        approveBtn.addActionListener(e -> {
            approveBtn.setEnabled(false);
            rejectBtn.setEnabled(false);
            statusLabel.setText("⏳ 执行中...");
            statusLabel.setVisible(true);
            userDecision.complete(true);
        });

        rejectBtn.addActionListener(e -> {
            approveBtn.setEnabled(false);
            rejectBtn.setEnabled(false);
            statusLabel.setText("✗ 已拒绝");
            statusLabel.setVisible(true);
            userDecision.complete(false);
        });

        buttonPanel.add(approveBtn);
        buttonPanel.add(rejectBtn);
        buttonPanel.add(statusLabel);
        wrapper.add(buttonPanel, BorderLayout.SOUTH);
    }

    @Override
    public BlockType getType() {
        return BlockType.TOOL_CALL;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    public CompletableFuture<Boolean> awaitDecision() {
        return userDecision;
    }

    public void markExecuted() {
        SwingUtilities.invokeLater(() -> {
            approveBtn.setVisible(false);
            rejectBtn.setVisible(false);
            statusLabel.setText("✓ " + toolName + " - 已执行");
            statusLabel.setForeground(ACCENT_COLOR);
            statusLabel.setVisible(true);
        });
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
