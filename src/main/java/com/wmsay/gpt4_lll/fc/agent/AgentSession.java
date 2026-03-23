package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.context.ExecutionContext;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Agent 会话 — 封装单个 Agent 实例的完整运行状态。
 * <p>
 * 线程安全，使用 ReentrantLock 保护状态转换。
 * 持有独立的 ContextManager、FileChangeTracker 和 TaskManager（每个会话隔离）。
 * <p>
 * 纯 Java 实现，不依赖任何 com.intellij.* API。
 */
public class AgentSession {

    /** 合法状态转换表 */
    private static final Map<SessionState, Set<SessionState>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(SessionState.class);
        VALID_TRANSITIONS.put(SessionState.CREATED,
                EnumSet.of(SessionState.RUNNING, SessionState.DESTROYED));
        VALID_TRANSITIONS.put(SessionState.RUNNING,
                EnumSet.of(SessionState.PAUSED, SessionState.COMPLETED, SessionState.ERROR));
        VALID_TRANSITIONS.put(SessionState.PAUSED,
                EnumSet.of(SessionState.RUNNING));
        VALID_TRANSITIONS.put(SessionState.COMPLETED,
                EnumSet.of(SessionState.RUNNING, SessionState.DESTROYED));
        VALID_TRANSITIONS.put(SessionState.ERROR,
                EnumSet.of(SessionState.RUNNING, SessionState.DESTROYED));
        VALID_TRANSITIONS.put(SessionState.DESTROYED,
                EnumSet.noneOf(SessionState.class));
    }

    private final String sessionId;
    private final AgentDefinition definition;
    private final ConversationMemory memory;
    private final ExecutionContext executionContext;
    private final long createdAtMs;
    private final ReentrantLock stateLock = new ReentrantLock();
    private final FileChangeTracker fileChangeTracker;
    private final TaskManager taskManager;
    private final ContextManager contextManager;

    private volatile SessionState state = SessionState.CREATED;
    private volatile int delegationDepth = 0;
    private volatile Throwable lastError;

    public AgentSession(String sessionId, AgentDefinition definition,
                        ConversationMemory memory, ExecutionContext executionContext) {
        this.sessionId = sessionId;
        this.definition = definition;
        this.memory = memory;
        this.executionContext = executionContext;
        this.createdAtMs = System.currentTimeMillis();
        this.fileChangeTracker = new FileChangeTracker();
        this.taskManager = new TaskManager();
        this.contextManager = new ContextManager();
    }

    /**
     * 将会话状态转换到目标状态。
     * <p>
     * 检查顺序：DESTROYED 拒绝 → 合法转换验证 → CREATED→RUNNING 时验证 ExecutionContext →
     * DESTROYED 时释放 ConversationMemory。
     *
     * @param newState 目标状态
     * @throws IllegalStateException 非法转换或会话已销毁
     */
    public void transitionTo(SessionState newState) {
        stateLock.lock();
        try {
            if (state == SessionState.DESTROYED) {
                throw new IllegalStateException(
                        "Session " + sessionId + " is DESTROYED, cannot transition to " + newState);
            }

            Set<SessionState> allowed = VALID_TRANSITIONS.getOrDefault(
                    state, EnumSet.noneOf(SessionState.class));
            if (!allowed.contains(newState)) {
                throw new IllegalStateException(
                        "Illegal state transition: " + state + " → " + newState);
            }

            // CREATED→RUNNING 时验证 ExecutionContext 完整性
            if (state == SessionState.CREATED && newState == SessionState.RUNNING) {
                ExecutionContext.ValidationResult vr =
                        executionContext.validateRequired("project", "projectRoot");
                if (!vr.isValid()) {
                    throw new IllegalStateException(
                            "ExecutionContext incomplete: " + vr.getErrorMessage());
                }
            }

            // DESTROYED 时释放记忆资源
            if (newState == SessionState.DESTROYED) {
                memory.clear();
            }

            this.state = newState;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * 记录异常并转换到 ERROR 状态。
     *
     * @param error 导致错误的异常
     * @throws IllegalStateException 如果当前状态不允许转换到 ERROR
     */
    public void transitionToError(Throwable error) {
        stateLock.lock();
        try {
            this.lastError = error;
            transitionTo(SessionState.ERROR);
        } finally {
            stateLock.unlock();
        }
    }

    // ---- Getters ----

    public String getSessionId() { return sessionId; }
    public AgentDefinition getDefinition() { return definition; }
    public ConversationMemory getMemory() { return memory; }
    public ExecutionContext getExecutionContext() { return executionContext; }
    public SessionState getState() { return state; }
    public Throwable getLastError() { return lastError; }
    public FileChangeTracker getFileChangeTracker() { return fileChangeTracker; }
    public TaskManager getTaskManager() { return taskManager; }
    public ContextManager getContextManager() { return contextManager; }

    public int getDelegationDepth() { return delegationDepth; }
    public void setDelegationDepth(int depth) { this.delegationDepth = depth; }

    /** 返回从会话创建到当前时刻的经过时间（毫秒）。 */
    public long getElapsedTimeMs() { return System.currentTimeMillis() - createdAtMs; }

    /** 创建当前会话状态的快照（复用 ExecutionContext.Snapshot 机制）。 */
    public ExecutionContext.Snapshot createSnapshot() { return executionContext.createSnapshot(); }
}
