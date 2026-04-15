package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.tools.ApprovalProvider;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.mcp.McpContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * IntelliJ Platform 宿主适配层的 ApprovalProvider 实现。
 * <p>
 * 通过 {@code ApplicationManager.invokeAndWait()} 在 EDT 上弹出
 * {@link ApprovalDialog}，实现工具调用的用户审批。支持"总是允许"偏好
 * 和 60 秒超时（超时视为拒绝）。
 * </p>
 *
 * <p>审批流程：</p>
 * <ol>
 *   <li>检查"总是允许"偏好，命中则直接返回 true</li>
 *   <li>从 ToolContext 中提取 Project 对象</li>
 *   <li>在 EDT 上弹出 {@link ApprovalDialog}</li>
 *   <li>用户可选择"允许"、"拒绝"或"总是允许"</li>
 *   <li>60 秒超时视为拒绝</li>
 * </ol>
 *
 * @see ApprovalProvider
 * @see ApprovalDialog
 */
public class IntelliJApprovalProvider implements ApprovalProvider {

    /** 审批超时时间（秒） */
    private static final long APPROVAL_TIMEOUT_SECONDS = 60;

    /** "总是允许"的工具偏好，key 为工具名称 */
    private final Map<String, Boolean> alwaysAllowedTools = new ConcurrentHashMap<>();

    // ─── Task 4.1: 路由判断（静态方法，便于属性测试） ───

    /**
     * 判断指定工具是否应使用 DiffCommitDialog 进行审批。
     * 当前仅 "write_file" 工具使用 Diff 审批。
     *
     * @param toolName 工具名称
     * @return true 表示应使用 DiffCommitDialog
     */
    public static boolean shouldUseDiffDialog(String toolName) {
        return "write_file".equals(toolName);
    }

    @Override
    public boolean requestApproval(ToolCall toolCall, ToolContext context) {
        String toolName = toolCall.getToolName();

        // 检查是否已经"总是允许"（对所有工具保持不变）
        if (Boolean.TRUE.equals(alwaysAllowedTools.get(toolName))) {
            return true;
        }

        // 从 ToolContext 中提取 Project 对象
        Project project = extractProject(context);
        if (project == null) {
            // 无法获取 Project，无法弹出审批对话框，默认拒绝
            return false;
        }

        // write_file 工具使用 DiffCommitDialog 进行审批
        if (shouldUseDiffDialog(toolName)) {
            return requestWriteFileApproval(toolCall, context, project);
        }

        // 其他工具：保持现有 ApprovalDialog 逻辑
        return requestGenericApproval(toolCall, project);
    }

    // ─── Task 4.2: write_file 专用审批方法 ───

