package com.wmsay.gpt4_lll.fc.state;

/**
 * Prompt 区段枚举 — 按优先级从高到低排列。
 * SYSTEM_PROMPT 最高优先级（不裁剪），TOOLS 最低优先级（优先裁剪）。
 */
public enum PromptSection {
    SYSTEM_PROMPT, KNOWLEDGE, HISTORY, TOOLS
}
