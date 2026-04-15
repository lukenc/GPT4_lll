package com.wmsay.gpt4_lll.fc.tools;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.wmsay.gpt4_lll.fc.error.ConcurrentExecutionException;
import com.wmsay.gpt4_lll.fc.error.ToolNotFoundException;
import com.wmsay.gpt4_lll.fc.error.UserRejectedException;
import com.wmsay.gpt4_lll.fc.model.ToolCall;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 执行引擎。
 * 管理工具调用的实际执行，包括基于 contextId 的并发控制、超时管理、用户审批和重试逻辑。
 *
 * <ul>
 *   <li>每个 contextId（从 {@link ToolContext#getWorkspaceRoot()} 派生）持有一把
 *       {@link ReentrantLock}，非并发安全工具互斥执行</li>
 *   <li>使用 {@link CompletableFuture} + timeout 实现超时控制</li>
 *   <li>集成 {@link RetryStrategy} 实现指数退避重试</li>
 *   <li>集成 {@link ApprovalProvider} 实现敏感工具审批</li>
 * </ul>
 *
 * @see Tool
 * @see ToolContext
 * @see ToolResult
 * @see ToolRegistry
 * @see ApprovalProvider
 */
public class ExecutionEngine {

    private static final Logger LOG = Logger.getLogger(ExecutionEngine.class.getName());

    /** 默认超时时间（秒） */
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    /** 已知的并发安全工具（只读工具） */
    private static final Set<String> CONCURRENT_SAFE_TOOLS = Set.of(
            "read_file", "tree", "grep", "keyword_search"
    );

    /** 已知的需要用户审批的工具（写入/删除/执行类） */
    private static final Set<String> APPROVAL_REQUIRED_TOOLS = Set.of(
            "write_file", "delete_file", "execute_command", "run_command", "shell_exec"
    );

    /** 工具自定义超时配置，key 为工具名称，value 为超时秒数 */
    private final Map<String, Long> toolTimeoutOverrides = new ConcurrentHashMap<>();

    /** contextId 级别的执行锁（从 ToolContext.getWorkspaceRoot() 派生） */
    private final Map<String, ReentrantLock> contextLocks = new ConcurrentHashMap<>();

    /** 工具执行线程池 */
    private final ExecutorService threadPool;

    private final ToolRegistry toolRegistry;
    private final ApprovalProvider approvalProvider;
    private final RetryStrategy retryStrategy;

    /**
     * 创建有界线程池，使用守护线程以确保 JVM 可正常退出。
     * core=2, max=4, keepAlive=60s, 队列容量=32。
     */
    private static ExecutorService createDefaultThreadPool() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2,                          // corePoolSize
                4,                          // maximumPoolSize
                60L, TimeUnit.SECONDS,      // keepAliveTime
                new LinkedBlockingQueue<>(32) // bounded work queue
        );
        pool.setThreadFactory(r -> {
            Thread t = new Thread(r, "fc-tool-exec");
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /**
     * 使用默认有界线程池创建执行引擎。
     *
     * @param toolRegistry     工具注册表，用于查找工具实例
     * @param approvalProvider 审批提供者，用于敏感工具的用户审批
     * @param retryStrategy    重试策略，用于指数退避重试
     */
    public ExecutionEngine(ToolRegistry toolRegistry, ApprovalProvider approvalProvider,
                           RetryStrategy retryStrategy) {
        this(toolRegistry, approvalProvider, retryStrategy, createDefaultThreadPool());
    }

    /**
     * 使用自定义线程池创建执行引擎（便于测试）。
     *
     * @param toolRegistry     工具注册表，用于查找工具实例
     * @param approvalProvider 审批提供者，用于敏感工具的用户审批
     * @param retryStrategy    重试策略，用于指数退避重试
     * @param threadPool       自定义线程池
     */
    public ExecutionEngine(ToolRegistry toolRegistry, ApprovalProvider approvalProvider,
                           RetryStrategy retryStrategy, ExecutorService threadPool) {
        this.toolRegistry = toolRegistry;
        this.approvalProvider = approvalProvider;
        this.retryStrategy = retryStrategy;
        this.threadPool = threadPool;
    }

    /**
     * 执行工具调用。
     * <ol>
     *   <li>从注入的 {@link ToolRegistry} 获取工具，不存在则抛出 {@link ToolNotFoundException}</li>
     *   <li>获取 contextId 级别锁（并发安全工具跳过）</li>
     *   <li>检查用户审批（需要审批的工具）</li>
     *   <li>带超时和重试执行工具</li>
     *   <li>finally 中释放锁</li>
     * </ol>
     *
     * @param toolCall 工具调用请求
     * @param context  工具执行上下文
     * @return 工具执行结果
     * @throws ToolNotFoundException        工具不存在
     * @throws ConcurrentExecutionException 并发执行冲突
     * @throws UserRejectedException        用户拒绝执行
     */
    public ToolResult execute(ToolCall toolCall, ToolContext context) {
        if (toolCall == null) {
            throw new IllegalArgumentException("toolCall must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Tool tool = toolRegistry.getTool(toolCall.getToolName());
        if (tool == null) {
            throw new ToolNotFoundException(toolCall.getToolName());
        }

        // 1. 并发控制
        String contextId = deriveContextId(context);
        boolean lockAcquired = acquireLock(contextId, tool);
        if (!lockAcquired) {
            throw new ConcurrentExecutionException(
                    "Another tool is executing for this context. Tool: " + toolCall.getToolName());
        }

        try {
            // 2. 用户审批
            if (requiresApproval(tool)) {
                boolean approved = approvalProvider.requestApproval(toolCall, context);
                if (!approved) {
                    throw new UserRejectedException(
                            "User rejected execution of tool: " + toolCall.getToolName());
                }
            }

            // 3. 带超时和重试执行
            return executeWithTimeoutAndRetry(tool, toolCall, context);

        } finally {
            releaseLock(contextId, tool);
        }
    }

    /**
     * 带超时和重试的工具执行。
     * 使用 {@link CompletableFuture#supplyAsync} 提交到线程池，配合 {@code get(timeout)} 实现超时控制。
     * 可重试异常按指数退避重试，最多重试 {@link RetryStrategy#getMaxRetries()} 次。
     */
    ToolResult executeWithTimeoutAndRetry(Tool tool, ToolCall toolCall, ToolContext context) {
        int maxRetries = retryStrategy.getMaxRetries();
        long timeout = getTimeout(tool);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(
                        () -> tool.execute(context, toolCall.getParameters()),
                        threadPool
                );

                ToolResult result = future.get(timeout, TimeUnit.SECONDS);

                if (attempt > 0) {
                    LOG.info("Tool '" + toolCall.getToolName()
                            + "' succeeded after " + attempt + " retries");
                }
                return result;

            } catch (TimeoutException e) {
                LOG.log(Level.WARNING, "Tool '" + toolCall.getToolName()
                        + "' timed out (attempt " + (attempt + 1) + "/" + (maxRetries + 1)
                        + ", timeout=" + timeout + "s)");

                if (attempt == maxRetries) {
                    return ToolResult.error(
                            String.format("Tool execution timed out after %d seconds. "
                                    + "Consider using smaller data range or step-by-step execution.",
                                    timeout));
                }
                // Timeout is retryable — apply backoff then continue
                sleep(retryStrategy.getBackoffDelay(attempt));

            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;

                if (!retryStrategy.isRetryable(cause) || attempt == maxRetries) {
                    LOG.log(Level.WARNING, "Tool '" + toolCall.getToolName()
                            + "' execution failed (non-retryable or max retries reached): "
                            + cause.getMessage());
                    return ToolResult.error("Tool execution failed: " + cause.getMessage());
                }

                LOG.info("Tool '" + toolCall.getToolName()
                        + "' failed with retryable error (attempt " + (attempt + 1)
                        + "/" + (maxRetries + 1) + "): " + cause.getMessage());
                sleep(retryStrategy.getBackoffDelay(attempt));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.error("Tool execution interrupted: " + e.getMessage());

            } catch (CancellationException e) {
                return ToolResult.error("Tool execution cancelled: " + e.getMessage());
            }
        }

        return ToolResult.error("Tool execution failed after all retries");
    }

    /**
     * 从 ToolContext 派生 contextId，用于并发锁隔离。
     * 使用 workspaceRoot 的字符串表示作为 contextId。
     *
     * @param context 工具执行上下文
     * @return contextId 字符串，若 workspaceRoot 为 null 则返回 "__default__"
     */
    String deriveContextId(ToolContext context) {
        if (context == null || context.getWorkspaceRoot() == null) {
            return "__default__";
        }
        return context.getWorkspaceRoot().toString();
    }

    /**
     * 获取 contextId 级别的执行锁。
     * 并发安全工具直接返回 true（不需要锁）。
     * 非并发安全工具使用 {@link ReentrantLock#tryLock()} 尝试获取锁。
     *
     * @param contextId 上下文标识
     * @param tool      待执行的工具
     * @return true 表示成功获取锁（或不需要锁）
     */
    boolean acquireLock(String contextId, Tool tool) {
        if (isConcurrentSafe(tool)) {
            return true;
        }
        if (contextId == null) {
            return true;
        }
        ReentrantLock lock = contextLocks.computeIfAbsent(contextId, k -> new ReentrantLock());
        return lock.tryLock();
    }

    /**
     * 释放 contextId 级别的执行锁。
     * 并发安全工具无需释放。仅当当前线程持有锁时才释放。
     *
     * @param contextId 上下文标识
     * @param tool      已执行的工具
     */
    void releaseLock(String contextId, Tool tool) {
        if (isConcurrentSafe(tool)) {
            return;
        }
        if (contextId == null) {
            return;
        }
        ReentrantLock lock = contextLocks.get(contextId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 设置工具的自定义超时时间。
     *
     * @param toolName       工具名称
     * @param timeoutSeconds 超时秒数
     */
    public void setToolTimeout(String toolName, long timeoutSeconds) {
        toolTimeoutOverrides.put(toolName, timeoutSeconds);
    }

    /**
     * 关闭线程池，释放资源。
     */
    public void shutdown() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ---- private helpers ----

    /**
     * 判断工具是否并发安全（只读工具可并发执行）。
     * 优先使用 Tool 接口的 isConcurrentSafe() 方法，回退到静态白名单。
     */
    private boolean isConcurrentSafe(Tool tool) {
        if (tool.isConcurrentSafe()) {
            return true;
        }
        return CONCURRENT_SAFE_TOOLS.contains(tool.name());
    }

    /**
     * 判断工具是否需要用户审批。
     * 优先使用 Tool 接口的 requiresApproval() 方法，回退到静态白名单。
     */
    private boolean requiresApproval(Tool tool) {
        if (tool.requiresApproval()) {
            return true;
        }
        return APPROVAL_REQUIRED_TOOLS.contains(tool.name());
    }

    /**
     * 获取工具的超时时间（秒）。优先使用自定义配置，否则使用默认值。
     */
    private long getTimeout(Tool tool) {
        Long override = toolTimeoutOverrides.get(tool.name());
        return override != null ? override : DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * 线程休眠（用于退避等待），捕获中断异常并恢复中断标志。
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
