package com.wmsay.gpt4_lll.fc.tools;

/**
 * 工具注册/注销事件监听器。
 * <p>
 * 通过 {@link ToolRegistry#addToolRegistrationListener(ToolRegistrationListener)}
 * 注册后，在工具注册或注销时收到通知。
 * </p>
 *
 * @see ToolRegistry
 * @see Tool
 */
public interface ToolRegistrationListener {

    /**
     * 当工具被注册到 ToolRegistry 时调用。
     *
     * @param tool 被注册的工具实例，不为 null
     */
    void onToolRegistered(Tool tool);

    /**
     * 当工具从 ToolRegistry 中注销时调用。
     *
     * @param toolName 被注销的工具名称，不为 null
     */
    void onToolUnregistered(String toolName);
}
