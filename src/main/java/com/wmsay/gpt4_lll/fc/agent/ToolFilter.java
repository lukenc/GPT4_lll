package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.strategy.PlanStep;
import com.wmsay.gpt4_lll.mcp.McpTool;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具过滤器 — 根据意图识别结果从全量工具列表中筛选相关工具子集。
 * <p>
 * 支持两种过滤模式：
 * - per-session（ReAct 策略）：会话开始时一次性筛选
 * - per-task（PlanAndExecute 策略）：每个计划步骤执行前动态筛选
 * <p>
 * BaseTool 集合始终包含在过滤结果中。
 */
public class ToolFilter {

    /** 基础工具集合 — 始终包含，不受过滤影响 */
    public static final Set<String> BASE_TOOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "keyword_search", "read_file", "write_file", "shell_exec", "project_tree")));

    private final ObservabilityManager observability;

    public ToolFilter(ObservabilityManager observability) {
        this.observability = observability;
    }

    /**
     * per-session 过滤 — BaseTool 始终包含，filteredToolNames 为空时回退全量。
     */
    public List<McpTool> filter(IntentResult intent, List<McpTool> allTools) {
        if (allTools == null || allTools.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> filteredNames = intent != null ? intent.getFilteredToolNames() : null;

        List<McpTool> result;
        if (filteredNames == null || filteredNames.isEmpty()) {
            // 回退为全量工具
            result = new ArrayList<>(allTools);
        } else {
            // 过滤：BaseTool + filteredToolNames
            Set<String> keepNames = new HashSet<>(filteredNames);
            keepNames.addAll(BASE_TOOLS);
            result = allTools.stream()
                    .filter(t -> keepNames.contains(t.name()))
                    .collect(Collectors.toList());
        }
        return result;
    }

    /**
     * per-task 过滤 — PlanAndExecute 策略中按步骤过滤工具。
     * 通过独立 LLM 调用分析步骤描述并返回该步骤所需的工具子集。
     */
    public List<McpTool> filterForStep(PlanStep step, List<McpTool> allTools,
                                       FunctionCallOrchestrator.LlmCaller llmCaller) {
        if (allTools == null || allTools.isEmpty()) {
            return Collections.emptyList();
        }
        // BaseTool 始终包含
        // 当前实现：返回全量工具（完整 LLM 分析可在后续迭代中实现）
        return new ArrayList<>(allTools);
    }
}
