package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * 工具调用审批对话框。
 * 显示工具名称、参数和潜在影响，提供"允许"、"拒绝"和"总是允许"三个选项。
 * 继承 IntelliJ 的 DialogWrapper API。
 */
public class ApprovalDialog extends DialogWrapper {

    /** 用户选择了"总是允许" */
    private boolean alwaysAllow = false;

    private final ToolCall toolCall;

    /**
     * 创建审批对话框。
     *
     * @param project  当前项目
     * @param toolCall 待审批的工具调用
     */
    public ApprovalDialog(@NotNull Project project, @NotNull ToolCall toolCall) {
        super(project, false);
        this.toolCall = toolCall;
        setTitle("工具调用审批 / Tool Call Approval");
        setOKButtonText("允许 / Allow");
        setCancelButtonText("拒绝 / Deny");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(500, 350));
        mainPanel.setBorder(JBUI.Borders.empty(10));

        // 顶部：工具名称
        JBLabel toolNameLabel = new JBLabel("工具 / Tool: " + toolCall.getToolName());
        toolNameLabel.setFont(toolNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        toolNameLabel.setBorder(JBUI.Borders.emptyBottom(8));
        mainPanel.add(toolNameLabel, BorderLayout.NORTH);

        // 中间：参数和潜在影响
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // 参数区域
        centerPanel.add(createSectionLabel("参数 / Parameters:"));
        JTextArea paramsArea = new JTextArea(formatParameters(toolCall.getParameters()));
        paramsArea.setEditable(false);
        paramsArea.setLineWrap(true);
        paramsArea.setWrapStyleWord(true);
        paramsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JBScrollPane paramsScroll = new JBScrollPane(paramsArea);
        paramsScroll.setPreferredSize(new Dimension(480, 150));
        centerPanel.add(paramsScroll);

        centerPanel.add(Box.createVerticalStrut(10));

        // 潜在影响区域
        centerPanel.add(createSectionLabel("潜在影响 / Potential Impact:"));
        JBLabel impactLabel = new JBLabel(describeImpact(toolCall.getToolName()));
        impactLabel.setBorder(JBUI.Borders.empty(4));
        centerPanel.add(impactLabel);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        Action okAction = getOKAction();
        Action cancelAction = getCancelAction();

        Action alwaysAllowAction = new DialogWrapperAction("总是允许 / Always Allow") {
            @Override
            protected void doAction(java.awt.event.ActionEvent e) {
                alwaysAllow = true;
                doOKAction();
            }
        };

        return new Action[]{okAction, alwaysAllowAction, cancelAction};
    }

    /**
     * 用户是否选择了"总是允许"。
     *
     * @return true 表示用户选择了"总是允许"
     */
    public boolean isAlwaysAllow() {
        return alwaysAllow;
    }

    private JBLabel createSectionLabel(String text) {
        JBLabel label = new JBLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(JBUI.Borders.emptyBottom(4));
        return label;
    }

    private String formatParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "(无参数 / no parameters)";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    private String describeImpact(String toolName) {
        if (toolName == null) {
            return "未知影响 / Unknown impact";
        }
        String lower = toolName.toLowerCase();
        if (lower.contains("write") || lower.contains("edit") || lower.contains("modify")) {
            return "⚠ 此工具可能修改文件内容 / This tool may modify file contents";
        } else if (lower.contains("delete") || lower.contains("remove")) {
            return "⚠ 此工具可能删除文件或数据 / This tool may delete files or data";
        } else if (lower.contains("exec") || lower.contains("run") || lower.contains("command")) {
            return "⚠ 此工具可能执行外部命令 / This tool may execute external commands";
        } else if (lower.contains("read") || lower.contains("search") || lower.contains("tree")) {
            return "ℹ 此工具为只读操作 / This tool is read-only";
        }
        return "ℹ 请检查参数确认操作安全 / Please review parameters to confirm safety";
    }
}
