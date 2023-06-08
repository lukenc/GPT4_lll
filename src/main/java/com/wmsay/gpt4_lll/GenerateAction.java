package com.wmsay.gpt4_lll;

import com.alibaba.fastjson.JSON;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SseResponse;
import org.apache.commons.lang3.StringUtils;


import javax.swing.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class GenerateAction extends AnAction {
    public static List<Message> chatHistory = new ArrayList<>();

    @Override
    public void actionPerformed(AnActionEvent e) {
        chatHistory.clear();
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
        String model="gpt-3.5-turbo";
        model = getModelName(toolWindow);
        String replyLanguage= getSystemLanguage();
        MyPluginSettings settings = MyPluginSettings.getInstance();
        Project project = e.getProject();
        if (project != null) {
            Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
            String fileType= getOpenFileType(project);
            SelectionModel selectionModel = editor.getSelectionModel();
            String selectedText = selectionModel.getSelectedText();
            Message systemMessage=new Message();
            systemMessage.setRole("system");
            systemMessage.setName("owner");
            systemMessage.setContent("你是一个有用的助手，同时也是一个计算机科学家，数据专家，有着多年的代码重构经验和多年的代码优化的架构师");

            Message message=new Message();
            Boolean coding=false;
            if (selectedText!=null) {
                selectedText = selectedText.trim();
                if (Boolean.TRUE.equals(isSelectedTextAllComments(project))){
                    coding=true;
                    message.setRole("user");
                    message.setName("owner");
                    message.setContent("请帮我完成下面的功能，同时使用"+fileType+"，注释语言请使用"+replyLanguage+",只要代码部分，代码部分要包含代码和注释,其他任何内容我都不要看到，功能如下：" + selectedText);
                }else {
                    coding=false;
                    message.setRole("user");
                    message.setName("owner");
                    message.setContent("请帮我重构下面的代码，不局限于代码性能优化，命名优化，增加注释，简化代码，优化逻辑，同时可以代码如下：" + selectedText);
                }
                ChatContent chatContent = new ChatContent();
                chatContent.setMessages(List.of(message, systemMessage));
                chatContent.setModel(model);
                chatHistory.addAll(List.of(message, systemMessage));
                Boolean finalCoding = coding;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        chat(chatContent, project, editor,finalCoding);
                    }
                }).start();
                // WindowTool.updateShowText(res);
            }
        }
        // TODO: insert action logic here
    }


    public static String chat(ChatContent content,Project project,Editor editorPre,Boolean coding){
        MyPluginSettings settings = MyPluginSettings.getInstance();
        String apiKey = settings.getApiKey();
        String proxy = settings.getProxyAddress();
        if (StringUtils.isEmpty(apiKey)){
            Messages.showMessageDialog(project, "先去申请一个apikey。参考：https://blog.wmsay.com/article/60/", "ChatGpt", Messages.getInformationIcon());
            return "";
        }

        String requestBody= JSON.toJSONString(content);
        HttpClient.Builder clientBuilder=HttpClient.newBuilder();
        if (StringUtils.isNotEmpty(proxy)) {
            String[] addressAndPort = proxy.split(":");
            if (addressAndPort.length == 2 && isValidPort(addressAndPort[1])) {
                int port = Integer.parseInt(addressAndPort[1]);
                clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(addressAndPort[0], port)));
            } else {
                Messages.showMessageDialog(project, "格式错误，格式为ip:port", "科学冲浪失败", Messages.getInformationIcon());
            }
        }
        String url = settings.getGptUrl();
        HttpClient client = clientBuilder
                .build()
                ;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Authorization","Bearer "+apiKey)
                .header("Content-Type","application/json")
                .header("Accept","text/event-stream")
                .build();
        AtomicInteger lastInsertPosition = new AtomicInteger(-1);
        StringBuffer stringBuffer=new StringBuffer();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    response.body().forEach(line -> {
                        if (line.startsWith("data")) {

                            line = line.substring(5);
                            SseResponse sseResponse = null;
                            try {
                                sseResponse = JSON.parseObject(line, SseResponse.class);
                            } catch (Exception e) {
                                //// TODO: 2023/6/9
                            }
                            if (sseResponse != null){
                                String resContent = sseResponse.getChoices().get(0).getDelta().getContent();
                            if (resContent != null) {
                                WindowTool.appendContent(resContent);
                                if (Boolean.TRUE.equals(coding)) {

                                    ApplicationManager.getApplication().invokeLater(() -> {
                                    ApplicationManager.getApplication().runWriteAction(() -> {
                                        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                                        if (editor != null) {
                                            Document document = editor.getDocument();
                                            SelectionModel selectionModel = editor.getSelectionModel();

                                            int insertPosition;
                                            if (lastInsertPosition.get() == -1) { // This means it's the first time to insert
                                                if (selectionModel.hasSelection()) {
                                                    // If there's a selection, find the end line of the selection
                                                    int selectionEnd = selectionModel.getSelectionEnd();
                                                    int endLine = document.getLineNumber(selectionEnd);
                                                    // Insert at the end of the line where the selection ends
                                                    insertPosition = document.getLineEndOffset(endLine);
                                                } else {
                                                    // If there's no selection, insert at the end of the document
                                                    insertPosition = document.getTextLength();
                                                }
                                            } else { // This is not the first time, so we insert at the last insert position
                                                insertPosition = lastInsertPosition.get();
                                            }

                                            if (stringBuffer.indexOf("```") > 0 && stringBuffer.indexOf("```") == stringBuffer.lastIndexOf("```")) {
                                                // Insert a newline and the data
                                                String textToInsert = resContent;
                                                WriteCommandAction.runWriteCommandAction(project, () -> document.insertString(insertPosition, textToInsert.replace("`", "")));

                                                // Update the last insert position to the end of the inserted text
                                                lastInsertPosition.set(insertPosition + textToInsert.length());
                                            }
                                        }
                                    });
                                });
                                }
                                stringBuffer.append(resContent);
                            }
                        }
                        }
                    });
                }).join();


        return stringBuffer.toString();
    }


    public static boolean isValidPort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            // 端口范围必须在0-65535之间
            return port >= 0 && port <= 65535;
        } catch (NumberFormatException e) {
            // 如果无法解析为整数，则返回false
            return false;
        }
    }


    private JRadioButton findRadioButton(JComponent component, String radioButtonContent) {
        if (component instanceof JRadioButton ) {
            if (radioButtonContent.equals(((JRadioButton) component).getText())) {
                return (JRadioButton) component;
            }
        }

        for (int i = 0; i < component.getComponentCount(); i++) {
            JComponent child = (JComponent) component.getComponent(i);
            JRadioButton radioButton = findRadioButton(child, radioButtonContent);
            if (radioButton != null) {
                return radioButton;
            }
        }

        return null;
    }


    private String getModelName(ToolWindow toolWindow) {
        if (toolWindow != null && toolWindow.isVisible()) {
            JPanel contentPanel = (JPanel) toolWindow.getContentManager().getContent(0).getComponent();

            JRadioButton gpt4Option = findRadioButton(contentPanel, "gpt-4");
            JRadioButton gpt35TurboOption = findRadioButton(contentPanel, "gpt-3.5-turbo");
            JRadioButton codeOption = findRadioButton(contentPanel, "code-davinci-002");

            if (gpt4Option != null) {
                boolean selected = gpt4Option.isSelected();
                if (selected) {
                    return "gpt-4";
                }
            }
            if (gpt35TurboOption != null) {
                boolean selected = gpt35TurboOption.isSelected();
                if (selected) {
                    return "gpt-3.5-turbo";
                }
            }
            if (codeOption != null) {
                boolean selected = codeOption.isSelected();
                if (selected) {
                    return "code-davinci-002";
                }
            }
        }
        return "gpt-3.5-turbo";
    }

    public String getOpenFileType(Project project) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        VirtualFile file = fileEditorManager.getSelectedFiles()[0];  // 获取当前正在编辑的文件

        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                return psiFile.getFileType().getName();
            }
        }
        return "java";
    }

    public int countSelectedLines(Project project) {
        // 获取当前活动的编辑器
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        if (editor != null) {
            SelectionModel selectionModel = editor.getSelectionModel();
            if (selectionModel.hasSelection()) {
                // 获取选中文本的起始和结束位置
                int startLine = editor.getDocument().getLineNumber(selectionModel.getSelectionStart());
                int endLine = editor.getDocument().getLineNumber(selectionModel.getSelectionEnd());

                // 计算选中的行数
                int lineCount = endLine - startLine + 1;
                return lineCount;
            }
        }
        return 0;
    }



    /**
     *
     * 检查是否都是注释
    * @author liuchuan
    * @date 2023/6/8 2:06 PM
     * @param project
     * @return java.lang.Boolean
    */
    public boolean isSelectedTextAllComments(Project project) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        if (editor != null) {
            SelectionModel selectionModel = editor.getSelectionModel();

            if (selectionModel.hasSelection()) {
                int startOffset = selectionModel.getSelectionStart();
                int endOffset = selectionModel.getSelectionEnd();

                PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

                if (psiFile != null) {
                    PsiElement startElement = psiFile.findElementAt(startOffset);
                    PsiElement endElement = psiFile.findElementAt(endOffset - 1); // -1 because endOffset points to the next character after the selection

                    if (startElement != null && endElement != null) {
                        PsiElement commonParent = PsiTreeUtil.findCommonParent(startElement, endElement);

                        if (commonParent != null) {
                            PsiElement[] elements = PsiTreeUtil.collectElements(commonParent, element -> element instanceof PsiComment);
                            String selectedText = selectionModel.getSelectedText();

                            if (selectedText != null) {
                                // Removing leading and trailing white spaces and line breaks
                                selectedText = selectedText.trim();
                                StringBuilder commentTextBuilder = new StringBuilder();
                                for (PsiElement element : elements) {
                                    if (element instanceof PsiComment && element.getTextRange().getStartOffset() >= startOffset &&
                                            element.getTextRange().getEndOffset() <= endOffset) {
                                        commentTextBuilder.append(element.getText().trim());
                                    }
                                }

                                String commentText = commentTextBuilder.toString();
                                return commentText.equals(selectedText);
                            }
                        }
                    }
                }
            }
        }

        // If there's no selection or if any other error occurs, we assume that the selection is not all comments
        return false;
    }

    public String  getSystemLanguage() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        return language;
    }
}

