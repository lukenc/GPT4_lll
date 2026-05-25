package com.wmsay.gpt4_lll.fc.state;

import com.wmsay.gpt4_lll.fc.events.SubAgentProgressListener;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 子 Agent 进度提供者 — 进度数据中心枢纽。
 * <p>
 * 参考 {@link com.wmsay.gpt4_lll.fc.planning.PlanProgressProvider} 的 push/pull 双模式设计：
 * <ul>
 *   <li>Push 模式：通过 {@link #addListener}/{@link #removeListener} 注册监听器，
 *       状态变更时自动回调 {@link SubAgentProgressListener}</li>
 *   <li>Pull 模式：通过 {@link #getSnapshot()} 获取当前 {@link SubAgentProgressSnapshot} 快照</li>
 * </ul>
 * <p>
 * 线程安全：使用 CopyOnWriteArrayList 管理监听器，volatile 保护快照引用。
 * fc 层纯组件，不依赖任何 IntelliJ Platform API。
 */
public class SubAgentProgressProvider {

    private static final Logger LOG = Logger.getLogger(SubAgentProgressProvider.class.getName());

    private final CopyOnWriteArrayList<SubAgentProgressListener> listeners = new CopyOnWriteArrayList<>();
    private volatile SubAgentProgressSnapshot currentSnapshot = SubAgentProgressSnapshot.empty();

    // ── Push 模式 ──────────────────────────────────────────────

    public void addListener(SubAgentProgressListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(SubAgentProgressListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    // ── Pull 模式 ──────────────────────────────────────────────

    /**
     * 获取当前子 Agent 进度快照（pull 模式）。
     */
    public SubAgentProgressSnapshot getSnapshot() {
        return currentSnapshot;
    }

    // ── 内部状态更新（由 SubAgentFactory 调用） ─────────────────

    /**
     * 通知子 Agent 开始执行。
     *
     * @param skillName 匹配到的 Skill 名称
     * @param generated 该 Skill 是否为动态生成（招募模式）
     */
    public void notifyStarting(String skillName, boolean generated) {
        currentSnapshot = new SubAgentProgressSnapshot(
                skillName,
                generated,
                SubAgentProgressSnapshot.SubAgentPhase.EXECUTING,
                System.currentTimeMillis(),
                0L,
                false,
                ""
        );
        for (SubAgentProgressListener l : listeners) {
            try {
                l.onSubAgentStarting(skillName, generated);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener onSubAgentStarting threw exception", e);
            }
        }
    }

    /**
     * 通知子 Agent 流式输出增量文本。
     *
     * @param delta 增量文本片段
     */
    public void notifyTextDelta(String delta) {
        SubAgentProgressSnapshot prev = currentSnapshot;
        // 追加 delta 到 outputPreview
        String updatedPreview = prev.getOutputPreview() + delta;
        currentSnapshot = new SubAgentProgressSnapshot(
                prev.getSkillName(),
                prev.isGenerated(),
                prev.getPhase(),
                prev.getStartTimeMs(),
                System.currentTimeMillis() - prev.getStartTimeMs(),
                prev.isSuccess(),
                updatedPreview
        );
        for (SubAgentProgressListener l : listeners) {
            try {
                l.onSubAgentTextDelta(delta);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener onSubAgentTextDelta threw exception", e);
            }
        }
    }

    /**
     * 通知子 Agent 执行完成（成功）。
     *
     * @param result 子 Agent 执行结果
     */
    public void notifyCompleted(SubAgentResult result) {
        currentSnapshot = new SubAgentProgressSnapshot(
                result.getSkillName(),
                currentSnapshot.isGenerated(),
                SubAgentProgressSnapshot.SubAgentPhase.COMPLETED,
                currentSnapshot.getStartTimeMs(),
                result.getDurationMs(),
                result.isSuccess(),
                currentSnapshot.getOutputPreview()
        );
        for (SubAgentProgressListener l : listeners) {
            try {
                l.onSubAgentCompleted(result.getSkillName(), result.isSuccess(), result.getDurationMs());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener onSubAgentCompleted threw exception", e);
            }
        }
    }

    /**
     * 通知子 Agent 执行失败。
     *
     * @param reason 失败原因
     */
    public void notifyFailed(String reason) {
        SubAgentProgressSnapshot prev = currentSnapshot;
        currentSnapshot = new SubAgentProgressSnapshot(
                prev.getSkillName(),
                prev.isGenerated(),
                SubAgentProgressSnapshot.SubAgentPhase.FAILED,
                prev.getStartTimeMs(),
                prev.getStartTimeMs() > 0 ? System.currentTimeMillis() - prev.getStartTimeMs() : 0L,
                false,
                reason != null ? reason : ""
        );
        for (SubAgentProgressListener l : listeners) {
            try {
                l.onSubAgentCompleted(prev.getSkillName(), false,
                        prev.getStartTimeMs() > 0 ? System.currentTimeMillis() - prev.getStartTimeMs() : 0L);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener onSubAgentCompleted (failure) threw exception", e);
            }
        }
    }
}
