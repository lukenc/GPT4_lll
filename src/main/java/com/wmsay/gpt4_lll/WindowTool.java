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
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.model.key.Gpt4lllHistoryButtonKey;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SelectModelOption;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC;
import static com.wmsay.gpt4_lll.utils.ChatUtils.getModelName;

public class WindowTool implements ToolWindowFactory {
    private static final ConcurrentHashMap<String, WindowTool> projectInstances = new ConcurrentHashMap<>();

    private Gpt4lllTextArea readOnlyTextArea;

    private final Map<String, List<SelectModelOption>> providerModels;
    private ComboBox<String> providerComboBox;
    private ComboBox<SelectModelOption> modelComboBox;
    private JButton historyButton;

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
        readOnlyTextArea = new Gpt4lllTextArea();
        readOnlyTextArea.setContentType("text/html");
        project.putUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA, readOnlyTextArea);

        JPanel modelSelectionPanel = createModelSelectionPanel(project);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 0.1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(modelSelectionPanel, c);

        // 创建只读文本框
        readOnlyTextArea.setEditable(false);
        readOnlyTextArea.clearShowWindow();

        JScrollPane scrollPane = new JBScrollPane(readOnlyTextArea);
        readOnlyTextArea.setScrollPane(scrollPane);
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
                Gpt4lllTextArea area = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
                String input = textField.getText();
                if (StringUtil.isEmpty(input)) {
                    Messages.showMessageDialog(project, "Please enter what you want to say in the chat box first, and then click Send\n请先在聊天框中输入您想说的话，然后再点击发送哦~", "Error", Messages.getErrorIcon());
                    return;
                }
                area.appendContent("\n\n- - - - - - - - - - - \n");
                area.appendContent("YOU:" + input);
                area.appendContent("\n- - - - - - - - - - - \n");
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
                new Thread(() -> {
                    String res = GenerateAction.chat(chatContent, project, false, true, "");
                }).start();


                textField.setText("");
            }
        });
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 1;
//        c.weighty = 0.05;  // 10% of the vertical space
        panel.add(button, c);

        JPanel newAndHistoryPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5)); // 左侧对齐，水平和垂直间隔为5像素

        // 创建“新建会话”按钮并添加图标（如果需要）
        JButton newSessionButton = new JButton("新建会话/New Session");
        newSessionButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // todo 新建会话的逻辑
                if (ChatUtils.getProjectChatHistory(project) != null && !ChatUtils.getProjectChatHistory(project).isEmpty() && !ChatUtils.getProjectTopic(project).isEmpty()) {
                    JsonStorage.saveConservation(ChatUtils.getProjectTopic(project), ChatUtils.getProjectChatHistory(project));
                    ChatUtils.getProjectChatHistory(project).clear();
                }
                ChatUtils.setProjectTopic(project,"");
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


        // 在此处添加你的插件界面的组件和布局
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);


        // 添加项目关闭监听器
        project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
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
}
