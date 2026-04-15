package com.wmsay.gpt4_lll.fc.planning.hooks;

import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.planning.ExecutionHook;

import java.util.List;

/**
 * 连续失败检测钩子 — 当工具调用连续失败达到阈值时终止执行。
 * <p>
 * 防止 Agent 在工具不可用或参数持续错误时无意义地消耗 token。
 * 任何一次成功的工具调用会重置计数器。
 * <p>
 * 通过 {@link #beforeExecution()} 在每次执行开始时重置所有计数器。
 */
public class ConsecutiveFailureHook implements ExecutionHook {

    private final int maxConsecutiveFailures;
    private final int maxConsecutiveStepFailures;
    private int consecutiveFailures = 0;
    private int totalFailures = 0;
    private int consecutiveStepFailures = 0;

    /**
     * @param maxConsecutiveFailures     工具连续失败次数上限
     * @param maxConsecutiveStepFailures Plan-and-Execute 步骤连续失败次数上限
     */
    public ConsecutiveFailureHook(int maxConsecutiveFailures, int maxConsecutiveStepFailures) {
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.maxConsecutiveStepFailures = maxConsecutiveStepFailures;
    }

    public ConsecutiveFailureHook(int maxConsecutiveFailures) {
        this(maxConsecutiveFailures, 3);
    }

    public static ConsecutiveFailureHook withDefaults() {
        return new ConsecutiveFailureHook(3, 3);
    }

    @Override
    public void beforeExecution() {
        consecutiveFailures = 0;
        totalFailures = 0;
        consecutiveStepFailures = 0;
    }

    @Override
    public HookResult afterRound(int round, List<ToolCallResult> results) {
        if (results == null || results.isEmpty()) {
            return HookResult.continueExecution();
        }

        boolean allFailed = results.stream().noneMatch(ToolCallResult::isSuccess);
        boolean anySuccess = results.stream().anyMatch(ToolCallResult::isSuccess);

        if (allFailed) {
            consecutiveFailures++;
            totalFailures += results.size();
        }
        if (anySuccess) {
            consecutiveFailures = 0;
        }

        if (consecutiveFailures >= maxConsecutiveFailures) {
            return HookResult.abort("Tool calls failed " + consecutiveFailures
                    + " consecutive rounds (total failures: " + totalFailures
                    + "). Aborting to prevent token waste.");
        }
        return HookResult.continueExecution();
    }

    @Override
    public HookResult afterStep(int stepIndex, FunctionCallResult result) {
        if (result != null && !result.isSuccess()) {
            consecutiveStepFailures++;
        } else {
            consecutiveStepFailures = 0;
        }

        if (consecutiveStepFailures >= maxConsecutiveStepFailures) {
            return HookResult.abort("Plan steps failed " + consecutiveStepFailures
                    + " consecutive times. Aborting plan execution.");
        }
        return HookResult.continueExecution();
    }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getTotalFailures() { return totalFailures; }
}
