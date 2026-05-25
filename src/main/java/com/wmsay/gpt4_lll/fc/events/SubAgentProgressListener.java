package com.wmsay.gpt4_lll.fc.events;

/**
 * 子 Agent 进度监听器 — push 模式接收子 Agent 状态变更事件。
 * <p>
 * 所有方法使用 default 空实现，调用方仅需覆盖关心的回调。
 * fc 层定义的纯接口，不依赖任何 IntelliJ Platform API。
 *
 * @see PlanProgressListener
 */
public interface SubAgentProgressListener {

    /**
     * 子 Agent 开始执行。
     *
     * @param skillName 匹配到的 Skill 名称
     * @param generated 该 Skill 是否为动态生成（招募模式）
     */
    default void onSubAgentStarting(String skillName, boolean generated) {}

    /**
     * 子 Agent 流式输出增量文本。
     *
     * @param delta 增量文本片段
     */
    default void onSubAgentTextDelta(String delta) {}

    /**
     * 子 Agent 执行完成（成功或失败）。
     *
     * @param skillName  匹配到的 Skill 名称
     * @param success    是否执行成功
     * @param durationMs 执行耗时（毫秒）
     */
    default void onSubAgentCompleted(String skillName, boolean success, long durationMs) {}
}
