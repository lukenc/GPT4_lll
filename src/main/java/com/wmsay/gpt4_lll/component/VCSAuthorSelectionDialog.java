package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.wmsay.gpt4_lll.GenerateAction;
import com.wmsay.gpt4_lll.JsonStorage;
import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CodeUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class VCSAuthorSelectionDialog extends DialogWrapper {

    private final Project project;
    private JTextField authorField;
    private JCheckBox useCurrentUserCheckBox;
    private JDatePicker fromDatePicker;
    private JDatePicker toDatePicker;

    public VCSAuthorSelectionDialog(Project project) {
        super(project);
        this.project = project;
        init();
        setTitle("Select Git Author(s)");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JPanel authorPanel = new JPanel(new BorderLayout());
        authorField = new JTextField();
        useCurrentUserCheckBox = new JCheckBox("Use Current Git User");
        authorPanel.add(new JLabel("Author(s) (comma separated):"), BorderLayout.NORTH);
        authorPanel.add(authorField, BorderLayout.CENTER);
        authorPanel.add(useCurrentUserCheckBox, BorderLayout.SOUTH);

        JPanel datePanel = new JPanel(new GridLayout(2, 2, 5, 5));
        fromDatePicker = new JDatePicker();
        toDatePicker = new JDatePicker();
        datePanel.add(new JLabel("From Date:"));
        datePanel.add(fromDatePicker);
        datePanel.add(new JLabel("To Date:"));
        datePanel.add(toDatePicker);

        mainPanel.add(authorPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(datePanel);

        return mainPanel;
    }

    @Override
    protected void doOKAction() {
        super.doOKAction();
        String authorsInput = authorField.getText();
        boolean useCurrentUser = useCurrentUserCheckBox.isSelected();
        LocalDate fromDate = fromDatePicker.getDate();
        LocalDate toDate = toDatePicker.getDate();


        if (project == null) {
            Messages.showMessageDialog(project, "不是一个项目/no project here", "Error", Messages.getErrorIcon());
            return;
        }
        if (CommonUtil.isRunningStatus(project)) {
            Messages.showMessageDialog(project, "Please wait, another task is running", "Error", Messages.getErrorIcon());
            return;
        } else {
            CommonUtil.startRunningStatus(project);
        }
        if (ChatUtils.getProjectChatHistory(project) != null && !ChatUtils.getProjectChatHistory(project).isEmpty() && !ChatUtils.getProjectTopic(project).isEmpty()) {
            JsonStorage.saveConservation(ChatUtils.getProjectTopic(project), ChatUtils.getProjectChatHistory(project));
            ChatUtils.getProjectChatHistory(project).clear();
        }
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
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
        String model = ChatUtils.getModelName(project);
        String replyLanguage = CommonUtil.getSystemLanguage();
        Message systemMessage = new Message();
        if (ProviderNameEnum.BAIDU.getProviderName().equals(ModelUtils.getSelectedProvider(project))) {
            systemMessage.setRole("user");
        } else {
            systemMessage.setRole("system");
        }
        String assistantSystemMess = "You are a technical analyst responsible for translating Git commit messages into clear business-oriented progress reports. Based on the commit messages I provide, generate a report in {Language} that emphasizes business progress and context over technical implementation details.";
        systemMessage.setContent(assistantSystemMess.replace("{Language}", replyLanguage));

       List<String> commitMessages = GitCommitFetcher.fetchCommits(project, authorsInput, useCurrentUser, fromDate, toDate);
        if (commitMessages.isEmpty()) {
            Messages.showMessageDialog(project, "No commits found for the specified author(s) and date range.", "No Commits Found", Messages.getInformationIcon());
            CommonUtil.stopRunningStatus(project);
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String commitMessage : commitMessages) {
            stringBuilder.append(commitMessage).append("\n");
        }

        Message message = new Message();
        ChatUtils.setProjectTopic(project,CommonUtil.generateTopicByMethodAndTime("", "WorkReport"));
        message.setRole("user");
        message.setContent(CodeUtils.REPORT_AI_PROMPT.replace("{Language}", replyLanguage).replace("{CommitMessages}",stringBuilder.toString()));

        String providerName = ModelUtils.getSelectedProvider(project);
        String modelName = ChatUtils.getModelName(project);
        MyPluginSettings settings = MyPluginSettings.getInstance();
        ChatContent chatContent = new ChatContent();
        chatContent.setMessages(new ArrayList<>(List.of(systemMessage, message)), providerName);
        chatContent.setModel(modelName);
        chatContent.setTemperature(0.1);
        try {
            //清理界面
            Gpt4lllTextArea textArea= project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
            if (textArea != null) {
                textArea.clearShowWindow();
            }
            new Thread(() -> GenerateAction.chat(chatContent, project, false,true,"")).start();

        }catch (Exception e){
            Messages.showMessageDialog(project, e.getMessage(), "Error", Messages.getErrorIcon());
        }
        finally {
            CommonUtil.stopRunningStatus(project);
        }
    }
}
