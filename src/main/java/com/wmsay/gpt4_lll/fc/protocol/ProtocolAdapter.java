package com.wmsay.gpt4_lll.fc.protocol;

import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.model.Message;

import java.util.List;

/**
 * 协议适配器接口。
 * 适配不同 AI 供应商的 function calling 协议,提供统一的工具描述、
 * 调用解析和结果格式化接口。
 *
 * <p>内置实现：
 * <ul>
 *   <li>{@link OpenAIProtocolAdapter} — OpenAI Function Calling 格式</li>
 *   <li>{@link AnthropicProtocolAdapter} — Anthropic Tool Use 格式</li>
 *   <li>{@link MarkdownProtocolAdapter} — 自定义 Markdown 代码块格式（向后兼容）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("openai");
 *
 * // 格式化工具描述
 * Object toolDescriptions = adapter.formatToolDescriptions(tools);
 *
 * // 解析 LLM 响应中的工具调用
 * List<ToolCall> calls = adapter.parseToolCalls(llmResponse);
 *
 * // 格式化工具执行结果
 * Message msg = adapter.formatToolResult(result);
 * }</pre>
 *
 * @see MarkdownProtocolAdapter
 * @see ProtocolAdapterRegistry
 */
public interface ProtocolAdapter {

    /**
     * 获取适配器名称。
     *
     * @return 适配器名称，如 "openai"、"anthropic"、"markdown"
     */
    String getName();

    /**
     * 判断是否支持指定供应商。
     *
     * @param providerName 供应商名称
     * @return 是否支持
     */
    boolean supports(String providerName);

    /**
     * 将 MCP 工具列表转换为供应商特定格式的工具描述。
     *
     * @param tools MCP 工具列表
     * @return 供应商特定格式的工具描述(类型取决于具体适配器)
     */
    Object formatToolDescriptions(List<McpTool> tools);

    /**
     * 从 LLM 响应中解析工具调用请求。
     * 如果响应中不包含工具调用,返回空列表而非 null。
     *
     * @param response LLM 响应文本
     * @return 解析出的工具调用列表,按出现顺序排列
     */
    List<ToolCall> parseToolCalls(String response);

    /**
     * 将工具执行结果格式化为供应商特定的消息格式。
     *
     * @param result 工具执行结果
     * @return 格式化后的消息
     */
    Message formatToolResult(ToolCallResult result);

    /**
     * 判断是否支持原生 function calling。
     * 不支持原生协议的适配器使用 Prompt Engineering 模拟。
     *
     * @return 是否支持原生 function calling
     */
    boolean supportsNativeFunctionCalling();
}
