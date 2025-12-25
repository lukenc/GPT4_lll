package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.model.CodeChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Linter 修复建议对话框
 * 以 diff 视图展示变更操作，支持回车键确认应用
 */
public class LinterFixDialog extends DialogWrapper {

    private final Project project;
    private final String originalCode;
    private final int startLine;
    private final Editor editor;
    private final List<CodeChange> changes;
    private final List<Boolean> changeSelections;

    // 颜色定义
    private static final Color DELETE_BG = new JBColor(new Color(255, 220, 220), new Color(80, 40, 40));
    private static final Color INSERT_BG = new JBColor(new Color(220, 255, 220), new Color(40, 80, 40));
    private static final Color MODIFY_OLD_BG = new JBColor(new Color(255, 240, 200), new Color(80, 70, 40));
    private static final Color MODIFY_NEW_BG = new JBColor(new Color(200, 240, 255), new Color(40, 70, 80));

    private EditorEx previewEditor;
    private FileType previewFileType;

    public LinterFixDialog(@NotNull Project project,
                           @NotNull Editor editor,
                           @NotNull String originalCode,
                           @NotNull List<CodeChange> changes,
                           int startLine,
                           int selectionStart,
                           int selectionEnd) {
        super(project, true);
        this.project = project;
        this.originalCode = originalCode;
        this.startLine = startLine;
        this.editor = editor;
        this.changes = new ArrayList<>(changes);
        this.changeSelections = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            this.changeSelections.add(true); // 默认全部勾选
        }

        setTitle("Linter 修复建议 / Linter Fix Suggestions");
        setOKButtonText("应用修复 (Enter) / Apply Fixes");
        setCancelButtonText("取消 / Cancel");

