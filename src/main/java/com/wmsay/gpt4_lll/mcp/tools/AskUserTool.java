package com.wmsay.gpt4_lll.mcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.wmsay.gpt4_lll.component.AgentChatView;
import com.wmsay.gpt4_lll.component.block.AskUserBlock;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 向用户提问工具。
 * <p>
 * 当 Agent 需要澄清、确认或让用户在多个分支中做选择才能继续时调用此工具。
 * 工具会在聊天视图中渲染一个 {@link AskUserBlock}，阻塞等待用户点击选项，
 * 然后将用户选择的 value 作为 tool_result 返回，让 ReAct 循环自然恢复。
 * </p>
 *
 * <p>调用协议：
 * <ul>
 *   <li><b>question</b>（必填）：问题文本，展示给用户</li>
 *   <li><b>options</b>（必填）：选项数组，每项形如
 *       {@code {"label": "继续处理下一章", "value": "continue"}}</li>
 *   <li><b>timeout_seconds</b>（可选，默认 600）：等待用户响应的最长时间</li>
 * </ul>
 * </p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>通过 {@code CompletableFuture} 在工具执行线程上阻塞，
 *       ReAct 循环天然暂停；用户选择后循环自然恢复</li>
 *   <li>响应用户点击 Stop：工具线程被中断时，future.get() 抛 InterruptedException，
 *       工具返回取消结果，ReAct 循环通过 isInterrupted() 检查及时退出</li>
 *   <li>超时保护：避免用户关闭窗口后工具无限期阻塞</li>
 * </ul>
 */
public class AskUserTool implements Tool {

    private static final Logger LOG = Logger.getInstance(AskUserTool.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 600;

    @Override
    public String name() {
        return "ask_user";
    }

    @Override
    public String description() {
        return "Ask the user a question and wait for their response when you need clarification, "
                + "confirmation, or a choice between branches to proceed.\n\n"
                + "WHEN TO USE:\n"
                + "- You genuinely cannot decide the next step without user input\n"
                + "- You need the user to pick from multiple paths (e.g., continue / stop / view detail)\n"
                + "- You need to confirm a destructive or irreversible action\n\n"
                + "WHEN NOT TO USE:\n"
                + "- For rhetorical or informational purposes — just output text instead\n"
                + "- When you can make a reasonable decision yourself based on context\n"
                + "- To ask multiple questions in sequence — batch them into one well-scoped question\n\n"
                + "PARAMETERS:\n"
                + "- question (string, required): The question to display to the user\n"
                + "- options (array, required): List of {label, value} objects. "
                + "label is the button text shown to the user; value is returned to you.\n"
                + "- timeout_seconds (integer, optional, default 600): Max wait time.\n\n"
                + "RETURNS:\n"
                + "The value of the option the user selected. On timeout or cancellation, an error result.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("question", Map.of(
                "type", "string",
                "required", true,
                "description", "The question to display to the user"));
        Map<String, Object> optionItemProperties = new LinkedHashMap<>();
        optionItemProperties.put("label", Map.of(
                "type", "string",
                "description", "Button text shown to the user."));
        optionItemProperties.put("value", Map.of(
                "type", "string",
                "description", "Value returned to the agent when this option is chosen."));
        Map<String, Object> optionItemSchema = new LinkedHashMap<>();
        optionItemSchema.put("type", "object");
        optionItemSchema.put("properties", optionItemProperties);
        optionItemSchema.put("required", List.of("label", "value"));

        schema.put("options", Map.of(
                "type", "array",
                "required", true,
                "items", optionItemSchema,
                "description", "Array of {label, value} objects. label is the button text; "
                        + "value is what you receive back."));
        schema.put("timeout_seconds", Map.of(
                "type", "integer",
                "required", false,
                "default", DEFAULT_TIMEOUT_SECONDS,
                "description", "Maximum seconds to wait for user response."));
        return schema;
    }

    @Override
    public boolean isConcurrentSafe() {
        // UI 交互工具不应并发调用，避免多个提问同时出现在视图中
        return false;
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> params) {
        // 1. 参数校验
        String question = asString(params.get("question"));
        if (question == null || question.isBlank()) {
            return ToolResult.error("Parameter 'question' is required and must be a non-empty string.");
        }

        List<AskUserBlock.Option> parsedOptions = parseOptions(params.get("options"));
        if (parsedOptions.isEmpty()) {
            return ToolResult.error("Parameter 'options' is required and must contain at least one "
                    + "{label, value} entry.");
        }

        int timeoutSeconds = asInt(params.get("timeout_seconds"), DEFAULT_TIMEOUT_SECONDS);
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }

        // 2. 获取 UI 引用
        AgentChatView chatView = context.get("chatView", AgentChatView.class);
        if (chatView == null) {
            return ToolResult.error("UI is not available (no chatView in ToolContext). "
                    + "ask_user cannot be used in headless contexts.");
        }

        // 3. 在 EDT 添加 AskUserBlock，并获取其 future
        final AskUserBlock[] blockRef = new AskUserBlock[1];
        final List<AskUserBlock.Option> finalOptions = parsedOptions;
        final String finalQuestion = question;
        try {
            ApplicationManager.getApplication().invokeAndWait(
                    () -> blockRef[0] = chatView.addAskUserBlock(finalQuestion, finalOptions));
        } catch (Exception e) {
            LOG.warn("Failed to create AskUserBlock", e);
            return ToolResult.error("Failed to render ask_user UI: " + e.getMessage());
        }

        AskUserBlock block = blockRef[0];
        if (block == null) {
            return ToolResult.error("AskUserBlock was not created.");
        }

        // 4. 阻塞等待用户选择
        try {
            String chosenValue = block.awaitChoice().get(timeoutSeconds, TimeUnit.SECONDS);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tool", name());
            result.put("user_choice", chosenValue);
            result.put("question", finalQuestion);
            return ToolResult.structured(result);

        } catch (InterruptedException e) {
            // Stop 按钮触发的中断：恢复中断标志，让 ReAct 循环的 isInterrupted() 检查生效
            Thread.currentThread().interrupt();
            block.cancel("用户取消");
            return ToolResult.error("ask_user was interrupted by user stop.");

        } catch (TimeoutException e) {
            block.cancel("超时");
            return ToolResult.error("ask_user timed out after " + timeoutSeconds + " seconds "
                    + "without user response.");

        } catch (Exception e) {
            LOG.warn("ask_user failed", e);
            block.cancel(e.getMessage());
            return ToolResult.error("ask_user failed: " + e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析 options 参数。支持两种常见输入形式：
     * <ul>
     *   <li>{@code [{"label":"继续","value":"continue"}, ...]}</li>
     *   <li>{@code ["继续", "取消"]} — label 与 value 等同</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static List<AskUserBlock.Option> parseOptions(Object raw) {
        List<AskUserBlock.Option> result = new ArrayList<>();
        if (!(raw instanceof List)) {
            return result;
        }
        List<?> list = (List<?>) raw;
        for (Object item : list) {
            if (item == null) continue;
            if (item instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) item;
                String label = asString(m.get("label"));
                String value = asString(m.get("value"));
                if (label == null || label.isBlank()) continue;
                if (value == null) value = label;
                result.add(new AskUserBlock.Option(label, value));
            } else {
                String s = item.toString();
                if (!s.isBlank()) {
                    result.add(new AskUserBlock.Option(s, s));
                }
            }
        }
        return result;
    }
}
