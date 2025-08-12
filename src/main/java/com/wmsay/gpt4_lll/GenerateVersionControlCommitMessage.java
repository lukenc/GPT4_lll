package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY;
import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC;

public class GenerateVersionControlCommitMessage extends AnAction {
    
    // 单机应用就是好，没有并发就是爽
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // 检查运行状态
        if(CommonUtil.isRunningStatus(project)){
            Messages.showMessageDialog(project, "Please wait, another task is running", "Error", Messages.getErrorIcon());
            return;
        } else {
            CommonUtil.startRunningStatus(project);
        }

        // 1. 初始化新的对话 - 保存当前对话历史并清空
        List<Message> chatHistory = project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY);
        String nowTopic = project.getUserData(GPT_4_LLL_NOW_TOPIC);
        if (chatHistory != null && !chatHistory.isEmpty() && nowTopic != null && !nowTopic.isEmpty()) {
            JsonStorage.saveConservation(nowTopic, chatHistory);
            chatHistory.clear();
        }
        
        // 设置新的主题
        LocalDateTime now = LocalDateTime.now();
        nowTopic = formatter.format(now) + "--CommitMessageGeneration";
        project.putUserData(GPT_4_LLL_NOW_TOPIC, nowTopic);
        
        // 初始化对话历史
        if (chatHistory == null) {
            project.putUserData(GPT_4_LLL_CONVERSATION_HISTORY, new ArrayList<>());
        }

        // 打开工具窗口
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("GPT4_lll");
        if (toolWindow != null && !toolWindow.isVisible()) {
            toolWindow.show();
            int tryTimes = 0;
            while (!toolWindow.isVisible() && tryTimes < 3) {
                tryTimes++;
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Get staged diff
        String diff = getStagedDiff(project, e);
        if (diff.isEmpty()) {
            Messages.showMessageDialog(project, "No changes to commit.", "Info", Messages.getInformationIcon());
            CommonUtil.stopRunningStatus(project);
            return;
        }

        // Build prompt
        String replyLanguage = CommonUtil.getSystemLanguage();
        String prompt = """
                Based on the following Git diff, generate a Git commit message that strictly follows the Conventional Commits specification.
                
                Your goals:
                1. Understand the code change: Analyze what was changed, why it was changed, and what the expected impact is. Focus on the intent behind the code change.
                2. Generate a commit message that follows the Conventional Commits format:
                
                   <type>(<scope>): <subject line>
                
                   <body: explain what and why, wrapped at 72 characters per line>
                
                   <footer: optional, e.g., BREAKING CHANGE or Closes #123>
                
                Requirements:
                - Use one of the allowed types: feat, fix, docs, style, refactor, perf, test, build, ci, chore.
                - Use imperative mood (e.g., "add", "fix", not "added" or "adds").
                - Subject line must be concise (≤ 50 characters) and have no period at the end.
                - Body should explain the reason and impact clearly. Use multiple paragraphs if needed.
                - Footer can include "Closes #123" or "BREAKING CHANGE" if applicable.
                - Do not include section headers like "Header", "Body", or "Footer".
                - Return only the complete commit message wrapped in a Markdown code block.
                - The entire output must be in: %s.
                
                Git diff:
                %s
                """.formatted(replyLanguage, diff);

        // System message
        Message systemMessage = new Message();
        if (ProviderNameEnum.BAIDU.getProviderName().equals(ModelUtils.getSelectedProvider(project))) {
            systemMessage.setRole("user");
        } else {
            systemMessage.setRole("system");
        }
        systemMessage.setContent("You are an expert Git commit message writer. You follow conventional commit practices and generate clear, concise messages that adhere to best practices.");

        // User message
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(prompt);

        // Chat content
        ChatContent chatContent = new ChatContent();
        chatContent.setMessages(new ArrayList<>(List.of(systemMessage, userMessage)), ModelUtils.getSelectedProvider(project));
        chatContent.setModel(ChatUtils.getModelName(project));
        chatContent.setTemperature(0.2);

        // 添加到对话历史
        project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY).addAll(List.of(systemMessage, userMessage));

        // 清理界面
        Gpt4lllTextArea textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
        if (textArea != null) {
            textArea.clearShowWindow();
        }

        // 获取commit文档
        Document commitDoc = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT);

        // 使用扩展后的GenerateAction.chat()方法，传入commitDoc参数
         new Thread(()->GenerateAction.chat(chatContent, project, false, true, "", 0, commitDoc)).start();
    }

    /**
     * 尝试从commit对话框的不同数据源获取选中的变更
     */
    @NotNull
    private Change[] getSelectedChangesFromCommitDialog(AnActionEvent e) {
        Change[] changes = e.getData(VcsDataKeys.CHANGES_SELECTION) != null ? e.getData(VcsDataKeys.CHANGES_SELECTION).getList().toArray(Change[]::new) : null;
        if (changes != null && changes.length > 0) {
            return changes;
        }
        changes = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION);
        if (changes != null && changes.length > 0) {
            return changes;
        }
        changes = e.getData(VcsDataKeys.SELECTED_CHANGES);
        if (changes != null && changes.length > 0) {
            return changes;
        }
        changes = e.getData(VcsDataKeys.CHANGES);
        if (changes != null && changes.length > 0) {
            return changes;
        }
        return new Change[0];
    }

    private String getStagedDiff(Project project, AnActionEvent e) {
        try {
            VirtualFile root = ProjectLevelVcsManager.getInstance(project)
                    .getVcsRootFor(ProjectUtil.guessProjectDir(project));
            if (root == null) return "";

            // 尝试从commit对话框获取选中的变更
            Change[] changes = getSelectedChangesFromCommitDialog(e);

            // 如果还是为空，尝试从默认变更列表中获取
            if (changes == null || changes.length == 0) {
                changes = getChangesFromDefaultChangeList(project);
            }

            if (changes == null || changes.length == 0) {
                return "";
            }

            // Group by file and determine if any is unstaged
            Map<String, Boolean> fileToIsUnstaged = new HashMap<>();
            for (Change change : changes) {
                VirtualFile file = change.getVirtualFile();
                if (file != null) {
                    String relativePath = getRelativePath(file, root);
                    if (relativePath != null) {
                        boolean isUnstaged = change.getAfterRevision() instanceof CurrentContentRevision;
                        fileToIsUnstaged.put(relativePath, fileToIsUnstaged.getOrDefault(relativePath, false) || isUnstaged);
                    }
                }
            }

            // 获取diff
            StringBuilder allDiff = new StringBuilder();
            for (Map.Entry<String, Boolean> entry : fileToIsUnstaged.entrySet()) {
                String relativePath = entry.getKey();
                boolean isUnstaged = entry.getValue();
                String fileDiff = getSingleFileDiff(root, relativePath, isUnstaged);
                if (!fileDiff.isEmpty()) {
                    allDiff.append("=== File: ").append(relativePath).append(" ===\n");
                    allDiff.append(fileDiff).append("\n");
                }
            }
            return allDiff.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * 从默认变更列表中获取变更信息
     */
    private Change[] getChangesFromDefaultChangeList(Project project) {
        try {
            ChangeListManager changeListManager = ChangeListManager.getInstance(project);
            if (changeListManager != null) {
                LocalChangeList defaultChangeList = changeListManager.getDefaultChangeList();
                if (defaultChangeList != null) {
                    Collection<Change> allChanges = defaultChangeList.getChanges();
                    return allChanges.toArray(new Change[0]);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new Change[0];
    }

    /**
     * 获取相对路径
     */
    private String getRelativePath(VirtualFile file, VirtualFile root) {
        try {
            String filePath = file.getPath();
            String rootPath = root.getPath();

            if (filePath.startsWith(rootPath)) {
                String relativePath = filePath.substring(rootPath.length());
                // 移除开头的路径分隔符
                if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                    relativePath = relativePath.substring(1);
                }
                return relativePath;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * 不使用文件过滤的git diff命令
     */
    private String getStagedDiffWithoutFiltering(Project project, VirtualFile root) {
        try {
            String[] command = {"git", "diff"};

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(root.getPath()));
            Process process = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            return sb.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * 获取单个文件的diff
     */
    private String getSingleFileDiff(VirtualFile root, String filePath, boolean isUnstaged) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.add("diff");
            if (isUnstaged) {
                command.add("HEAD");
            } else {
                command.add("--cached");
            }
            command.add("--");
            command.add(filePath);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(root.getPath()));
            Process process = pb.start();

            // 读取标准输出
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            // 读取错误输出（用于调试）
            java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getErrorStream()));
            StringBuilder errorSb = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                errorSb.append(line).append("\n");
            }

            // 等待进程结束
            process.waitFor();

            return sb.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }
}
