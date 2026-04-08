package com.wmsay.gpt4_lll.fc.state;

import com.wmsay.gpt4_lll.fc.core.AgentDefinition;
import com.wmsay.gpt4_lll.fc.core.SessionState;
import com.wmsay.gpt4_lll.fc.memory.ConversationMemory;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Agent 会话 — 封装单个 Agent 实例的完整运行状态。
 * <p>
 * 线程安全，使用 ReentrantLock 保护状态转换。
 * 持有 {@link ToolContext}（平台无关的工具上下文）以及独立的
 * ContextManager、FileChangeTracker 和 TaskManager（每个会话隔离）。
 * <p>
 * 纯 Java 实现，不依赖任何 com.intellij.* API。
 *
 * @see ToolContext
 * @see SessionState
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
    private final ToolContext toolContext;
    private final long createdAtMs;
    private final ReentrantLock stateLock = new ReentrantLock();
    private final FileChangeTracker fileChangeTracker;
    private final TaskManager taskManager;
    private final ContextManager contextManager;

    private volatile SessionState state = SessionState.CREATED;
    private volatile int delegationDepth = 0;
    private volatile Throwable lastError;

    /**
     * 创建新的 AgentSession。
     *
     * @param sessionId   会话唯一标识
     * @param definition  Agent 定义
     * @param memory      对话记忆
     * @param toolContext 工具上下文（平台无关）
     */
    public AgentSession(String sessionId, AgentDefinition definition,
                        ConversationMemory memory, ToolContext toolContext) {
        this.sessionId = sessionId;
        this.definition = definition;
        this.memory = memory;
        this.toolContext = toolContext;
        this.createdAtMs = System.currentTimeMillis();
        this.fileChangeTracker = new FileChangeTracker();
        this.taskManager = new TaskManager();
        this.contextManager = new ContextManager();
    }

    /**
     * 将会话状态转换到目标状态。
     * <p>
     * 使用 {@link ReentrantLock} 保证线程安全。
     * 检查顺序：DESTROYED 拒绝 → 合法转换验证 → DESTROYED 时释放 ConversationMemory。
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
    public ToolContext getToolContext() { return toolContext; }
    public SessionState getState() { return state; }
    public Throwable getLastError() { return lastError; }
    public FileChangeTracker getFileChangeTracker() { return fileChangeTracker; }
    public TaskManager getTaskManager() { return taskManager; }
    public ContextManager getContextManager() { return contextManager; }

    public int getDelegationDepth() { return delegationDepth; }
    public void setDelegationDepth(int depth) { this.delegationDepth = depth; }

    /** 返回从会话创建到当前时刻的经过时间（毫秒）。 */
    public long getElapsedTimeMs() { return System.currentTimeMillis() - createdAtMs; }
}
