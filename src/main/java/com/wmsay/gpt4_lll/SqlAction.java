package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.wmsay.gpt4_lll.component.AgentChatView;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import com.intellij.psi.PsiDocumentManager;

import java.util.ArrayList;
import java.util.List;


public class SqlAction extends AnAction {

    public static String SQL_PROMPT = """
            请严格按照以下要求对SQL语句进行评估和优化：
                                    
                1. 输入信息：
                SQL语句：${selectedText}
                评估语言：${replyLanguage}
                                    
                2. 评估要求：
                - 语法正确性检查
                - 性能优化建议
                - 安全性评估
                - 索引使用建议
                - 查询效率分析
                - 最佳实践建议
                                    
                3. 输出格式：
                - 使用${replyLanguage}语言回复
                - 提供详细的评估报告
                - 给出优化后的SQL语句
                - 说明优化理由
                                    
                4. 评估维度：
                - 语法规范性
                - 查询性能
                - 安全性
                - 可维护性
                - 索引使用
                - 资源消耗
            """;

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project==null){
            ApplicationManager.getApplication().invokeLater(() ->
                Messages.showMessageDialog(e.getProject(), "不是一个项目/no project here", "Error", Messages.getErrorIcon())
            );
            return;
        }
        if(CommonUtil.isRunningStatus(project)){
            ApplicationManager.getApplication().invokeLater(() ->
                Messages.showMessageDialog(project, "Please wait, another task is running", "Error", Messages.getErrorIcon())
            );
            return;
        }else {
            CommonUtil.startRunningStatus(project);
        }

        // 初始化新的对话 - 保存并清空历史对话
        if (ChatUtils.getProjectChatHistory(project) != null && !ChatUtils.getProjectChatHistory(project).isEmpty() && !ChatUtils.getProjectTopic(project).isEmpty()) {
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
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor==null){
            ApplicationManager.getApplication().invokeLater(() ->
                Messages.showMessageDialog(project, "Editor is not open. Please open the file that you want to do something", "Error", Messages.getErrorIcon())
            );
            CommonUtil.stopRunningStatus(project);
            return;
        }
        String fileType = CommonUtil.getOpenFileType(project);
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        
        if (selectedText == null || selectedText.isEmpty()) {
            ApplicationManager.getApplication().invokeLater(() ->
                Messages.showMessageDialog(project, "No text selected. Please select the SQL you want to analyze", "Error", Messages.getErrorIcon())
            );
            CommonUtil.stopRunningStatus(project);
            return;
        }
        
        // 验证是否为SQL语句
        if (!CommonUtil.isSql(selectedText)){
            ApplicationManager.getApplication().invokeLater(() ->
                Messages.showMessageDialog(project, "Not a SQL, Please give me a SQL", "Not A SQL", Messages.getInformationIcon())
            );
            CommonUtil.stopRunningStatus(project);
            return;
        }
        
        Message systemMessage = new Message();
        systemMessage.setRole(ChatUtils.getSystemRole(ModelUtils.getSelectedProvider(project)));
        systemMessage.setContent("你是一个资深的数据库专家和SQL优化专家，有着多年的数据库设计和SQL优化经验。你对SQL语句的分析十分专业，能够从性能、安全性、规范性等多个维度进行评估和优化。");

        Message message = new Message();
        List<Message> moreMessageList = new ArrayList<>();
        
        if (selectedText != null) {
            // 清空对话历史并设置新主题
            ChatUtils.getProjectChatHistory(project).clear();
            selectedText = selectedText.trim();
            ChatUtils.setProjectTopic(project, CommonUtil.generateTopicByMethodAndTime(selectedText, "SQL"));

            message.setRole("user");
            message.setName("owner");
            String prompt = SQL_PROMPT
                    .replace("${replyLanguage}", replyLanguage)
                    .replace("${selectedText}", selectedText);
            message.setContent(prompt);
            
            // 如果是MyBatis XML文件，添加额外的上下文信息
            if ("xml".equalsIgnoreCase(fileType)) {
                PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                if (psiFile instanceof XmlFile) {
                    XmlFile xmlFile = (XmlFile) psiFile;
                    XmlTag rootTag = xmlFile.getRootTag();
                    if (rootTag != null) {
                        Message contextMessage = new Message();
                        contextMessage.setRole("user");
                        contextMessage.setName("context");
                        contextMessage.setContent("这是MyBatis XML文件中的SQL语句，文件类型为：" + fileType + "，根标签为：" + rootTag.getName());
                        moreMessageList.add(contextMessage);
                    }
                }
            }
            
            ChatContent chatContent = new ChatContent();
            List<Message> sendMessageList = new ArrayList<>();
            if (!moreMessageList.isEmpty()){
                sendMessageList.add(systemMessage);
                sendMessageList.addAll(moreMessageList);
                sendMessageList.add(message);
                chatContent.setMessages(sendMessageList, ModelUtils.getSelectedProvider(project));
            }else {
                sendMessageList = new ArrayList<>(List.of(systemMessage, message));
            }
            chatContent.setMessages(sendMessageList, ModelUtils.getSelectedProvider(project));
            chatContent.setModel(model);
            chatContent.setTemperature(0.2);
            
            // 添加到对话历史
            ChatUtils.getProjectChatHistory(project).addAll(chatContent.getMessages());

            // 清理界面
            AgentChatView textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
            if (textArea != null) {
                textArea.clearShowWindow();
            }
            
            // 启动对话线程
            Thread dochatThread = new Thread(() -> {
                GenerateAction.chat(chatContent, project, false, true, "");
            });
            dochatThread.start();
        }
        
        CommonUtil.stopRunningStatus(project);
    }

    private void processXmlTag(XmlTag tag) {
        for (PsiElement child : tag.getChildren()) {
            if (child instanceof XmlTag) {
                XmlTag xmlTag = (XmlTag) child;
                if ("select".equals(xmlTag.getLocalName()) ||
                        "insert".equals(xmlTag.getLocalName()) ||
                        "update".equals(xmlTag.getLocalName()) ||
                        "delete".equals(xmlTag.getLocalName())) {
                    // 处理SQL标签
                    String sqlContent = xmlTag.getValue().getText();
                    // 这里可以添加对SQL内容的处理逻辑
                }
            }
        }
    }
}
