package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.languages.FileAnalysisManager;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;


public class CommentAction extends AnAction {

    public static String PROMPT =
            """
                    请严格按照以下要求为代码添加注释：
                                        
                        1. 输入信息：
                        代码语言：${fileType}
                        注释语言：${replyLanguage}
                        代码内容：
                        ```
                        ${selectedText}
                        ```  
                        2. 注释要求：
                        - 使用${fileType}的标准注释语法
                        - 注释内容使用${replyLanguage}编写
                        - 保持原有代码的逻辑和命名完全不变
                        - 只注释关键步骤和重要逻辑，不需要每行都加注释
                        - 注释应简洁明确，说明代码的目的而不是重复代码在做什么
                                        
                        3. 注释内容规范：
                        - 方法注释：说明方法的功能、参数含义、返回值和可能的异常,使用块注释
                        - 类注释：说明类的主要功能和职责，使用块注释
                        - 方法内的代码注释：仅使用行上注释
                        - 复杂逻辑注释：解释复杂算法或业务逻辑的整体思路
                        - 关键变量注释：说明重要变量的用途和取值范围
                        - 边界条件注释：说明循环、判断等关键条件的目的
                        - 特殊处理注释：说明异常处理、特殊情况的处理逻辑
                                        
                        4. 注释风格要求：
                        - 使用规范的专业术语
                        - 语言表述清晰、简洁
                        - 避免使用过于口语化的表达
                        - 注释中的标点符号使用${replyLanguage}语言规范
                        - 相似代码段使用统一的注释风格
                        - 避免无意义或显而易见的注释
                                        
                        5. 输出格式：
                        - 只输出一个包含注释后代码的Markdown代码块
                        - 使用```${fileType}作为代码块标记
                                        
                        6. 禁止事项：
                        - 不要修改任何代码逻辑
                        - 不要改变变量或函数命名
                        - 不要添加新的代码
                        - 不要写重复或冗余的注释
                        - 不要包含版权、作者等无关信息
                    
                    """;


    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project==null){
            Messages.showMessageDialog(e.getProject(), "不是一个项目/no project here", "Error", Messages.getErrorIcon());
            return;
        }
        if(CommonUtil.isRunningStatus(project)){
            Messages.showMessageDialog(project, "Please wait, another task is running", "Error", Messages.getErrorIcon());
            return;
        }else {
            CommonUtil.startRunningStatus(project);
        }

        if (ChatUtils.getProjectChatHistory(project) != null && !ChatUtils.getProjectChatHistory(project).isEmpty() &&ChatUtils.getProjectTopic(project)!=null && !ChatUtils.getProjectTopic(project).isEmpty()) {
            JsonStorage.saveConservation(ChatUtils.getProjectTopic(project), ChatUtils.getProjectChatHistory(project));
            ChatUtils.getProjectChatHistory(project).clear();
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

        String model = ChatUtils.getModelName(e.getProject());
        String replyLanguage = CommonUtil.getSystemLanguage();
        if (project != null) {
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor==null){
                Messages.showMessageDialog(project, "Editor is not open. Please open the file that you want to do something", "Error", Messages.getErrorIcon());
                CommonUtil.stopRunningStatus(project);
                return;
            }
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
            if (selectedText == null || selectedText.isEmpty()) {
                Messages.showMessageDialog(project, "No text selected. Please select the code you want to do something", "Error", Messages.getErrorIcon());
                CommonUtil.stopRunningStatus(project);
                return;
            }
            Message systemMessage = new Message();
            if (ProviderNameEnum.BAIDU.getProviderName().equals(ModelUtils.getSelectedProvider(project))){
                systemMessage.setRole("user");
            }else {
                systemMessage.setRole("system");
            }
            //systemMessage.setName("owner");
            systemMessage.setContent("你是一个资深的软件开发工程师，会写出详细的文档和代码注释，会使用md语法回复我");

            Message message = new Message();
            List<Message> moreMessageList=new ArrayList<>();
            if (selectedText != null) {
                ChatUtils.getProjectChatHistory(project).clear();
                selectedText = selectedText.trim();
                ChatUtils.setProjectTopic(project,CommonUtil.generateTopicByMethodAndTime(selectedText, "Comment"));

                message.setRole("user");
                message.setName("owner");
                String prompt=PROMPT
                        .replace("${replyLanguage}", replyLanguage)
                        .replace("${fileType}", fileType)
                        .replace("${selectedText}", selectedText);
                message.setContent(prompt);
                if ("java".equalsIgnoreCase(fileType)) {
                    FileAnalysisManager analysisManager= ApplicationManager.getApplication().getService(FileAnalysisManager.class);
                    List<Message> messageList = analysisManager.analyzeFile(project, editor);
                    if (!messageList.isEmpty()) {
                        moreMessageList.addAll(messageList);
                    }
                    List<Message> moreMessageList2=analysisManager.analyzeCurrentFile(project, editor);
                    if (!moreMessageList2.isEmpty()) {
                        moreMessageList.addAll(moreMessageList2);
                    }
                }
                ChatContent chatContent = new ChatContent();
                List<Message> sendMessageList= new ArrayList<>();
                if (!moreMessageList.isEmpty()){
                    sendMessageList.add(systemMessage);
                    sendMessageList.addAll(moreMessageList);
                    sendMessageList.add(message);
                    chatContent.setMessages(sendMessageList,ModelUtils.getSelectedProvider(project));
                }else {
                    sendMessageList = new ArrayList<>(List.of(systemMessage,message));
                }
                chatContent.setMessages(sendMessageList,ModelUtils.getSelectedProvider(project));
                chatContent.setModel(model);
                chatContent.setTemperature(0.2);
                ChatUtils.getProjectChatHistory(project).addAll(chatContent.getMessages());

                //清理界面
                Gpt4lllTextArea textArea= project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
                if (textArea != null) {
                    textArea.clearShowWindow();
                }
                Thread dochatThread = new Thread(() -> {
                    try {
                        GenerateAction.chat(chatContent, project, true, true, "");
                        SwingUtilities.invokeLater(() -> deleteSelection(editor, selectStartPosition, selectEndPosition));
                    } finally {
                        CommonUtil.stopRunningStatus(project);
                    }
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