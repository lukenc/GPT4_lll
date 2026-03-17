package com.wmsay.gpt4_lll.component;

import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.CommentAction;
import com.wmsay.gpt4_lll.component.block.*;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.Message.ContentBlockRecord;
import com.wmsay.gpt4_lll.model.Message.ToolCallSummary;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 对话视图，完全替代 Gpt4lllTextArea。
 * <p>
 * 三层嵌套结构：AgentChatView（对话）→ TurnPanel（轮次）→ ContentBlock（内容块）。
 * <p>
 * 保留 Gpt4lllTextArea 的全部公开 API 以保证调用方无感切换：
 * appendContent / appendMessage / appendThinkingTitle / appendThinkingEnd /
 * clearShowWindow / setScrollPane / setText / getText
 */
public class AgentChatView extends JPanel implements Scrollable {

    private final JPanel turnContainer;
    private JScrollPane scrollPane;

    private final List<TurnPanel> turns = new ArrayList<>();
    private TurnPanel activeTurn;

    /** 用户是否主动向上滚动，接管了滚动位置 */
    private volatile boolean userScrolledAway = false;
    /** 标记是否由程序触发的滚动（避免误判为用户操作） */
    private volatile boolean programmaticScroll = false;

    public AgentChatView() {
        super(new BorderLayout());

        turnContainer = new JPanel();
        turnContainer.setLayout(new BoxLayout(turnContainer, BoxLayout.Y_AXIS));
        turnContainer.setBorder(JBUI.Borders.empty(0, 0, 40, 0));

        JPanel topAligned = new JPanel(new BorderLayout());
        topAligned.add(turnContainer, BorderLayout.NORTH);

        add(topAligned, BorderLayout.CENTER);
    }

    // ==================== Scrollable 实现（让 JScrollPane 正确布局） ====================

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return visibleRect.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // ==================== 滚动面板关联 ====================

