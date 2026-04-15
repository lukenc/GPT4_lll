package com.wmsay.gpt4_lll;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey;
import com.wmsay.gpt4_lll.model.key.Gpt4lllComboxKey;
import com.wmsay.gpt4_lll.component.Gpt4lllPlaceholderTextArea;
import com.wmsay.gpt4_lll.component.AgentChatView;
import com.wmsay.gpt4_lll.component.AgentRuntimeBridge;
import com.wmsay.gpt4_lll.component.StatusBorderPainter;
import com.wmsay.gpt4_lll.component.StatusIndicatorPanel;
import com.wmsay.gpt4_lll.component.StickyProgressBar;
import com.wmsay.gpt4_lll.component.theme.GlassButton;
import com.wmsay.gpt4_lll.component.theme.GlassPanel;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.model.key.Gpt4lllHistoryButtonKey;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.ConfigLoader;
import com.wmsay.gpt4_lll.fc.core.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.model.SelectModelOption;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.events.ProgressCallback;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.llm.StreamingLlmCaller;
import com.wmsay.gpt4_lll.fc.config.ExtensionLoader;
import com.wmsay.gpt4_lll.fc.tools.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.tools.RetryStrategy;
import com.wmsay.gpt4_lll.fc.tools.ApprovalProvider;
import com.wmsay.gpt4_lll.fc.tools.ErrorHandler;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolRegistry;
import com.wmsay.gpt4_lll.fc.tools.ValidationEngine;
import com.wmsay.gpt4_lll.component.IntelliJApprovalProvider;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.component.block.StepBlock;
import com.wmsay.gpt4_lll.fc.planning.ExecutionStrategy;
import com.wmsay.gpt4_lll.fc.planning.ExecutionStrategyFactory;
import com.wmsay.gpt4_lll.fc.planning.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.planning.PlanStep;
import com.wmsay.gpt4_lll.fc.llm.LlmApiException;
import com.wmsay.gpt4_lll.fc.llm.LlmClient;
import com.wmsay.gpt4_lll.fc.llm.LlmErrorClassifier;
import com.wmsay.gpt4_lll.fc.llm.LlmErrorInfo;
import com.wmsay.gpt4_lll.fc.llm.LlmRequest;
import com.wmsay.gpt4_lll.fc.llm.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.llm.ProtocolAdapterRegistry;
import com.wmsay.gpt4_lll.fc.llm.StreamingFcCollector;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapterRegistry;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import com.wmsay.gpt4_lll.model.RuntimeStatus;
import com.wmsay.gpt4_lll.model.AgentPhase;
import com.wmsay.gpt4_lll.model.AgentStatusContext;
import com.wmsay.gpt4_lll.fc.state.FileChangeTracker;
import com.wmsay.gpt4_lll.fc.state.FileSnapshot;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import com.wmsay.gpt4_lll.utils.RuntimeStatusManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC;
import static com.wmsay.gpt4_lll.utils.ChatUtils.getModelName;

public class WindowTool implements ToolWindowFactory {
    private static final ConcurrentHashMap<String, WindowTool> projectInstances = new ConcurrentHashMap<>();

    /**
     * Per-project 状态容器。
     * WindowTool 是 ToolWindowFactory 单例，所有项目共享同一个实例，
     * 因此必须按项目隔离所有可变状态，避免后打开的项目覆盖前一个。
     */
    static class ProjectState {
        AgentChatView readOnlyTextArea;
        ComboBox<String> providerComboBox;
        ComboBox<SelectModelOption> modelComboBox;
        JButton historyButton;
        JButton stopButton;
        ComboBox<String> strategyComboBox;
        FunctionCallOrchestrator functionCallOrchestrator;
        AgentRuntimeBridge agentRuntimeBridge;
        FunctionCallConfig functionCallConfig;
        ValidationEngine validationEngine;
        ErrorHandler errorHandler;
        ObservabilityManager observabilityManager;
        StatusIndicatorPanel statusIndicatorPanel;
        StatusBorderPainter statusBorderPainterRef;
        RuntimeStatusManager.AgentPhaseListener stopButtonPhaseListener;
    }

    private static final ConcurrentHashMap<String, ProjectState> projectStates = new ConcurrentHashMap<>();

    private final Map<String, List<SelectModelOption>> providerModels;

    /** 获取或创建指定项目的状态容器 */
    private static ProjectState getState(Project project) {
        return projectStates.computeIfAbsent(project.getBasePath(), k -> new ProjectState());
    }

    public WindowTool() {
        providerModels = new HashMap<>(ModelUtils.provider2ModelList);
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ProjectState ps = getState(project);

        projectInstances.put(project.getBasePath(), this);
        // 创建 ComboBox 实例
        ps.providerComboBox = new ComboBox<>();
        ps.modelComboBox = new ComboBox<>();
        project.putUserData(Gpt4lllComboxKey.GPT_4_LLL_MODEL_COMBO_BOX, ps.modelComboBox);
        project.putUserData(Gpt4lllComboxKey.GPT_4_LLL_PROVIDER_COMBO_BOX, ps.providerComboBox);

        // 设置渲染器
        setUpProviderComboBox(ps.providerComboBox);
        setUpComboBox(ps.modelComboBox);

        // 创建工具窗口内容
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        ps.readOnlyTextArea = new AgentChatView();
        project.putUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA, ps.readOnlyTextArea);

