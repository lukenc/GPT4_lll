package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
    private JRadioButton gpt4Option ;
    private JRadioButton gpt35TurboOption ;
    private JRadioButton codeOption;
    private JRadioButton gpt40TurboOption ;
    private JRadioButton baiduOption ;

    private JRadioButton freeOption;

    public static volatile Boolean isGenerating=false;


    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        // 创建工具窗口内容
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        readOnlyTextArea= new Gpt4lllTextArea();
        readOnlyTextArea.setContentType("text/html");
        project.putUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA,readOnlyTextArea);

        //语言模型选择
        JPanel radioButtonPanel = new JPanel(new GridBagLayout());
        freeOption = new JRadioButton("Free-免费");
        freeOption.setToolTipText("use author's api usage，need to wait in line./使用作者自己的api，但需要排队等待");
        gpt4Option = new JRadioButton("gpt-4");
        gpt35TurboOption = new JRadioButton("gpt-3.5-turbo");
        codeOption = new JRadioButton("code-davinci-002");
        codeOption.setToolTipText("这是一个专门为代码训练的Gpt3.5模型，token是普通的3.5turbo的2倍，笔者正在努力开发中");
        codeOption.setEnabled(true);
        gpt40TurboOption=new JRadioButton("gpt-4-turbo");
        gpt40TurboOption.setToolTipText("GPT-4是最新的模型，具备改进的指令跟随、JSON模式、可复现的输出、并行函数调用等功能。该模型最多返回4,096个输出令牌。这个预览模型目前还不适合用于生产环境的流量。");
        baiduOption=new JRadioButton("文心一言-baidu");
        baiduOption.setToolTipText("这是一个很多样性的平台，有各种模型（例如Gemma-7B-it、Llama-2-70b-chat），通过不同Api Url来区分，具体参考：https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");


        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(freeOption);
        buttonGroup.add(gpt4Option);
        buttonGroup.add(gpt35TurboOption);
        buttonGroup.add(codeOption);
        buttonGroup.add(gpt40TurboOption);
        buttonGroup.add(baiduOption);
        //第一行
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0.4;
        c.gridwidth = 2;
        c.weighty = 0.05;  // 10% of the vertical space
        radioButtonPanel.add(freeOption,c);
        c.gridx=2;
        c.gridwidth = 2;
        c.weightx = 0.4;
        radioButtonPanel.add(baiduOption,c);

        //第二行
        c.gridwidth = 1;
        c.weightx = 0.20;
        c.weighty = 0.05;
        c.gridy = 1;
        c.gridx = 0;
        radioButtonPanel.add(gpt4Option, c);
        c.gridx = 1;
        radioButtonPanel.add(gpt35TurboOption, c);
        c.gridx = 2;
        radioButtonPanel.add(codeOption, c);
        c.gridx=3;
        radioButtonPanel.add(gpt40TurboOption,c);

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;  // span across all columns
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(radioButtonPanel, c);

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
                String model=getModelName(toolWindow);
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
//        c.weighty = 0.05;


        SwingUtilities.invokeLater(() -> {
            freeOption.setSelected(true);
        });



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
}