    public void setScrollPane(JScrollPane scrollPane) {
        this.scrollPane = scrollPane;
        // 监听滚动条变化，检测用户是否主动向上滚动
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (programmaticScroll || e.getValueIsAdjusting()) {
                // 程序触发的滚动或拖拽中，不改变状态
                return;
            }
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 30;
            if (atBottom) {
                userScrolledAway = false;
            }
        });
        // 监听鼠标滚轮，检测用户主动向上滚动
        scrollPane.getViewport().addChangeListener(e -> {
            if (programmaticScroll) {
                return;
            }
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 30;
            if (!atBottom) {
                userScrolledAway = true;
            } else {
                userScrolledAway = false;
            }
        });
    }

    // ==================== 兼容旧 API：流式内容追加 ====================

    /**
     * 获取当前活跃的 TurnPanel。
     */
    public TurnPanel getActiveTurn() {
        return activeTurn;
    }

    /**
     * 追加流式内容到当前活跃 Turn 的活跃 Block。
     * 如果没有 activeTurn，自动创建 assistant TurnPanel。
     * 确保在 EDT 上执行以避免 Swing 线程安全问题。
     */
    public void appendContent(String content) {
        if (SwingUtilities.isEventDispatchThread()) {
            ensureActiveTurn("assistant");
            activeTurn.appendContent(content);
        } else {
            SwingUtilities.invokeLater(() -> {
                ensureActiveTurn("assistant");
                activeTurn.appendContent(content);
            });
        }
    }

    // ==================== 兼容旧 API：思考过程 ====================

    /**
     * 对应原 appendThingkingTitle()。
     * 在当前 activeTurn 中创建 ThinkingBlock 并设为 activeBlock。
     */
    public void appendThingkingTitle() {
        if (SwingUtilities.isEventDispatchThread()) {
            ensureActiveTurn("assistant");
            activeTurn.startThinking();
        } else {
            SwingUtilities.invokeLater(() -> {
                ensureActiveTurn("assistant");
                activeTurn.startThinking();
            });
        }
    }

    /**
     * 对应原 appendThingkingEnd()。
     * 将 ThinkingBlock 标记完成并折叠，清除 activeBlock。
     */
    public void appendThingkingEnd() {
        if (SwingUtilities.isEventDispatchThread()) {
            if (activeTurn != null) {
                activeTurn.endThinking();
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                if (activeTurn != null) {
                    activeTurn.endThinking();
                }
            });
        }
    }

    // ==================== 兼容旧 API：清空 ====================

    /**
     * 清空所有轮次和内容块。
     */
    public void clearShowWindow() {
        if (SwingUtilities.isEventDispatchThread()) {
            doClearShowWindow();
        } else {
            SwingUtilities.invokeLater(this::doClearShowWindow);
        }
    }

    private void doClearShowWindow() {
        for (TurnPanel turn : turns) {
            turn.clear();
        }
        turns.clear();
        activeTurn = null;
        userScrolledAway = false;
        turnContainer.removeAll();
        turnContainer.revalidate();
        turnContainer.repaint();
    }

    // ==================== 兼容旧 API：历史消息加载 ====================

    /**
     * 加载一条历史消息（非流式，一次性渲染）。
     * 保持与原 Gpt4lllTextArea.appendMessage 完全一致的行为。
     */
    public void appendMessage(Message content) {
        String role = content.getRole();

        // 跳过 FC 循环中的中间消息：
        // 1. tool 角色消息（工具执行结果，已包含在最终 assistant 消息的 content_blocks 中）
        // 2. 带 tool_calls 的 assistant 消息（中间轮次，其文本已汇总到最终消息中）
        if ("tool".equals(role)) {
            return;
        }
        if ("assistant".equals(role) && content.getToolCalls() != null && !content.getToolCalls().isEmpty()) {
            return;
        }

        String msgContent = content.getContent();
        if (msgContent == null) msgContent = "";

        if (msgContent.startsWith("你是一个")) {
            return;
        } else if (msgContent.startsWith("请帮我完成下面的功能，同时使用")) {
            String[] str = msgContent.split("回复我，功能如下：");
            String xuqiu = str[1];
            appendHistoryText("user", "Generate：" + xuqiu);
        } else if (msgContent.startsWith("请帮我重构下面的代码，不局限于代码性能优化、命名优化、增加注释、简化代码、优化逻辑，请使用")) {
            String[] str = msgContent.split("回复我，代码如下：");
            String xuqiu = str[1];
            appendHistoryText("user", "Optimize：" + xuqiu);
        } else if (msgContent.startsWith("todo后的文字是需要完成的功能，请帮我实现这些描述的功能，同时使用")) {
            String[] str = msgContent.split("需要实现的代码如下：");
            String xuqiu = str[1];
            appendHistoryText("user", "Complete：" + xuqiu);
        } else if (msgContent.startsWith(CommentAction.PROMPT.split("\n")[0])) {
            String[] str = msgContent.split("代码内容：");
            String xuqiu = str[1];
            xuqiu = xuqiu.split("2. 注释要求：")[0];
            appendHistoryText("user", "Comment：" + xuqiu);
        } else if (msgContent.startsWith("评估不限于以下")) {
            String[] str = msgContent.split("如下代码:");
            String xuqiu = str[1];
            appendHistoryText("user", "Score：" + xuqiu);
        } else {
            if ("user".equals(role)) {
                appendHistoryText(role, "YOU:" + msgContent);
            } else {
                appendHistoryAssistantMessage(content);
            }
        }
    }

    // ==================== 兼容旧 API：setText / getText ====================

    /**
     * 兼容旧版 setText 调用（用于 loading dots 场景）。
     * 作用于 activeTurn 的 activeBlock 的底层 markdown 内容。
     */
    public void setText(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            doSetText(text);
        } else {
            SwingUtilities.invokeLater(() -> doSetText(text));
        }
    }

    private void doSetText(String text) {
        if (activeTurn != null && activeTurn.getActiveBlock() instanceof MarkdownBlock mb) {
            mb.setContent(text);
        }
    }

    /**
     * 兼容旧版 getText 调用。
     * 返回 activeTurn 的 activeBlock 的 markdown 原始文本。
     */
    public String getText() {
        if (activeTurn != null && activeTurn.getActiveBlock() instanceof MarkdownBlock mb) {
            return mb.getContentText();
        }
        return "";
    }

    // ==================== Agent 新 API：Tool Call / Result ====================

    /**
     * 添加工具调用审批块。返回 CompletableFuture 用于等待用户决策。
     */
    public CompletableFuture<Boolean> addToolCallBlock(String toolCallId, String toolName,
                                                        Map<String, Object> params) {
        ensureActiveTurn("assistant");
        ToolCallBlock block = new ToolCallBlock(toolCallId, toolName, params);
        activeTurn.addBlock(block);
        activeTurn.setActiveBlock(null);
        scrollToBottomLater();
        return block.awaitDecision();
    }

    /**
     * 添加工具使用进度块（带旋转动画）。返回 ToolUseBlock 引用，
     * 调用方可在工具执行完成后调用 markCompleted() 更新状态。
     */
    public ToolUseBlock addToolUseBlock(String toolName, Map<String, Object> params) {
        ensureActiveTurn("assistant");
        ToolUseBlock block = new ToolUseBlock(toolName, params);
        activeTurn.addBlock(block);
        activeTurn.setActiveBlock(null);
        scrollToBottomLater();
        return block;
    }

    /**
     * 添加工具执行结果块（默认折叠）。
     */
    public void addToolResultBlock(String toolName, String resultText) {
        ensureActiveTurn("assistant");
        ToolResultBlock block = new ToolResultBlock(toolName, resultText);
        activeTurn.addBlock(block);
        activeTurn.setActiveBlock(null);
        scrollToBottomLater();
    }

    // ==================== 轮次管理 ====================

    /**
     * 显式开始一个用户轮次。
     */
    public TurnPanel startUserTurn() {
        return startTurn("user");
    }

    /**
     * 显式开始一个助手轮次。
     */
    public TurnPanel startAssistantTurn() {
        return startTurn("assistant");
    }

    // ==================== 内部方法 ====================

    private TurnPanel startTurn(String role) {
        TurnPanel turn = new TurnPanel(role);
        turn.setOnContentChanged(this::scrollToBottomIfNeeded);
        turns.add(turn);
        activeTurn = turn;

        JComponent comp = turn.getComponent();
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        turnContainer.add(comp);
        turnContainer.revalidate();
        turnContainer.repaint();

        return turn;
    }

    private void ensureActiveTurn(String defaultRole) {
        if (activeTurn == null) {
            startTurn(defaultRole);
        }
    }

    private static final Pattern THINK_PATTERN = Pattern.compile(
            "<think>(.*?)</think>", Pattern.DOTALL);
    private static final Pattern TOOL_CALL_BLOCK_PATTERN = Pattern.compile(
            "```tool_call\\s*\\n.*?\\n```", Pattern.DOTALL);

    /**
     * 渲染一条历史助手消息。
     * 优先使用 contentBlocks（有序内容块列表）还原交错顺序；
     * 若不存在则回退到旧逻辑（思考→工具→正文）。
     */
    private void appendHistoryAssistantMessage(Message message) {
        TurnPanel turn = new TurnPanel("assistant");
        turns.add(turn);

        List<ContentBlockRecord> blocks = message.getContentBlocks();
        if (blocks != null && !blocks.isEmpty()) {
            renderOrderedBlocks(turn, blocks);
        } else {
            renderLegacyAssistantMessage(turn, message);
        }

        JComponent comp = turn.getComponent();
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        turnContainer.add(comp);
        turnContainer.revalidate();
        turnContainer.repaint();
    }

    /**
     * 按保存顺序渲染内容块，保持思考/文本/工具调用的交错顺序。
     */
    private void renderOrderedBlocks(TurnPanel turn, List<ContentBlockRecord> blocks) {
        for (ContentBlockRecord block : blocks) {
            String type = block.getType();
            if (type == null) continue;
            switch (type) {
                case "thinking":
                    if (block.getContent() != null && !block.getContent().isEmpty()) {
                        ThinkingBlock tb = turn.startThinking();
                        tb.appendContent(block.getContent());
                        turn.endThinking();
                    }
                    break;
                case "text":
                    if (block.getContent() != null && !block.getContent().isEmpty()) {
                        // 强制创建新的 MarkdownBlock，避免内容追加到之前的块
                        turn.setActiveBlock(null);
                        turn.appendContent(block.getContent());
                        turn.flushContent();
                        turn.setActiveBlock(null);
                    }
                    break;
                case "tool_use":
                    turn.setActiveBlock(null);
                    ToolUseBlock useBlock = new ToolUseBlock(block.getToolName(), block.getParams());
                    turn.addBlock(useBlock);
                    useBlock.markCompleted(block.isSuccess(), block.getDurationMs());
                    break;
                case "tool_result":
                    turn.setActiveBlock(null);
                    String label = block.getToolName() != null ? block.getToolName() : "tool";
                    String resultText = block.getResultText() != null ? block.getResultText() : "(no output)";
                    ToolResultBlock resultBlock = new ToolResultBlock(label, resultText);
                    turn.addBlock(resultBlock);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 旧格式回退：从旧字段重建有序内容块列表，然后复用 renderOrderedBlocks 渲染。
     * 顺序：thinking → tool calls (with results) → text（最终回复）。
     * 用于没有 contentBlocks 字段的历史消息。
     */
    private void renderLegacyAssistantMessage(TurnPanel turn, Message message) {
        String rawContent = message.getContent();
        String thinking = message.getThinkingContent();

        rawContent = tryExtractTextFromJson(rawContent);
        rawContent = stripEmbeddedToolJson(rawContent);

        if ((thinking == null || thinking.isEmpty()) && rawContent != null) {
            thinking = extractThinkTagContent(rawContent);
            if (thinking != null) {
                rawContent = THINK_PATTERN.matcher(rawContent).replaceAll("").trim();
            }
        }

        // 从旧字段重建有序 ContentBlockRecord 列表
        List<ContentBlockRecord> rebuiltBlocks = new ArrayList<>();

        // 1. thinking 始终在最前
        if (thinking != null && !thinking.isEmpty()) {
            rebuiltBlocks.add(ContentBlockRecord.thinking(thinking));
        }

        // 2. tool calls（每个工具调用后紧跟其结果）
        List<ToolCallSummary> summaries = message.getToolCallSummaries();
        if (summaries != null && !summaries.isEmpty()) {
            for (ToolCallSummary s : summaries) {
                rebuiltBlocks.add(ContentBlockRecord.toolUse(
                        s.getToolName(), s.getParams(), s.isSuccess(), s.getDurationMs()));
                String resultLabel = s.isSuccess() ? s.getToolName() : s.getToolName() + " (ERROR)";
                String resultText = s.getResultText() != null ? s.getResultText() : "(no output)";
                rebuiltBlocks.add(ContentBlockRecord.toolResult(resultLabel, resultText));
            }
        }

        // 3. text 放在最后（通常是工具执行后的最终回复）
        if (rawContent != null && !rawContent.isEmpty()) {
            rebuiltBlocks.add(ContentBlockRecord.text(rawContent));
        }

        // 复用有序渲染逻辑
        if (!rebuiltBlocks.isEmpty()) {
            renderOrderedBlocks(turn, rebuiltBlocks);
        }
    }

    /**
     * 从文本中提取 &lt;think&gt;...&lt;/think&gt; 标签内的内容。
     */
    private static String extractThinkTagContent(String text) {
        if (text == null) return null;
        Matcher m = THINK_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(m.group(1).trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 尝试将内容解析为 JSON 并提取纯文本（兼容旧历史中保存的原始 API 响应）。
     * 支持 OpenAI 格式 (choices[0].message.content)。
     * 如果不是 JSON 或解析失败，返回原始文本。
     */
    private static String tryExtractTextFromJson(String text) {
        if (text == null || text.isEmpty()) return text;
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) return text;
        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(trimmed);
            if (json == null) return text;
            com.alibaba.fastjson.JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                com.alibaba.fastjson.JSONObject choice = choices.getJSONObject(0);
                if (choice != null) {
                    com.alibaba.fastjson.JSONObject message = choice.getJSONObject("message");
                    if (message != null) {
                        String content = message.getString("content");
                        return content != null ? content : "";
                    }
                }
            }
        } catch (Exception e) {
            // 不是有效 JSON，返回原文
        }
        return text;
    }

    /**
     * 剥离文本中嵌入的工具调用/结果 JSON 对象和 ```tool_call``` 代码块。
     * 例如：
     *   "让我搜索文件：{ \"tool\":\"tree\", \"path\":\"...\", ... }"
     * 处理后只保留 "让我搜索文件："
     */
    private static String stripEmbeddedToolJson(String text) {
        if (text == null || text.isEmpty()) return text;

        // 1. 剥离 ```tool_call ... ``` 代码块（Markdown 协议格式）
        text = TOOL_CALL_BLOCK_PATTERN.matcher(text).replaceAll("").trim();

        // 2. 扫描并移除内嵌的工具相关 JSON 对象
        if (!text.contains("{")) return text;

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '{') {
                int end = findMatchingBrace(text, i);
                if (end > i) {
                    String candidate = text.substring(i, end + 1);
                    if (isToolRelatedJson(candidate)) {
                        i = end + 1;
                        continue;
                    }
                }
            }
            result.append(text.charAt(i));
            i++;
        }
        return result.toString().trim();
    }

    /**
     * 从 openPos（必须是 '{'）开始，找到配对的 '}'，返回其索引。
     * 正确处理字符串内的转义和嵌套。找不到返回 -1。
     */
    private static int findMatchingBrace(String text, int openPos) {
        int depth = 0;
        boolean inStr = false;
        boolean escaped = false;
        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inStr) { escaped = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * 判断一段 JSON 文本是否为工具调用或工具执行结果。
     * 包含以下特征字段时视为工具相关：
     * - "tool"（工具结果）
     * - "name" + "parameters"（工具调用）
     * - "tool_call_id" / "function"（OpenAI 格式）
     */
    private static boolean isToolRelatedJson(String jsonStr) {
        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(jsonStr);
            if (json == null) return false;
            if (json.containsKey("tool")) return true;
            if (json.containsKey("name") && json.containsKey("parameters")) return true;
            if (json.containsKey("tool_call_id")) return true;
            if (json.containsKey("function")) return true;
            if (json.containsKey("id") && json.containsKey("name")) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 追加一条历史消息（非流式，不设为 activeTurn）。
     * 通过 splitter 解析，使代码块等也能正确拆分。
     */
    private void appendHistoryText(String role, String text) {
        TurnPanel turn = new TurnPanel(role);
        turns.add(turn);

        turn.appendContent(text);
        turn.flushContent();

        JComponent comp = turn.getComponent();
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        turnContainer.add(comp);
        turnContainer.revalidate();
        turnContainer.repaint();
    }

    private void scrollToBottomIfNeeded() {
        if (scrollPane == null || userScrolledAway) {
            return;
        }
        scrollToBottomLater();
    }

    private void scrollToBottomLater() {
        if (scrollPane == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            revalidate();
            SwingUtilities.invokeLater(() -> {
                programmaticScroll = true;
                try {
                    JScrollBar vertical = scrollPane.getVerticalScrollBar();
                    vertical.setValue(vertical.getMaximum());
                } finally {
                    // 延迟重置标志，让事件处理完成
                    SwingUtilities.invokeLater(() -> programmaticScroll = false);
                }
            });
        });
    }
}
