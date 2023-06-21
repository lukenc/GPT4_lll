package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.utils.CommonUtil;

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
        String replyLanguage = CommonUtil.getSystemLanguage();
        Project project = e.getProject();
        if (project != null) {
            Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
            String fileType = CommonUtil.getOpenFileType(project);
            SelectionModel selectionModel = editor.getSelectionModel();
            String selectedText = selectionModel.getSelectedText();
            Message systemMessage = new Message();
            systemMessage.setRole("system");
            systemMessage.setName("owner");
            systemMessage.setContent("你是一个有用的助手，同时也是一个计算机科学家，数据专家，有着多年的代码重构经验和多年的代码优化的架构师");

            Message message = new Message();
            if (selectedText != null) {
                chatHistory.clear();
                selectedText = selectedText.trim();
                nowTopic = CommonUtil.generateTopicByMethodAndTime(selectedText, "Score");

                message.setRole("user");
                message.setName("owner");
                message.setContent("评估不限于以下方面：1、类、方法、变量的命名 2、空指针风险 3、数组越界风险 4、并发控制 5、注释完整性 6、异常捕捉及处理 7、日志合规性 8、是否有安全方面的问题 9、是否有性能方面的问题 10、，其余方面。如果该评估总分是100，帮忙使用" + replyLanguage + "语言，评估下面的" + fileType + "代码的得分，代码如下:" + selectedText);
                ChatContent chatContent = new ChatContent();
                chatContent.setMessages(List.of(message, systemMessage));
                chatContent.setModel(model);
                chatContent.setTemperature(0.2);
                chatHistory.addAll(List.of(message, systemMessage));

                //清理界面
                WindowTool.clearShowWindow();
                new Thread(() -> GenerateAction.chat(chatContent, project, false)).start();
            }
        }
        // TODO: insert action logic here
    }
}
