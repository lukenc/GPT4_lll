package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.component.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.wmsay.gpt4_lll.GenerateAction.chatHistory;
import static com.wmsay.gpt4_lll.GenerateAction.nowTopic;

public class CommentAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        if (chatHistory != null && !chatHistory.isEmpty() && !nowTopic.isEmpty()) {
            JsonStorage.saveConservation(nowTopic, chatHistory);
            chatHistory.clear();
        }
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(e.getProject());
        ToolWindow toolWindow = toolWindowManager.getToolWindow("GPT4_lll");
        if (toolWindow != null && toolWindow.isVisible()) {
            // 工具窗口已打开
            // 在这里编写处理逻辑
        } else {
            // 工具窗口未打开
            if (toolWindow != null) {
                toolWindow.show(); // 打开工具窗口
            }
        }

        String model = ChatUtils.getModelName(toolWindow);
        String replyLanguage = CommonUtil.getSystemLanguage();
        Project project = e.getProject();
        if (project != null) {
            Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
            String fileType = CommonUtil.getOpenFileType(project);
            SelectionModel selectionModel = editor.getSelectionModel();
            int selectStartPosition;
            int selectEndPosition;
            if (selectionModel.hasSelection()) {
                selectStartPosition = selectionModel.getSelectionStart();
                selectEndPosition = selectionModel.getSelectionEnd();
            } else {
                selectEndPosition = 0;
                selectStartPosition = 0;
            }
            String selectedText = selectionModel.getSelectedText();
            Message systemMessage = new Message();
            if (model.contains("baidu")){
                systemMessage.setRole("user");
            }else {
                systemMessage.setRole("system");
            }
            systemMessage.setName("owner");
            systemMessage.setContent("你是一个资深的软件开发工程师，会写出详细的文档和代码注释，会使用md语法回复我");

            Message message = new Message();
            List<Message> moreMessageList=new ArrayList<>();
            if (selectedText != null) {
                chatHistory.clear();
                selectedText = selectedText.trim();
                nowTopic = CommonUtil.generateTopicByMethodAndTime(selectedText, "Score");

                message.setRole("user");
                message.setName("owner");
                message.setContent("请帮忙使用" + replyLanguage + "语言，在不改变任何逻辑与命名的情况下，写出下面的" + fileType + "代码的注释，注释需要清晰规范，注释不需要每一行都写注释，但是关键步骤必须写注释,所有的返回代码应该在一个Markdown语法的代码块中，且回复中只能存在一个代码块，且使用行上注释的方式，注释范围仅为如下代码:" + selectedText);
                if ("java".equalsIgnoreCase(fileType)) {
                    List<Message> messageList = GenerateAction.getClassInfoToMessageType(project, editor);
                    if (!messageList.isEmpty()) {
                        moreMessageList.addAll(messageList);
                    }
                }
                ChatContent chatContent = new ChatContent();
                List<Message> sendMessageList= new ArrayList<>(List.of(message, systemMessage));
                if (!moreMessageList.isEmpty()){
                    sendMessageList.addAll(1,moreMessageList);
                    chatContent.setMessages(sendMessageList);
                }
                chatContent.setMessages(sendMessageList);
                chatContent.setModel(model);
                chatContent.setTemperature(0.2);
                chatHistory.addAll(List.of(message, systemMessage));

                //清理界面
                Gpt4lllTextArea textArea= project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
                if (textArea != null) {
                    textArea.clearShowWindow();
                }
                Thread dochatThread = new Thread(() -> {
                    GenerateAction.chat(chatContent, project, true, true, "");
                    SwingUtilities.invokeLater(() -> deleteSelection(editor, selectStartPosition, selectEndPosition));
                });
                dochatThread.start();
            }
        }
        // TODO: insert action logic here
    }


    private void deleteSelection(Editor editor,int startOffset,int endOffset) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                CommandProcessor.getInstance().executeCommand(editor.getProject(), new Runnable() {
                    @Override
                    public void run() {
                        ApplicationManager.getApplication().runWriteAction(new Runnable() {
                            @Override
                            public void run() {
                                //editor.getDocument().deleteString(startOffset, endOffset);
                                // 此处可以使用 param 参数进行其他操作
                                int startLine = editor.getDocument().getLineNumber(startOffset);
                                int endLine = editor.getDocument().getLineNumber(endOffset);
                                for (int i = startLine; i <= endLine; i++) {
                                    int lineStartOffset = editor.getDocument().getLineStartOffset(i);
                                    editor.getDocument().insertString(lineStartOffset, "//");
                                }
                            }
                        });
                    }
                }, "Delete Selection", null);
            }
        });
    }

}