package com.wmsay.gpt4_lll.model;

/**
 * Agent 状态上下文 — 不可变对象。
 * 携带 AgentPhase + 可选的 detail/progress + 时间戳。
 */
public final class AgentStatusContext {

    private final AgentPhase phase;
    private final String detail;
    private final int progress;
    private final long timestamp;

    private AgentStatusContext(AgentPhase phase, String detail, int progress, long timestamp) {
        this.phase = phase;
        this.detail = detail;
        this.progress = progress;
        this.timestamp = timestamp;
    }

    /** 创建仅含 phase 的上下文（detail=null, progress=-1） */
    public static AgentStatusContext of(AgentPhase phase) {
        return new AgentStatusContext(phase, null, -1, System.currentTimeMillis());
    }

    /** 创建含 phase + detail 的上下文 */
    public static AgentStatusContext of(AgentPhase phase, String detail) {
        return new AgentStatusContext(phase, detail, -1, System.currentTimeMillis());
    }

    /** 创建含 phase + detail + progress 的上下文 */
    public static AgentStatusContext of(AgentPhase phase, String detail, int progress) {
        return new AgentStatusContext(phase, detail, progress, System.currentTimeMillis());
    }

    /** IDLE 阶段的默认上下文（单例） */
    public static final AgentStatusContext IDLE_DEFAULT =
        new AgentStatusContext(AgentPhase.IDLE, null, -1, 0);

    public AgentPhase getPhase() { return phase; }
    public String getDetail() { return detail; }
    public int getProgress() { return progress; }
    public long getTimestamp() { return timestamp; }
}
