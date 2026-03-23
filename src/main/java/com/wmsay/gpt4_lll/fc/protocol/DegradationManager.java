package com.wmsay.gpt4_lll.fc.protocol;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 降级管理器。
 * <p>
 * 跟踪 Prompt Engineering 模式（{@link MarkdownProtocolAdapter}）的解析失败率，
 * 当失败率超过 50% 时自动禁用 function calling，回退到传统单轮对话模式。
 * <p>
 * 同时检测供应商是否支持原生 function calling，在不支持时记录降级事件。
 *
 * <h3>Requirements:</h3>
 * <ul>
 *   <li>Req 16.1: 供应商不支持 function calling 时，自动降级到 Prompt Engineering 模式</li>
 *   <li>Req 16.2: Prompt Engineering 模式解析失败率超过 50% 时，禁用 function calling</li>
 *   <li>Req 16.3: 降级时通知用户当前模式</li>
 *   <li>Req 16.5: function calling 被禁用时，回退到传统单轮对话模式</li>
 *   <li>Req 16.6: 记录降级事件和原因到日志</li>
 * </ul>
 */
public class DegradationManager {

    private static final Logger LOG = Logger.getLogger(DegradationManager.class.getName());

    /** 失败率阈值：超过此值则禁用 function calling */
    public static final double FAILURE_RATE_THRESHOLD = 0.50;

    /** 最少需要的尝试次数，低于此值不触发自动禁用（避免样本过少） */
    public static final int MIN_ATTEMPTS_FOR_DISABLE = 4;

    private final AtomicInteger totalAttempts = new AtomicInteger(0);
    private final AtomicInteger failedAttempts = new AtomicInteger(0);
    private final AtomicBoolean disabled = new AtomicBoolean(false);

    /**
     * 记录一次解析尝试的结果。
     * <p>
     * 每次通过 Prompt Engineering 模式（MarkdownProtocolAdapter）解析工具调用后，
     * 应调用此方法记录结果。当失败率超过 50% 且样本量足够时，自动禁用 function calling。
     *
     * @param success true 表示解析成功（解析出至少一个工具调用），false 表示解析失败
     */
    public void recordParseAttempt(boolean success) {
        int total = totalAttempts.incrementAndGet();
        if (!success) {
            failedAttempts.incrementAndGet();
        }

        // 检查是否需要自动禁用
        if (!disabled.get() && total >= MIN_ATTEMPTS_FOR_DISABLE) {
            double failureRate = getFailureRate();
            if (failureRate > FAILURE_RATE_THRESHOLD) {
                disable("Parse failure rate " + String.format("%.1f%%", failureRate * 100)
                        + " exceeded threshold of " + String.format("%.0f%%", FAILURE_RATE_THRESHOLD * 100)
                        + " after " + total + " attempts");
            }
        }
    }

    /**
     * 检查 function calling 是否已被禁用。
     *
     * @return true 表示已禁用，应回退到传统单轮对话模式
     */
    public boolean isDisabled() {
        return disabled.get();
    }

    /**
     * 获取当前解析失败率。
     *
     * @return 失败率（0.0 ~ 1.0），如果没有尝试则返回 0.0
     */
    public double getFailureRate() {
        int total = totalAttempts.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) failedAttempts.get() / total;
    }

    /**
     * 获取总尝试次数。
     *
     * @return 总解析尝试次数
     */
    public int getTotalAttempts() {
        return totalAttempts.get();
    }

    /**
     * 获取失败次数。
     *
     * @return 解析失败次数
     */
    public int getFailedAttempts() {
        return failedAttempts.get();
    }

    /**
     * 重置所有统计数据并重新启用 function calling。
     */
    public void reset() {
        totalAttempts.set(0);
        failedAttempts.set(0);
        disabled.set(false);
        LOG.info("DegradationManager reset: function calling re-enabled, statistics cleared");
    }

    /**
     * 记录供应商不支持原生 function calling 的降级事件。
     * (Req 16.1, 16.3, 16.6)
     *
     * @param providerName 供应商名称
     */
    public void recordDegradationToPromptEngineering(String providerName) {
        LOG.log(Level.WARNING, "Degradation: Provider '" + providerName
                + "' does not support native function calling. "
                + "Falling back to Prompt Engineering mode.");
    }

    /**
     * 记录 function calling 被禁用的降级事件。
     * (Req 16.2, 16.3, 16.5, 16.6)
     *
     * @param reason 禁用原因
     */
    public void recordDisabledEvent(String reason) {
        LOG.log(Level.WARNING, "Degradation: Function calling disabled. Reason: " + reason
                + ". Falling back to traditional single-turn conversation mode.");
    }

    /**
     * 获取当前模式的描述（用于通知用户，Req 16.3）。
     *
     * @param nativeFunctionCalling 是否使用原生 function calling
     * @return 当前模式描述
     */
    public String getCurrentModeDescription(boolean nativeFunctionCalling) {
        if (disabled.get()) {
            return "Function calling is disabled due to high parse failure rate. "
                    + "Using traditional single-turn conversation mode.";
        }
        if (nativeFunctionCalling) {
            return "Using native function calling mode.";
        }
        return "Using Prompt Engineering mode (provider does not support native function calling).";
    }

    // ---- internal ----

    private void disable(String reason) {
        if (disabled.compareAndSet(false, true)) {
            recordDisabledEvent(reason);
        }
    }
}
