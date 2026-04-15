package com.wmsay.gpt4_lll.fc.planning.hooks;

import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.fc.planning.ExecutionHook;

import java.util.List;

/**
 * 超时保护钩子 — 防止 Agent 执行时间过长。
 * <p>
 * 通过 {@link #beforeExecution()} 在每次执行开始时重置计时器，
 * 确保超时判断基于当前执行的实际耗时。
 */
public class TimeoutGuardHook implements ExecutionHook {

    private final long timeoutMs;
    private volatile long startTime;

    /**
     * @param timeoutMs 超时阈值（毫秒），0 或负数表示不限时
     */
    public TimeoutGuardHook(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.startTime = 0;
    }

    public static TimeoutGuardHook ofSeconds(int seconds) {
        return new TimeoutGuardHook(seconds * 1000L);
    }

    public static TimeoutGuardHook ofMinutes(int minutes) {
        return new TimeoutGuardHook(minutes * 60_000L);
    }

    @Override
    public void beforeExecution() {
        this.startTime = System.currentTimeMillis();
    }

    private boolean isTimedOut() {
        if (timeoutMs <= 0 || startTime == 0) return false;
        return System.currentTimeMillis() - startTime > timeoutMs;
    }

    private long elapsedMs() {
        if (startTime == 0) return 0;
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public HookResult afterRound(int round, List<ToolCallResult> results) {
        if (isTimedOut()) {
            return HookResult.abort("Execution timeout after " + elapsedMs() + "ms (limit: " + timeoutMs + "ms)");
        }
        return HookResult.continueExecution();
    }

    @Override
    public HookResult beforeStep(int stepIndex, String description) {
        if (isTimedOut()) {
            return HookResult.abort("Execution timeout before step " + (stepIndex + 1)
                    + " after " + elapsedMs() + "ms");
        }
        return HookResult.continueExecution();
    }

    @Override
    public HookResult afterStep(int stepIndex, FunctionCallResult result) {
        if (isTimedOut()) {
            return HookResult.abort("Execution timeout after step " + (stepIndex + 1)
                    + " after " + elapsedMs() + "ms");
        }
        return HookResult.continueExecution();
    }
}
