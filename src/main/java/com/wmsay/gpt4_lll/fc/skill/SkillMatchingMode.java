package com.wmsay.gpt4_lll.fc.skill;

/**
 * Skill 匹配的路由模式。
 * <p>
 * 控制 {@code AgentRuntime.send()} 在每个回合开始时,如何决定 skill 是通过
 * <strong>in-context dispatcher tool</strong>(模型自己在主循环里调用 invoke_skill)
 * 还是通过 <strong>sidecar 旁路 LLM</strong>(SkillMatcher 单独发请求)进行匹配。
 *
 * <ul>
 *   <li>{@link #AUTO} — 默认。当 provider 支持原生 function calling 且 skill 总数小于
 *       {@code inContextSkillThreshold} 时走 in-context;否则走 sidecar。这是绝大多数场景
 *       的推荐设置。</li>
 *   <li>{@link #FORCE_SIDECAR} — 强制 sidecar,即便支持原生 FC 也用旁路 LLM。主要用于
 *       回归对比和故障排查。</li>
 *   <li>{@link #FORCE_IN_CONTEXT} — 强制 in-context,即便是 Markdown 模式 fallback 链也
 *       通过主循环让模型自己挑(Markdown 模式下 tool 描述会落在 system message 里,行为
 *       不算最优但应可跑通)。主要用于测试 in-context 路径自身。</li>
 *   <li>{@link #DISABLED} — 完全禁用 skill 匹配,主循环只看常规工具,registered skills
 *       被忽略。</li>
 * </ul>
 *
 * <p>这是个显式过渡态枚举:当 Markdown 模式弃用、所有目标 provider 都支持原生 FC 后,
 * sidecar 路径可整体删除,届时只保留 {@code AUTO} 与 {@code DISABLED} 即可。
 */
public enum SkillMatchingMode {
    /** 自动按 provider 能力与 skill 数量决定路由(默认)。*/
    AUTO,
    /** 强制 sidecar 旁路 LLM 路径。*/
    FORCE_SIDECAR,
    /** 强制 in-context dispatcher tool 路径。*/
    FORCE_IN_CONTEXT,
    /** 禁用 skill 匹配。*/
    DISABLED
}
