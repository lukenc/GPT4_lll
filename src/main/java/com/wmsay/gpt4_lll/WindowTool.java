package com.wmsay.gpt4_lll;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
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
import com.wmsay.gpt4_lll.component.StatusBorderPainter;
import com.wmsay.gpt4_lll.model.key.Gpt4lllHistoryButtonKey;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SelectModelOption;
import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.config.ConfigLoader;
import com.wmsay.gpt4_lll.fc.config.ExtensionLoader;
import com.wmsay.gpt4_lll.fc.error.ErrorHandler;
import com.wmsay.gpt4_lll.fc.execution.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.execution.RetryStrategy;
import com.wmsay.gpt4_lll.fc.execution.UserApprovalManager;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapter;
import com.wmsay.gpt4_lll.fc.protocol.ProtocolAdapterRegistry;
import com.wmsay.gpt4_lll.fc.validation.ValidationEngine;
import com.wmsay.gpt4_lll.llm.LlmClient;
import com.wmsay.gpt4_lll.llm.LlmRequest;
import com.wmsay.gpt4_lll.llm.StreamingFcCollector;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import com.wmsay.gpt4_lll.model.RuntimeStatus;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import com.wmsay.gpt4_lll.utils.RuntimeStatusManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC;
import static com.wmsay.gpt4_lll.utils.ChatUtils.getModelName;

public class WindowTool implements ToolWindowFactory {
    private static final ConcurrentHashMap<String, WindowTool> projectInstances = new ConcurrentHashMap<>();

    private AgentChatView readOnlyTextArea;

    private final Map<String, List<SelectModelOption>> providerModels;
    private ComboBox<String> providerComboBox;
    private ComboBox<SelectModelOption> modelComboBox;
    private JButton historyButton;

    // ---- Stop button & request thread ----
    private JButton stopButton;
    private volatile Thread currentRequestThread;

    // ---- Function Calling 框架组件 ----
    private FunctionCallOrchestrator functionCallOrchestrator;
    private FunctionCallConfig functionCallConfig;
    private ValidationEngine validationEngine;
    private ErrorHandler errorHandler;
    private ObservabilityManager observabilityManager;

    public WindowTool() {
        providerModels = new HashMap<>(ModelUtils.provider2ModelList);
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        projectInstances.put(project.getName(), this);
        // 创建 ComboBox 实例
        providerComboBox = new ComboBox<>();
        modelComboBox = new ComboBox<>();
        project.putUserData(Gpt4lllComboxKey.GPT_4_LLL_MODEL_COMBO_BOX, modelComboBox);
        project.putUserData(Gpt4lllComboxKey.GPT_4_LLL_PROVIDER_COMBO_BOX, providerComboBox);

        historyButton = new JButton("历史记录/chat history");
        project.putUserData(Gpt4lllHistoryButtonKey.GPT_4_LLL_HISTORY_BUTTON,historyButton);
        // 设置渲染器
        setUpProviderComboBox(providerComboBox);
        setUpComboBox(modelComboBox);

        // 创建工具窗口内容
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        readOnlyTextArea = new AgentChatView();
        project.putUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA, readOnlyTextArea);

        JPanel modelSelectionPanel = createModelSelectionPanel(project);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 0.1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(modelSelectionPanel, c);

        readOnlyTextArea.clearShowWindow();

        JScrollPane scrollPane = new JBScrollPane(readOnlyTextArea);
        readOnlyTextArea.setScrollPane(scrollPane);
        StatusBorderPainter statusBorderPainter = new StatusBorderPainter(scrollPane);
        RuntimeStatusManager.addListener(project, statusBorderPainter);
        scrollPane.setBorder(statusBorderPainter);
        Insets insets = JBUI.insets(0, 5, 15, 5); // 设置上下左右各10像素的边距
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0.9;
        c.weighty = 0.75;  // 75% of the vertical space
        c.insets = insets;
        panel.add(scrollPane, c);
        //对话框

