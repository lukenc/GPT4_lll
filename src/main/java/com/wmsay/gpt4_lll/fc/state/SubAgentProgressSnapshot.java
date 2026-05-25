package com.wmsay.gpt4_lll.fc.state;

/**
 * 子 Agent 进度快照 — 不可变数据对象。
 * 以 pull 模式提供子 Agent 当前执行状态的只读视图。
 */
public class SubAgentProgressSnapshot {

    /**
     * 子 Agent 执行阶段枚举。
     */
    public enum SubAgentPhase {
        MATCHING,
        CREATING,
        EXECUTING,
        COMPLETED,
        FAILED
    }

    private final String skillName;
    private final boolean generated;
    private final SubAgentPhase phase;
    private final long startTimeMs;
    private final long durationMs;
    private final boolean success;
    private final String outputPreview;

    public SubAgentProgressSnapshot(String skillName, boolean generated,
                                     SubAgentPhase phase, long startTimeMs,
                                     long durationMs, boolean success,
                                     String outputPreview) {
        this.skillName = skillName;
        this.generated = generated;
        this.phase = phase;
        this.startTimeMs = startTimeMs;
        this.durationMs = durationMs;
        this.success = success;
        this.outputPreview = outputPreview;
    }

    public static SubAgentProgressSnapshot empty() {
        return new SubAgentProgressSnapshot("", false, SubAgentPhase.MATCHING, 0L, 0L, false, "");
    }

    public String getSkillName() { return skillName; }
    public boolean isGenerated() { return generated; }
    public SubAgentPhase getPhase() { return phase; }
    public long getStartTimeMs() { return startTimeMs; }
    public long getDurationMs() { return durationMs; }
    public boolean isSuccess() { return success; }
    public String getOutputPreview() { return outputPreview; }
    public boolean isEmpty() { return skillName.isEmpty() && phase == SubAgentPhase.MATCHING && startTimeMs == 0L; }
}
