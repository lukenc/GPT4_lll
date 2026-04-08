package com.wmsay.gpt4_lll.fc.events;

import com.wmsay.gpt4_lll.fc.core.SessionState;
import com.wmsay.gpt4_lll.fc.state.AgentSession;

/**
 * Agent 生命周期监听器 — 在会话创建、状态变更和销毁时接收通知。
 * <p>
 * 所有方法使用 default 空实现，调用方仅需覆盖关心的回调。
 * 通过 {@code AgentRuntime.addLifecycleListener()} 注册。
 * <p>
 * 纯框架层接口，不依赖任何 IntelliJ Platform API。
 *
 * @see AgentSession
 * @see SessionState
 */
public interface AgentLifecycleListener {

    /**
     * 新会话创建时调用。
     *
     * @param session 新创建的会话实例
     */
    default void onSessionCreated(AgentSession session) {}

    /**
     * 会话状态发生变更时调用。
     *
     * @param session 发生状态变更的会话
     * @param from    变更前的状态
     * @param to      变更后的状态
     */
    default void onSessionStateChanged(AgentSession session,
                                        SessionState from, SessionState to) {}

    /**
     * 会话被销毁时调用。
     *
     * @param session 被销毁的会话实例
     */
    default void onSessionDestroyed(AgentSession session) {}
}