    /**
     * 使用 DiffCommitDialog 对 write_file 工具调用进行审批。
     * <p>
     * 从 ToolCall 参数中提取文件路径、写入模式和内容，读取原始文件，
     * 通过 {@link WritePreviewCalculator} 计算预览内容，然后在 EDT 上
     * 弹出 {@link DiffCommitDialog} 展示 Diff 对比。
     * </p>
     * <p>
     * 任何异常（文件读取失败、参数缺失等）均回退到 {@link #requestGenericApproval}。
     * </p>
     *
     * @param toolCall 工具调用请求
     * @param context  工具执行上下文
     * @param project  IntelliJ 项目实例
     * @return true 表示用户批准，false 表示拒绝或超时
     */
    private boolean requestWriteFileApproval(ToolCall toolCall, ToolContext context, Project project) {
        try {
            Map<String, Object> params = toolCall.getParameters();
            String relativePath = (String) params.get("path");
            Object modeObj = params.get("mode");
            String mode = modeObj != null ? modeObj.toString() : "patch";
            String content = (String) params.get("content");

            // 解析绝对路径
            Path workspaceRoot = context.getWorkspaceRoot();
            Path filePath = workspaceRoot.resolve(relativePath);

            // 读取原始文件内容（不存在则为空字符串）
            String originalContent = "";
            boolean isNewFile = !Files.exists(filePath);
            if (!isNewFile) {
                originalContent = Files.readString(filePath);
            }

            // 根据写入模式计算预览内容
            String previewContent = WritePreviewCalculator.computePreview(
                    mode, originalContent, content, params, isNewFile);

            // 提取 insert_after_line 的行号
            Integer lineNumber = "insert_after_line".equals(mode)
                    ? ((Number) params.get("line_number")).intValue() : null;

            String toolName = toolCall.getToolName();

            // 在 EDT 上弹出 DiffCommitDialog，使用与现有相同的 CompletableFuture + invokeAndWait 模式
            CompletableFuture<ApprovalOutcome> future = new CompletableFuture<>();
            String finalOriginalContent = originalContent;

            ApplicationManager.getApplication().invokeAndWait(() -> {
                try {
                    DiffCommitDialog dialog = new DiffCommitDialog(
                            project, toolCall,
                            finalOriginalContent, previewContent,
                            relativePath, mode, isNewFile, lineNumber);
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
            }, ModalityState.any());

            try {
                ApprovalOutcome outcome = future.get(APPROVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (outcome == ApprovalOutcome.ALWAYS_ALLOW) {
                    alwaysAllowedTools.put(toolName, true);
                    return true;
                }
                return outcome == ApprovalOutcome.ALLOW;

            } catch (TimeoutException e) {
                return false;
            } catch (Exception e) {
                return false;
            }

        } catch (Exception e) {
            // 降级：回退到 ApprovalDialog
            return requestGenericApproval(toolCall, project);
        }
    }

    // ─── Task 4.3: 现有审批逻辑提取为独立方法（逻辑不变） ───

    /**
     * 使用现有 ApprovalDialog 对非 write_file 工具调用进行审批。
     * <p>
     * 逻辑与原 requestApproval 中的 ApprovalDialog 部分完全一致：
     * CompletableFuture + invokeAndWait + ModalityState.any() + 60 秒超时。
     * </p>
     *
     * @param toolCall 工具调用请求
     * @param project  IntelliJ 项目实例
     * @return true 表示用户批准，false 表示拒绝或超时
     */
    private boolean requestGenericApproval(ToolCall toolCall, Project project) {
        String toolName = toolCall.getToolName();

        CompletableFuture<ApprovalOutcome> future = new CompletableFuture<>();

        // 使用 ModalityState.any() 而非 defaultModalityState()，
        // 避免模态状态泄漏导致窗口在切换后无法接收输入事件
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                ApprovalDialog dialog = new ApprovalDialog(project, toolCall);
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
        }, ModalityState.any());

        try {
            ApprovalOutcome outcome = future.get(APPROVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (outcome == ApprovalOutcome.ALWAYS_ALLOW) {
                alwaysAllowedTools.put(toolName, true);
                return true;
            }
            return outcome == ApprovalOutcome.ALLOW;

        } catch (TimeoutException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isAlwaysAllowed(String toolName) {
        return Boolean.TRUE.equals(alwaysAllowedTools.get(toolName));
    }

    @Override
    public void setAlwaysAllowed(String toolName) {
        alwaysAllowedTools.put(toolName, true);
    }

    @Override
    public void clearAlwaysAllowed() {
        alwaysAllowedTools.clear();
    }

    @Override
    public void removeAlwaysAllowed(String toolName) {
        alwaysAllowedTools.remove(toolName);
    }

    /**
     * 从 ToolContext 中提取 IntelliJ Project 对象。
     * 优先从 "project" key 获取，回退到 McpContext。
     */
    private Project extractProject(ToolContext context) {
        if (context == null) {
            return null;
        }
        Project project = context.get("project", Project.class);
        if (project != null) {
            return project;
        }
        // 回退：尝试从 McpContext 获取
        McpContext mcpContext = context.get("mcpContext", McpContext.class);
        if (mcpContext != null) {
            return mcpContext.getProject();
        }
        return null;
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
