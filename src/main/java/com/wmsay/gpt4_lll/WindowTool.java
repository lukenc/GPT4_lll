package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

public class WindowTool implements ToolWindowFactory {
    private static final JTextArea readOnlyTextArea = new JTextArea(30,40);

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        // 创建工具窗口内容
        JPanel panel = new JPanel(new GridBagLayout());
        // 创建只读文本框
        readOnlyTextArea.setEditable(false);
        readOnlyTextArea.setText("");
        readOnlyTextArea.setLineWrap(true);
        readOnlyTextArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JBScrollPane(readOnlyTextArea);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2; // Make the text area span two columns
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(scrollPane,c);
        //对话框
        JTextField textField = new JTextField(30);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2; // Make the text area span two columns
        c.weighty=10;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(textField, c);

        JButton button = new JButton("点击");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                String input = textField.getText();
                Message message=new Message();
                message.setRole("user");
                message.setName("owner");
                message.setContent(input);
                GenerateAction.chatHistory.add(message);

                ChatContent chatContent= new ChatContent();
                chatContent.setMessages(GenerateAction.chatHistory);
                chatContent.setModel("gpt-3.5-turbo");
                String replayMessage= GenerateAction.chat(chatContent,project);
                // 在此处处理输入内容的逻辑
                String[]  res= replayMessage.split("\\n");
                Arrays.stream(res).forEachOrdered(s-> {readOnlyTextArea.append(s);readOnlyTextArea.append("\n");});
            }
        });
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2; // Make the text area span two columns
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(button, c);



        // 在此处添加你的插件界面的组件和布局
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    Editor getEditor(AnActionEvent e) {
        return e.getRequiredData(CommonDataKeys.EDITOR);
    }

    public static void updateShowText(String replayMessage) {
        readOnlyTextArea.setText("");
        String[]  res= replayMessage.split("\\n");
        Arrays.stream(res).forEachOrdered(s-> {readOnlyTextArea.append(s);readOnlyTextArea.append("\n");});
        //readOnlyTextArea.append(selectedText);
    }
}
