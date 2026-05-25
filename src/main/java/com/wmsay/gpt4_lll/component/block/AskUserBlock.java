package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * "向用户提问"交互块。
 * <p>
 * 由 {@code ask_user} 工具触发渲染。当 Agent 需要用户的澄清、选择或确认时，
 * 通过此块展示问题与可选项按钮，并以 {@link CompletableFuture} 的形式
 * 将用户的选择回传到工具 {@code execute()} 方法，使 ReAct 循环自然暂停-恢复。
 * </p>
 *
 * <p>视觉样式：左侧紫色强调线，与工具调用块（蓝色）、文件变更块（绿色）区分。</p>
 */
public class AskUserBlock implements ContentBlock {

    /**
     * 单个可选项。label 用于按钮展示，value 作为工具结果回传给 LLM。
     */
    public static class Option {
        private final String label;
        private final String value;

        public Option(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }
    }

    private final JPanel wrapper;
    private final JLabel statusLabel;
    private final CompletableFuture<String> userChoice = new CompletableFuture<>();

    private final String question;
    private final List<Option> options;

    /** 左侧强调色：紫色系表示"等待用户"。 */
    private static final JBColor ACCENT_COLOR = new JBColor(
            new Color(0x8B5CF6), new Color(0xA78BFA));

    public AskUserBlock(String question, List<Option> options) {
        this.question = question == null ? "" : question;
        this.options = options;

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
                Dimension pref = getPreferredSize();
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    return new Dimension(p.getWidth(), pref.height);
                }
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_COLOR),
                JBUI.Borders.empty(8, 10)
        ));
        wrapper.setBackground(new JBColor(new Color(0xF5F3FF), new Color(0x2D2540)));

        // --- 标题行 ---
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerPanel.setOpaque(false);
        JLabel iconLabel = new JLabel("\u2753");
        JLabel nameLabel = new JLabel("请选择 / Choose an option");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        nameLabel.setForeground(ACCENT_COLOR);
        headerPanel.add(iconLabel);
        headerPanel.add(nameLabel);
        wrapper.add(headerPanel, BorderLayout.NORTH);

        // --- 问题文本 ---
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(JBUI.Borders.empty(6, 4, 6, 0));

        JTextArea questionArea = new JTextArea(this.question);
        questionArea.setEditable(false);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setOpaque(false);
        questionArea.setFont(questionArea.getFont().deriveFont(13f));
        questionArea.setBorder(null);
        questionArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(questionArea);

        wrapper.add(centerPanel, BorderLayout.CENTER);

        // --- 按钮面板 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttonPanel.setOpaque(false);

        statusLabel = new JLabel();
        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setVisible(false);

        if (options != null) {
            for (Option opt : options) {
                JButton btn = new JButton(opt.getLabel());
                btn.addActionListener(e -> onChoose(opt));
                buttonPanel.add(btn);
            }
        }
        buttonPanel.add(statusLabel);
        wrapper.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void onChoose(Option chosen) {
        // 禁用所有按钮，防止重复点击
        setButtonsEnabled(false);
        statusLabel.setText("\u2713 已选择：" + chosen.getLabel());
        statusLabel.setForeground(ACCENT_COLOR);
        statusLabel.setVisible(true);
        userChoice.complete(chosen.getValue());
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Component c : ((JPanel) wrapper.getComponent(2)).getComponents()) {
            if (c instanceof JButton) {
                c.setEnabled(enabled);
            }
        }
    }

    /**
     * 取消等待（例如用户点击 Stop 按钮或工具超时）。
     */
    public void cancel(String reason) {
        SwingUtilities.invokeLater(() -> {
            setButtonsEnabled(false);
            statusLabel.setText("\u2717 已取消" + (reason != null ? "：" + reason : ""));
            statusLabel.setForeground(JBColor.GRAY);
            statusLabel.setVisible(true);
        });
        userChoice.completeExceptionally(new RuntimeException(
                reason != null ? reason : "ask_user cancelled"));
    }

    @Override
    public BlockType getType() {
        return BlockType.ASK_USER;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    /**
     * 等待用户选择。返回被选中选项的 value。
     */
    public CompletableFuture<String> awaitChoice() {
        return userChoice;
    }

    public String getQuestion() {
        return question;
    }

    public List<Option> getOptions() {
        return options;
    }
}
