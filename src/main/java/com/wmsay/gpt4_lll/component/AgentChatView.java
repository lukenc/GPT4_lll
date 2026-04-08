package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.CommentAction;
import com.wmsay.gpt4_lll.component.block.*;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.core.Message.ContentBlockRecord;
import com.wmsay.gpt4_lll.fc.core.Message.ToolCallSummary;
import com.wmsay.gpt4_lll.fc.state.FileSnapshot;
import com.wmsay.gpt4_lll.fc.state.PlanStepInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    /** 当前活跃的 StepBlock（步骤执行期间，内容路由到此 StepBlock 内部） */
    private StepBlock activeStepBlock;

    /** 当前活跃的计划进度面板 */
    private PlanProgressPanel activePlanPanel;
    /** 粘性进度横条（PlanProgressPanel 滚出可视区时显示） */
    private StickyProgressBar stickyProgressBar;
    /** AgentRuntimeBridge 引用，用于注册/移除计划进度监听器 */
    private AgentRuntimeBridge bridge;

    /** 用户是否主动向上滚动，接管了滚动位置 */
    private volatile boolean userScrolledAway = false;
    /** 标记是否由程序触发的滚动（避免误判为用户操作） */
    private volatile boolean programmaticScroll = false;

    /**
     * 滚动防抖 Timer：合并高频 scrollToBottom 请求，避免 EDT 队列洪泛。
     * 窗口失焦期间积累的多次请求只会触发一次实际滚动。
     */
    private final Timer scrollCoalesceTimer;
    private static final int SCROLL_COALESCE_MS = 100;

    /**
     * 不可见期间积压的 UI 操作缓冲区（类型化）。
     * 后台线程在窗口不可见时将操作写入此队列（无锁），
     * 恢复可见时由 HierarchyListener 在 EDT 上一次性 flush。
     * <p>
     * 使用类型化的 BufferedOp 而非 Runnable，使 flush 时能够
     * 将连续的 APPEND 操作合并为一个大字符串，只触发一次
     * StreamContentSplitter 解析和 flexmark 渲染，避免逐条渲染导致假死。
     */
    private final ConcurrentLinkedQueue<BufferedOp> offscreenBuffer = new ConcurrentLinkedQueue<>();

    /** 离屏缓冲操作类型 */
    private enum OpType { APPEND, START_THINKING, END_THINKING }

    /** 离屏缓冲操作记录 */
    private record BufferedOp(OpType type, String content) {
        static BufferedOp append(String content) { return new BufferedOp(OpType.APPEND, content); }
        static BufferedOp startThinking() { return new BufferedOp(OpType.START_THINKING, null); }
        static BufferedOp endThinking() { return new BufferedOp(OpType.END_THINKING, null); }
    }

    public AgentChatView() {
        super(new BorderLayout());

        turnContainer = new JPanel();
        turnContainer.setLayout(new BoxLayout(turnContainer, BoxLayout.Y_AXIS));
        turnContainer.setBorder(JBUI.Borders.empty(0, 0, 40, 0));

        JPanel topAligned = new JPanel(new BorderLayout());
        topAligned.add(turnContainer, BorderLayout.NORTH);

        add(topAligned, BorderLayout.CENTER);

        // 滚动防抖：无论多少次 scrollToBottomIfNeeded 被调用，
        // 实际滚动操作最多每 SCROLL_COALESCE_MS 毫秒执行一次
        scrollCoalesceTimer = new Timer(SCROLL_COALESCE_MS, e -> doScrollToBottom());
        scrollCoalesceTimer.setRepeats(false);

        // 窗口可见性变化监听：恢复可见时 flush 积压的流式内容
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    flushOffscreenBuffer();
                }
            }
        });

        // 创建 StickyProgressBar 实例（初始隐藏）
        stickyProgressBar = new StickyProgressBar();
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

        // StickyProgressBar 可见性检测：PlanProgressPanel 滚出可视区时显示横条
        // 使用 viewport ChangeListener 比 AdjustmentListener 更可靠
        scrollPane.getViewport().addChangeListener(e2 -> {
            if (stickyProgressBar != null && activePlanPanel != null) {
                boolean panelVisible = isPanelInViewport(activePlanPanel.getComponent());
                stickyProgressBar.updateVisibility(panelVisible);
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

    // ==================== Bridge 注入与计划进度集成 ====================

    /**
     * 注入 AgentRuntimeBridge 引用，用于注册/移除计划进度监听器。
     */
    public void setBridge(AgentRuntimeBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * 在当前 assistant TurnPanel 中创建 PlanProgressPanel 并注册为监听器。
     * 参数使用 PlanStepInfo DTO，不使用 PlanStep，确保与策略层解耦。
     *
     * @param steps 计划步骤信息列表（DTO）
     * @return 创建的 PlanProgressPanel 实例
     */
    public PlanProgressPanel addPlanProgressBlock(List<PlanStepInfo> steps) {
        ensureActiveTurn("assistant");
        PlanProgressPanel panel = new PlanProgressPanel(steps);
        activeTurn.addBlock(panel);
        activePlanPanel = panel;
        // 通过 Bridge 注册监听器（不直接接触 Provider）
        if (bridge != null) {
            bridge.addPlanProgressListener(panel);
        }
        // 配置 StickyProgressBar 跟踪此面板
        if (stickyProgressBar != null) {
            stickyProgressBar.setTrackedPanel(panel);
            if (bridge != null) {
                bridge.addPlanProgressListener(stickyProgressBar);
            }
            // 手动初始化 StickyProgressBar 状态：因为 onPlanGenerated 事件在注册监听器之前已经触发，
            // stickyBar 错过了该事件，需要手动通知它有活跃计划
            stickyProgressBar.onPlanGenerated(steps);
        }
        scrollToBottomIfNeeded();
        return panel;
    }

    // ==================== StepBlock 管理 ====================

    /**
     * 创建一个新的 StepBlock 并添加到当前 assistant TurnPanel。
     * 设置为当前活跃的 StepBlock，后续内容将路由到此 StepBlock 内部。
     */
    public StepBlock addStepBlock(int stepIndex, String description) {
        ensureActiveTurn("assistant");
        StepBlock block = new StepBlock(stepIndex, description);
        block.setOnContentChanged(() -> scrollToBottomIfNeeded());
        activeTurn.addBlock(block);
        activeTurn.setActiveBlock(null); // Reset active block so new content goes through StepBlock
        activeStepBlock = block;
        scrollToBottomIfNeeded();
        return block;
    }

    /**
     * 获取当前活跃的 StepBlock。
     */
    public StepBlock getActiveStepBlock() {
        return activeStepBlock;
    }

    /**
     * 清除当前活跃的 StepBlock，使后续内容恢复到 TurnPanel 顶层渲染。
     */
    public void clearActiveStepBlock() {
        activeStepBlock = null;
    }

    /**
     * 获取 StickyProgressBar 实例（供 WindowTool 添加到 JLayeredPane）。
     */
    public StickyProgressBar getStickyProgressBar() {
        return stickyProgressBar;
    }

    /**
     * 检查指定面板是否在 scrollPane 的可视区域内。
     *
     * @param panel 要检查的组件
     * @return true 如果面板的任何部分在可视区域内
     */
    private boolean isPanelInViewport(JComponent panel) {
        if (scrollPane == null || panel == null || panel.getParent() == null) return false;
        try {
            Rectangle viewRect = scrollPane.getViewport().getViewRect();
            Rectangle panelBounds = SwingUtilities.convertRectangle(
                    panel.getParent(), panel.getBounds(), scrollPane.getViewport().getView());
            return viewRect.intersects(panelBounds);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 追加流式内容到当前活跃 Turn 的活跃 Block。
     * 如果没有 activeTurn，自动创建 assistant TurnPanel。
     * <p>
     * 可见性感知：窗口不可见时将 delta 缓存到 offscreenBuffer，
     * 不投递 invokeLater，避免 EDT 队列积压。
     * 恢复可见时由 HierarchyListener 一次性 flush 所有积压内容，
     * 只触发一次 flexmark 渲染，消除切回时的假死。
     */
    public void appendContent(String content) {
        if (SwingUtilities.isEventDispatchThread()) {
            // 已在 EDT 上：直接追加（历史加载等场景）
            ensureActiveTurn("assistant");
            if (activeStepBlock != null) {
                activeStepBlock.appendContent(content);
            } else {
                activeTurn.appendContent(content);
            }
        } else {
            // 后台线程：检查可见性
            if (!isShowing()) {
                // 不可见：缓存到类型化 offscreenBuffer，不投递 invokeLater
                offscreenBuffer.add(BufferedOp.append(content));
                return;
            }
            SwingUtilities.invokeLater(() -> {
                ensureActiveTurn("assistant");
                if (activeStepBlock != null) {
                    activeStepBlock.appendContent(content);
                } else {
                    activeTurn.appendContent(content);
                }
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
            if (activeStepBlock != null) {
                activeStepBlock.startThinking();
            } else {
                activeTurn.startThinking();
            }
        } else {
            if (!isShowing()) {
                offscreenBuffer.add(BufferedOp.startThinking());
                return;
            }
            SwingUtilities.invokeLater(() -> {
                ensureActiveTurn("assistant");
                if (activeStepBlock != null) {
                    activeStepBlock.startThinking();
                } else {
                    activeTurn.startThinking();
                }
            });
        }
    }

    /**
     * 对应原 appendThingkingEnd()。
     * 将 ThinkingBlock 标记完成并折叠，清除 activeBlock。
     */
    public void appendThingkingEnd() {
        if (SwingUtilities.isEventDispatchThread()) {
            if (activeStepBlock != null) {
                activeStepBlock.endThinking();
            } else if (activeTurn != null) {
                activeTurn.endThinking();
            }
        } else {
            if (!isShowing()) {
                offscreenBuffer.add(BufferedOp.endThinking());
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (activeStepBlock != null) {
                    activeStepBlock.endThinking();
                } else if (activeTurn != null) {
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
        // 清理计划进度监听器
        if (bridge != null && activePlanPanel != null) {
            bridge.removePlanProgressListener(activePlanPanel);
        }
        if (bridge != null && stickyProgressBar != null) {
            bridge.removePlanProgressListener(stickyProgressBar);
        }
        activePlanPanel = null;
        activeStepBlock = null;

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
        scrollToBottomIfNeeded();
        return block.awaitDecision();
    }

    /**
     * 添加工具使用进度块（带旋转动画）。返回 ToolUseBlock 引用，
     * 调用方可在工具执行完成后调用 markCompleted() 更新状态。
     */
    public ToolUseBlock addToolUseBlock(String toolName, Map<String, Object> params) {
        ensureActiveTurn("assistant");
        ToolUseBlock block = new ToolUseBlock(toolName, params);
        if (activeStepBlock != null) {
            activeStepBlock.addChildBlock(block);
        } else {
            activeTurn.addBlock(block);
        }
        activeTurn.setActiveBlock(null);
        scrollToBottomIfNeeded();
        return block;
    }

    /**
     * 添加工具执行结果块（默认折叠）。
     */
    public void addToolResultBlock(String toolName, String resultText) {
        ensureActiveTurn("assistant");
        ToolResultBlock block = new ToolResultBlock(toolName, resultText);
        if (activeStepBlock != null) {
            activeStepBlock.addChildBlock(block);
        } else {
            activeTurn.addBlock(block);
        }
        activeTurn.setActiveBlock(null);
        scrollToBottomIfNeeded();
    }

    /**
     * 添加文件变更摘要块，展示 Agent 本轮修改的文件列表。
     * 空列表时跳过，不创建 UI 组件。
     */
    public void addFileChangesBlock(List<FileSnapshot> snapshots, Project project) {
        if (snapshots == null || snapshots.isEmpty()) return;
        ensureActiveTurn("assistant");
        FileChangesBlock block = new FileChangesBlock(snapshots, project);
        activeTurn.addBlock(block);
        activeTurn.setActiveBlock(null);
        scrollToBottomIfNeeded();
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
        // Pre-process: identify plan_step boundaries to group content into StepBlocks.
        // In the orderedBlocks list, a step's content (thinking, tool_use, tool_result, text)
        // appears BEFORE its corresponding plan_step record.
        // We need to find each plan_step and retroactively group the preceding blocks into it.

        // First pass: find indices of plan_step blocks and the first plan_step's start boundary
        List<Integer> planStepIndices = new ArrayList<>();
        int firstPlanStepContentStart = -1; // index where the first step's content begins
        for (int i = 0; i < blocks.size(); i++) {
            String type = blocks.get(i).getType();
            if ("plan_step".equals(type)) {
                planStepIndices.add(i);
                if (firstPlanStepContentStart < 0) {
                    // The first step's content starts right after the last "plan" block before this plan_step,
                    // or at the beginning if there's no plan block
                    firstPlanStepContentStart = 0;
                    for (int j = i - 1; j >= 0; j--) {
                        String prevType = blocks.get(j).getType();
                        if ("plan".equals(prevType)) {
                            firstPlanStepContentStart = j + 1;
                            break;
                        }
                    }
                }
            }
        }

        if (planStepIndices.isEmpty()) {
            // No plan steps — render everything flat (original behavior)
            for (ContentBlockRecord block : blocks) {
                renderSingleBlock(turn, block, null);
            }
            return;
        }

        // Render blocks before the first step's content (plan text, etc.) directly to turn
        // Special handling for "plan" blocks: create PlanProgressPanel with step statuses
        for (int i = 0; i < firstPlanStepContentStart; i++) {
            ContentBlockRecord blk = blocks.get(i);
            if ("plan".equals(blk.getType()) && blk.getContent() != null && !blk.getContent().isEmpty()) {
                // Parse plan text to extract step descriptions, then build PlanProgressPanel
                // with statuses from the plan_step records
                List<PlanStepInfo> stepInfos = buildHistoryPlanStepInfos(blk.getContent(), blocks, planStepIndices);
                if (!stepInfos.isEmpty()) {
                    PlanProgressPanel panel = new PlanProgressPanel(stepInfos);
                    // Mark plan as completed since this is history
                    panel.onPlanCompleted();
                    turn.addBlock(panel);
                } else {
                    // Fallback: render as text if parsing fails
                    renderSingleBlock(turn, blk, null);
                }
            } else {
                renderSingleBlock(turn, blk, null);
            }
        }

        // Render each step group: content blocks + plan_step
        // First, parse step descriptions from the plan text (if available)
        List<String> stepDescriptions = new ArrayList<>();
        for (int i = 0; i < firstPlanStepContentStart; i++) {
            ContentBlockRecord blk = blocks.get(i);
            if ("plan".equals(blk.getType()) && blk.getContent() != null) {
                for (String line : blk.getContent().split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.matches("\\d+\\.\\s+.+")) {
                        stepDescriptions.add(trimmed.replaceFirst("\\d+\\.\\s+", ""));
                    }
                }
            }
        }

        int contentStart = firstPlanStepContentStart;
        for (int planStepIdx : planStepIndices) {
            ContentBlockRecord stepRecord = blocks.get(planStepIdx);
            Integer idx = stepRecord.getStepIndex();
            boolean ok = Boolean.TRUE.equals(stepRecord.getStepSuccess());
            String res = stepRecord.getStepResult() != null ? stepRecord.getStepResult() : "";
            int stepIdx = idx != null ? idx : 0;

            // Use the parsed step description if available
            String stepDesc = stepIdx < stepDescriptions.size()
                    ? stepDescriptions.get(stepIdx)
                    : "Step " + (stepIdx + 1);

            StepBlock historyStep = new StepBlock(stepIdx, stepDesc);

            // Add child content blocks (thinking, tool_use, tool_result, text) into the StepBlock
            for (int i = contentStart; i < planStepIdx; i++) {
                renderSingleBlock(turn, blocks.get(i), historyStep);
            }

            // Set terminal state directly (skip animation for history)
            if (ok) {
                historyStep.markCompleted(res);
            } else {
                historyStep.markFailed(res);
            }
            historyStep.setCollapsed(true);
            turn.addBlock(historyStep);

            // Next step's content starts after this plan_step
            contentStart = planStepIdx + 1;
        }

        // Render any remaining blocks after the last plan_step (e.g., final text response)
        for (int i = contentStart; i < blocks.size(); i++) {
            renderSingleBlock(turn, blocks.get(i), null);
        }
    }

    /**
     * Render a single ContentBlockRecord. If targetStep is non-null, content is added
     * as a child of that StepBlock; otherwise it's added directly to the TurnPanel.
     */
    private void renderSingleBlock(TurnPanel turn, ContentBlockRecord block, StepBlock targetStep) {
        String type = block.getType();
        if (type == null) return;
        switch (type) {
            case "thinking":
                if (block.getContent() != null && !block.getContent().isEmpty()) {
                    if (targetStep != null) {
                        ThinkingBlock tb = targetStep.startThinking();
                        tb.appendContent(block.getContent());
                        targetStep.endThinking();
                    } else {
                        ThinkingBlock tb = turn.startThinking();
                        tb.appendContent(block.getContent());
                        turn.endThinking();
                    }
                }
                break;
            case "text":
                if (block.getContent() != null && !block.getContent().isEmpty()) {
                    if (targetStep != null) {
                        targetStep.appendContent(block.getContent());
                    } else {
                        turn.setActiveBlock(null);
                        turn.appendContent(block.getContent());
                        turn.flushContent();
                        turn.setActiveBlock(null);
                    }
                }
                break;
            case "tool_use":
                turn.setActiveBlock(null);
                ToolUseBlock useBlock = new ToolUseBlock(block.getToolName(), block.getParams());
                if (targetStep != null) {
                    targetStep.addChildBlock(useBlock);
                } else {
                    turn.addBlock(useBlock);
                }
                useBlock.markCompleted(block.isSuccess(), block.getDurationMs());
                break;
            case "tool_result":
                turn.setActiveBlock(null);
                String label = block.getToolName() != null ? block.getToolName() : "tool";
                String resultText = block.getResultText() != null ? block.getResultText() : "(no output)";
                ToolResultBlock resultBlock = new ToolResultBlock(label, resultText);
                if (targetStep != null) {
                    targetStep.addChildBlock(resultBlock);
                } else {
                    turn.addBlock(resultBlock);
                }
                break;
            case "plan":
                if (block.getContent() != null && !block.getContent().isEmpty()) {
                    turn.setActiveBlock(null);
                    turn.appendContent(block.getContent());
                    turn.flushContent();
                    turn.setActiveBlock(null);
                }
                break;
            case "plan_step":
                // Handled by the grouping logic in renderOrderedBlocks; if we get here
                // it means this plan_step wasn't grouped (shouldn't happen normally)
                turn.setActiveBlock(null);
                Integer idx = block.getStepIndex();
                boolean ok = Boolean.TRUE.equals(block.getStepSuccess());
                String res = block.getStepResult() != null ? block.getStepResult() : "";
                int stepIdx = idx != null ? idx : 0;
                String stepDesc = "Step " + (stepIdx + 1) + (ok ? " 完成" : " 失败");
                StepBlock historyStep = new StepBlock(stepIdx, stepDesc);
                if (ok) historyStep.markCompleted(res);
                else historyStep.markFailed(res);
                historyStep.setCollapsed(true);
                turn.addBlock(historyStep);
                break;
            case "file_changes":
                turn.setActiveBlock(null);
                List<ContentBlockRecord.FileSnapshotRecord> records = block.getFileSnapshots();
                if (records != null && !records.isEmpty()) {
                    FileChangesBlock fcb = new FileChangesBlock(records);
                    turn.addBlock(fcb);
                }
                break;
            default:
                break;
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

    private static String truncateForDisplay(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /**
     * 从 plan 文本中解析步骤描述，结合 plan_step 记录的成功/失败状态，
     * 构建 PlanStepInfo 列表用于历史模式的 PlanProgressPanel。
     *
     * plan 文本格式：
     *   **执行计划** (N 步):
     *     1. 步骤描述
     *     2. 步骤描述
     */
    private static List<PlanStepInfo> buildHistoryPlanStepInfos(
            String planText, List<ContentBlockRecord> allBlocks, List<Integer> planStepIndices) {
        // Parse step descriptions from plan text
        List<String> descriptions = new ArrayList<>();
        if (planText != null) {
            for (String line : planText.split("\n")) {
                String trimmed = line.trim();
                // Match lines like "1. description" or "  1. description"
                if (trimmed.matches("\\d+\\.\\s+.+")) {
                    String desc = trimmed.replaceFirst("\\d+\\.\\s+", "");
                    descriptions.add(desc);
                }
            }
        }

        // Build a map of stepIndex -> (success, result) from plan_step records
        java.util.Map<Integer, boolean[]> stepResults = new java.util.HashMap<>();
        java.util.Map<Integer, String> stepResultTexts = new java.util.HashMap<>();
        for (int idx : planStepIndices) {
            ContentBlockRecord rec = allBlocks.get(idx);
            Integer si = rec.getStepIndex();
            if (si != null) {
                stepResults.put(si, new boolean[]{Boolean.TRUE.equals(rec.getStepSuccess())});
                stepResultTexts.put(si, rec.getStepResult() != null ? rec.getStepResult() : "");
            }
        }

        // Build PlanStepInfo list
        List<PlanStepInfo> infos = new ArrayList<>();
        int stepCount = Math.max(descriptions.size(), stepResults.size());
        for (int i = 0; i < stepCount; i++) {
            String desc = i < descriptions.size() ? descriptions.get(i) : "Step " + (i + 1);
            PlanStepInfo.Status status;
            String result = "";
            if (stepResults.containsKey(i)) {
                status = stepResults.get(i)[0] ? PlanStepInfo.Status.COMPLETED : PlanStepInfo.Status.FAILED;
                result = stepResultTexts.getOrDefault(i, "");
            } else {
                status = PlanStepInfo.Status.SKIPPED;
            }
            infos.add(new PlanStepInfo(i, desc, status, result));
        }
        return infos;
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
        // 防抖：重置 timer，合并高频调用为一次实际滚动
        if (!scrollCoalesceTimer.isRunning()) {
            scrollCoalesceTimer.restart();
        }
    }

    /**
     * 实际执行滚动到底部的操作。由 scrollCoalesceTimer 触发，
     * 已在 EDT 上执行（Swing Timer 回调天然在 EDT）。
     * 不再使用嵌套 invokeLater，避免 EDT 队列积压。
     */
    private void doScrollToBottom() {
        if (scrollPane == null || userScrolledAway) {
            return;
        }
        revalidate();
        programmaticScroll = true;
        try {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        } finally {
            // 使用单次 invokeLater 重置标志（让当前事件处理完成后再重置）
            SwingUtilities.invokeLater(() -> programmaticScroll = false);
        }
    }

    /**
     * 将不可见期间积压的流式内容一次性 flush 到 activeTurn。
     * <p>
     * 核心优化：将连续的 APPEND 操作合并为一个大字符串，
     * 只调用一次 activeTurn.appendContent()，从而只触发一次
     * StreamContentSplitter 解析。遇到 START_THINKING / END_THINKING
     * 时先 flush 已合并的文本，再执行非文本操作，再继续合并。
     * <p>
     * 由 HierarchyListener 在恢复可见时调用，保证在 EDT 上执行。
     */
    private void flushOffscreenBuffer() {
        if (offscreenBuffer.isEmpty()) {
            return;
        }
        // drain 到本地列表
        List<BufferedOp> pending = new ArrayList<>();
        BufferedOp op;
        while ((op = offscreenBuffer.poll()) != null) {
            pending.add(op);
        }

        StringBuilder merged = new StringBuilder();
        for (BufferedOp p : pending) {
            switch (p.type()) {
                case APPEND -> merged.append(p.content());
                case START_THINKING -> {
                    // 先 flush 已合并的文本
                    if (merged.length() > 0) {
                        ensureActiveTurn("assistant");
                        activeTurn.appendContent(merged.toString());
                        merged.setLength(0);
                    }
                    ensureActiveTurn("assistant");
                    activeTurn.startThinking();
                }
                case END_THINKING -> {
                    // 先 flush 已合并的文本（思考内容）
                    if (merged.length() > 0) {
                        ensureActiveTurn("assistant");
                        activeTurn.appendContent(merged.toString());
                        merged.setLength(0);
                    }
                    if (activeTurn != null) {
                        activeTurn.endThinking();
                    }
                }
            }
        }
        // flush 剩余的合并文本
        if (merged.length() > 0) {
            ensureActiveTurn("assistant");
            activeTurn.appendContent(merged.toString());
        }
        // 强制 flush splitter 缓冲
        if (activeTurn != null) {
            activeTurn.flushContent();
        }
        scrollToBottomIfNeeded();
    }
}
