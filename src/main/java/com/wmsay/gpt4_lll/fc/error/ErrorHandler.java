package com.wmsay.gpt4_lll.fc.error;

import com.wmsay.gpt4_lll.fc.model.ErrorMessage;
import com.wmsay.gpt4_lll.fc.model.ValidationError;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 错误处理器。
 * 处理各种异常情况，生成友好的错误消息和建议。
 * <p>
 * 支持以下异常类型：
 * <ul>
 *   <li>{@link ToolNotFoundException} - 工具不存在</li>
 *   <li>{@link ValidationException} - 参数验证失败</li>
 *   <li>{@link TimeoutException} - 执行超时</li>
 *   <li>{@link ConcurrentExecutionException} - 并发执行冲突</li>
 *   <li>其他通用异常</li>
 * </ul>
 * <p>
 * 支持通过 {@link CustomErrorHandler} 接口扩展自定义错误处理逻辑。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 基本用法
 * ErrorHandler handler = new ErrorHandler();
 * ErrorMessage msg = handler.handle(new ToolNotFoundException("unknown_tool"));
 * System.out.println(msg.getSuggestion()); // "Did you mean 'read_file'? ..."
 *
 * // 带自定义处理器
 * ErrorHandler customHandler = new ErrorHandler(List.of((toolName, error) -> {
 *     if (error instanceof MyCustomException) {
 *         return ErrorMessage.builder()
 *             .type("custom_error")
 *             .message(error.getMessage())
 *             .build();
 *     }
 *     return null; // 交给内置处理器
 * }));
 * }</pre>
 *
 * @see CustomErrorHandler
 * @see ErrorMessage
 */
public class ErrorHandler {

    private final List<CustomErrorHandler> customHandlers;

    /**
     * 创建不带自定义处理器的 ErrorHandler。
     */
    public ErrorHandler() {
        this.customHandlers = new ArrayList<>();
    }

    /**
     * 创建带自定义处理器的 ErrorHandler。
     * 自定义处理器在内置处理逻辑之前被调用，如果返回非 null 结果则直接使用。
     *
     * @param customHandlers 自定义错误处理器列表，可以为 null
     */
    public ErrorHandler(List<CustomErrorHandler> customHandlers) {
        this.customHandlers = customHandlers == null
                ? new ArrayList<>()
                : new ArrayList<>(customHandlers);
    }

    /**
     * 处理异常并生成错误消息。
     * 先尝试自定义处理器，再按异常类型分派到内置处理方法。
     *
     * @param error 异常
     * @return 格式化的错误消息
     */
    public ErrorMessage handle(Throwable error) {
        // 先尝试自定义处理器
        for (CustomErrorHandler handler : customHandlers) {
            ErrorMessage result = handler.handle(null, error);
            if (result != null) {
                return result;
            }
        }

        if (error instanceof ToolNotFoundException) {
            return handleToolNotFound((ToolNotFoundException) error);
        } else if (error instanceof ValidationException) {
            return handleValidationError((ValidationException) error);
        } else if (error instanceof TimeoutException) {
            return handleTimeout((TimeoutException) error);
        } else if (error instanceof ConcurrentExecutionException) {
            return handleConcurrentExecution((ConcurrentExecutionException) error);
        } else {
            return handleGenericError(error);
        }
    }

    /**
     * 处理工具不存在异常。
     * 使用 Levenshtein 距离算法建议最相似的工具名称。
     *
     * @param error 工具不存在异常
     * @return 包含建议的错误消息
     */
    public ErrorMessage handleToolNotFound(ToolNotFoundException error) {
        String requestedTool = error.getToolName();
        List<String> availableTools = McpToolRegistry.getAllTools()
                .stream()
                .map(McpTool::name)
                .collect(Collectors.toList());

        String suggestion = findMostSimilar(requestedTool, availableTools);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requested_tool", requestedTool);
        details.put("available_tools", availableTools);
        if (suggestion != null) {
            details.put("suggestion", suggestion);
        }

        String suggestionText;
        if (suggestion != null) {
            suggestionText = String.format(
                    "Did you mean '%s'? Available tools: %s",
                    suggestion, String.join(", ", availableTools));
        } else {
            suggestionText = "Available tools: " + String.join(", ", availableTools);
        }

        return ErrorMessage.builder()
                .type("tool_not_found")
                .message(String.format("Tool '%s' not found", requestedTool))
                .details(details)
                .suggestion(suggestionText)
                .build();
    }

    /**
     * 处理参数验证异常。
     * 为每个验证错误生成详细的错误报告，包含字段名、错误类型、期望值、实际值和建议。
     *
     * @param error 验证异常
     * @return 包含所有验证错误详情的错误消息
     */
    public ErrorMessage handleValidationError(ValidationException error) {
        List<ValidationError> errors = error.getErrors();

        List<Map<String, Object>> errorDetails = errors.stream()
                .map(e -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("field", e.getFieldName());
                    detail.put("error_type", e.getType() != null ? e.getType().name() : "UNKNOWN");
                    detail.put("expected", e.getExpected());
                    detail.put("actual", e.getActual());
                    if (e.getSuggestion() != null) {
                        detail.put("suggestion", e.getSuggestion());
                    }
                    return detail;
                })
                .collect(Collectors.toList());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errors", errorDetails);
        details.put("error_count", errors.size());

