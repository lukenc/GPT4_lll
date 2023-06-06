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
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class WindowTool implements ToolWindowFactory {
    //private static final JTextArea readOnlyTextArea = new JTextArea(30,40);
    private static final JEditorPane readOnlyTextArea = new JEditorPane();
    private JRadioButton gpt4Option ;
    private JRadioButton gpt35TurboOption ;
    private JRadioButton codeOption;

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        // 创建工具窗口内容
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        //语言模型选择
        JPanel radioButtonPanel = new JPanel(new GridBagLayout());
        gpt4Option = new JRadioButton("gpt-4");
        gpt35TurboOption = new JRadioButton("gpt-3.5-turbo");
        codeOption = new JRadioButton("code-davinci-002");
        codeOption.setToolTipText("这是一个专门为代码训练的Gpt3.5模型，token是普通的3.5turbo的2倍，笔者正在努力开发中");
        codeOption.setEnabled(false);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(gpt4Option);
        buttonGroup.add(gpt35TurboOption);
        buttonGroup.add(codeOption);
        c.gridy = 0;
        c.weightx = 0.33;
        c.weighty = 0.05;  // 10% of the vertical space
        radioButtonPanel.add(gpt4Option, c);
        c.gridx = 1;
        radioButtonPanel.add(gpt35TurboOption, c);
        c.gridx = 2;
        radioButtonPanel.add(codeOption, c);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;  // span across all columns
        c.fill = GridBagConstraints.HORIZONTAL;
       // panel.add(radioButtonPanel, c);

        // 创建只读文本框
        readOnlyTextArea.setEditable(false);
        readOnlyTextArea.setContentType("text/html");

        readOnlyTextArea.setText("");
        //readOnlyTextArea.setLineWrap(true);
        //readOnlyTextArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JBScrollPane(readOnlyTextArea);
//        panel.addComponentListener(new ComponentAdapter() {
//            public void componentResized(ComponentEvent e) {
//                int sidebarWidth = panel.getWidth();
//                readOnlyTextArea.setSize(new Dimension(scrollPane. getWidth() - sidebarWidth, 30));
//            }
//        });
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 1;
        c.weighty = 0.8;  // 80% of the vertical space
        panel.add(scrollPane,c);
        //对话框
        JTextField textField = new JTextField(30);

        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 1;
        c.weighty = 0.1;
        panel.add(textField, c);

        JButton button = new JButton("发送聊天");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                String input = textField.getText();
                appendContentToEditorPane(readOnlyTextArea,"<HR>");
                appendContentToEditorPane(readOnlyTextArea,convertMarkdownToHtml("YOU:"+input));
                appendContentToEditorPane(readOnlyTextArea,"<HR>");

                Message message=new Message();
                message.setRole("user");
                message.setName("owner");
                message.setContent(input);
                GenerateAction.chatHistory.add(message);

                ChatContent chatContent= new ChatContent();
                chatContent.setMessages(GenerateAction.chatHistory);
                chatContent.setModel("gpt-3.5-turbo");
                String replayMessage= GenerateAction.chat(chatContent,project);
                // 将新内容附加到原有的 HTML 后面
                appendContentToEditorPane(readOnlyTextArea,convertMarkdownToHtml(replayMessage));
                textField.setText("");
                // 在此处处理输入内容的逻辑
                //Arrays.stream(res).forEachOrdered(s-> {readOnlyTextArea.append(s);readOnlyTextArea.append("\n");});
            }
        });
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 1;
        c.weighty = 0.05;  // 10% of the vertical space
        panel.add(button, c);





        SwingUtilities.invokeLater(() -> {
            gpt35TurboOption.setSelected(true);
        });

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
        readOnlyTextArea.setText(convertMarkdownToHtml(replayMessage) );
        //Arrays.stream(res).forEachOrdered(s-> {readOnlyTextArea.append(s);readOnlyTextArea.append("\n");});
        //readOnlyTextArea.append(selectedText);
    }


    public static String convertMarkdownToHtml(String markdown) {
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    public void appendContentToEditorPane(JEditorPane editorPane, String content) {
        HTMLDocument document = (HTMLDocument) editorPane.getDocument();
        try {
            int length = document.getLength();
            document.insertAfterEnd(document.getCharacterElement(length), content);
        } catch (BadLocationException | IOException e) {
            e.printStackTrace();
        }
    }


}
