package com.wmsay.gpt4_lll;

import com.intellij.openapi.project.Project;
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
import com.wmsay.gpt4_lll.component.Gpt4lllPlaceholderTextArea;
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.component.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SelectModelOption;
import com.wmsay.gpt4_lll.utils.ModelUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

import static com.wmsay.gpt4_lll.utils.ChatUtils.getModelName;

public class WindowTool implements ToolWindowFactory {
    private Gpt4lllTextArea readOnlyTextArea;

    public static volatile Boolean isGenerating=false;

    private Map<String, List<SelectModelOption>> providerModels = ModelUtils.provider2ModelList;
    private static ComboBox<String> providerComboBox;
    private static ComboBox<SelectModelOption> modelComboBox;

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        // 创建工具窗口内容
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        readOnlyTextArea= new Gpt4lllTextArea();
        readOnlyTextArea.setContentType("text/html");
        project.putUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA,readOnlyTextArea);

        JPanel modelSelectionPanel = createModelSelectionPanel();
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
        Insets insets = JBUI.insets(0, 5, 15, 5); // 设置上下左右各10像素的边距
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0.9;
        c.weighty = 0.75;  // 75% of the vertical space
        c.insets = insets;
        panel.add(scrollPane,c);
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
                Gpt4lllTextArea area= project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
                String input = textField.getText();
                if(StringUtil.isEmpty(input)){
                    Messages.showMessageDialog(project, "Please enter what you want to say in the chat box first, and then click Send\n请先在聊天框中输入您想说的话，然后再点击发送哦~", "Error", Messages.getErrorIcon());
                    return;
                }
                area.appendContent("\n\n- - - - - - - - - - - \n");
                area.appendContent("YOU:"+input);
                area.appendContent("\n- - - - - - - - - - - \n");
                if (GenerateAction.nowTopic.isEmpty()){
                    GenerateAction.nowTopic=GenerateAction.formatter.format(LocalDateTime.now())+"--Chat:"+input;
                }

                Message message=new Message();
                message.setRole("user");
                message.setName("owner");
                message.setContent(input);
                GenerateAction.chatHistory.add(message);

                ChatContent chatContent= new ChatContent();
                chatContent.setMessages(GenerateAction.chatHistory);
                String model=getModelName();
                chatContent.setModel(model);
                    new Thread(() -> {
                       String res= GenerateAction.chat(chatContent,project,false,true,"");
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
                if (GenerateAction.chatHistory!=null&&!GenerateAction.chatHistory.isEmpty()&&!GenerateAction.nowTopic.isEmpty()){
                    JsonStorage.saveConservation(GenerateAction.nowTopic,GenerateAction.chatHistory);
                    GenerateAction.chatHistory.clear();
                }
                GenerateAction.nowTopic="";
                readOnlyTextArea.clearShowWindow();

            }
        });
        newAndHistoryPanel.add(newSessionButton);



        JButton historyButton = new JButton("历史记录/chat history");
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
        panel.add(newAndHistoryPanel,c);




        // 在此处添加你的插件界面的组件和布局
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }







    public void showPopup(Project project) {
        Map<String ,List<Message>> historyData= new LinkedHashMap<>();
        try {
            historyData= JsonStorage.loadData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 构建列表数据
        List<Pair<String,String>> data = new ArrayList<Pair<String,String>>();

        for (String topic:
        historyData.keySet()) {
            data.add(Pair.create(take(topic,100),topic));
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
                List<Message> messageList= finalHistoryData.get(selectedItem);
                messageList.forEach(message -> {
                    project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA).appendMessage(message);
                });
                GenerateAction.nowTopic=selectedItem;
                GenerateAction.chatHistory=messageList;
            }
        });

        // 创建滚动面板并添加列表
        JBScrollPane scrollPane = new JBScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(500, 150));

        // 创建弹窗并显示
        JBPopupFactory.getInstance()
                .createComponentPopupBuilder(scrollPane, null)
                .setTitle("Gpt History")
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



    private JPanel createModelSelectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(5);

        // Create and populate the provider combo box
        providerComboBox = new ComboBox<>(providerModels.keySet().toArray(new String[0]));
        setUpProviderComboBox(providerComboBox);

        // Create the model combo box
        modelComboBox = new ComboBox<>();
        setUpComboBox(modelComboBox);

        // Add action listener to provider combo box
        providerComboBox.addActionListener(e -> updateModelComboBox());

        // Initialize model combo box
        updateModelComboBox();


        // Add components to panel
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
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Model: "), gbc);

        gbc.gridx = 4;
        gbc.weightx = 0.6;
        panel.add(modelComboBox, gbc);

        return panel;
    }

    private void updateModelComboBox() {
        String selectedProvider = (String) providerComboBox.getSelectedItem();
        modelComboBox.removeAllItems();
        for (SelectModelOption model : providerModels.get(selectedProvider)) {
            modelComboBox.addItem(model);
        }
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
    }
}