        return ErrorMessage.builder()
                .type("validation_error")
                .message("Parameter validation failed")
                .details(details)
                .suggestion(generateValidationSuggestion(errors))
                .build();
    }

    /**
     * 处理执行超时异常。
     *
     * @param error 超时异常
     * @return 包含超时建议的错误消息
     */
    public ErrorMessage handleTimeout(TimeoutException error) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("error_type", "timeout");
        if (error.getMessage() != null) {
            details.put("message", error.getMessage());
        }

        return ErrorMessage.builder()
                .type("timeout")
                .message("Tool execution timed out")
                .details(details)
                .suggestion("Consider using smaller data range or step-by-step execution. "
                        + "You can also increase the timeout in configuration.")
                .build();
    }

    /**
     * 处理并发执行冲突异常。
     *
     * @param error 并发执行异常
     * @return 包含等待建议的错误消息
     */
    public ErrorMessage handleConcurrentExecution(ConcurrentExecutionException error) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("error_type", "concurrent_execution");
        if (error.getMessage() != null) {
            details.put("message", error.getMessage());
        }

        return ErrorMessage.builder()
                .type("concurrent_execution")
                .message("Another tool is currently executing for this project")
                .details(details)
                .suggestion("Please wait for the current tool execution to complete before starting a new one.")
                .build();
    }

    /**
     * 处理通用异常。
     * 对异常进行分类（超时、权限、IO、业务逻辑），并生成相应的错误消息。
     *
     * @param error 通用异常
     * @return 分类后的错误消息
     */
    public ErrorMessage handleGenericError(Throwable error) {
        String classification = classifyException(error);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("error_type", classification);
        details.put("exception_class", error.getClass().getSimpleName());
        if (error.getMessage() != null) {
            details.put("message", error.getMessage());
        }
        // 堆栈摘要：取前 3 行
        details.put("stack_summary", getStackSummary(error, 3));

        String suggestion = generateGenericSuggestion(classification);

        return ErrorMessage.builder()
                .type(classification)
                .message(String.format("Tool execution failed: %s",
                        error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()))
                .details(details)
                .suggestion(suggestion)
                .build();
    }

    // ---- Levenshtein 距离算法 ----

    /**
     * 在候选列表中找到与目标字符串最相似的字符串（基于 Levenshtein 距离）。
     *
     * @param target     目标字符串
     * @param candidates 候选列表
     * @return 最相似的候选字符串，如果候选列表为空则返回 null
     */
    String findMostSimilar(String target, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .min(Comparator.comparingInt(c -> levenshteinDistance(
                        target.toLowerCase(), c.toLowerCase())))
                .orElse(null);
    }

    /**
     * 计算两个字符串之间的 Levenshtein 编辑距离。
     *
     * @param s1 第一个字符串
     * @param s2 第二个字符串
     * @return 编辑距离
     */
    int levenshteinDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            for (int j = 0; j <= len2; j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + cost
                    );
                }
            }
        }

        return dp[len1][len2];
    }

    // ---- 异常分类 ----

    /**
     * 对异常进行分类。
     *
     * @param error 异常
     * @return 分类字符串：timeout, permission, io, business_logic
     */
    String classifyException(Throwable error) {
        if (error instanceof TimeoutException || error instanceof SocketTimeoutException) {
            return "timeout";
        }
        if (error instanceof SecurityException) {
            return "permission";
        }
        if (error instanceof IOException) {
            return "io";
        }
        // 检查 cause 链
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
                return "timeout";
            }
            if (cause instanceof IOException) {
                return "io";
            }
        }
        return "business_logic";
    }

    // ---- 辅助方法 ----

    private String generateValidationSuggestion(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Please check the parameters and try again.";
        }

        StringBuilder sb = new StringBuilder("Please fix the following parameter errors: ");
        for (int i = 0; i < errors.size(); i++) {
            ValidationError e = errors.get(i);
            if (i > 0) {
                sb.append("; ");
            }
            if (e.getFieldName() != null) {
                sb.append(e.getFieldName()).append(" - ");
            }
            if (e.getSuggestion() != null) {
                sb.append(e.getSuggestion());
            } else {
                sb.append(e.getType() != null ? e.getType().name() : "error");
            }
        }
        return sb.toString();
    }

    private String generateGenericSuggestion(String classification) {
        switch (classification) {
            case "timeout":
                return "The operation timed out. Consider using smaller data range or step-by-step execution.";
            case "permission":
                return "Permission denied. Please check that you have the necessary permissions for this operation.";
            case "io":
                return "An I/O error occurred. Please check that the file or resource exists and is accessible.";
            default:
                return "An unexpected error occurred. Please try again or use a different approach.";
        }
    }

    private List<String> getStackSummary(Throwable error, int maxLines) {
        StackTraceElement[] stack = error.getStackTrace();
        List<String> summary = new ArrayList<>();
        int limit = Math.min(stack.length, maxLines);
        for (int i = 0; i < limit; i++) {
            summary.add(stack[i].toString());
        }
        return summary;
    }
}
