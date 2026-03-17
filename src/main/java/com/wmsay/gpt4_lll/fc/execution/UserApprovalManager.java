package com.wmsay.gpt4_lll.fc.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.mcp.McpContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Map;

/**
 * 用户审批管理器。
 * 管理需要用户审批的工具调用，支持"总是允许"偏好持久化和 60 秒超时机制。
 *
 * <p>审批流程：
 * <ol>
 *   <li>检查"总是允许"偏好，命中则直接返回 true</li>
 *   <li>在 EDT 上弹出 {@link ApprovalDialog}，显示工具名称、参数和潜在影响</li>
 *   <li>用户可选择"允许"、"拒绝"或"总是允许"</li>
 *   <li>60 秒超时视为拒绝</li>
 * </ol>
 *
 * @see ApprovalDialog
 * @see ExecutionEngine
 */
public class UserApprovalManager {

    /** 审批超时时间（秒） */
    private static final long APPROVAL_TIMEOUT_SECONDS = 60;

    /** "总是允许"的工具偏好，key 为工具名称 */
    private final Map<String, Boolean> alwaysAllowedTools = new ConcurrentHashMap<>();

    /**
     * 请求用户审批。
     * <ol>
     *   <li>先检查"总是允许"偏好，命中则直接返回 true</li>
     *   <li>在 EDT 上弹出 {@link ApprovalDialog}</li>
     *   <li>等待用户操作，60 秒超时视为拒绝</li>
     * </ol>
     *
     * @param toolCall 待审批的工具调用
     * @param context  执行上下文
     * @return true 表示用户批准执行，false 表示拒绝或超时
     */
    public boolean requestApproval(ToolCall toolCall, McpContext context) {
        String toolName = toolCall.getToolName();

        // 检查是否已经"总是允许"
        if (Boolean.TRUE.equals(alwaysAllowedTools.get(toolName))) {
            return true;
        }

        // 在 EDT 上显示审批对话框，通过 CompletableFuture 桥接结果
        CompletableFuture<ApprovalOutcome> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                ApprovalDialog dialog = new ApprovalDialog(context.getProject(), toolCall);
                boolean approved = dialog.showAndGet();

                if (approved && dialog.isAlwaysAllow()) {
                    future.complete(ApprovalOutcome.ALWAYS_ALLOW);
                } else if (approved) {
                    future.complete(ApprovalOutcome.ALLOW);
                } else {
                    future.complete(ApprovalOutcome.DENY);
                }
            } catch (Exception e) {
                future.complete(ApprovalOutcome.DENY);
            }
        }, ModalityState.defaultModalityState());

        try {
            ApprovalOutcome outcome = future.get(APPROVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (outcome == ApprovalOutcome.ALWAYS_ALLOW) {
                alwaysAllowedTools.put(toolName, true);
                return true;
            }
            return outcome == ApprovalOutcome.ALLOW;

        } catch (TimeoutException e) {
            // 超时视为拒绝
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查指定工具是否已被标记为"总是允许"。
     *
     * @param toolName 工具名称
     * @return true 表示该工具已被"总是允许"
     */
    public boolean isAlwaysAllowed(String toolName) {
        return Boolean.TRUE.equals(alwaysAllowedTools.get(toolName));
    }

    /**
     * 清除所有"总是允许"偏好。
     */
    public void clearAlwaysAllowedTools() {
        alwaysAllowedTools.clear();
    }

    /**
     * 移除指定工具的"总是允许"偏好。
     *
     * @param toolName 工具名称
     */
    public void removeAlwaysAllowed(String toolName) {
        alwaysAllowedTools.remove(toolName);
    }

    /**
     * 审批结果枚举。
     */
    private enum ApprovalOutcome {
        ALLOW,
        DENY,
        ALWAYS_ALLOW
    }
}
