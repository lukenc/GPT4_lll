package com.wmsay.gpt4_lll.fc.tools;

import com.wmsay.gpt4_lll.fc.model.ToolCall;

import java.util.logging.Logger;

/**
 * 默认审批提供者，自动批准所有工具调用。
 * <p>
 * 适用于 CLI 应用、单元测试和其他无 UI 环境。所有审批请求均返回 {@code true}，
 * "总是允许"相关方法为空操作（no-op），因为所有调用本身就被自动批准。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ApprovalProvider provider = new DefaultApprovalProvider();
 * // 始终返回 true
 * boolean approved = provider.requestApproval(toolCall, context);
 * }</pre>
 *
 * @see ApprovalProvider
 */
public class DefaultApprovalProvider implements ApprovalProvider {

    private static final Logger LOG = Logger.getLogger(DefaultApprovalProvider.class.getName());

    /**
     * 自动批准所有工具调用。
     *
     * @param toolCall 待审批的工具调用，不能为 null
     * @param context  工具执行上下文，不能为 null
     * @return 始终返回 {@code true}
     * @throws IllegalArgumentException 如果 toolCall 或 context 为 null
     */
    @Override
    public boolean requestApproval(ToolCall toolCall, ToolContext context) {
        if (toolCall == null) {
            throw new IllegalArgumentException("toolCall must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        LOG.fine(() -> "Auto-approving tool call: " + toolCall.getToolName());
        return true;
    }

    /**
     * 始终返回 {@code true}，因为默认实现自动批准所有工具。
     *
     * @param toolName 工具名称，不能为 null
     * @return 始终返回 {@code true}
     * @throws IllegalArgumentException 如果 toolName 为 null
     */
    @Override
    public boolean isAlwaysAllowed(String toolName) {
        if (toolName == null) {
            throw new IllegalArgumentException("toolName must not be null");
        }
        return true;
    }

    /**
     * 空操作。默认实现自动批准所有调用，无需维护"总是允许"列表。
     *
     * @param toolName 工具名称，不能为 null
     * @throws IllegalArgumentException 如果 toolName 为 null
     */
    @Override
    public void setAlwaysAllowed(String toolName) {
        if (toolName == null) {
            throw new IllegalArgumentException("toolName must not be null");
        }
        // No-op: all tools are auto-approved
    }

    /**
     * 空操作。默认实现自动批准所有调用，无需维护"总是允许"列表。
     */
    @Override
    public void clearAlwaysAllowed() {
        // No-op: all tools are auto-approved
    }
}
