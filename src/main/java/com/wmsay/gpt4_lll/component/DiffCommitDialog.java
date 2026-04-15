package com.wmsay.gpt4_lll.component;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 带 Diff 对比的文件写入审批确认对话框。
 * 内嵌 IntelliJ DiffRequestPanel 展示修改前后差异，底部保留三个审批按钮。
 */
public class DiffCommitDialog extends DialogWrapper {

    private boolean alwaysAllow = false;

    private final Project project;
    private final ToolCall toolCall;
    private final String originalContent;
    private final String previewContent;
    private final String filePath;
    private final String writeMode;
    private final boolean isNewFile;
    private final Integer lineNumber;

    /**
     * 创建带 Diff 对比的审批对话框。
     *
     * @param project         当前项目
     * @param toolCall        待审批的工具调用
     * @param originalContent 修改前的完整文件内容（新建文件为空字符串）
     * @param previewContent  修改后的完整文件内容（预计算）
     * @param filePath        目标文件的相对路径
     * @param writeMode       写入模式：overwrite / patch / append / insert_after_line
     * @param isNewFile       是否为新建文件
     * @param lineNumber      insert_after_line 模式的行号（其他模式为 null）
     */
    public DiffCommitDialog(
            @NotNull Project project,
            @NotNull ToolCall toolCall,
            @NotNull String originalContent,
            @NotNull String previewContent,
            @NotNull String filePath,
            @NotNull String writeMode,
            boolean isNewFile,
            @Nullable Integer lineNumber) {
        super(project, false);
        this.project = project;
        this.toolCall = toolCall;
        this.originalContent = originalContent;
        this.previewContent = previewContent;
        this.filePath = filePath;
        this.writeMode = writeMode;
        this.isNewFile = isNewFile;
        this.lineNumber = lineNumber;

        setTitle(buildTitle(filePath, isNewFile));
        setOKButtonText("允许 / Allow");
        setCancelButtonText("拒绝 / Deny");
        init();
    }

    // ─── Task 2.2: 对话框标题逻辑（静态方法，便于测试） ───

    /**
     * 根据文件路径和是否新建文件生成对话框标题。
     *
     * @param filePath  目标文件路径
     * @param isNewFile 是否为新建文件
     * @return 对话框标题
     */
    public static String buildTitle(String filePath, boolean isNewFile) {
        String fileName = Paths.get(filePath).getFileName().toString();
        if (isNewFile) {
            return "新建文件 / New File: " + fileName;
        }
        return "文件写入审批 / Write File Approval: " + fileName;
    }

    // ─── Task 2.3: 信息栏文本生成逻辑（静态方法，便于测试） ───

    /**
     * 根据写入模式、文件路径等参数生成信息栏文本行列表。
     *
     * @param writeMode 写入模式
     * @param filePath  目标文件路径
     * @param isNewFile 是否为新建文件
     * @param lineNumber insert_after_line 模式的行号（其他模式为 null）
     * @return 信息栏文本行列表
     */
    public static List<String> buildInfoLines(String writeMode, String filePath,
                                               boolean isNewFile, Integer lineNumber) {
        List<String> lines = new ArrayList<>();
        lines.add("写入模式 / Mode: " + writeMode);
        lines.add("目标文件 / Target: " + filePath);
        if ("insert_after_line".equals(writeMode) && lineNumber != null) {
            lines.add("插入位置 / Insert after line: " + lineNumber);
        }
        if (isNewFile) {
            lines.add("⚠ 此文件将被新建 / This file will be created");
        }
        return lines;
    }

    // ─── Task 2.1: createCenterPanel / createActions / isAlwaysAllow ───

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setMinimumSize(new Dimension(700, 500));
        mainPanel.setPreferredSize(new Dimension(800, 600));

        // 顶部信息栏
        JPanel infoPanel = createInfoPanel();
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        // 主体 Diff 对比面板
        JComponent diffPanel = createDiffPanel();
        mainPanel.add(diffPanel, BorderLayout.CENTER);

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

    // ─── Task 2.3: 信息栏面板创建 ───

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.emptyBottom(8));

        List<String> infoLines = buildInfoLines(writeMode, filePath, isNewFile, lineNumber);
        for (String line : infoLines) {
            JBLabel label = new JBLabel(line);
            if (line.startsWith("⚠")) {
                label.setForeground(new Color(255, 152, 0));
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setBorder(JBUI.Borders.emptyBottom(2));
            panel.add(label);
        }

        return panel;
    }

    // ─── Task 2.4: Diff 对比面板与语法高亮 ───

    private JComponent createDiffPanel() {
        String fileName = Paths.get(filePath).getFileName().toString();
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);

        DiffContent left = DiffContentFactory.getInstance()
                .create(project, originalContent, fileType);
        DiffContent right = DiffContentFactory.getInstance()
                .create(project, previewContent, fileType);

        String dialogTitle = buildTitle(filePath, isNewFile);
        SimpleDiffRequest request = new SimpleDiffRequest(
                dialogTitle,
                left, right,
                "修改前 / Before", "修改后 / After"
        );

        DiffRequestPanel diffPanel = DiffManager.getInstance()
                .createRequestPanel(project, getDisposable(), null);
        diffPanel.setRequest(request);

        return diffPanel.getComponent();
    }
}
