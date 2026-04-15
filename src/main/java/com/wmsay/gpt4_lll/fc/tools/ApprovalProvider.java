package com.wmsay.gpt4_lll.fc.tools;

import com.wmsay.gpt4_lll.fc.model.ToolCall;

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工具执行审批 SPI 接口。
 * <p>
 * 框架层通过此接口请求用户审批工具调用，不直接调用任何 UI 框架或 EDT 调度 API。
 * 宿主层提供具体的审批实现（如 IntelliJ 弹窗），框架层提供
 * {@link DefaultApprovalProvider} 默认实现（自动批准所有调用）。
 * </p>
 *
 * <p>支持通过 {@link ServiceLoader} 自动发现 SPI 实现。使用 {@link #loadFromServiceLoader()}
 * 获取第一个 SPI 发现的实现，若无 SPI 实现则返回 {@link DefaultApprovalProvider}。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ApprovalProvider provider = ApprovalProvider.loadFromServiceLoader();
 * boolean approved = provider.requestApproval(toolCall, context);
 * if (!approved) {
 *     throw new UserRejectedException("用户拒绝执行工具: " + toolCall.getToolName());
 * }
 * }</pre>
 *
 * @see DefaultApprovalProvider
 * @see ToolCall
 * @see ToolContext
 */
public interface ApprovalProvider {

    Logger LOG = Logger.getLogger(ApprovalProvider.class.getName());

    /**
     * 通过 SPI（{@link ServiceLoader}）自动发现 {@link ApprovalProvider} 实现。
     * <p>
     * 返回第一个 SPI 发现的实现；若无 SPI 实现，返回 {@link DefaultApprovalProvider}。
     * 加载异常时记录警告日志并返回默认实现，不中断框架初始化。
     * </p>
     *
     * @return ApprovalProvider 实例（非 null）
     */
    static ApprovalProvider loadFromServiceLoader() {
        try {
            ServiceLoader<ApprovalProvider> loader = ServiceLoader.load(ApprovalProvider.class);
            for (ApprovalProvider provider : loader) {
                LOG.fine("SPI discovered ApprovalProvider: " + provider.getClass().getName());
                return provider;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load ApprovalProvider via SPI: " + e.getMessage(), e);
        }
        return new DefaultApprovalProvider();
    }

    /**
     * 请求用户审批工具调用。
     * <p>
     * 实现方可通过弹窗、命令行确认或自动批准等方式完成审批。
     * 返回 {@code true} 表示用户批准执行，{@code false} 表示拒绝。
     * </p>
     *
     * @param toolCall 待审批的工具调用，不能为 null
     * @param context  工具执行上下文，不能为 null
     * @return {@code true} 表示批准执行，{@code false} 表示拒绝
     */
    boolean requestApproval(ToolCall toolCall, ToolContext context);

    /**
     * 检查指定工具是否已被标记为"总是允许"。
     *
     * @param toolName 工具名称，不能为 null
     * @return {@code true} 表示该工具已被"总是允许"
     */
    boolean isAlwaysAllowed(String toolName);

    /**
     * 将指定工具添加到"总是允许"列表。
     * <p>
     * 添加后，后续对该工具的 {@link #requestApproval} 调用可直接返回 {@code true}，
     * 无需再次请求用户确认。
     * </p>
     *
     * @param toolName 工具名称，不能为 null
     */
    void setAlwaysAllowed(String toolName);

    /**
     * 清除所有"总是允许"偏好。
     * <p>
     * 清除后，所有工具的后续调用都需要重新请求审批。
     * </p>
     */
    void clearAlwaysAllowed();

    /**
     * 移除指定工具的"总是允许"偏好。
     * 移除后，该工具的后续调用需要重新请求审批。
     *
     * @param toolName 工具名称，不能为 null
     */
    default void removeAlwaysAllowed(String toolName) {
        // 默认空操作，子类可覆写
    }
}
