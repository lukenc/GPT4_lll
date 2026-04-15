package com.wmsay.gpt4_lll.component.block;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.fc.core.Message.ContentBlockRecord.FileSnapshotRecord;
import com.wmsay.gpt4_lll.fc.state.FileSnapshot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件变更内容块 — 展示 Agent 在本轮对话中修改的文件列表。
 * 实时模式：接收 FileSnapshot 列表和 Project，支持点击打开 diff（后续任务实现）。
 * 历史模式：仅展示文件列表，不支持 diff（后续任务实现）。
 */
public class FileChangesBlock implements ContentBlock {

    private static final JBColor ACCENT_COLOR = new JBColor(
            new Color(0x2196F3), new Color(0x42A5F5));
    private static final JBColor ADDED_COLOR = new JBColor(
            new Color(0x4CAF50), new Color(0x66BB6A));
    private static final JBColor DELETED_COLOR = new JBColor(
            new Color(0xF44336), new Color(0xEF5350));
    private static final JBColor MODIFIED_COLOR = new JBColor(
            new Color(0x2196F3), new Color(0x42A5F5));
    private static final JBColor HOVER_BG = new JBColor(
            new Color(0xE3EEFF), new Color(0x353849));
    private static final JBColor DIR_FG = new JBColor(
            new Color(0x888888), new Color(0x999999));

    private static final int COLLAPSE_THRESHOLD = 5;

    private final List<FileSnapshot> snapshots;
    private final Project project;
    private final boolean historyMode;

    private final JPanel wrapper;
    private final JPanel fileListPanel;
    private final List<JPanel> fileEntryPanels = new ArrayList<>();
    private JLabel toggleLabel;
    private boolean expanded = false;