        this.previewFileType = editor.getVirtualFile() != null
                ? editor.getVirtualFile().getFileType()
                : FileTypeManager.getInstance().getFileTypeByExtension("txt");

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(800, 500));
        mainPanel.setBorder(JBUI.Borders.empty(10));

        // 顶部提示
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // 中间区域：上部变更列表，下部预览
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.65);
        splitPane.setBorder(null);

        // 变更列表
        JPanel changesPanel = createChangesPanel();
        JBScrollPane changesScroll = new JBScrollPane(changesPanel);
        changesScroll.setBorder(JBUI.Borders.customLine(JBColor.border()));
        changesScroll.getVerticalScrollBar().setUnitIncrement(16);
        splitPane.setTopComponent(changesScroll);

        // 预览区
        previewEditor = createPreviewEditor(originalCode);
        JComponent previewComponent = previewEditor.getComponent();
        previewComponent.setBorder(JBUI.Borders.customLine(JBColor.border()));

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(JBUI.Borders.empty(8, 0, 0, 0));
        JBLabel previewLabel = new JBLabel("预览 / Preview");
        previewLabel.setBorder(JBUI.Borders.empty(0, 0, 6, 0));
        previewPanel.add(previewLabel, BorderLayout.NORTH);
        previewPanel.add(previewComponent, BorderLayout.CENTER);

        splitPane.setBottomComponent(previewPanel);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 底部统计
        JPanel statsPanel = createStatsPanel();
        mainPanel.add(statsPanel, BorderLayout.SOUTH);

        // 初始化预览
        refreshPreview();

        return mainPanel;
    }

    /**
     * 创建顶部提示面板
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(0, 0, 10, 0));

        JBLabel hintLabel = new JBLabel(
                "<html><b>提示：</b>按 <code>Enter</code> 键应用所有修复 | " +
                "<b>Hint:</b> Press <code>Enter</code> to apply all fixes</html>");
        hintLabel.setForeground(JBColor.GRAY);
        panel.add(hintLabel, BorderLayout.WEST);

        // 图例
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legendPanel.add(createLegendItem("- 删除", DELETE_BG));
        legendPanel.add(createLegendItem("+ 插入", INSERT_BG));
        legendPanel.add(createLegendItem("~ 修改", MODIFY_NEW_BG));
        panel.add(legendPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * 创建图例项
     */
    private JPanel createLegendItem(String text, Color bgColor) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        
        JPanel colorBox = new JPanel();
        colorBox.setPreferredSize(new Dimension(12, 12));
        colorBox.setBackground(bgColor);
        colorBox.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(11f));
        
        item.add(colorBox);
        item.add(label);
        return item;
    }

    /**
     * 创建变更列表面板
     */
    private JPanel createChangesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(JBColor.background());

        // 按行号排序变更
        changes.sort(Comparator.comparingInt(CodeChange::getLineNumber));

        for (int i = 0; i < changes.size(); i++) {
            CodeChange change = changes.get(i);
            JPanel changePanel = createChangeItemPanel(change, i + 1);
            panel.add(changePanel);
            
            // 添加分隔线
            if (i < changes.size() - 1) {
                JSeparator separator = new JSeparator();
                separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                panel.add(separator);
            }
        }

        return panel;
    }

    /**
     * 创建单个变更项面板
     */
    private JPanel createChangeItemPanel(CodeChange change, int index) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        // 左侧：复选框 + 变更信息
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setOpaque(false);

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(true);
        checkBox.addActionListener(e -> {
            changeSelections.set(index - 1, checkBox.isSelected());
            refreshPreview();
        });
        infoPanel.add(checkBox, BorderLayout.WEST);
        
        // 变更类型标签
        JLabel typeLabel = new JLabel(String.format("#%d  %s  行 %d", 
                index, change.getTypeDisplayName(), change.getLineNumber()));
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD, 12f));
        typeLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
        infoPanel.add(typeLabel, BorderLayout.NORTH);

        // 变更原因
        if (change.getReason() != null && !change.getReason().isEmpty()) {
            JLabel reasonLabel = new JLabel("原因: " + change.getReason());
            reasonLabel.setForeground(JBColor.GRAY);
            reasonLabel.setFont(reasonLabel.getFont().deriveFont(11f));
            reasonLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
            infoPanel.add(reasonLabel, BorderLayout.CENTER);
        }

        panel.add(infoPanel, BorderLayout.NORTH);

        // 代码变更显示
        JPanel codePanel = createCodeDisplayPanel(change);
        panel.add(codePanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建代码显示面板
     */
    private JPanel createCodeDisplayPanel(CodeChange change) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(5, 0, 0, 0));

        Font monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);

        switch (change.getType()) {
            case DELETE:
                JPanel deletePanel = createCodeLinePanel("-", change.getLineNumber(), 
                        change.getOriginalContent(), DELETE_BG, monoFont);
                panel.add(deletePanel);
                break;
                
            case INSERT:
                JPanel insertPanel = createCodeLinePanel("+", change.getLineNumber(), 
                        change.getNewContent(), INSERT_BG, monoFont);
                panel.add(insertPanel);
                break;
                
            case MODIFY:
                JPanel oldPanel = createCodeLinePanel("-", change.getLineNumber(), 
                        change.getOriginalContent(), MODIFY_OLD_BG, monoFont);
                panel.add(oldPanel);
                
                panel.add(Box.createVerticalStrut(2));
                
                JPanel newPanel = createCodeLinePanel("+", change.getLineNumber(), 
                        change.getNewContent(), MODIFY_NEW_BG, monoFont);
                panel.add(newPanel);
                break;
        }

        return panel;
    }

    /**
     * 创建代码行显示面板
     */
    private JPanel createCodeLinePanel(String symbol, int lineNum, String content, Color bgColor, Font font) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bgColor);
        panel.setBorder(new EmptyBorder(3, 8, 3, 8));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        // 行号和符号
        JLabel prefixLabel = new JLabel(String.format("%s %4d | ", symbol, lineNum));
        prefixLabel.setFont(font);
        prefixLabel.setForeground(JBColor.DARK_GRAY);
        panel.add(prefixLabel, BorderLayout.WEST);

        // 代码内容
        JLabel codeLabel = new JLabel(content != null ? content : "");
        codeLabel.setFont(font);
        panel.add(codeLabel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建底部统计面板
     */
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(JBUI.Borders.empty(10, 0, 0, 0));

        long deleteCount = changes.stream().filter(c -> c.getType() == CodeChange.ChangeType.DELETE).count();
        long insertCount = changes.stream().filter(c -> c.getType() == CodeChange.ChangeType.INSERT).count();
        long modifyCount = changes.stream().filter(c -> c.getType() == CodeChange.ChangeType.MODIFY).count();

        JBLabel statsLabel = new JBLabel(String.format(
                "共 %d 个变更: %d 删除, %d 插入, %d 修改（可勾选保留/取消）",
                changes.size(), deleteCount, insertCount, modifyCount));
        statsLabel.setForeground(JBColor.GRAY);
        panel.add(statsLabel);

        return panel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected void doOKAction() {
        applyChanges();
        super.doOKAction();
    }

    /**
     * 应用所有变更到编辑器
     */
    private void applyChanges() {
        // 按行号降序排序，从后往前应用变更，避免行号偏移问题
        List<CodeChange> sortedChanges = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            if (Boolean.TRUE.equals(changeSelections.get(i))) {
                sortedChanges.add(changes.get(i));
            }
        }
        sortedChanges.sort((a, b) -> Integer.compare(b.getLineNumber(), a.getLineNumber()));

        if (editor.getProject() == null || editor.getProject().isDisposed()) {
            return;
        }

        Runnable writeTask = () -> WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
            Document document = editor.getDocument();
            for (CodeChange change : sortedChanges) {
                applyChange(document, change);
            }
        });

        // 在与对话框相同的模态状态下执行写操作，避免 NON_MODAL 写安全错误
        if (ApplicationManager.getApplication().isDispatchThread()) {
            writeTask.run();
        } else {
            ApplicationManager.getApplication().invokeLater(
                    writeTask,
                    ModalityState.stateForComponent(getRootPane())
            );
        }
    }

    /**
     * 刷新预览内容
     */
    private void refreshPreview() {
        if (previewEditor == null) {
            return;
        }
        String[] lines = originalCode.split("\\n", -1);
        List<String> lineList = new ArrayList<>();
        for (String line : lines) {
            lineList.add(line);
        }

        // 应用已选中的变更（按行号升序依次应用，并维护偏移）
        List<CodeChange> selectedChanges = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            if (Boolean.TRUE.equals(changeSelections.get(i))) {
                selectedChanges.add(changes.get(i));
            }
        }
        selectedChanges.sort(Comparator.comparingInt(CodeChange::getLineNumber));

        int offset = 0; // 记录因插入/删除导致的行位移
        for (CodeChange change : selectedChanges) {
            int relative = change.getLineNumber() - startLine + offset; // 当前变更的相对行索引
            switch (change.getType()) {
                case DELETE -> {
                    if (relative >= 0 && relative < lineList.size()) {
                        lineList.remove(relative);
                        offset--; // 删除使后续行号前移
                    }
                }
                case INSERT -> {
                    int insertPos = change.getLineNumber() - startLine + offset + 1; // 在该行之后插入
                    insertPos = Math.max(0, Math.min(insertPos, lineList.size()));
                    lineList.add(insertPos, change.getNewContent() == null ? "" : change.getNewContent());
                    offset++; // 插入使后续行号后移
                }
                case MODIFY -> {
                    if (relative >= 0 && relative < lineList.size()) {
                        lineList.set(relative, change.getNewContent() == null ? "" : change.getNewContent());
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineList.size(); i++) {
            sb.append(lineList.get(i));
            if (i < lineList.size() - 1) {
                sb.append("\n");
            }
        }
        Document doc = previewEditor.getDocument();
        ApplicationManager.getApplication().runWriteAction(() -> doc.setText(sb.toString()));
        previewEditor.getCaretModel().moveToOffset(0);
    }

    /**
     * 创建预览编辑器
     */
    private EditorEx createPreviewEditor(String content) {
        Document document = EditorFactory.getInstance().createDocument(content);
        EditorEx editorEx = (EditorEx) EditorFactory.getInstance().createViewer(document, project);
        EditorSettings settings = editorEx.getSettings();
        settings.setLineNumbersShown(true);
        settings.setFoldingOutlineShown(false);
        settings.setIndentGuidesShown(true);
        settings.setLineMarkerAreaShown(false);
        settings.setGutterIconsShown(false);
        settings.setAdditionalColumnsCount(0);
        settings.setAdditionalLinesCount(0);
        settings.setCaretRowShown(false);
        settings.setUseSoftWraps(true);

        editorEx.setHighlighter(EditorHighlighterFactory.getInstance()
                .createEditorHighlighter(project, previewFileType));
        editorEx.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
        return editorEx;
    }

    /**
     * 应用单个变更
     */
    private void applyChange(Document document, CodeChange change) {
        int lineNum = change.getLineNumber() - 1; // 转换为 0-based
        
        // 确保行号有效
        if (lineNum < 0 || lineNum >= document.getLineCount()) {
            return;
        }

        switch (change.getType()) {
            case DELETE:
                deleteLineAt(document, lineNum);
                break;
                
            case INSERT:
                insertLineAfter(document, lineNum, change.getNewContent());
                break;
                
            case MODIFY:
                modifyLineAt(document, lineNum, change.getNewContent());
                break;
        }
    }

    /**
     * 删除指定行
     */
    private void deleteLineAt(Document document, int lineNum) {
        if (lineNum < 0 || lineNum >= document.getLineCount()) {
            return;
        }
        
        int startOffset = document.getLineStartOffset(lineNum);
        int endOffset = document.getLineEndOffset(lineNum);
        
        // 如果不是最后一行，也删除换行符
        if (lineNum < document.getLineCount() - 1) {
            endOffset = document.getLineStartOffset(lineNum + 1);
        } else if (lineNum > 0) {
            // 如果是最后一行，删除前面的换行符
            startOffset = document.getLineEndOffset(lineNum - 1);
        }
        
        document.deleteString(startOffset, endOffset);
    }

    /**
     * 在指定行之后插入新行
     */
    private void insertLineAfter(Document document, int afterLineNum, String content) {
        if (content == null) {
            content = "";
        }
        
        int insertOffset;
        String textToInsert;
        
        if (afterLineNum <= 0) {
            // 在文档开头插入
            insertOffset = 0;
            textToInsert = content + "\n";
        } else if (afterLineNum >= document.getLineCount()) {
            // 在文档末尾插入
            insertOffset = document.getTextLength();
            textToInsert = "\n" + content;
        } else {
            // 在指定行末尾插入
            insertOffset = document.getLineEndOffset(afterLineNum - 1);
            textToInsert = "\n" + content;
        }
        
        document.insertString(insertOffset, textToInsert);
    }

    /**
     * 修改指定行内容
     */
    private void modifyLineAt(Document document, int lineNum, String newContent) {
        if (lineNum < 0 || lineNum >= document.getLineCount()) {
            return;
        }
        
        if (newContent == null) {
            newContent = "";
        }
        
        int startOffset = document.getLineStartOffset(lineNum);
        int endOffset = document.getLineEndOffset(lineNum);
        
        document.replaceString(startOffset, endOffset, newContent);
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel southPanel = new JPanel(new BorderLayout());

        // 快捷键提示
        JBLabel shortcutHint = new JBLabel("快捷键: Enter = 应用修复, Esc = 取消");
        shortcutHint.setForeground(JBColor.GRAY);
        shortcutHint.setBorder(JBUI.Borders.empty(5, 10));
        southPanel.add(shortcutHint, BorderLayout.WEST);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton okButton = new JButton("应用修复 / Apply Fixes");
        okButton.addActionListener(e -> doOKAction());
        okButton.setMnemonic(KeyEvent.VK_ENTER);

        JButton cancelButton = new JButton("取消 / Cancel");
        cancelButton.addActionListener(e -> doCancelAction());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        southPanel.add(buttonPanel, BorderLayout.EAST);

        // 注册 Enter 键监听
        getRootPane().setDefaultButton(okButton);

        return southPanel;
    }

    @Override
    protected void dispose() {
        if (previewEditor != null) {
            EditorFactory.getInstance().releaseEditor(previewEditor);
            previewEditor = null;
        }
        super.dispose();
    }
}