        JPanel modelSelectionPanel = createModelSelectionPanel(project);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4,
                LiquidGlassTheme.PANEL_MARGIN, 0, LiquidGlassTheme.PANEL_MARGIN);
        panel.add(modelSelectionPanel, c);

        ps.readOnlyTextArea.clearShowWindow();

        JScrollPane scrollPane = new JBScrollPane(ps.readOnlyTextArea);
        // 禁用 macOS 平滑滚动动画，减少 MacScrollBarAnimationBehavior 协程泛滥
        scrollPane.putClientProperty("JScrollPane.smoothScrolling", Boolean.FALSE);
        ps.readOnlyTextArea.setScrollPane(scrollPane);
        StatusBorderPainter statusBorderPainter = new StatusBorderPainter(scrollPane);
        ps.statusBorderPainterRef = statusBorderPainter;
        RuntimeStatusManager.addListener(project, statusBorderPainter);
        scrollPane.setBorder(statusBorderPainter);

        // Create StatusIndicatorPanel overlay
        ps.statusIndicatorPanel = new StatusIndicatorPanel();

        // StickyProgressBar 放在 scrollPane 上方，使用 BorderLayout 实现固定在顶部的效果。
        // 当 PlanProgressPanel 滚出可视区时，StickyProgressBar 显示在 scrollPane 上方（NORTH）；
        // 当 PlanProgressPanel 可见时，StickyProgressBar 隐藏，scrollPane 占满全部空间。
        StickyProgressBar stickyBar = ps.readOnlyTextArea.getStickyProgressBar();

        // ── 玻璃效果包裹聊天内容区域 ──
        // scrollPane 自身透明，让 GlassPanel 的磨砂玻璃背景穿透
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        ps.readOnlyTextArea.setOpaque(false);

        GlassPanel chatGlassWrapper = new GlassPanel(LiquidGlassTheme.RADIUS_MEDIUM, GlassPanel.BlurLevel.MEDIUM);
        chatGlassWrapper.setLayout(new BorderLayout());

        // scrollWrapper: StickyProgressBar(NORTH) + scrollPane(CENTER)
        JPanel scrollWrapper = new JPanel(new BorderLayout());
        scrollWrapper.setOpaque(false);
        if (stickyBar != null) {
            scrollWrapper.add(stickyBar, BorderLayout.NORTH);
        }
        scrollWrapper.add(scrollPane, BorderLayout.CENTER);

        chatGlassWrapper.add(scrollWrapper, BorderLayout.CENTER);

        // Use JLayeredPane to overlay StatusIndicatorPanel on scrollWrapper.
        // 避免 null layout + componentResized 的布局循环：
        // 改用覆写 doLayout() 的方式，由 Swing 布局管理器自然调用，不会产生事件循环。
        JLayeredPane layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                // chatGlassWrapper 填满整个 layeredPane
                chatGlassWrapper.setBounds(0, 0, getWidth(), getHeight());
                chatGlassWrapper.doLayout();
                // StatusIndicatorPanel 定位在底部居中
                Dimension pref = ps.statusIndicatorPanel.getPreferredSize();
                int x = (getWidth() - pref.width) / 2;
                int y = getHeight() - pref.height - 10;
                ps.statusIndicatorPanel.setBounds(x, Math.max(0, y), pref.width, pref.height);
            }

            @Override
            public Dimension getPreferredSize() {
                // 返回固定的小值，让 GridBagLayout 完全通过 weighty 分配空间，
                // 而不是被 scrollWrapper 内部的内容高度撑开。
                // 之前使用 scrollWrapper.getMinimumSize() 可能在 revalidate 时
                // 返回巨大的值（JBScrollPane 的 minimumSize 可能跟随内容增长），
                // 导致 GridBagLayout 给滚动区域分配过多空间，挤掉输入框和底部栏。
                return new Dimension(100, 100);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(100, 100);
            }
        };
        layeredPane.add(chatGlassWrapper, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(ps.statusIndicatorPanel, JLayeredPane.PALETTE_LAYER);

        Insets insets = JBUI.insets(LiquidGlassTheme.PANEL_GAP,
                LiquidGlassTheme.PANEL_MARGIN, LiquidGlassTheme.PANEL_GAP, LiquidGlassTheme.PANEL_MARGIN);
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 1;
        c.weighty = 0.85;
        c.insets = insets;
        panel.add(layeredPane, c);
        //对话框

        Gpt4lllPlaceholderTextArea textField = new Gpt4lllPlaceholderTextArea("请输入内容/input here");
        textField.setOpaque(false); // 需求 10.2：透明背景，使 GlassPanel 玻璃效果穿透

        JScrollPane scrollInputPane = new JBScrollPane(textField);
        scrollInputPane.putClientProperty("JScrollPane.smoothScrolling", Boolean.FALSE);
        scrollInputPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollInputPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollInputPane.setOpaque(false);
        scrollInputPane.getViewport().setOpaque(false);
        scrollInputPane.setBorder(BorderFactory.createEmptyBorder());

        // ── 需求 10.1: GlassPanel 包裹输入框，需求 10.3/10.4: 高光+边框，需求 10.5: 焦点反馈 ──
        GlassPanel inputGlassWrapper = new GlassPanel(LiquidGlassTheme.RADIUS_MEDIUM, GlassPanel.BlurLevel.MEDIUM) {
            private float borderAlphaBoost = 0f;

            {
                // 焦点反馈：focusGained 增加边框 alpha(+0.2)，focusLost 恢复
                textField.addFocusListener(new FocusListener() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        borderAlphaBoost = 0.2f;
                        repaint();
                    }
                    @Override
                    public void focusLost(FocusEvent e) {
                        borderAlphaBoost = 0f;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // 焦点状态下额外绘制增强边框
                if (borderAlphaBoost > 0f) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int s = LiquidGlassTheme.SHADOW_SPREAD;
                        int w = getWidth() - s * 2;
                        int h = getHeight() - s * 2;
                        if (w > 0 && h > 0) {
                            Color border = LiquidGlassTheme.BORDER;
                            int boostedAlpha = Math.min(255, border.getAlpha() + (int)(255 * borderAlphaBoost));
                            g2.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), boostedAlpha));
                            g2.setStroke(new BasicStroke(1.5f));
                            g2.drawRoundRect(s, s, w - 1, h - 1,
                                    LiquidGlassTheme.RADIUS_MEDIUM, LiquidGlassTheme.RADIUS_MEDIUM);
                        }
                    } finally {
                        g2.dispose();
                    }
                }
            }
        };
        inputGlassWrapper.setLayout(new BorderLayout());
        inputGlassWrapper.add(scrollInputPane, BorderLayout.CENTER);

        c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 1;
        c.weighty = 0.12;
        c.insets = new Insets(0, LiquidGlassTheme.PANEL_MARGIN, 0, LiquidGlassTheme.PANEL_MARGIN);
        panel.add(inputGlassWrapper, c);

        // ── 需求 11: 统一三栏式底部操作栏 ──
        // 合并 buttonPanel + newAndHistoryPanel 为一个 GlassPanel 三栏布局
        // 左: New Session (图标+文字), 中: Send message (强调色大圆角), 右: History (图标+文字)
        // RUNNING 状态时中间 CardLayout 切换为 Stop 按钮

        // ── 左栏: New Session 按钮（中性色，小圆角 8px）──
        GlassButton newSessionButton = new GlassButton("New Session",
                LiquidGlassTheme.RADIUS_SMALL, LiquidGlassTheme.BORDER);
        newSessionButton.addActionListener(e -> {
            // Capture data needed for background save while still on EDT
            List<Message> history = ChatUtils.getProjectChatHistory(project);
            String topic = ChatUtils.getProjectTopic(project);
            boolean needsSave = history != null && !history.isEmpty()
                    && topic != null && !topic.isEmpty();
            List<Message> snapshotForSave = needsSave ? new ArrayList<>(history) : null;

            if (needsSave) {
                history.clear();
            }
            ChatUtils.setProjectTopic(project, "");
            if (project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA) != null) {
                project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA).clearShowWindow();
            }
            if (ps.agentRuntimeBridge != null) {
                ps.agentRuntimeBridge.resetSession();
            }

            // Move file I/O off EDT
            if (needsSave) {
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    JsonStorage.saveConservation(topic, snapshotForSave);
                });
            }
        });

        // ── 中栏: Send message 主按钮（强调色 tint，大圆角 16px）──
        GlassButton sendButton = new GlassButton("发送聊天/Send message",
                LiquidGlassTheme.RADIUS_LARGE, LiquidGlassTheme.ACCENT);
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 防止在请求进行中重复发送，避免多线程同时操作 UI 导致假死
                Thread existingThread = ps.agentRuntimeBridge != null
                        ? ps.agentRuntimeBridge.getCurrentRequestThread() : null;
                if (existingThread != null && existingThread.isAlive()) {
                    return;
                }

                AgentChatView area = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
                String input = textField.getText();
                if (StringUtil.isEmpty(input)) {
                    Messages.showMessageDialog(project, "Please enter what you want to say in the chat box first, and then click Send\n请先在聊天框中输入您想说的话，然后再点击发送哦~", "Error", Messages.getErrorIcon());
                    return;
                }
                area.startUserTurn();
                area.appendContent("YOU:" + input);
                // activeTurn 置空，让后续 AI 回复自动创建新的 assistant turn
                area.startAssistantTurn();
                if (project.getUserData(GPT_4_LLL_NOW_TOPIC)==null||project.getUserData(GPT_4_LLL_NOW_TOPIC).isEmpty()) {
                    project.putUserData(GPT_4_LLL_NOW_TOPIC,GenerateAction.formatter.format(LocalDateTime.now()) + "--Chat:" + input);
                }

                Message message = new Message();
                message.setRole("user");
                message.setName("owner");
                message.setContent(input);
                ChatUtils.getProjectChatHistory(project).add(message);

                ChatContent chatContent = new ChatContent();
                String providerName = ModelUtils.getSelectedProvider(project);
                List<Message> adaptedMessages = ProviderAdapterRegistry.getAdapter(providerName).adaptMessages(ChatUtils.getProjectChatHistory(project));
                chatContent.setMessages(adaptedMessages);
                String model = getModelName(project);
                chatContent.setModel(model);

                // 检查是否启用 function calling
                if (isFunctionCallingEnabled(project)) {
                    // NOTE: Using new Thread() instead of ApplicationManager.executeOnPooledThread()
                    // is intentional here. agentRuntimeBridge.setCurrentRequestThread(Thread.currentThread())
                    // needs a direct reference to the thread for interruption via the Stop button.
                    // executeOnPooledThread() returns a Future, not a Thread, making interrupt impossible.
                    new Thread(() -> {
                        if (ps.agentRuntimeBridge != null) {
                            ps.agentRuntimeBridge.setCurrentRequestThread(Thread.currentThread());
                        }
                        try {
                            if (ps.agentRuntimeBridge != null && ps.agentRuntimeBridge.isInitialized()) {
                                executeViaAgentRuntime(input, chatContent, project, area);
                            } else {
                                executeFunctionCallingChat(chatContent, project, area);
                            }
                        } finally {
                            if (ps.agentRuntimeBridge != null) {
                                ps.agentRuntimeBridge.setCurrentRequestThread(null);
                            }
                        }
                    }).start();
                } else {
                    // NOTE: Same rationale as above — new Thread() needed for Stop button interruption.
                    new Thread(() -> {
                        if (ps.agentRuntimeBridge != null) {
                            ps.agentRuntimeBridge.setCurrentRequestThread(Thread.currentThread());
                        }
                        try {
                            String res = GenerateAction.chat(chatContent, project, false, true, "");
                        } finally {
                            if (ps.agentRuntimeBridge != null) {
                                ps.agentRuntimeBridge.setCurrentRequestThread(null);
                            }
                        }
                    }).start();
                }

                textField.setText("");
            }
        });

        // Stop 按钮（红色/橙色 tint，大圆角 16px）
        GlassButton stopGlassButton = new GlassButton("停止/Stop",
                LiquidGlassTheme.RADIUS_LARGE, LiquidGlassTheme.ERROR);
        ps.stopButton = stopGlassButton;
        ps.stopButton.addActionListener(e -> {
            ps.agentRuntimeBridge.requestStop(project);
        });

        // 中间面板：CardLayout 切换 Send / Stop
        CardLayout centerCardLayout = new CardLayout();
        JPanel centerPanel = new JPanel(centerCardLayout);
        centerPanel.setOpaque(false);
        centerPanel.add(sendButton, "SEND");
        centerPanel.add(stopGlassButton, "STOP");

        // ── 右栏: History 按钮（中性色，小圆角 8px）──
        GlassButton historyGlassButton = new GlassButton("History",
                LiquidGlassTheme.RADIUS_SMALL, LiquidGlassTheme.BORDER);
        ps.historyButton = historyGlassButton;
        project.putUserData(Gpt4lllHistoryButtonKey.GPT_4_LLL_HISTORY_BUTTON, historyGlassButton);
        historyGlassButton.addActionListener(e -> showPopup(project));

        // ── 统一底部操作栏: GlassPanel + BorderLayout(WEST, CENTER, EAST) ──
        GlassPanel bottomBar = new GlassPanel(LiquidGlassTheme.RADIUS_MEDIUM, GlassPanel.BlurLevel.MEDIUM);
        bottomBar.setLayout(new BorderLayout(8, 0));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(
                LiquidGlassTheme.PANEL_PADDING, LiquidGlassTheme.PANEL_PADDING,
                LiquidGlassTheme.PANEL_PADDING, LiquidGlassTheme.PANEL_PADDING));
        bottomBar.add(newSessionButton, BorderLayout.WEST);
        bottomBar.add(centerPanel, BorderLayout.CENTER);
        bottomBar.add(historyGlassButton, BorderLayout.EAST);

        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 1;
        c.insets = new Insets(0, LiquidGlassTheme.PANEL_MARGIN, LiquidGlassTheme.PANEL_MARGIN, LiquidGlassTheme.PANEL_MARGIN);
        panel.add(bottomBar, c);

        // Register AgentPhaseListener to toggle Send/Stop via CardLayout
        ps.stopButtonPhaseListener = (oldCtx, newCtx) -> {
            SwingUtilities.invokeLater(() -> {
                if (project.isDisposed()) return;
                boolean running = newCtx.getPhase() == AgentPhase.RUNNING;
                centerCardLayout.show(centerPanel, running ? "STOP" : "SEND");
            });
        };
        // Also keep the old StatusListener as fallback for non-AgentRuntime paths
        RuntimeStatusManager.addListener(project, (oldStatus, newStatus) -> {
            SwingUtilities.invokeLater(() -> {
                if (project.isDisposed()) return;
                boolean running = newStatus == RuntimeStatus.RUNNING;
                centerCardLayout.show(centerPanel, running ? "STOP" : "SEND");
            });
        });

        initializeComboBoxes(project);

        // 初始化 Function Calling 框架
        initializeFunctionCalling(project);

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);


        // 使用 Disposer 注册清理逻辑，确保 ToolWindow 关闭或插件卸载时自动清理资源
        Disposer.register(toolWindow.getDisposable(), () -> {
            disposeProjectState(project, ps, statusBorderPainter);
        });

        // 添加项目关闭监听器（作为 fallback，确保项目关闭时也能清理）
        project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                disposeProjectState(project, ps, statusBorderPainter);
            }
        });
    }

    /**
     * 清理指定项目的所有资源：移除 listener、停止 timer、释放 AgentRuntime。
     * 此方法可被多次调用（Disposer + projectClosing），内部做幂等处理。
     */
    private void disposeProjectState(Project project, ProjectState ps, StatusBorderPainter statusBorderPainter) {
        if (ps == null) return;
        // 幂等：如果已经从 map 中移除，说明已经清理过
        if (projectStates.remove(project.getBasePath()) == null
                && !projectInstances.containsKey(project.getBasePath())) {
            return;
        }
        projectInstances.remove(project.getBasePath());

        // Remove AgentPhaseListeners
        if (ps.statusIndicatorPanel != null) {
            RuntimeStatusManager.removePhaseListener(project, ps.statusIndicatorPanel);
            ps.statusIndicatorPanel.dispose();
        }
        if (ps.statusBorderPainterRef != null) {
            RuntimeStatusManager.removePhaseListener(project, ps.statusBorderPainterRef);
            ps.statusBorderPainterRef.dispose();
        }
        if (ps.stopButtonPhaseListener != null) {
            RuntimeStatusManager.removePhaseListener(project, ps.stopButtonPhaseListener);
        }
        // Dispose AgentChatView（清理 timer 和 TurnPanel）
        if (ps.readOnlyTextArea != null) {
            ps.readOnlyTextArea.dispose();
        }
        // 清理 RuntimeStatusManager 中的所有 listener 引用
        RuntimeStatusManager.clearListeners(project);
        // 关闭 AgentRuntime 并释放资源 — 移到后台线程执行，
        // 因为 AgentRuntime.shutdown() 内部调用 threadPool.awaitTermination(5s)，
        // 在 EDT 上执行会导致 UI 冻结最多 5 秒。
        if (ps.agentRuntimeBridge != null) {
            AgentRuntimeBridge bridge = ps.agentRuntimeBridge;
            ApplicationManager.getApplication().executeOnPooledThread(bridge::shutdown);
        }
    }


    public void showPopup(Project project) {
        // Load history data off EDT, then build and show popup on EDT
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Map<String, List<Message>> historyData;
            try {
                historyData = JsonStorage.loadData();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Switch back to EDT for UI work
            SwingUtilities.invokeLater(() -> {
                if (project.isDisposed()) return;

                // 构建列表数据
                List<Pair<String, String>> data = new ArrayList<Pair<String, String>>();

                for (String topic : historyData.keySet()) {
                    data.add(Pair.create(take(topic, 100), topic));
                }

                // 构建列表模型
                var model = new CollectionListModel<>(data);

                // 创建列表
                var list = new JBList<>(model);
                list.setCellRenderer((list1, value, index, isSelected, cellHasFocus) -> {
                    JLabel label = new JLabel(value.first);
                    label.setToolTipText(value.second);
                    return label;
                });

                // 设置列表项点击事件
                list.addListSelectionListener(event -> {
                    if (!event.getValueIsAdjusting()) {
                        project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA).clearShowWindow();
                        String selectedItem = list.getSelectedValue().second;
                        // 实现你想要的点击事件逻辑
                        List<Message> messageList = historyData.get(selectedItem);
                        messageList.forEach(message -> {
                            project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA).appendMessage(message);
                        });
                        ChatUtils.setProjectTopic(project, selectedItem);
                        project.putUserData(Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY, messageList);
                    }
                });

                // 创建滚动面板并添加列表
                JBScrollPane scrollPane = new JBScrollPane(list);
                scrollPane.setPreferredSize(new Dimension(500, 150));

                // 创建弹窗并显示
                JBPopupFactory.getInstance()
                        .createComponentPopupBuilder(scrollPane, null)
                        .setTitle("Gpt History")
                        .setProject(project)
                        .createPopup()
                        .showInFocusCenter();
            });
        });
    }


    public static String take(String str, int length) {
        if (str.length() <= length) {
            return str;
        } else {
            return str.substring(0, length);
        }
    }


    private JPanel createModelSelectionPanel(Project project) {
        ProjectState ps = getState(project);

        // ── GlassPanel 容器替换原 JPanel（需求 3.1）──
        GlassPanel panel = new GlassPanel(LiquidGlassTheme.RADIUS_MEDIUM, GlassPanel.BlurLevel.MEDIUM);
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(5);

        // 添加提供商下拉框的监听器
        ps.providerComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (project.isDisposed()) return;
                    updateModelComboBox(project);
                    saveSettings(project);
                });
            }
        });
        ps.modelComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                saveSettings(project);
            }
        });

        // ── 为 ComboBox 应用玻璃质感样式（需求 3.2, 3.3, 3.4）──
        applyGlassComboBoxStyle(ps.providerComboBox);
        applyGlassComboBoxStyle(ps.modelComboBox);

        // Row 0: Provider + Model
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel providerLabel = new JLabel("Provider: ");
        providerLabel.setForeground(LiquidGlassTheme.FOREGROUND);
        panel.add(providerLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.4;
        panel.add(ps.providerComboBox, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(Box.createHorizontalStrut(10), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0;
        JLabel modelLabel = new JLabel("Model: ");
        modelLabel.setForeground(LiquidGlassTheme.FOREGROUND);
        panel.add(modelLabel, gbc);

        gbc.gridx = 4;
        gbc.weightx = 0.6;
        panel.add(ps.modelComboBox, gbc);

        // Row 1: Strategy selector
        ps.strategyComboBox = new ComboBox<>();
        for (ExecutionStrategy strategy : ExecutionStrategyFactory.getAll()) {
            ps.strategyComboBox.addItem(strategy.getDisplayName());
        }
        ps.strategyComboBox.setSelectedItem("ReAct");
        ps.strategyComboBox.setToolTipText("选择执行策略 / Select execution strategy");
        ps.strategyComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String selectedDisplay = (String) ps.strategyComboBox.getSelectedItem();
                switchExecutionStrategy(selectedDisplay);
            }
        });

        // ── 为 Strategy ComboBox 应用玻璃质感样式 ──
        applyGlassComboBoxStyle(ps.strategyComboBox);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        JLabel strategyLabel = new JLabel("Strategy: ");
        strategyLabel.setForeground(LiquidGlassTheme.FOREGROUND);
        panel.add(strategyLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0.4;
        panel.add(ps.strategyComboBox, gbc);
        gbc.gridwidth = 1;

        // Strategy description label
        JLabel strategyDescLabel = new JLabel("观察→思考→行动循环，适用于通用任务");
        strategyDescLabel.setFont(strategyDescLabel.getFont().deriveFont(Font.ITALIC, 11f));
        strategyDescLabel.setForeground(java.awt.Color.GRAY);
        gbc.gridx = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 0.6;
        panel.add(strategyDescLabel, gbc);
        gbc.gridwidth = 1;

        // Update description when strategy changes
        ps.strategyComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String selectedDisplay = (String) ps.strategyComboBox.getSelectedItem();
                for (ExecutionStrategy s : ExecutionStrategyFactory.getAll()) {
                    if (s.getDisplayName().equals(selectedDisplay)) {
                        strategyDescLabel.setText(s.getDescription());
                        break;
                    }
                }
            }
        });

        return panel;
    }

    /**
     * 为 ComboBox 应用 Liquid Glass 玻璃质感样式。
     * 半透明背景 + 圆角(8px) + 高光反射条 + 悬停反馈（alpha +0.1）。
     * （需求 3.2, 3.3, 3.4）
     */
    private void applyGlassComboBoxStyle(JComboBox<?> comboBox) {
        int radius = LiquidGlassTheme.RADIUS_SMALL; // 8px

        comboBox.setOpaque(false);
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel renderer = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof SelectModelOption opt) {
                    renderer.setText(opt.getDisplayName());
                    renderer.setToolTipText(opt.getDescription());
                } else if (value != null) {
                    renderer.setToolTipText(value.toString());
                }
                renderer.setForeground(LiquidGlassTheme.FOREGROUND);
                if (!isSelected) {
                    renderer.setBackground(new Color(0, 0, 0, 0));
                }
                return renderer;
            }
        });

        // 自定义绘制：半透明背景 + 圆角 + 高光反射条
        comboBox.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            private boolean hovered = false;

            @Override
            public void installUI(JComponent c) {
                super.installUI(c);
                c.setOpaque(false);
                c.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        c.repaint();
                    }
                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        c.repaint();
                    }
                });
            }

            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = c.getWidth();
                    int h = c.getHeight();

                    // 半透明背景（悬停时 alpha +0.1 ≈ +25）
                    Color bg = LiquidGlassTheme.SECONDARY_BG;
                    if (hovered) {
                        bg = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
                                Math.min(255, bg.getAlpha() + 25));
                    }
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, w, h, radius, radius);

                    // 高光反射条（顶部渐变淡出）
                    Color hlColor = LiquidGlassTheme.HIGHLIGHT;
                    int fadeH = 4;
                    g2.setPaint(new GradientPaint(0, 0, hlColor,
                            0, fadeH, new Color(hlColor.getRed(), hlColor.getGreen(), hlColor.getBlue(), 0)));
                    g2.fillRect(radius / 2, 0, w - radius, fadeH);

                    // 圆角边框（1px 半透明）
                    g2.setColor(LiquidGlassTheme.BORDER);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
                } finally {
                    g2.dispose();
                }
                // 绘制 ComboBox 内容（文本 + 箭头）
                super.paint(g, c);
            }

            @Override
            protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        try {
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            int w = getWidth();
                            int h = getHeight();
                            // 绘制下拉箭头三角形
                            int triW = 8;
                            int triH = 5;
                            int cx = w / 2;
                            int cy = h / 2;
                            int[] xPoints = {cx - triW / 2, cx + triW / 2, cx};
                            int[] yPoints = {cy - triH / 2, cy - triH / 2, cy + triH / 2};
                            g2.setColor(LiquidGlassTheme.FOREGROUND);
                            g2.fillPolygon(xPoints, yPoints, 3);
                        } finally {
                            g2.dispose();
                        }
                    }
                };
                arrow.setOpaque(false);
                arrow.setContentAreaFilled(false);
                arrow.setBorderPainted(false);
                arrow.setFocusPainted(false);
                arrow.setBorder(BorderFactory.createEmptyBorder());
                arrow.setPreferredSize(new Dimension(24, 24));
                return arrow;
            }
        });
    }

    /**
     * 切换执行策略（通过 UI 下拉框触发）。
     */
    private void switchExecutionStrategy(String displayName) {
        // Note: this is called from UI combo box which doesn't have project context easily.
        // We iterate all project states to apply the change.
        // In practice, the combo box is per-project (stored in ps), so this affects the right one.
        for (ProjectState ps : projectStates.values()) {
            if (ps.functionCallOrchestrator == null) continue;
            for (ExecutionStrategy strategy : ExecutionStrategyFactory.getAll()) {
                if (strategy.getDisplayName().equals(displayName)) {
                    ps.functionCallOrchestrator.setExecutionStrategyName(strategy.getName());
                    System.out.println("[FC] Execution strategy switched to: "
                            + strategy.getName() + " (" + strategy.getDisplayName() + ")");
                    break;
                }
            }
        }
    }

    private void updateModelComboBox(Project project) {
        String selectedProvider = (String) project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_PROVIDER_COMBO_BOX).getSelectedItem();

        // 在EDT中执行UI更新
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_MODEL_COMBO_BOX).removeAllItems();

            if (selectedProvider != null) {
                List<SelectModelOption> models = providerModels.get(selectedProvider);
                if (models != null) {
                    for (SelectModelOption model : models) {
                        project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_MODEL_COMBO_BOX).addItem(model);
                    }
                }
            }
        });
    }


    private void setUpComboBox(JComboBox<SelectModelOption> comboBox) {
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel renderer = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    renderer.setText(((SelectModelOption) value).getDisplayName());
                    renderer.setToolTipText(((SelectModelOption) value).getDescription());
                }
                return renderer;
            }
        });
    }


    private void setUpProviderComboBox(JComboBox<String> comboBox) {
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel renderer = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    renderer.setToolTipText(value.toString());
                }
                return renderer;
            }
        });
    }


    public SelectModelOption getSelectedModel(Project project) {
        return project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_MODEL_COMBO_BOX) != null
                ? (SelectModelOption) project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_MODEL_COMBO_BOX).getSelectedItem()
                : null;
    }

    public String getSelectedProvider(Project project) {
        return project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_PROVIDER_COMBO_BOX) != null
                ? (String) project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_PROVIDER_COMBO_BOX).getSelectedItem()
                : null;
    }

    public static WindowTool getInstance(Project project) {
        if (project == null) return null;
        return projectInstances.get(project.getBasePath());
    }

    /**
     * 获取 FunctionCallOrchestrator 实例（供 GenerateAction 等外部调用方使用）。
     *
     * @return 编排器实例，如果 FC 未初始化则返回 null
     */
    public FunctionCallOrchestrator getFunctionCallOrchestrator() {
        return null; // deprecated — use getFunctionCallOrchestrator(project)
    }

    public FunctionCallOrchestrator getFunctionCallOrchestrator(Project project) {
        ProjectState ps = projectStates.get(project.getBasePath());
        return ps != null ? ps.functionCallOrchestrator : null;
    }

    /**
     * 获取 FunctionCallConfig 实例（供 GenerateAction 等外部调用方使用）。
     *
     * @return 配置实例，如果 FC 未初始化则返回 null
     */
    public FunctionCallConfig getFunctionCallConfig() {
        return null; // deprecated — use getFunctionCallConfig(project)
    }

    public FunctionCallConfig getFunctionCallConfig(Project project) {
        ProjectState ps = projectStates.get(project.getBasePath());
        return ps != null ? ps.functionCallConfig : null;
    }

    /**
     * 获取 AgentRuntimeBridge 实例（供 GenerateAction.chat() 注册请求线程使用）。
     *
     * @return AgentRuntimeBridge 实例，如果未初始化则返回 null
     */
    public AgentRuntimeBridge getAgentRuntimeBridge() {
        return null; // deprecated — use getAgentRuntimeBridge(project)
    }

    public AgentRuntimeBridge getAgentRuntimeBridge(Project project) {
        ProjectState ps = projectStates.get(project.getBasePath());
        return ps != null ? ps.agentRuntimeBridge : null;
    }


    private void initializeComboBoxes(Project project) {
        ProjectState ps = getState(project);
        // 初始化提供商下拉框
        ps.providerComboBox.removeAllItems();
        for (String provider : providerModels.keySet()) {
            ps.providerComboBox.addItem(provider);
        }

        // 确保选择了一个提供商
        if (ps.providerComboBox.getItemCount() > 0) {
            ps.providerComboBox.setSelectedIndex(0);
            // 初始化模型下拉框
            updateModelComboBox(project);
        }

        ProjectSettings settings = ProjectSettings.getInstance(project);
        String lastProvider = settings.getLastProvider();
        String lastModel = settings.getLastModelDisplayName();
        System.out.println("Loading settings: provider=" + lastProvider + ", model=" + lastModel);
        if (!lastProvider.isEmpty()) {
            ps.providerComboBox.setSelectedItem(lastProvider);
            updateModelComboBox(project);
            if (!lastModel.isEmpty()) {
                for (int i = 0; i < ps.modelComboBox.getItemCount(); i++) {
                    SelectModelOption option = ps.modelComboBox.getItemAt(i);
                    if (option.getDisplayName().equals(lastModel)) {
                        ps.modelComboBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
    }

    private void saveSettings(Project project) {
        ProjectState ps = getState(project);
        ProjectSettings settings = ProjectSettings.getInstance(project);
        String provider = (String) ps.providerComboBox.getSelectedItem();
        SelectModelOption selectedModel = (SelectModelOption) ps.modelComboBox.getSelectedItem();
        String modelDisplay = selectedModel != null ? selectedModel.getDisplayName() : "";
        System.out.println("Saving settings: provider=" + provider + ", model=" + modelDisplay);
        settings.setLastProvider(provider);
        settings.setLastModelDisplayName(modelDisplay);
    }

    // ==================== Function Calling 集成 ====================

    /**
     * 初始化 Function Calling 框架组件。
     * 加载配置、创建各组件实例、加载 SPI 扩展、组装编排器。
     * 初始化失败不影响现有功能，仅记录日志。
     */
    private void initializeFunctionCalling(Project project) {
        ProjectState ps = getState(project);
        // Move heavy initialization (file I/O, SPI loading, AgentRuntime setup) off EDT
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                System.out.println("[FC] Initializing for project: name=" + project.getName()
                        + ", basePath=" + project.getBasePath());
                // 1. 加载配置 (file I/O)
                ConfigLoader configLoader = new ConfigLoader();
                Path configPath = null;
                if (project.getBasePath() != null) {
                    configPath = Paths.get(project.getBasePath(), ".lll", "function-calling-config.json");
                }
                ps.functionCallConfig = configLoader.load(configPath);

                // 2. 创建核心组件
                ToolRegistry toolRegistry = new ToolRegistry();
                // Bridge tools from McpToolRegistry into the framework ToolRegistry
                for (Tool tool : McpToolRegistry.getAllTools()) {
                    toolRegistry.registerTool(tool);
                }
                ps.validationEngine = new ValidationEngine(toolRegistry);
                ps.observabilityManager = new ObservabilityManager();
                ps.observabilityManager.setLogLevel(ps.functionCallConfig.getLogLevel());

                RetryStrategy retryStrategy = new RetryStrategy();
                ApprovalProvider approvalProvider = new IntelliJApprovalProvider();
                ExecutionEngine executionEngine = new ExecutionEngine(toolRegistry, approvalProvider, retryStrategy);

                ps.errorHandler = new ErrorHandler(
                        () -> toolRegistry.getAllTools().stream().map(Tool::name).collect(Collectors.toList()));

                // 3. 加载 SPI 扩展
                ExtensionLoader.loadAll(ps.validationEngine, ps.errorHandler);

                // 4. 根据当前供应商选择协议适配器并创建编排器
                String currentProvider = ModelUtils.getSelectedProvider(project);
                ProtocolAdapter protocolAdapter = ProtocolAdapterRegistry.getAdapter(
                        currentProvider != null ? currentProvider : "");

                ps.functionCallOrchestrator = new FunctionCallOrchestrator(
                        protocolAdapter, ps.validationEngine, executionEngine,
                        ps.errorHandler, ps.observabilityManager);

                // 注册默认执行钩子（超时保护 + 连续失败检测）
                ps.functionCallOrchestrator.addExecutionHook(
                        com.wmsay.gpt4_lll.fc.planning.hooks.TimeoutGuardHook.ofMinutes(35));
                ps.functionCallOrchestrator.addExecutionHook(
                        com.wmsay.gpt4_lll.fc.planning.hooks.ConsecutiveFailureHook.withDefaults());

                // 应用配置中的执行策略
                String configStrategy = ps.functionCallConfig.getExecutionStrategy();
                if (configStrategy != null && !configStrategy.isEmpty()) {
                    ps.functionCallOrchestrator.setExecutionStrategyName(configStrategy);
                }

                // 5. 初始化 AgentRuntimeBridge (may read project files)
                ps.agentRuntimeBridge = new AgentRuntimeBridge(project);
                if (ps.agentRuntimeBridge.initialize()) {
                    ps.agentRuntimeBridge.setOrchestrator(ps.functionCallOrchestrator);
                    System.out.println("[Agent] AgentRuntime initialized successfully");
                } else {
                    System.err.println("[Agent] AgentRuntime initialization failed, will use fallback path");
                }

                // 5.1 双向关联 Bridge ↔ ChatView，用于计划进度 UI 集成
                ps.agentRuntimeBridge.setChatView(ps.readOnlyTextArea);
                ps.readOnlyTextArea.setBridge(ps.agentRuntimeBridge);

                // 6. UI updates must happen on EDT
                SwingUtilities.invokeLater(() -> {
                    if (project.isDisposed()) return;

                    // 同步 UI 下拉框 (strategy combo box)
                    if (configStrategy != null && !configStrategy.isEmpty() && ps.strategyComboBox != null) {
                        ExecutionStrategy strategy = ExecutionStrategyFactory.get(configStrategy);
                        if (strategy != null) {
                            ps.strategyComboBox.setSelectedItem(strategy.getDisplayName());
                        }
                    }

                    // Register AgentPhaseListeners for status visualization
                    if (ps.statusIndicatorPanel != null) {
                        RuntimeStatusManager.addPhaseListener(project, ps.statusIndicatorPanel);
                    }
                    if (ps.statusBorderPainterRef != null) {
                        RuntimeStatusManager.addPhaseListener(project, ps.statusBorderPainterRef);
                    }
                    if (ps.stopButtonPhaseListener != null) {
                        RuntimeStatusManager.addPhaseListener(project, ps.stopButtonPhaseListener);
                    }
                });

                System.out.println("[FC] Function calling framework initialized. Enabled="
                        + ps.functionCallConfig.isEnableFunctionCalling()
                        + ", Strategy=" + ps.functionCallOrchestrator.getExecutionStrategyName());
            } catch (Exception e) {
                System.err.println("[FC] Failed to initialize function calling framework: " + e.getMessage());
                e.printStackTrace();
                // 初始化失败不影响现有功能
                ps.functionCallOrchestrator = null;
                ps.functionCallConfig = null;
            }
        });
    }

    /**
     * 检查 function calling 是否已启用且可用。
     */
    private boolean isFunctionCallingEnabled(Project project) {
        ProjectState ps = getState(project);
        return ps.functionCallOrchestrator != null
                && ps.functionCallConfig != null
                && ps.functionCallConfig.isEnableFunctionCalling();
    }

    /**
     * 通过 AgentRuntimeBridge 执行聊天，利用完整的 Agent 流程
     * （意图识别 → 工具过滤 → 上下文组装 → 策略选择 → 执行）。
     * 复用与 executeFunctionCallingChat 相同的 LlmCaller、StreamingLlmCaller、
     * ProgressCallback、结果处理和历史保存逻辑。
     * 异常时回退到 GenerateAction.chat() 并在 UI 显示警告。
     */
    private void executeViaAgentRuntime(String userMessage, ChatContent chatContent, Project project, AgentChatView area) {
        ProjectState ps = getState(project);
        try {
            System.out.println("[AgentRT-Chat] project.basePath=" + project.getBasePath());
            // 创建 LlmCaller 回调（非流式回退用），桥接到现有 LlmClient
            LlmCaller llmCaller = (req) -> {
                MyPluginSettings settings = MyPluginSettings.getInstance();
                String url = ChatUtils.getUrl(settings, project);
                String apiKey = ChatUtils.getApiKey(settings, project);
                String proxy = settings.getProxyAddress();
                String currentProvider = ModelUtils.getSelectedProvider(project);

                // 确保 sidecar 调用（SkillMatcher / IntentRecognizer 等）使用正确的 model name
                // 这些组件构建的 ChatContent 可能没有设置 model 或使用了默认值 "gpt-3.5-turbo"，
                // 对于 Azure OpenAI 等需要 deployment name 的供应商会导致 "Model Not Found" 错误
                ChatContent reqChatContent = req.getChatContent();
                String modelInRequest = reqChatContent.getModel();
                if (modelInRequest == null || modelInRequest.isBlank() || "gpt-3.5-turbo".equals(modelInRequest)) {
                    String correctModel = ChatUtils.getModelName(project);
                    if (correctModel != null && !correctModel.isBlank()) {
                        reqChatContent.setModel(correctModel);
                    }
                }

                LlmRequest llmRequest = LlmRequest.builder()
                        .url(url)
                        .requestBody(reqChatContent.toRequestJson())
                        .apiKey(apiKey)
                        .proxy(proxy)
                        .provider(currentProvider)
                        .build();

                return LlmClient.syncChatRaw(llmRequest);
            };

            // 创建流式 LlmCaller：reasoning/content 实时展示，tool_calls 流式收集
            StreamingLlmCaller streamingLlmCaller = (req, displayCb) -> {
                MyPluginSettings settings = MyPluginSettings.getInstance();
                String url = ChatUtils.getUrl(settings, project);
                String apiKey = ChatUtils.getApiKey(settings, project);
                String proxy = settings.getProxyAddress();
                String currentProvider = ModelUtils.getSelectedProvider(project);

                // 同 llmCaller：确保 model name 正确（Azure deployment name 兼容）
                ChatContent reqChatContent = req.getChatContent();
                String modelInRequest = reqChatContent.getModel();
                if (modelInRequest == null || modelInRequest.isBlank() || "gpt-3.5-turbo".equals(modelInRequest)) {
                    String correctModel = ChatUtils.getModelName(project);
                    if (correctModel != null && !correctModel.isBlank()) {
                        reqChatContent.setModel(correctModel);
                    }
                }

                LlmRequest llmRequest = LlmRequest.builder()
                        .url(url)
                        .requestBody(reqChatContent.toRequestJson())
                        .apiKey(apiKey)
                        .proxy(proxy)
                        .provider(currentProvider)
                        .build();

                StreamingFcCollector collector = new StreamingFcCollector(displayCb);
                LlmClient.streamChat(llmRequest, collector);
                return collector.reconstructResponse();
            };

            // 注入 streamingLlmCaller 到 agentRuntimeBridge
            ps.agentRuntimeBridge.setStreamingLlmCaller(streamingLlmCaller);

            // 按时间顺序收集内容块，用于历史加载时还原正确的交错顺序
            final List<Message.ContentBlockRecord> orderedBlocks =
                    Collections.synchronizedList(new ArrayList<>());
            // 兼容旧字段（仍填充以保证旧逻辑可用）
            final StringBuilder collectedThinking = new StringBuilder();
            final StringBuilder collectedAllText = new StringBuilder();
            final List<Message.ToolCallSummary> collectedToolSummaries = new ArrayList<>();

            // 创建进度回调，实时更新 UI（使用结构化块）— 使用共享实现，避免 invokeLater 积压
            ProgressCallback progressCallback = createSharedProgressCallback(
                    area, orderedBlocks, collectedThinking, collectedAllText, collectedToolSummaries);

            // 通过 AgentRuntimeBridge 发送消息（透传原始 chatContent）
            FunctionCallResult result = ps.agentRuntimeBridge.sendMessage(
                    userMessage, chatContent, llmCaller, progressCallback);

            // 展示文件变更摘要块
            FileChangeTracker tracker = ps.agentRuntimeBridge.getFileChangeTracker();
            List<FileSnapshot> fileSnapshots = (tracker != null) ? tracker.getChanges() : List.of();
            if (!fileSnapshots.isEmpty()) {
                SwingUtilities.invokeLater(() -> area.addFileChangesBlock(fileSnapshots, project));
                // 转换为持久化记录并添加到 orderedBlocks
                List<Message.ContentBlockRecord.FileSnapshotRecord> snapshotRecords = new ArrayList<>();
                for (FileSnapshot fs : fileSnapshots) {
                    String changeType;
                    boolean origEmpty = fs.getOriginalContent() == null || fs.getOriginalContent().isEmpty();
                    boolean newEmpty = fs.getNewContent() == null || fs.getNewContent().isEmpty();
                    if (origEmpty && !newEmpty) {
                        changeType = "added";
                    } else if (!origEmpty && newEmpty) {
                        changeType = "deleted";
                    } else {
                        changeType = "modified";
                    }
                    snapshotRecords.add(new Message.ContentBlockRecord.FileSnapshotRecord(fs.getFilePath(), changeType));
                }
                orderedBlocks.add(Message.ContentBlockRecord.fileChanges(snapshotRecords));
            }

            // 处理非成功结果
            if (!result.isSuccess() && result.getType() != FunctionCallResult.ResultType.SUCCESS) {
                handleFunctionCallResult(result, project, area);
            }

            // 保存到对话历史（含所有轮次的文本、思考内容和工具调用摘要）
            String allText = collectedAllText.length() > 0
                    ? collectedAllText.toString()
                    : (result.getContent() != null ? result.getContent() : "");
            Message assistantMessage = new Message();
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(allText);
            if (collectedThinking.length() > 0) {
                assistantMessage.setThinkingContent(collectedThinking.toString());
            }
            if (!collectedToolSummaries.isEmpty()) {
                assistantMessage.setToolCallSummaries(collectedToolSummaries);
            }
            if (!orderedBlocks.isEmpty()) {
                assistantMessage.setContentBlocks(new ArrayList<>(orderedBlocks));
            }
            ChatUtils.getProjectChatHistory(project).add(assistantMessage);
            JsonStorage.saveConservation(
                    project.getUserData(GPT_4_LLL_NOW_TOPIC),
                    ChatUtils.getProjectChatHistory(project));

        } catch (Exception e) {
            // Check if this is an interrupt-caused exception (user clicked Stop)
            if (Thread.currentThread().isInterrupted() || isInterruptCaused(e)) {
                SwingUtilities.invokeLater(() -> area.appendContent("\n⏹ 已停止生成\n"));
                return;
            }
            // 检查异常链中是否包含 LlmApiException
            LlmApiException llmApiEx = findLlmApiException(e);
            if (llmApiEx != null) {
                LlmErrorInfo info = llmApiEx.getErrorInfo();
                String errorDisplay = "⚠️ " + info.getMessage() + "\n💡 " + info.getSuggestion();
                SwingUtilities.invokeLater(() -> area.appendContent("\n" + errorDisplay + "\n"));
            } else {
                // 通用错误回退：显示警告并使用传统对话
                SwingUtilities.invokeLater(() -> {
                    area.appendContent("\n⚠️ AgentRuntime error: " + e.getMessage()
                            + "\nFalling back to traditional chat...\n");
                });
                // 回退到传统对话
                GenerateAction.chat(chatContent, project, false, true, "");
            }
        }
    }

    /**
     * 遍历异常链查找 LlmApiException。
     * @return 找到的 LlmApiException，未找到则返回 null
     */
    private static LlmApiException findLlmApiException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof LlmApiException) {
                return (LlmApiException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 检查异常链中是否包含 InterruptedException 或中断相关消息。
     * 用于检测由用户点击 Stop 按钮引起的异常。
     */
    private static boolean isInterruptCaused(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase().contains("interrupted")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 使用 FunctionCallOrchestrator 执行带工具调用的对话。
     * 在后台线程中运行，通过 EDT 更新 UI。
     */
    private void executeFunctionCallingChat(ChatContent chatContent, Project project, AgentChatView area) {
        ProjectState ps = getState(project);
        try {
            System.out.println("[FC-Chat] project.basePath=" + project.getBasePath());
            // 构建 FunctionCallRequest
            FunctionCallRequest request = FunctionCallRequest.builder()
                    .chatContent(chatContent)
                    .availableTools(McpToolRegistry.getAllTools())
                    .maxRounds(ps.functionCallConfig.getMaxRounds())
                    .config(ps.functionCallConfig)
                    .build();

            // 构建 McpContext
            McpContext context = McpContext.fromIdeState(project, null);

            // 创建 LlmCaller 回调（非流式回退用），桥接到现有 LlmClient
            LlmCaller llmCaller = (req) -> {
                MyPluginSettings settings = MyPluginSettings.getInstance();
                String url = ChatUtils.getUrl(settings, project);
                String apiKey = ChatUtils.getApiKey(settings, project);
                String proxy = settings.getProxyAddress();
                String currentProvider = ModelUtils.getSelectedProvider(project);

                // 确保 model name 正确（Azure deployment name 兼容）
                ChatContent reqChatContent = req.getChatContent();
                String modelInRequest = reqChatContent.getModel();
                if (modelInRequest == null || modelInRequest.isBlank() || "gpt-3.5-turbo".equals(modelInRequest)) {
                    String correctModel = ChatUtils.getModelName(project);
                    if (correctModel != null && !correctModel.isBlank()) {
                        reqChatContent.setModel(correctModel);
                    }
                }

                LlmRequest llmRequest = LlmRequest.builder()
                        .url(url)
                        .requestBody(reqChatContent.toRequestJson())
                        .apiKey(apiKey)
                        .proxy(proxy)
                        .provider(currentProvider)
                        .build();

                return LlmClient.syncChatRaw(llmRequest);
            };

            // 创建流式 LlmCaller：reasoning/content 实时展示，tool_calls 流式收集
            StreamingLlmCaller streamingLlmCaller = (req, displayCb) -> {
                MyPluginSettings settings = MyPluginSettings.getInstance();
                String url = ChatUtils.getUrl(settings, project);
                String apiKey = ChatUtils.getApiKey(settings, project);
                String proxy = settings.getProxyAddress();
                String currentProvider = ModelUtils.getSelectedProvider(project);

                // 同 llmCaller：确保 model name 正确（Azure deployment name 兼容）
                ChatContent reqChatContent = req.getChatContent();
                String modelInRequest = reqChatContent.getModel();
                if (modelInRequest == null || modelInRequest.isBlank() || "gpt-3.5-turbo".equals(modelInRequest)) {
                    String correctModel = ChatUtils.getModelName(project);
                    if (correctModel != null && !correctModel.isBlank()) {
                        reqChatContent.setModel(correctModel);
                    }
                }

                LlmRequest llmRequest = LlmRequest.builder()
                        .url(url)
                        .requestBody(reqChatContent.toRequestJson())
                        .apiKey(apiKey)
                        .proxy(proxy)
                        .provider(currentProvider)
                        .build();

                StreamingFcCollector collector = new StreamingFcCollector(displayCb);
                LlmClient.streamChat(llmRequest, collector);
                return collector.reconstructResponse();
            };

            ps.functionCallOrchestrator.setStreamingLlmCaller(streamingLlmCaller);

            // 按时间顺序收集内容块，用于历史加载时还原正确的交错顺序
            final List<Message.ContentBlockRecord> orderedBlocks =
                    Collections.synchronizedList(new ArrayList<>());
            // 兼容旧字段（仍填充以保证旧逻辑可用）
            final StringBuilder collectedThinking = new StringBuilder();
            final StringBuilder collectedAllText = new StringBuilder();
            final List<Message.ToolCallSummary> collectedToolSummaries = new ArrayList<>();

            // 创建进度回调，实时更新 UI（使用结构化块）— 使用共享实现，避免 invokeLater 积压
            ProgressCallback progressCallback = createSharedProgressCallback(
                    area, orderedBlocks, collectedThinking, collectedAllText, collectedToolSummaries);

            // 执行编排器（带实时进度回调，文本内容通过 onTextContent 实时展示）
            FunctionCallResult result = ps.functionCallOrchestrator.execute(
                    request, context, llmCaller, progressCallback);

            // 处理非成功结果
            if (!result.isSuccess() && result.getType() != FunctionCallResult.ResultType.SUCCESS) {
                handleFunctionCallResult(result, project, area);
            }

            // 保存到对话历史（含所有轮次的文本、思考内容和工具调用摘要）
            String allText = collectedAllText.length() > 0
                    ? collectedAllText.toString()
                    : (result.getContent() != null ? result.getContent() : "");
            Message assistantMessage = new Message();
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(allText);
            if (collectedThinking.length() > 0) {
                assistantMessage.setThinkingContent(collectedThinking.toString());
            }
            if (!collectedToolSummaries.isEmpty()) {
                assistantMessage.setToolCallSummaries(collectedToolSummaries);
            }
            if (!orderedBlocks.isEmpty()) {
                assistantMessage.setContentBlocks(new ArrayList<>(orderedBlocks));
            }
            ChatUtils.getProjectChatHistory(project).add(assistantMessage);
            JsonStorage.saveConservation(
                    project.getUserData(GPT_4_LLL_NOW_TOPIC),
                    ChatUtils.getProjectChatHistory(project));

        } catch (Exception e) {
            // 错误回退：显示错误并使用传统对话
            SwingUtilities.invokeLater(() -> {
                area.appendContent("\n⚠️ Function calling error: " + e.getMessage()
                        + "\nFalling back to traditional chat...\n");
            });
            // 回退到传统对话
            GenerateAction.chat(chatContent, project, false, true, "");
        }
    }

    /**
     * 创建共享的 ProgressCallback，用于实时更新 UI（使用结构化块）。
     * <p>
     * 所有 area.appendContent / appendThingkingTitle / appendThingkingEnd 调用
     * 不再用 SwingUtilities.invokeLater 包裹——这些方法内部已有 EDT 安全检查
     * 和 offscreen 缓冲机制。直接调用可以让 offscreen 缓冲在窗口不可见时生效，
     * 避免大量 invokeLater 任务积压导致切回时假死。
     * <p>
     * 仅 addToolUseBlock / addToolResultBlock / markCompleted 等必须在 EDT 上
     * 原子执行的操作仍使用 invokeLater。
     */
    private ProgressCallback createSharedProgressCallback(
            AgentChatView area,
            List<Message.ContentBlockRecord> orderedBlocks,
            StringBuilder collectedThinking,
            StringBuilder collectedAllText,
            List<Message.ToolCallSummary> collectedToolSummaries) {

        return new ProgressCallback() {
            private final java.util.concurrent.atomic.AtomicReference<com.wmsay.gpt4_lll.component.block.ToolUseBlock>
                    currentToolBlock = new java.util.concurrent.atomic.AtomicReference<>();
            private final java.util.concurrent.atomic.AtomicReference<StepBlock>
                    currentStepBlock = new java.util.concurrent.atomic.AtomicReference<>();
            private final java.util.concurrent.atomic.AtomicReference<String> currentToolName =
                    new java.util.concurrent.atomic.AtomicReference<>();
            private final java.util.concurrent.atomic.AtomicReference<Map<String, Object>> currentToolParams =
                    new java.util.concurrent.atomic.AtomicReference<>();
            private final java.util.concurrent.atomic.AtomicBoolean hasReasoning =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            private final java.util.concurrent.atomic.AtomicBoolean streamingMode =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

            @Override
            public void onLlmCallStarting(int round) {
                hasReasoning.set(false);
                streamingMode.set(false);
                collectedAllText.setLength(0);
            }

            @Override
            public void onReasoningContent(int round, String reasoningContent) {
                hasReasoning.set(true);
                collectedThinking.setLength(0);
                collectedThinking.append(reasoningContent);
                orderedBlocks.add(Message.ContentBlockRecord.thinking(reasoningContent));
                area.appendThingkingTitle();
                area.appendContent(reasoningContent);
                area.appendThingkingEnd();
            }

            @Override
            public void onTextContent(int round, String content) {
                if (collectedAllText.length() > 0) {
                    collectedAllText.append("\n\n");
                }
                collectedAllText.append(content);
                orderedBlocks.add(Message.ContentBlockRecord.text(content));
                area.appendContent(content);
            }

            @Override
            public void onReasoningStarted(int round) {
                streamingMode.set(true);
                hasReasoning.set(true);
                collectedThinking.setLength(0);
                area.appendThingkingTitle();
            }

            @Override
            public void onReasoningDelta(int round, String delta) {
                collectedThinking.append(delta);
                area.appendContent(delta);
            }

            @Override
            public void onReasoningComplete(int round) {
                orderedBlocks.add(Message.ContentBlockRecord.thinking(collectedThinking.toString()));
                area.appendThingkingEnd();
            }

            @Override
            public void onTextDelta(int round, String delta) {
                streamingMode.set(true);
                collectedAllText.append(delta);
                area.appendContent(delta);
            }

            @Override
            public void onLlmCallCompleted(int round, int toolCallCount) {
                if (streamingMode.get() && collectedAllText.length() > 0) {
                    orderedBlocks.add(Message.ContentBlockRecord.text(collectedAllText.toString()));
                }
            }

            @Override
            public void onToolExecutionStarting(String toolName, java.util.Map<String, Object> params) {
                currentToolName.set(toolName);
                currentToolParams.set(params);
                SwingUtilities.invokeLater(() -> {
                    var block = area.addToolUseBlock(toolName, params);
                    currentToolBlock.set(block);
                });
            }

            @Override
            public void onToolExecutionCompleted(ToolCallResult result) {
                String toolName = result.getToolName();
                String resultText;
                if (result.isSuccess()) {
                    resultText = result.getResult() != null
                            ? result.getResult().getDisplayText() : "(no output)";
                    if (resultText == null || resultText.isEmpty()) {
                        resultText = "(no output)";
                    }
                } else {
                    resultText = result.getError() != null
                            ? result.getError().getMessage()
                            + (result.getError().getSuggestion() != null
                                ? "\n" + result.getError().getSuggestion() : "")
                            : "Unknown error";
                }
                Map<String, Object> toolParams = currentToolParams.get();
                collectedToolSummaries.add(new Message.ToolCallSummary(
                        toolName, toolParams,
                        result.isSuccess(), result.getDurationMs(), resultText));
                orderedBlocks.add(Message.ContentBlockRecord.toolUse(
                        toolName, toolParams, result.isSuccess(), result.getDurationMs()));
                orderedBlocks.add(Message.ContentBlockRecord.toolResult(toolName, resultText));

                final String displayResultText = resultText;
                SwingUtilities.invokeLater(() -> {
                    var block = currentToolBlock.getAndSet(null);
                    if (block != null) {
                        block.markCompleted(result.isSuccess(), result.getDurationMs());
                    }
                    if (result.isSuccess()) {
                        area.addToolResultBlock(toolName, displayResultText);
                    } else {
                        area.addToolResultBlock(toolName + " (ERROR)", displayResultText);
                    }
                });
            }

            @Override
            public void onStrategyPhase(String phase, String description) {
                area.appendContent("\n" + description + "\n");
            }

            @Override
            public void onPlanningContentDelta(String delta) {
                // 规划阶段的 content 是 JSON 格式的计划数据，不直接展示给用户
                // 解析完成后会通过 onPlanGenerated 以格式化的方式展示
            }

            @Override
            public void onPlanningReasoningDelta(String delta) {
                // 规划阶段流式思考过程：实时展示在对话框中，不加入主 Agent 上下文
                area.appendContent(delta);
            }

            @Override
            public void onPlanGenerated(List<PlanStep> steps) {
                StringBuilder planText = new StringBuilder("\n**执行计划** (" + steps.size() + " 步):\n");
                for (PlanStep step : steps) {
                    planText.append("  ").append(step.getIndex() + 1)
                            .append(". ").append(step.getDescription()).append("\n");
                }
                // 仅记录到 orderedBlocks 用于历史持久化；
                // UI 面板由 AgentRuntimeBridge.wrapCallback 中的 onPlanGenerated 创建，
                // 此处不再重复调用 addPlanProgressBlock，避免出现两个计划面板。
                orderedBlocks.add(Message.ContentBlockRecord.plan(planText.toString()));
            }

            @Override
            public void onPlanStepStarting(int stepIndex, String stepDescription) {
                SwingUtilities.invokeLater(() -> {
                    StepBlock block = area.addStepBlock(stepIndex, stepDescription);
                    currentStepBlock.set(block);
                    block.markInProgress();
                });
            }

            @Override
            public void onPlanStepCompleted(int stepIndex, boolean success, String resultSummary) {
                orderedBlocks.add(Message.ContentBlockRecord.planStep(
                        stepIndex, success, resultSummary));
                SwingUtilities.invokeLater(() -> {
                    StepBlock block = currentStepBlock.getAndSet(null);
                    if (block != null) {
                        if (success) block.markCompleted(resultSummary);
                        else block.markFailed(resultSummary);
                    }
                    area.clearActiveStepBlock();
                });
            }

            @Override
            public void onPlanRevised(List<PlanStep> revisedSteps) {
                long remaining = revisedSteps.stream()
                        .filter(s -> s.getStatus() == PlanStep.Status.PENDING)
                        .count();
                area.appendContent("\n计划已修订，剩余 " + remaining + " 步\n");
            }
        };
    }

    /**
     * 处理非成功的 FunctionCallResult，在 UI 中显示适当的提示。
     */
    private void handleFunctionCallResult(FunctionCallResult result, Project project, AgentChatView area) {
        switch (result.getType()) {
            case ERROR:
                SwingUtilities.invokeLater(() -> {
                    String content = result.getContent();
                    String errorType = detectLlmErrorType(content);
                    if (errorType != null) {
                        // LLM API 结构化错误：使用 ⚠️ 前缀 + 修复建议
                        String suggestion = LlmErrorClassifier.getSuggestion(errorType);
                        area.appendContent("\n⚠️ " + content + "\n💡 " + suggestion);
                    } else {
                        // 非 LLM API 错误：保持原有格式
                        area.appendContent("\n⚠️ Error: " + content);
                    }
                });
                break;
            case MAX_ROUNDS_EXCEEDED:
                SwingUtilities.invokeLater(() ->
                        area.appendContent("\n⚠️ Maximum conversation rounds exceeded. "
                                + "The AI made too many tool calls without reaching a final answer."));
                break;
            case DEGRADED:
                SwingUtilities.invokeLater(() ->
                        area.appendContent("\nℹ️ " + result.getContent()));
                break;
            default:
                break;
        }
    }

    /**
     * 检测 FunctionCallResult 的 content 是否包含 LlmErrorClassifier 生成的中英双语错误消息，
     * 返回对应的错误类型标识；不匹配时返回 null。
     */
    private static String detectLlmErrorType(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        // LlmErrorClassifier.getUserMessage() 生成的消息以中英双语前缀开头
        if (content.startsWith("认证错误 / Authentication Error")) {
            return LlmErrorClassifier.TYPE_AUTHENTICATION;
        } else if (content.startsWith("请求频率超限 / Rate Limit Exceeded")) {
            return LlmErrorClassifier.TYPE_RATE_LIMIT;
        } else if (content.startsWith("账户余额不足 / Insufficient Balance")) {
            return LlmErrorClassifier.TYPE_INSUFFICIENT_BALANCE;
        } else if (content.startsWith("模型不存在 / Model Not Found")) {
            return LlmErrorClassifier.TYPE_MODEL_NOT_FOUND;
        } else if (content.startsWith("服务端错误 / Server Error")) {
            return LlmErrorClassifier.TYPE_SERVER_ERROR;
        } else if (content.startsWith("未收到有效响应 / No Valid Response")) {
            return LlmErrorClassifier.TYPE_NO_VALID_RESPONSE;
        } else if (content.startsWith("未知错误 / Unknown Error")) {
            return LlmErrorClassifier.TYPE_UNKNOWN;
        }
        return null;
    }
}
