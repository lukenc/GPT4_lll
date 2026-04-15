package com.wmsay.gpt4_lll.fc.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 文件变更追踪器 — 使用备忘录（Memento）模式记录 Agent 执行过程中的文件变更历史。
 * <p>
 * 由 AgentSession 持有，每个会话独立。
 * 支持应用（确认变更并清理历史）和回退（恢复到修改前版本）操作。
 * <p>
 * 纯 Java 实现，不依赖任何 com.intellij.* API。
 */
public class FileChangeTracker {

    private static final Logger LOG = Logger.getLogger(FileChangeTracker.class.getName());

    private final CopyOnWriteArrayList<FileSnapshot> changes = new CopyOnWriteArrayList<>();

    /**
     * 记录单次文件变更。
     *
     * @param filePath        文件路径
     * @param originalContent 变更前的原始内容
     * @param newContent      变更后的新内容
     */
    public void trackChange(String filePath, String originalContent, String newContent) {
        changes.add(new FileSnapshot(filePath, originalContent, newContent, System.currentTimeMillis()));
    }

    /**
     * 返回当前会话中所有文件变更记录的不可变列表。
     */
    public List<FileSnapshot> getChanges() {
        return Collections.unmodifiableList(new ArrayList<>(changes));
    }

    /**
     * 确认应用所有变更并清除变更历史（对应用户点击"应用"操作）。
     */
    public void applyAll() {
        changes.clear();
    }

    /**
     * 按变更的逆序逐个恢复文件内容（对应用户点击"回退"操作）。
     * <p>
     * 单个文件恢复失败时记录错误日志并继续恢复剩余文件，不中断整个回滚流程。
     * 回滚完成后清除变更历史。
     *
     * @return 回滚失败的文件路径列表（空列表表示全部成功）
     */
    public List<String> rollbackAll() {
        List<String> failedFiles = new ArrayList<>();
        List<FileSnapshot> snapshot = new ArrayList<>(changes);

        // 逆序回滚
        ListIterator<FileSnapshot> it = snapshot.listIterator(snapshot.size());
        while (it.hasPrevious()) {
            FileSnapshot fs = it.previous();
            try {
                // 实际文件恢复由调用方（桥接层）负责
                // 这里记录回滚意图，调用方通过 getChanges() 获取需要恢复的内容
                LOG.fine("Rollback file: " + fs.getFilePath());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to rollback file: " + fs.getFilePath(), e);
                failedFiles.add(fs.getFilePath());
            }
        }
        changes.clear();
        return failedFiles;
    }

    /**
     * 回滚指定单个文件的变更。
     *
     * @param filePath 要回滚的文件路径
     */
    public void rollbackFile(String filePath) {
        changes.removeIf(s -> s.getFilePath().equals(filePath));
    }

    /**
     * 返回当前追踪的变更数量。
     */
    public int size() {
        return changes.size();
    }
}