    /**
     * 实时模式构造器 — 接收 FileSnapshot 列表和 Project。
     */
    public FileChangesBlock(List<FileSnapshot> snapshots, Project project) {
        this.snapshots = snapshots != null ? snapshots : List.of();
        this.project = project;
        this.historyMode = false;

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
        wrapper.setBackground(new JBColor(new Color(0xF0F4FF), new Color(0x2A2D3E)));

        // --- 标题行：文件图标 + "已修改 N 个文件" ---
        JLabel titleLabel = new JLabel("📁 已修改 " + this.snapshots.size() + " 个文件");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(ACCENT_COLOR);
        titleLabel.setBorder(JBUI.Borders.emptyBottom(6));
        wrapper.add(titleLabel, BorderLayout.NORTH);

        // --- 文件列表面板 ---
        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setOpaque(false);

        for (FileSnapshot snapshot : this.snapshots) {
            JPanel entry = createFileEntry(snapshot);
            fileEntryPanels.add(entry);
            fileListPanel.add(entry);
        }

        // Collapse/expand logic: if more than COLLAPSE_THRESHOLD files, hide extras
        if (this.snapshots.size() > COLLAPSE_THRESHOLD) {
            toggleLabel = new JLabel("展开全部（共 " + this.snapshots.size() + " 个文件）");
            toggleLabel.setForeground(ACCENT_COLOR);
            toggleLabel.setFont(toggleLabel.getFont().deriveFont(Font.PLAIN, 11f));
            toggleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleLabel.setBorder(JBUI.Borders.emptyTop(4));
            toggleLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    expanded = !expanded;
                    updateVisibility();
                }
            });
            fileListPanel.add(toggleLabel);
            updateVisibility();
        }

        wrapper.add(fileListPanel, BorderLayout.CENTER);
    }

    /**
     * 历史模式构造器 — 接收 FileSnapshotRecord 列表，不支持 diff。
     */
    public FileChangesBlock(List<FileSnapshotRecord> records) {
        this.snapshots = List.of();
        this.project = null;
        this.historyMode = true;

        List<FileSnapshotRecord> safeRecords = records != null ? records : List.of();

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
        wrapper.setBackground(new JBColor(new Color(0xF0F4FF), new Color(0x2A2D3E)));

        // --- 标题行 ---
        JLabel titleLabel = new JLabel("📁 已修改 " + safeRecords.size() + " 个文件");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(ACCENT_COLOR);
        titleLabel.setBorder(JBUI.Borders.emptyBottom(6));
        wrapper.add(titleLabel, BorderLayout.NORTH);

        // --- 文件列表面板 ---
        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setOpaque(false);

        for (FileSnapshotRecord record : safeRecords) {
            JPanel entry = createHistoryFileEntry(record);
            fileEntryPanels.add(entry);
            fileListPanel.add(entry);
        }

        // Collapse/expand logic
        if (safeRecords.size() > COLLAPSE_THRESHOLD) {
            toggleLabel = new JLabel("展开全部（共 " + safeRecords.size() + " 个文件）");
            toggleLabel.setForeground(ACCENT_COLOR);
            toggleLabel.setFont(toggleLabel.getFont().deriveFont(Font.PLAIN, 11f));
            toggleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleLabel.setBorder(JBUI.Borders.emptyTop(4));
            toggleLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    expanded = !expanded;
                    updateHistoryVisibility(safeRecords.size());
                }
            });
            fileListPanel.add(toggleLabel);
            updateHistoryVisibility(safeRecords.size());
        }

        wrapper.add(fileListPanel, BorderLayout.CENTER);
    }

    /**
     * 创建历史模式下的单个文件条目面板（不支持 diff）。
     */
    private JPanel createHistoryFileEntry(FileSnapshotRecord record) {
        JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        entry.setOpaque(true);
        entry.setBackground(wrapper.getBackground());
        entry.setCursor(Cursor.getDefaultCursor());

        // 变更类型
        String changeType = record.getChangeType() != null ? record.getChangeType() : "modified";
        Color typeColor;
        String typeLabel;
        switch (changeType) {
            case "added":
                typeColor = ADDED_COLOR;
                typeLabel = "A";
                break;
            case "deleted":
                typeColor = DELETED_COLOR;
                typeLabel = "D";
                break;
            default:
                typeColor = MODIFIED_COLOR;
                typeLabel = "M";
                break;
        }

        JLabel changeBadge = new JLabel(typeLabel);
        changeBadge.setFont(changeBadge.getFont().deriveFont(Font.BOLD, 11f));
        changeBadge.setForeground(typeColor);
        entry.add(changeBadge);

        String fileName = extractFileName(record.getFilePath());
        JLabel fileNameLabel = new JLabel(fileName);
        fileNameLabel.setFont(fileNameLabel.getFont().deriveFont(Font.BOLD, 12f));
        fileNameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fileNameLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JOptionPane.showMessageDialog(
                        entry,
                        "历史记录不支持查看 diff",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });
        entry.add(fileNameLabel);

        String dirPath = extractDirectory(record.getFilePath());
        if (dirPath != null && !dirPath.isEmpty()) {
            JLabel dirLabel = new JLabel(dirPath);
            dirLabel.setFont(dirLabel.getFont().deriveFont(Font.PLAIN, 10f));
            dirLabel.setForeground(DIR_FG);
            dirLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dirLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openFileInEditor(record.getFilePath());
                }
            });
            entry.add(dirLabel);
        }

        // Hover effect
        Color normalBg = wrapper.getBackground();
        entry.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                entry.setBackground(HOVER_BG);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                entry.setBackground(normalBg);
            }
        });

        entry.setMaximumSize(new Dimension(Integer.MAX_VALUE, entry.getPreferredSize().height + 4));
        return entry;
    }

    /**
     * 历史模式下根据 expanded 状态更新可见性。
     */
    private void updateHistoryVisibility(int totalCount) {
        for (int i = 0; i < fileEntryPanels.size(); i++) {
            fileEntryPanels.get(i).setVisible(expanded || i < COLLAPSE_THRESHOLD);
        }
        if (toggleLabel != null) {
            toggleLabel.setText(expanded
                    ? "收起"
                    : "展开全部（共 " + totalCount + " 个文件）");
        }
        wrapper.revalidate();
        wrapper.repaint();
    }

    /**
     * 创建单个文件条目面板。
     */
    private JPanel createFileEntry(FileSnapshot snapshot) {
        JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        entry.setOpaque(true);
        entry.setBackground(wrapper.getBackground());

        // 变更类型判定
        String changeType = determineChangeType(snapshot);
        Color typeColor;
        String typeLabel;
        switch (changeType) {
            case "added":
                typeColor = ADDED_COLOR;
                typeLabel = "A";
                break;
            case "deleted":
                typeColor = DELETED_COLOR;
                typeLabel = "D";
                break;
            default:
                typeColor = MODIFIED_COLOR;
                typeLabel = "M";
                break;
        }

        // [A/D/M 标签]
        JLabel changeBadge = new JLabel(typeLabel);
        changeBadge.setFont(changeBadge.getFont().deriveFont(Font.BOLD, 11f));
        changeBadge.setForeground(typeColor);
        entry.add(changeBadge);

        // [文件名粗体] — 点击打开 diff 对比
        String fileName = extractFileName(snapshot.getFilePath());
        JLabel fileNameLabel = new JLabel(fileName);
        fileNameLabel.setFont(fileNameLabel.getFont().deriveFont(Font.BOLD, 12f));
        fileNameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fileNameLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openDiff(snapshot);
            }
        });
        entry.add(fileNameLabel);

        // [目录路径灰色] — 点击在编辑器中打开文件
        String dirPath = extractDirectory(snapshot.getFilePath());
        if (dirPath != null && !dirPath.isEmpty()) {
            JLabel dirLabel = new JLabel(dirPath);
            dirLabel.setFont(dirLabel.getFont().deriveFont(Font.PLAIN, 10f));
            dirLabel.setForeground(DIR_FG);
            dirLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dirLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openFileInEditor(snapshot.getFilePath());
                }
            });
            entry.add(dirLabel);
        }

        // [撤回按钮] — 将文件恢复到修改前的内容
        JLabel revertLabel = new JLabel("↩ 撤回");
        revertLabel.setFont(revertLabel.getFont().deriveFont(Font.PLAIN, 10f));
        revertLabel.setForeground(DELETED_COLOR);
        revertLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        revertLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                revertFile(snapshot, fileNameLabel, revertLabel);
            }
        });
        entry.add(revertLabel);

        // 鼠标悬停效果（仅视觉反馈，不处理点击）
        Color normalBg = wrapper.getBackground();
        entry.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                entry.setBackground(HOVER_BG);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                entry.setBackground(normalBg);
            }
        });

        // 限制条目最大高度，防止布局膨胀
        entry.setMaximumSize(new Dimension(Integer.MAX_VALUE, entry.getPreferredSize().height + 4));

        return entry;
    }

    /**
     * 判定文件变更类型。
     * originalContent 为 null/空 → "added"
     * newContent 为 null/空 → "deleted"
     * 两者均非空 → "modified"
     */
    public static String determineChangeType(FileSnapshot snapshot) {
        boolean originalEmpty = snapshot.getOriginalContent() == null
                || snapshot.getOriginalContent().isEmpty();
        boolean newEmpty = snapshot.getNewContent() == null
                || snapshot.getNewContent().isEmpty();

        if (originalEmpty && !newEmpty) {
            return "added";
        } else if (!originalEmpty && newEmpty) {
            return "deleted";
        } else {
            return "modified";
        }
    }

    /**
     * 从文件路径中提取短文件名。
     */
    static String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "(unknown)";
        }
        try {
            return Paths.get(filePath).getFileName().toString();
        } catch (Exception e) {
            return filePath;
        }
    }

    /**
     * 从文件路径中提取目录部分。
     */
    static String extractDirectory(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        try {
            java.nio.file.Path parent = Paths.get(filePath).getParent();
            return parent != null ? parent.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public BlockType getType() {
        return BlockType.FILE_CHANGES;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    /**
     * 打开 IntelliJ Diff 对比窗口，展示文件修改前后的内容差异。
     */
    private void openDiff(FileSnapshot snapshot) {
        if (project == null || historyMode) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                String filePath = snapshot.getFilePath();
                String fileName = Paths.get(filePath).getFileName().toString();
                String original = snapshot.getOriginalContent() != null
                        ? snapshot.getOriginalContent() : "";
                String modified = snapshot.getNewContent() != null
                        ? snapshot.getNewContent() : "";

                DiffContent left = DiffContentFactory.getInstance()
                        .create(project, original);
                DiffContent right = DiffContentFactory.getInstance()
                        .create(project, modified);

                SimpleDiffRequest request = new SimpleDiffRequest(
                        "Diff: " + fileName,
                        left, right,
                        "修改前", "修改后"
                );

                DiffManager.getInstance().showDiff(project, request);
            } catch (Exception e) {
                Notifications.Bus.notify(new Notification(
                        "GPT4_LLL", "Diff 打开失败",
                        "无法打开文件对比: " + snapshot.getFilePath(),
                        NotificationType.ERROR
                ), project);
            }
        });
    }

    /**
     * 在 IntelliJ 编辑器中打开指定文件。
     */
    private void openFileInEditor(String filePath) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                // 获取 Project：优先使用实例字段，回退到当前打开的项目
                Project proj = this.project;
                if (proj == null) {
                    Project[] openProjects = com.intellij.openapi.project.ProjectManager
                            .getInstance().getOpenProjects();
                    if (openProjects.length > 0) {
                        proj = openProjects[0];
                    }
                }
                if (proj == null) return;

                java.nio.file.Path path = Paths.get(filePath);
                if (!path.isAbsolute() && proj.getBasePath() != null) {
                    path = Paths.get(proj.getBasePath()).resolve(path);
                }
                VirtualFile vf = LocalFileSystem.getInstance()
                        .findFileByPath(path.toString());
                if (vf != null) {
                    FileEditorManager.getInstance(proj).openFile(vf, true);
                }
            } catch (Exception e) {
                // 静默失败，不打扰用户
            }
        });
    }

    /**
     * 撤回单个文件的修改，将文件内容恢复为修改前的原始内容。
     * 新建文件（originalContent 为空）则删除该文件。
     * 撤回后更新 UI：文件名添加删除线，撤回按钮变为"已撤回"并禁用。
     */
    private void revertFile(FileSnapshot snapshot, JLabel fileNameLabel, JLabel revertLabel) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                Project proj = this.project;
                if (proj == null) {
                    Project[] openProjects = com.intellij.openapi.project.ProjectManager
                            .getInstance().getOpenProjects();
                    if (openProjects.length > 0) {
                        proj = openProjects[0];
                    }
                }

                String filePath = snapshot.getFilePath();
                Path path = Paths.get(filePath);
                if (!path.isAbsolute() && proj != null && proj.getBasePath() != null) {
                    path = Paths.get(proj.getBasePath()).resolve(path);
                }

                String originalContent = snapshot.getOriginalContent();
                boolean isNewFile = (originalContent == null || originalContent.isEmpty());

                if (isNewFile) {
                    // 新建的文件 → 删除
                    Files.deleteIfExists(path);
                } else {
                    // 已有文件 → 写回原始内容
                    Files.writeString(path, originalContent, StandardCharsets.UTF_8);
                }

                // 刷新 VFS
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString());
                if (vf != null) {
                    vf.refresh(false, false);
                }

                // 更新 UI：文件名加删除线，按钮变为"已撤回"
                String name = fileNameLabel.getText();
                fileNameLabel.setText("<html><s>" + name + "</s></html>");
                fileNameLabel.setForeground(DIR_FG);
                revertLabel.setText("✓ 已撤回");
                revertLabel.setForeground(JBColor.GRAY);
                revertLabel.setCursor(Cursor.getDefaultCursor());
                // 移除所有鼠标监听器，防止重复撤回
                for (java.awt.event.MouseListener ml : revertLabel.getMouseListeners()) {
                    revertLabel.removeMouseListener(ml);
                }

            } catch (Exception e) {
                Notifications.Bus.notify(new Notification(
                        "GPT4_LLL", "撤回失败",
                        "无法撤回文件: " + snapshot.getFilePath() + " (" + e.getMessage() + ")",
                        NotificationType.ERROR
                ));
            }
        });
    }

    /**
     * 是否为历史模式。
     */
    public boolean isHistoryMode() {
        return historyMode;
    }

    /**
     * 获取 Project 引用。
     */
    public Project getProject() {
        return project;
    }

    /**
     * 获取文件快照列表。
     */
    public List<FileSnapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * 获取文件列表面板（供测试和后续折叠功能使用）。
     */
    public JPanel getFileListPanel() {
        return fileListPanel;
    }

    /**
     * 获取文件条目面板列表（供测试和后续折叠功能使用）。
     */
    public List<JPanel> getFileEntryPanels() {
        return fileEntryPanels;
    }

    /**
     * 根据 expanded 状态更新文件条目和折叠链接的可见性。
     */
    private void updateVisibility() {
        for (int i = 0; i < fileEntryPanels.size(); i++) {
            fileEntryPanels.get(i).setVisible(expanded || i < COLLAPSE_THRESHOLD);
        }
        if (toggleLabel != null) {
            toggleLabel.setText(expanded
                    ? "收起"
                    : "展开全部（共 " + snapshots.size() + " 个文件）");
        }
        wrapper.revalidate();
        wrapper.repaint();
    }

    /**
     * 是否已展开全部文件条目（供测试使用）。
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * 获取折叠/展开链接标签（供测试使用）。
     */
    public JLabel getToggleLabel() {
        return toggleLabel;
    }
}
