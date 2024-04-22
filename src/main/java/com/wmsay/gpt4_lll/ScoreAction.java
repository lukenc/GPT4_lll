package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import com.wmsay.gpt4_lll.utils.CodeUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;

import java.util.ArrayList;
import java.util.List;

import static com.wmsay.gpt4_lll.GenerateAction.chatHistory;
import static com.wmsay.gpt4_lll.GenerateAction.nowTopic;

public class ScoreAction extends AnAction {

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
        String model = "gpt-3.5-turbo";
        model = ChatUtils.getModelName(toolWindow);
        String replyLanguage = CommonUtil.getSystemLanguage();
        Project project = e.getProject();
        if (project != null) {
            Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
            String fileType = CommonUtil.getOpenFileType(project);
            SelectionModel selectionModel = editor.getSelectionModel();
            String selectedText = selectionModel.getSelectedText();
            Message systemMessage = new Message();
            if (model.contains("baidu")){
                systemMessage.setRole("user");
            }else {
                systemMessage.setRole("system");
            }
            systemMessage.setName("owner");
            systemMessage.setContent("你是一个计算机科学家，数据专家，有着多年的代码重构经验和多年的代码优化经验的架构师。你对代码的审查十分严格。对于审查评价代码，你总能指出代码中的各种问题，但你不会说不存在的问题，同时也不会遗漏任何一处问题。");

            Message message = new Message();
            if (selectedText != null) {
                chatHistory.clear();
                selectedText = selectedText.trim();
                nowTopic = CommonUtil.generateTopicByMethodAndTime(selectedText, "Score");

                message.setRole("user");
                message.setName("owner");
                //todo 通过fileType的筛选 确定message的Content，如果fileType中存在TypeScript或者JavaScript，忽略大小写，则message.setContent("1")
                if ("Vue".equalsIgnoreCase(fileType) || "TypeScript".equalsIgnoreCase(fileType) || "JavaScript".equalsIgnoreCase(fileType) || (fileType != null && fileType.toLowerCase().contains("javascript")) || (fileType != null && fileType.toLowerCase().contains("typescript"))) {
                    message.setContent("评估不限于以下方面：" + CodeUtils.WEB_DEV_STD + "。如果该评估总分是100，帮忙使用" + replyLanguage + "语言，评估下面的" + fileType + "代码的得分，一定要确保评估的准确性，接下来我将给你需要评估的代码。");
                } else {
                    message.setContent("评估不限于以下方面：" + CodeUtils.BACK_END_DEV_STD + "。如果该评估总分是100，帮忙使用" + replyLanguage + "语言，评估下面的" + fileType + "代码的得分，一定要确保评估的准确性，接下来我将给你需要评估的代码。");
                }
                Message codeMessage = new Message();
                codeMessage.setRole("user");
                codeMessage.setName("owner");
                codeMessage.setContent("认真对每一项打分，以及总体得分，请开始评估以下代码，：\n"+selectedText);

                ChatContent chatContent = new ChatContent();
                chatContent.setMessages(new ArrayList<>(List.of(systemMessage,message,codeMessage)));
                chatContent.setModel(model);
                chatContent.setTemperature(0.1);
                chatHistory.addAll(chatContent.getMessages());

                //清理界面
                Gpt4lllTextArea textArea= project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
                if (textArea != null) {
                    textArea.clearShowWindow();
                }
                new Thread(() -> GenerateAction.chat(chatContent, project, false,true,"")).start();
            }
        }
        // TODO: insert action logic here
    }
}
