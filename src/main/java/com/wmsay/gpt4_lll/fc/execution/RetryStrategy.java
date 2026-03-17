package com.wmsay.gpt4_lll.fc.execution;

import com.wmsay.gpt4_lll.fc.error.ConcurrentExecutionException;
import com.wmsay.gpt4_lll.fc.error.ToolNotFoundException;
import com.wmsay.gpt4_lll.fc.error.UserRejectedException;
import com.wmsay.gpt4_lll.fc.error.ValidationException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * 重试策略。
 * 定义工具调用失败后的重试逻辑，包括可重试异常分类和指数退避算法。
 *
 * <p>可重试异常：{@link TimeoutException}、{@link IOException}、
 * {@link SocketTimeoutException}、{@link ConnectException}。
 *
 * <p>不可重试异常：{@link ValidationException}、{@link UserRejectedException}、
 * {@link IllegalArgumentException}、{@link ToolNotFoundException}、
 * {@link ConcurrentExecutionException}。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * RetryStrategy strategy = new RetryStrategy();
 *
 * if (strategy.isRetryable(exception)) {
 *     for (int attempt = 0; attempt < strategy.getMaxRetries(); attempt++) {
 *         Thread.sleep(strategy.getBackoffDelay(attempt)); // 1s, 2s, 4s
 *         // retry...
 *     }
 * }
 * }</pre>
 *
 * @see ExecutionEngine
 */
public class RetryStrategy {

    private static final Set<Class<? extends Throwable>> RETRYABLE_EXCEPTIONS = Set.of(
            TimeoutException.class,
            IOException.class,
            SocketTimeoutException.class,
            ConnectException.class
    );

    private static final Set<Class<? extends Throwable>> NON_RETRYABLE_EXCEPTIONS = Set.of(
            ValidationException.class,
            UserRejectedException.class,
            IllegalArgumentException.class,
            ToolNotFoundException.class,
            ConcurrentExecutionException.class
    );

    /**
     * 判断异常是否可重试。
     * 先检查不可重试集合，再检查可重试集合，默认不重试。
     *
     * @param error 异常
     * @return 是否可重试
     */
    public boolean isRetryable(Throwable error) {
        // 明确不可重试的异常
        for (Class<? extends Throwable> type : NON_RETRYABLE_EXCEPTIONS) {
            if (type.isInstance(error)) {
                return false;
            }
        }

        // 明确可重试的异常
        for (Class<? extends Throwable> type : RETRYABLE_EXCEPTIONS) {
            if (type.isInstance(error)) {
                return true;
            }
        }

        // 默认不重试
        return false;
    }

    /**
     * 获取指数退避延迟时间（毫秒）。
     * 退避公式: 2^attempt * 1000ms → 1s, 2s, 4s ...
     *
     * @param attempt 当前尝试次数 (0-based)
     * @return 延迟时间（毫秒）
     */
    public long getBackoffDelay(int attempt) {
        return (long) Math.pow(2, attempt) * 1000;
    }

    /**
     * 获取最大重试次数。
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return 3;
    }
}
