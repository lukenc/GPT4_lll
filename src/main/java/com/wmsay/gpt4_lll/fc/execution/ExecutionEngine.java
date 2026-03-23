package com.wmsay.gpt4_lll.fc.execution;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.fc.error.ConcurrentExecutionException;
import com.wmsay.gpt4_lll.fc.error.ToolNotFoundException;
import com.wmsay.gpt4_lll.fc.error.UserRejectedException;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import com.wmsay.gpt4_lll.mcp.McpToolResult;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 执行引擎。
 * 管理工具调用的实际执行，包括 Project 级别并发控制、超时管理、用户审批和重试逻辑。
 *
 * <ul>
 *   <li>每个 Project 持有一把 {@link ReentrantLock}，非并发安全工具互斥执行</li>
 *   <li>使用 {@link CompletableFuture} + timeout 实现超时控制</li>
 *   <li>集成 {@link RetryStrategy} 实现指数退避重试</li>
 *   <li>集成 {@link UserApprovalManager} 实现敏感工具审批</li>
 * </ul>
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

    /** Project 级别的执行锁 */
    private final Map<Project, ReentrantLock> projectLocks = new ConcurrentHashMap<>();

    /** 工具执行线程池 */
    private final ExecutorService threadPool;

    private final RetryStrategy retryStrategy;
    private final UserApprovalManager approvalManager;

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
     */
    public ExecutionEngine(RetryStrategy retryStrategy, UserApprovalManager approvalManager) {
        this(retryStrategy, approvalManager, createDefaultThreadPool());
    }

    /**
     * 使用自定义线程池创建执行引擎（便于测试）。
     */
    public ExecutionEngine(RetryStrategy retryStrategy,
                           UserApprovalManager approvalManager,
                           ExecutorService threadPool) {
        this.retryStrategy = retryStrategy;
        this.approvalManager = approvalManager;
        this.threadPool = threadPool;
    }

    /**
     * 执行工具调用。
     * <ol>
     *   <li>从 McpToolRegistry 获取工具，不存在则抛出 {@link ToolNotFoundException}</li>
     *   <li>获取 Project 级别锁（并发安全工具跳过）</li>
     *   <li>检查用户审批（需要审批的工具）</li>
     *   <li>带超时和重试执行工具</li>
     *   <li>finally 中释放锁</li>
     * </ol>
     *
     * @param toolCall 工具调用请求
     * @param context  执行上下文
     * @return 工具执行结果
     * @throws ToolNotFoundException        工具不存在
     * @throws ConcurrentExecutionException 并发执行冲突
     * @throws UserRejectedException        用户拒绝执行
     */
    public McpToolResult execute(ToolCall toolCall, McpContext context) {
        McpTool tool = McpToolRegistry.getTool(toolCall.getToolName());
        if (tool == null) {
            throw new ToolNotFoundException(toolCall.getToolName());
        }

        // 1. 并发控制
        boolean lockAcquired = acquireLock(context.getProject(), tool);
        if (!lockAcquired) {
            throw new ConcurrentExecutionException(
                    "Another tool is executing for this project. Tool: " + toolCall.getToolName());
        }

        try {
            // 2. 用户审批
            if (requiresApproval(tool)) {
                boolean approved = approvalManager.requestApproval(toolCall, context);
                if (!approved) {
                    throw new UserRejectedException(
                            "User rejected execution of tool: " + toolCall.getToolName());
                }
            }

            // 3. 带超时和重试执行
            return executeWithTimeoutAndRetry(tool, toolCall, context);

        } finally {
            releaseLock(context.getProject(), tool);
        }
    }

    /**
     * 带超时和重试的工具执行。
     * 使用 {@link CompletableFuture#supplyAsync} 提交到线程池，配合 {@code get(timeout)} 实现超时控制。
     * 可重试异常按指数退避重试，最多重试 {@link RetryStrategy#getMaxRetries()} 次。
     */
    McpToolResult executeWithTimeoutAndRetry(McpTool tool, ToolCall toolCall, McpContext context) {
        int maxRetries = retryStrategy.getMaxRetries();
        long timeout = getTimeout(tool);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                CompletableFuture<McpToolResult> future = CompletableFuture.supplyAsync(
                        () -> tool.execute(context, toolCall.getParameters()),
                        threadPool
                );

                McpToolResult result = future.get(timeout, TimeUnit.SECONDS);

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
                    return McpToolResult.error(
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
                    return McpToolResult.error("Tool execution failed: " + cause.getMessage());
                }

                LOG.info("Tool '" + toolCall.getToolName()
                        + "' failed with retryable error (attempt " + (attempt + 1)
                        + "/" + (maxRetries + 1) + "): " + cause.getMessage());
                sleep(retryStrategy.getBackoffDelay(attempt));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return McpToolResult.error("Tool execution interrupted: " + e.getMessage());

            } catch (CancellationException e) {
                return McpToolResult.error("Tool execution cancelled: " + e.getMessage());
            }
        }

        return McpToolResult.error("Tool execution failed after all retries");
    }

    /**
     * 获取 Project 级别的执行锁。
     * 并发安全工具直接返回 true（不需要锁）。
     * 非并发安全工具使用 {@link ReentrantLock#tryLock()} 尝试获取锁。
     *
     * @param project 当前项目
     * @param tool    待执行的工具
     * @return true 表示成功获取锁（或不需要锁）
     */
    boolean acquireLock(Project project, McpTool tool) {
        if (isConcurrentSafe(tool)) {
            return true;
        }
        if (project == null) {
            return true;
        }
        ReentrantLock lock = projectLocks.computeIfAbsent(project, k -> new ReentrantLock());
        return lock.tryLock();
    }

    /**
     * 释放 Project 级别的执行锁。
     * 并发安全工具无需释放。仅当当前线程持有锁时才释放。
     *
     * @param project 当前项目
     * @param tool    已执行的工具
     */
    void releaseLock(Project project, McpTool tool) {
        if (isConcurrentSafe(tool)) {
            return;
        }
        if (project == null) {
            return;
        }
        ReentrantLock lock = projectLocks.get(project);
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
     */
    private boolean isConcurrentSafe(McpTool tool) {
        return CONCURRENT_SAFE_TOOLS.contains(tool.name());
    }

    /**
     * 判断工具是否需要用户审批。
     */
    private boolean requiresApproval(McpTool tool) {
        return APPROVAL_REQUIRED_TOOLS.contains(tool.name());
    }

    /**
     * 获取工具的超时时间（秒）。优先使用自定义配置，否则使用默认值。
     */
    private long getTimeout(McpTool tool) {
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
