package com.wmsay.gpt4_lll;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.component.LinterFixDialog;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.CodeChange;
import com.wmsay.gpt4_lll.model.SelectionContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SseResponse;
import com.wmsay.gpt4_lll.model.baidu.BaiduSseResponse;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.languages.FileAnalysisManager;
import com.intellij.openapi.application.ApplicationManager;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import com.wmsay.gpt4_lll.utils.SelectionUtils;

import javax.swing.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 IDE Linter 的代码修复建议 Action
 * 获取选中代码区域的 linter 错误信息，并调用 AI 生成行级别的修复建议
 */
public class LinterFixAction extends AnAction {

    public static final String LINTER_FIX_PROMPT = """
            请根据以下代码检查器（Linter）发现的问题，提供精确的行级别修复建议。所有修改只能作用于选中区域内的代码，但你可以利用选中区域内的上下文（包括未报错的行）来保证逻辑正确和风格一致。

            ## 输入信息
            - 编程语言：${fileType}
            - 选中代码起始行号：${startLine}

            ## Linter 检测到的问题列表
            ${linterErrors}

            ## 选中代码（带行号，供你输出行号参考）
            ```
            ${numberedCode}
            ```

            ## 输出要求
            返回一个 JSON 数组，包含所有需要的变更操作。每个操作是一个对象，格式如下：

            ```json
            [
              {
                "type": "DELETE",
                "line": 行号,
                "content": "要删除的行内容",
                "reason": "删除原因"
              },
              {
                "type": "INSERT",
                "afterLine": 在哪行之后插入（0表示在第一行之前）,
                "content": "要插入的新行内容",
                "reason": "插入原因"
              },
              {
                "type": "MODIFY",
                "line": 行号,
                "oldContent": "原始行内容",
                "newContent": "修改后的行内容",
                "reason": "修改原因"
              }
            ]
            ```

            ## 规则
            1. 可修改选中区域内的任意行（不局限于报错行）。
            2. 行号必须对应上面“带行号”代码中的行号。
            3. 保持原有逻辑与风格，修复 Linter 问题，必要时调整相关上下文行。
            4. reason 字段用 ${replyLanguage} 简要说明修改原因。
            5. 确保 JSON 格式正确，仅返回 JSON 数组，不要添加其他说明文字。

            请提供修复建议（只返回 JSON 数组）：
            """;

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showMessageDialog(e.getProject(), "不是一个项目/No project here", "Error", Messages.getErrorIcon());
            return;
        }
        
        if (Boolean.TRUE.equals(CommonUtil.isRunningStatus(project))) {
            Messages.showMessageDialog(project, "请等待，另一个任务正在运行/Please wait, another task is running", "Error", Messages.getErrorIcon());
            return;
        } else {
            CommonUtil.startRunningStatus(project);
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            Messages.showMessageDialog(project, "编辑器未打开/Editor is not open", "Error", Messages.getErrorIcon());
            CommonUtil.stopRunningStatus(project);
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        SelectionContent selectionContent = SelectionUtils.getSelectionWithLineNumbers(editor);
        
        if (selectionContent == null) {
            Messages.showMessageDialog(project, "请先选中需要检查的代码/Please select the code you want to check", "Error", Messages.getErrorIcon());
            CommonUtil.stopRunningStatus(project);
            return;
        }

        int selectionStart = selectionModel.getSelectionStart();
        int selectionEnd = selectionModel.getSelectionEnd();

        String selectedText = selectionContent.getRawText();
        int startLine = selectionContent.getStartLine();

        // 获取选中区域的 Linter 错误信息
        List<LinterError> linterErrors = getLinterErrors(project, editor, selectionStart, selectionEnd);
        
        if (linterErrors.isEmpty()) {
            Messages.showMessageDialog(project, "选中的代码没有发现 Linter 错误/No linter errors found in selected code", "Info", Messages.getInformationIcon());
            CommonUtil.stopRunningStatus(project);
            return;
        }

        // 打开工具窗口
        openToolWindow(project);

        String fileType = CommonUtil.getOpenFileType(project);
        String replyLanguage = CommonUtil.getSystemLanguage();

        // 构建带行号的代码
        String numberedCode = selectionContent.toNumberedText();

        // 构建 Linter 错误描述
        StringBuilder errorsDescription = new StringBuilder();
        for (int i = 0; i < linterErrors.size(); i++) {
            LinterError error = linterErrors.get(i);
            errorsDescription.append(String.format("%d. [%s] 行 %d: %s\n", 
                i + 1, error.severity, error.line, error.description));
        }

        // 构建 prompt
        String prompt = LINTER_FIX_PROMPT
                .replace("${fileType}", fileType)
                .replace("${replyLanguage}", replyLanguage)
                .replace("${startLine}", String.valueOf(startLine))
                .replace("${linterErrors}", errorsDescription.toString())
                .replace("${numberedCode}", numberedCode);

        // 构建上下文消息（项目内类信息与选区信息），特别是 Java 时
        List<Message> contextMessages = new ArrayList<>();
        if ("java".equalsIgnoreCase(fileType)) {
            FileAnalysisManager analysisManager = ApplicationManager.getApplication().getService(FileAnalysisManager.class);
            if (analysisManager != null) {
                List<Message> ctx1 = analysisManager.analyzeFile(project, editor); // 选区相关引用类信息
                if (ctx1 != null && !ctx1.isEmpty()) {
                    contextMessages.addAll(ctx1);
                }
                List<Message> ctx2 = analysisManager.analyzeCurrentFile(project, editor); // 当前文件概要
                if (ctx2 != null && !ctx2.isEmpty()) {
                    contextMessages.addAll(ctx2);
                }
            }
        }

        // 构建消息
        Message systemMessage = new Message();
        if (ProviderNameEnum.BAIDU.getProviderName().equals(ModelUtils.getSelectedProvider(project))) {
            systemMessage.setRole("user");
        } else {
            systemMessage.setRole("system");
        }
        systemMessage.setContent("你是一个专业的代码审查专家，擅长分析和修复代码中的问题。你只返回 JSON 格式的修复建议，不添加任何其他说明。");

        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(prompt);

        ChatContent chatContent = new ChatContent();
        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        if (!contextMessages.isEmpty()) {
            messages.addAll(contextMessages);
        }
        messages.add(userMessage);
        chatContent.setMessages(messages, ModelUtils.getSelectedProvider(project));
        chatContent.setModel(ChatUtils.getModelName(project));

        // 保存会话历史
        ChatUtils.setProjectTopic(project, CommonUtil.generateTopicByMethodAndTime("LinterFix", "LinterFix"));
        ChatUtils.getProjectChatHistory(project).clear();
        ChatUtils.getProjectChatHistory(project).addAll(messages);

        // 清理显示窗口
        Gpt4lllTextArea textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
        if (textArea != null) {
            textArea.clearShowWindow();
        }

        // 启动异步请求
        final int finalStartLine = startLine;
        new Thread(() -> {
            try {
                String response = chatWithLinterFix(chatContent, project, textArea);
                
                // 解析 JSON 响应获取变更操作列表
                List<CodeChange> changes = parseChangesFromResponse(response);
                
                if (changes != null && !changes.isEmpty()) {
                    // 在 EDT 中显示确认对话框
                    SwingUtilities.invokeLater(() -> {
                        LinterFixDialog dialog = new LinterFixDialog(project, editor, 
                                selectedText, changes, finalStartLine, selectionStart, selectionEnd);
                        dialog.show();
                    });
                } else {
                    SwingUtilities.invokeLater(() -> 
                        Messages.showMessageDialog(project, 
                            "无法解析 AI 返回的修复建议/Could not parse fix suggestions from AI response", 
                            "Warning", Messages.getWarningIcon()));
                }
            } finally {
                CommonUtil.stopRunningStatus(project);
            }
        }).start();
    }

    /**
     * 从 AI 响应中解析变更操作列表
     */
    private List<CodeChange> parseChangesFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        List<CodeChange> changes = new ArrayList<>();
        
        // 尝试提取 JSON 数组
        String jsonStr = extractJsonArray(response);
        if (jsonStr == null) {
            return null;
        }

        try {
            JSONArray jsonArray = JSON.parseArray(jsonStr);
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String type = obj.getString("type");
                String reason = obj.getString("reason");

                CodeChange change = null;
                switch (type.toUpperCase()) {
                    case "DELETE":
                        int deleteLine = obj.getIntValue("line");
                        String deleteContent = obj.getString("content");
                        change = CodeChange.delete(deleteLine, deleteContent, reason);
                        break;
                    case "INSERT":
                        int afterLine = obj.getIntValue("afterLine");
                        String insertContent = obj.getString("content");
                        change = CodeChange.insert(afterLine, insertContent, reason);
                        break;
                    case "MODIFY":
                        int modifyLine = obj.getIntValue("line");
                        String oldContent = obj.getString("oldContent");
                        String newContent = obj.getString("newContent");
                        change = CodeChange.modify(modifyLine, oldContent, newContent, reason);
                        break;
                }
                if (change != null) {
                    changes.add(change);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return changes;
    }

    /**
     * 从响应中提取 JSON 数组
     */
    private String extractJsonArray(String response) {
        // 尝试找到 JSON 数组
        // 首先尝试在代码块中查找
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\n?(\\[.*?])\\s*```", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 尝试直接匹配 JSON 数组
        Pattern jsonPattern = Pattern.compile("\\[\\s*\\{.*}\\s*]", Pattern.DOTALL);
        matcher = jsonPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group();
        }

        // 尝试找到 [ 开头的内容
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 获取选中区域的 Linter 错误信息
     */
    private List<LinterError> getLinterErrors(Project project, Editor editor, int startOffset, int endOffset) {
        List<LinterError> errors = new ArrayList<>();
        Document document = editor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        
        if (psiFile == null) {
            return errors;
        }

        // 处理错误级别
        DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR,
                startOffset, endOffset, highlightInfo -> {
                    if (highlightInfo != null && highlightInfo.getDescription() != null) {
                        int line = document.getLineNumber(highlightInfo.getStartOffset()) + 1;
                        String severity = getSeverityName(highlightInfo.getSeverity());
                        LinterError newError = new LinterError(line, highlightInfo.getDescription(), severity);
                        if (!errors.contains(newError)) {
                            errors.add(newError);
                        }
                    }
                    return true;
                });

        // 处理警告级别
        DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.WARNING,
                startOffset, endOffset, highlightInfo -> {
                    if (highlightInfo != null && highlightInfo.getDescription() != null) {
                        int line = document.getLineNumber(highlightInfo.getStartOffset()) + 1;
                        String severity = getSeverityName(highlightInfo.getSeverity());
                        LinterError newError = new LinterError(line, highlightInfo.getDescription(), severity);
                        if (!errors.contains(newError)) {
                            errors.add(newError);
                        }
                    }
                    return true;
                });

        // 处理弱警告级别
        DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.WEAK_WARNING,
                startOffset, endOffset, highlightInfo -> {
                    if (highlightInfo != null && highlightInfo.getDescription() != null) {
                        int line = document.getLineNumber(highlightInfo.getStartOffset()) + 1;
                        String severity = getSeverityName(highlightInfo.getSeverity());
                        LinterError newError = new LinterError(line, highlightInfo.getDescription(), severity);
                        if (!errors.contains(newError)) {
                            errors.add(newError);
                        }
                    }
                    return true;
                });

        return errors;
    }

    /**
     * 获取严重程度名称
     */
    private String getSeverityName(HighlightSeverity severity) {
        if (severity.compareTo(HighlightSeverity.ERROR) >= 0) {
            return "ERROR";
        } else if (severity.compareTo(HighlightSeverity.WARNING) >= 0) {
            return "WARNING";
        } else if (severity.compareTo(HighlightSeverity.WEAK_WARNING) >= 0) {
            return "WEAK_WARNING";
        } else {
            return "INFO";
        }
    }

    /**
     * 打开工具窗口
     */
    private void openToolWindow(Project project) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("GPT4_lll");
        if (toolWindow != null && !toolWindow.isVisible()) {
            toolWindow.show();
            int tryTimes = 0;
            while (!toolWindow.isVisible() && tryTimes < 3) {
                tryTimes++;
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 发送请求并获取 AI 响应
     */
    private String chatWithLinterFix(ChatContent content, Project project, Gpt4lllTextArea textArea) {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        String url = ChatUtils.getUrl(settings, project);
        if (url == null || url.isBlank()) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, 
                    "请输入正确的 API URL/Input the correct api url", "GPT4_LLL", Messages.getInformationIcon()));
            return "";
        }
        
        String apiKey = ChatUtils.getApiKey(settings, project);
        if (apiKey == null || apiKey.isBlank()) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, 
                    "请输入正确的 API Key/Input the correct apikey", "GPT4_LLL", Messages.getInformationIcon()));
            return "";
        }
        
        String proxy = settings.getProxyAddress();
        String requestBody = JSON.toJSONString(content);

        HttpClient client = ChatUtils.buildHttpClient(proxy, project);
        if (client == null) {
            return "";
        }

        HttpRequest request;
        try {
            request = ChatUtils.buildHttpRequest(url, requestBody, apiKey);
        } catch (IllegalArgumentException exception) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, 
                    "请求建立失败，请检查相关设置/Request failed", "GPT4_LLL", Messages.getInformationIcon()));
            return "";
        }

        StringBuilder responseBuffer = new StringBuilder();

        try {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        response.body().forEach(line -> {
                            if (line.startsWith("data")) {
                                line = line.substring(5);
                                String resContent = null;

                                if (ProviderNameEnum.BAIDU.getProviderName().equals(ModelUtils.getSelectedProvider(project)) 
                                        || ProviderNameEnum.FREE.getProviderName().equals(ModelUtils.getSelectedProvider(project))) {
                                    try {
                                        BaiduSseResponse baiduResponse = JSON.parseObject(line, BaiduSseResponse.class);
                                        if (baiduResponse != null) {
                                            resContent = baiduResponse.getResult();
                                        }
                                    } catch (Exception ignored) {}
                                } else {
                                    try {
                                        SseResponse sseResponse = JSON.parseObject(line, SseResponse.class);
                                        if (sseResponse != null && sseResponse.getChoices() != null 
                                                && !sseResponse.getChoices().isEmpty()) {
                                            resContent = sseResponse.getChoices().get(0).getDelta().getContent();
                                        }
                                    } catch (Exception ignored) {}
                                }

                                if (resContent != null && !resContent.isEmpty()) {
                                    responseBuffer.append(resContent);
                                    if (textArea != null) {
                                        textArea.appendContent(resContent);
                                    }
                                }
                            }
                        });
                    }).join();
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, 
                    e.getMessage(), "Error", Messages.getErrorIcon()));
        }

        // 保存响应到会话历史
        Message assistantMessage = new Message();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(responseBuffer.toString());
        ChatUtils.getProjectChatHistory(project).add(assistantMessage);
        JsonStorage.saveConservation(ChatUtils.getProjectTopic(project), ChatUtils.getProjectChatHistory(project));

        return responseBuffer.toString();
    }

    /**
     * Linter 错误信息类
     */
    private static class LinterError {
        final int line;
        final String description;
        final String severity;

        LinterError(int line, String description, String severity) {
            this.line = line;
            this.description = description;
            this.severity = severity;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            LinterError that = (LinterError) obj;
            return line == that.line && description.equals(that.description);
        }

        @Override
        public int hashCode() {
            return 31 * line + description.hashCode();
        }
    }
}