        Gpt4lllPlaceholderTextArea textField = new Gpt4lllPlaceholderTextArea("请输入内容/input here");
        JScrollPane scrollInputPane = new JBScrollPane(textField);
        scrollInputPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollInputPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 1;
        c.weighty = 0.05;
        panel.add(scrollInputPane, c);

        JButton button = new JButton("发送聊天/Send message");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
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
                chatContent.setMessages(ChatUtils.getProjectChatHistory(project), ModelUtils.getSelectedProvider(project));
                String model = getModelName(project);
                chatContent.setModel(model);

                // 检查是否启用 function calling
                if (isFunctionCallingEnabled()) {
                    new Thread(() -> {
                        currentRequestThread = Thread.currentThread();
                        try {
                            executeFunctionCallingChat(chatContent, project, area);
                        } finally {
                            currentRequestThread = null;
                        }
                    }).start();
                } else {
                    new Thread(() -> {
                        currentRequestThread = Thread.currentThread();
                        try {
                            String res = GenerateAction.chat(chatContent, project, false, true, "");
                        } finally {
                            currentRequestThread = null;
                        }
                    }).start();
                }

                textField.setText("");
            }
        });

        // Stop button — initially hidden, visible only when RUNNING
        stopButton = new JButton("停止/Stop");
        stopButton.setVisible(false);
        stopButton.addActionListener(e -> cancelCurrentRequest(project));

        // Wrap send + stop buttons in a horizontal panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        buttonPanel.add(button);
        buttonPanel.add(stopButton);

        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 1;
        panel.add(buttonPanel, c);

        // Register StatusListener to toggle stop button visibility
        RuntimeStatusManager.addListener(project, (oldStatus, newStatus) -> {
            SwingUtilities.invokeLater(() -> {
                stopButton.setVisible(newStatus == RuntimeStatus.RUNNING);
            });
        });

        JPanel newAndHistoryPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5)); // 左侧对齐，水平和垂直间隔为5像素

        // 创建“新建会话”按钮并添加图标（如果需要）
        JButton newSessionButton = new JButton("新建会话/New Session");
        newSessionButton.addActionListener(e -> {
            // todo 新建会话的逻辑
            if (ChatUtils.getProjectChatHistory(project) != null && !ChatUtils.getProjectChatHistory(project).isEmpty() && !ChatUtils.getProjectTopic(project).isEmpty()) {
                JsonStorage.saveConservation(ChatUtils.getProjectTopic(project), ChatUtils.getProjectChatHistory(project));
                ChatUtils.getProjectChatHistory(project).clear();
            }
            ChatUtils.setProjectTopic(project,"");
            if (project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA)!=null) {
                project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA).clearShowWindow();
            }

        });
        newAndHistoryPanel.add(newSessionButton);


        project.getUserData(Gpt4lllHistoryButtonKey.GPT_4_LLL_HISTORY_BUTTON);

        historyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showPopup(project);
            }
        });
        newAndHistoryPanel.add(historyButton, c);

        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 5;
        c.weightx = 1;
        c.insets = new Insets(5, 0, 5, 0); // 设置外部间距（上、左、下、右）
        panel.add(newAndHistoryPanel, c);


        initializeComboBoxes(project);

        // 初始化 Function Calling 框架
        initializeFunctionCalling(project);

        // 在此处添加你的插件界面的组件和布局
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);


        // 添加项目关闭监听器
        project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                statusBorderPainter.dispose();
                projectInstances.remove(project.getName());
            }
        });
    }


    public void showPopup(Project project) {
        Map<String, List<Message>> historyData = new LinkedHashMap<>();
        try {
            historyData = JsonStorage.loadData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 构建列表数据
        List<Pair<String, String>> data = new ArrayList<Pair<String, String>>();

        for (String topic :
                historyData.keySet()) {
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
        Map<String, List<Message>> finalHistoryData = historyData;
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA).clearShowWindow();
                String selectedItem = list.getSelectedValue().second;
                // 实现你想要的点击事件逻辑
                List<Message> messageList = finalHistoryData.get(selectedItem);
                messageList.forEach(message -> {
                    project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA).appendMessage(message);
                });
                ChatUtils.setProjectTopic(project,selectedItem);
                project.putUserData(Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY,messageList);
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
    }


    public static String take(String str, int length) {
        if (str.length() <= length) {
            return str;
        } else {
            return str.substring(0, length);
        }
    }


    private JPanel createModelSelectionPanel(Project project) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(5);

        // 添加提供商下拉框的监听器
        providerComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    updateModelComboBox(project);
                    saveSettings(project);
                });
            }
        });
        modelComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                saveSettings(project);
            }
        });

        // 添加组件到面板
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Provider: "), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.4;
        panel.add(providerComboBox, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(Box.createHorizontalStrut(10), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0;
        panel.add(new JLabel("Model: "), gbc);

        gbc.gridx = 4;
        gbc.weightx = 0.6;
        panel.add(modelComboBox, gbc);

        return panel;
    }

    private void updateModelComboBox(Project project) {
        String selectedProvider = (String) project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_PROVIDER_COMBO_BOX).getSelectedItem();

        // 在EDT中执行UI更新
        ApplicationManager.getApplication().invokeLater(() -> {
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
        return projectInstances.get(project.getName());
    }

    /**
     * 获取 FunctionCallOrchestrator 实例（供 GenerateAction 等外部调用方使用）。
     *
     * @return 编排器实例，如果 FC 未初始化则返回 null
     */
    public FunctionCallOrchestrator getFunctionCallOrchestrator() {
        return functionCallOrchestrator;
    }

    /**
     * 获取 FunctionCallConfig 实例（供 GenerateAction 等外部调用方使用）。
     *
     * @return 配置实例，如果 FC 未初始化则返回 null
     */
    public FunctionCallConfig getFunctionCallConfig() {
        return functionCallConfig;
    }


    private void initializeComboBoxes(Project project) {
        // 初始化提供商下拉框
        providerComboBox.removeAllItems();
        for (String provider : providerModels.keySet()) {
            providerComboBox.addItem(provider);
        }

        // 确保选择了一个提供商
        if (providerComboBox.getItemCount() > 0) {
            providerComboBox.setSelectedIndex(0);
            // 初始化模型下拉框
            updateModelComboBox(project);
        }

        ProjectSettings settings = ProjectSettings.getInstance(project);
        String lastProvider = settings.getLastProvider();
        String lastModel = settings.getLastModelDisplayName();
        System.out.println("Loading settings: provider=" + lastProvider + ", model=" + lastModel);
        if (!lastProvider.isEmpty()) {
            providerComboBox.setSelectedItem(lastProvider);
            updateModelComboBox(project);
            if (!lastModel.isEmpty()) {
                for (int i = 0; i < modelComboBox.getItemCount(); i++) {
                    SelectModelOption option = modelComboBox.getItemAt(i);
                    if (option.getDisplayName().equals(lastModel)) {
                        modelComboBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
    }

    private void saveSettings(Project project) {
        ProjectSettings settings = ProjectSettings.getInstance(project);
        String provider = (String) providerComboBox.getSelectedItem();
        SelectModelOption selectedModel = (SelectModelOption) modelComboBox.getSelectedItem();
        String modelDisplay = selectedModel != null ? selectedModel.getDisplayName() : "";
        System.out.println("Saving settings: provider=" + provider + ", model=" + modelDisplay);
        settings.setLastProvider(provider);
        settings.setLastModelDisplayName(modelDisplay);
    }

    // ==================== Cancellation ====================

    /**
     * Interrupts the current request thread (if alive) and transitions status to ERROR.
     */
    private void cancelCurrentRequest(Project project) {
        Thread thread = currentRequestThread;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        CommonUtil.stopRunningStatus(project, false);
    }

    // ==================== Function Calling 集成 ====================

    /**
     * 初始化 Function Calling 框架组件。
     * 加载配置、创建各组件实例、加载 SPI 扩展、组装编排器。
     * 初始化失败不影响现有功能，仅记录日志。
     */
    private void initializeFunctionCalling(Project project) {
        try {
            // 1. 加载配置
            ConfigLoader configLoader = new ConfigLoader();
            Path configPath = null;
            if (project.getBasePath() != null) {
                configPath = Paths.get(project.getBasePath(), ".lll", "function-calling-config.json");
            }
            functionCallConfig = configLoader.load(configPath);

            // 2. 创建核心组件
            validationEngine = new ValidationEngine();
            errorHandler = new ErrorHandler();
            observabilityManager = new ObservabilityManager();
            observabilityManager.setLogLevel(functionCallConfig.getLogLevel());

            RetryStrategy retryStrategy = new RetryStrategy();
            UserApprovalManager approvalManager = new UserApprovalManager();
            ExecutionEngine executionEngine = new ExecutionEngine(retryStrategy, approvalManager);

            // 3. 加载 SPI 扩展
            ExtensionLoader.loadAll(validationEngine, errorHandler);

            // 4. 根据当前供应商选择协议适配器并创建编排器
            String currentProvider = ModelUtils.getSelectedProvider(project);
            ProtocolAdapter protocolAdapter = ProtocolAdapterRegistry.getAdapter(
                    currentProvider != null ? currentProvider : "");

            functionCallOrchestrator = new FunctionCallOrchestrator(
                    protocolAdapter, validationEngine, executionEngine,
                    errorHandler, observabilityManager);

            System.out.println("[FC] Function calling framework initialized. Enabled="
                    + functionCallConfig.isEnableFunctionCalling());
        } catch (Exception e) {
            System.err.println("[FC] Failed to initialize function calling framework: " + e.getMessage());
            e.printStackTrace();
            // 初始化失败不影响现有功能
            functionCallOrchestrator = null;
            functionCallConfig = null;
        }
    }

    /**
     * 检查 function calling 是否已启用且可用。
     */
    private boolean isFunctionCallingEnabled() {
        return functionCallOrchestrator != null
                && functionCallConfig != null
                && functionCallConfig.isEnableFunctionCalling();
    }

    /**
     * 使用 FunctionCallOrchestrator 执行带工具调用的对话。
     * 在后台线程中运行，通过 EDT 更新 UI。
     */
    private void executeFunctionCallingChat(ChatContent chatContent, Project project, AgentChatView area) {
        try {
            // 构建 FunctionCallRequest
            FunctionCallRequest request = FunctionCallRequest.builder()
                    .chatContent(chatContent)
                    .availableTools(McpToolRegistry.getAllTools())
                    .maxRounds(functionCallConfig.getMaxRounds())
                    .config(functionCallConfig)
                    .build();

            // 构建 McpContext
            McpContext context = McpContext.fromIdeState(project, null);

            // 创建 LlmCaller 回调（非流式回退用），桥接到现有 LlmClient
            FunctionCallOrchestrator.LlmCaller llmCaller = (req) -> {
                MyPluginSettings settings = MyPluginSettings.getInstance();
                String url = ChatUtils.getUrl(settings, project);
                String apiKey = ChatUtils.getApiKey(settings, project);
                String proxy = settings.getProxyAddress();
                String currentProvider = ModelUtils.getSelectedProvider(project);

                LlmRequest llmRequest = LlmRequest.builder()
                        .url(url)
                        .chatContent(req.getChatContent())
                        .apiKey(apiKey)
                        .proxy(proxy)
                        .provider(currentProvider)
                        .build();

                return LlmClient.syncChatRaw(llmRequest);
            };

            // 创建流式 LlmCaller：reasoning/content 实时展示，tool_calls 流式收集
            FunctionCallOrchestrator.StreamingLlmCaller streamingLlmCaller = (req, displayCb) -> {
                MyPluginSettings settings = MyPluginSettings.getInstance();
                String url = ChatUtils.getUrl(settings, project);
                String apiKey = ChatUtils.getApiKey(settings, project);
                String proxy = settings.getProxyAddress();
                String currentProvider = ModelUtils.getSelectedProvider(project);

                LlmRequest llmRequest = LlmRequest.builder()
                        .url(url)
                        .chatContent(req.getChatContent())
                        .apiKey(apiKey)
                        .proxy(proxy)
                        .provider(currentProvider)
                        .build();

                StreamingFcCollector collector = new StreamingFcCollector(displayCb);
                LlmClient.streamChat(llmRequest, collector);
                return collector.reconstructResponse();
            };

            functionCallOrchestrator.setStreamingLlmCaller(streamingLlmCaller);

            // 按时间顺序收集内容块，用于历史加载时还原正确的交错顺序
            final List<Message.ContentBlockRecord> orderedBlocks =
                    Collections.synchronizedList(new ArrayList<>());
            // 兼容旧字段（仍填充以保证旧逻辑可用）
            final StringBuilder collectedThinking = new StringBuilder();
            final StringBuilder collectedAllText = new StringBuilder();
            final List<Message.ToolCallSummary> collectedToolSummaries = new ArrayList<>();

            // 创建进度回调，实时更新 UI（使用结构化块）
            FunctionCallOrchestrator.ProgressCallback progressCallback =
                    new FunctionCallOrchestrator.ProgressCallback() {
                private final java.util.concurrent.atomic.AtomicReference<com.wmsay.gpt4_lll.component.block.ToolUseBlock>
                        currentToolBlock = new java.util.concurrent.atomic.AtomicReference<>();
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

                // ---- 非流式路径回调 ----
                @Override
                public void onReasoningContent(int round, String reasoningContent) {
                    hasReasoning.set(true);
                    collectedThinking.setLength(0);
                    collectedThinking.append(reasoningContent);
                    orderedBlocks.add(Message.ContentBlockRecord.thinking(reasoningContent));
                    SwingUtilities.invokeLater(() -> {
                        area.appendThingkingTitle();
                        area.appendContent(reasoningContent);
                        area.appendThingkingEnd();
                    });
                }

                @Override
                public void onTextContent(int round, String content) {
                    if (collectedAllText.length() > 0) {
                        collectedAllText.append("\n\n");
                    }
                    collectedAllText.append(content);
                    orderedBlocks.add(Message.ContentBlockRecord.text(content));
                    SwingUtilities.invokeLater(() -> area.appendContent(content));
                }

                // ---- 流式路径回调：reasoning/content 实时增量展示 ----
                @Override
                public void onReasoningStarted(int round) {
                    streamingMode.set(true);
                    hasReasoning.set(true);
                    collectedThinking.setLength(0);
                    SwingUtilities.invokeLater(() -> area.appendThingkingTitle());
                }

                @Override
                public void onReasoningDelta(int round, String delta) {
                    collectedThinking.append(delta);
                    SwingUtilities.invokeLater(() -> area.appendContent(delta));
                }

                @Override
                public void onReasoningComplete(int round) {
                    orderedBlocks.add(Message.ContentBlockRecord.thinking(collectedThinking.toString()));
                    SwingUtilities.invokeLater(() -> area.appendThingkingEnd());
                }

                @Override
                public void onTextDelta(int round, String delta) {
                    streamingMode.set(true);
                    collectedAllText.append(delta);
                    SwingUtilities.invokeLater(() -> area.appendContent(delta));
                }

                @Override
                public void onLlmCallCompleted(int round, int toolCallCount) {
                    // 流式路径：text 通过 onTextDelta 增量展示，在此处汇总到 orderedBlocks
                    // 非流式路径：text 已在 onTextContent 中加入 orderedBlocks，此处跳过
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
            };

            // 执行编排器（带实时进度回调，文本内容通过 onTextContent 实时展示）
            FunctionCallResult result = functionCallOrchestrator.execute(
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
     * 在 UI 中显示工具调用历史（工具名称、状态、结果摘要）。
     */
    // displayToolCallHistory removed — replaced by real-time ProgressCallback

    /**
     * 处理非成功的 FunctionCallResult，在 UI 中显示适当的提示。
     */
    private void handleFunctionCallResult(FunctionCallResult result, Project project, AgentChatView area) {
        switch (result.getType()) {
            case ERROR:
                SwingUtilities.invokeLater(() ->
                        area.appendContent("\n⚠️ Error: " + result.getContent()));
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
}
