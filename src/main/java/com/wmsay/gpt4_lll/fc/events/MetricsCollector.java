package com.wmsay.gpt4_lll.fc.events;

import com.wmsay.gpt4_lll.fc.model.PerformanceMetrics;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * 性能指标收集器。
 * 线程安全地收集 function calling 框架的性能统计信息，包括会话时长、
 * 工具调用时长、成功率、错误率和按类型分组的错误计数。
 *
 * <p>所有计数器使用 {@link LongAdder} 和 {@link DoubleAdder} 实现，
 * 在高并发场景下性能优于 {@code AtomicLong}。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * MetricsCollector metrics = new MetricsCollector();
 * metrics.recordToolCallDuration("read_file", 150);
 * metrics.recordSessionDuration(5000);
 * metrics.incrementErrorCount("TimeoutException");
 *
 * PerformanceMetrics summary = metrics.getMetrics();
 * System.out.println("Success rate: " + summary.getSuccessRate());
 * }</pre>
 *
 * @see PerformanceMetrics
 * @see ObservabilityManager
 */
public class MetricsCollector {

    private final DoubleAdder totalSessionDuration = new DoubleAdder();
    private final LongAdder sessionCount = new LongAdder();

    private final DoubleAdder totalToolCallDuration = new DoubleAdder();
    private final LongAdder toolCallCount = new LongAdder();
    private final LongAdder successCount = new LongAdder();

    private final LongAdder errorCount = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> errorsByType = new ConcurrentHashMap<>();

    /**
     * 记录工具调用时长。
     *
     * @param toolName   工具名称
     * @param durationMs 执行时长（毫秒）
     */
    public void recordToolCallDuration(String toolName, long durationMs) {
        totalToolCallDuration.add(durationMs);
        toolCallCount.increment();
        successCount.increment();
    }

    /**
     * 记录会话时长。
     *
     * @param durationMs 会话时长（毫秒）
     */
    public void recordSessionDuration(long durationMs) {
        totalSessionDuration.add(durationMs);
        sessionCount.increment();
    }

    /**
     * 记录工具调用次数（用于会话结束时的批量记录）。
     *
     * @param count 本次会话的工具调用次数
     */
    public void recordToolCallCount(int count) {
        // 此方法用于会话级别的统计，实际的 toolCallCount 已在 recordToolCallDuration 中递增
    }

    /**
     * 递增错误计数。
     *
     * @param errorType 错误类型名称
     */
    public void incrementErrorCount(String errorType) {
        errorCount.increment();
        errorsByType.computeIfAbsent(errorType, k -> new LongAdder()).increment();
    }

    /**
     * 获取平均会话时长（毫秒）。
     *
     * @return 平均会话时长，无会话记录时返回 0.0
     */
    public double getAverageSessionDuration() {
        long count = sessionCount.sum();
        return count == 0 ? 0.0 : totalSessionDuration.sum() / count;
    }

    /**
     * 获取平均工具调用时长（毫秒）。
     *
     * @return 平均工具调用时长，无调用记录时返回 0.0
     */
    public double getAverageToolCallDuration() {
        long count = toolCallCount.sum();
        return count == 0 ? 0.0 : totalToolCallDuration.sum() / count;
    }

    /**
     * 获取总工具调用次数。
     *
     * @return 总工具调用次数
     */
    public long getTotalToolCalls() {
        return toolCallCount.sum();
    }

    /**
     * 获取成功率 (0.0 ~ 1.0)。
     *
     * @return 成功率，无调用记录时返回 1.0
     */
    public double getSuccessRate() {
        long total = toolCallCount.sum();
        if (total == 0) return 1.0;
        return (double) successCount.sum() / total;
    }

    /**
     * 获取错误率 (0.0 ~ 1.0)。
     *
     * @return 错误率，无调用记录时返回 0.0
     */
    public double getErrorRate() {
        long total = toolCallCount.sum() + errorCount.sum();
        if (total == 0) return 0.0;
        return (double) errorCount.sum() / total;
    }

    /**
     * 获取按类型分组的错误计数。
     *
     * @return 不可修改的错误类型到计数的映射
     */
    public Map<String, Long> getErrorsByType() {
        ConcurrentHashMap<String, Long> result = new ConcurrentHashMap<>();
        errorsByType.forEach((key, adder) -> result.put(key, adder.sum()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取汇总的性能指标。
     *
     * @return 当前时刻的性能指标快照
     */
    public PerformanceMetrics getMetrics() {
        return PerformanceMetrics.builder()
                .averageSessionDuration(getAverageSessionDuration())
                .averageToolCallDuration(getAverageToolCallDuration())
                .totalToolCalls(getTotalToolCalls())
                .successRate(getSuccessRate())
                .errorRate(getErrorRate())
                .errorsByType(getErrorsByType())
                .build();
    }

    /**
     * 重置所有指标（主要用于测试）。
     */
    public void reset() {
        totalSessionDuration.reset();
        sessionCount.reset();
        totalToolCallDuration.reset();
        toolCallCount.reset();
        successCount.reset();
        errorCount.reset();
        errorsByType.clear();
    }
}
