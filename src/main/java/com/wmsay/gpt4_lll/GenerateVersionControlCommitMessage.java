package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectUtil;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.ui.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapterRegistry;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler;
import com.intellij.vcs.commit.CommitWorkflowHandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.wmsay.gpt4_lll.ChangeSourceResolver;
import com.wmsay.gpt4_lll.component.AgentChatView;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY;
import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC;

public class GenerateVersionControlCommitMessage extends AnAction {
    
    // 单机应用就是好，没有并发就是爽
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final ChangeSourceResolver changeSourceResolver = new ChangeSourceResolver();
    
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

        Thread chatThread = null;
        try {
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
                        CommonUtil.stopRunningStatus(project);
                        return;
                    }
                }
            }

            // Get staged diff
            String diff = getStagedDiff(project, e);
            if (diff.isEmpty()) {
                Messages.showMessageDialog(project, "No changes to commit.", "Info", Messages.getInformationIcon());
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
            systemMessage.setRole(ChatUtils.getSystemRole(ModelUtils.getAvailableProvider(project)));
            systemMessage.setContent("You are an expert Git commit message writer. You follow conventional commit practices and generate clear, concise messages that adhere to best practices.");

            // User message
            Message userMessage = new Message();
            userMessage.setRole("user");
            userMessage.setContent(prompt);

            // Chat content
            ChatContent chatContent = new ChatContent();
            chatContent.setMessages(
                ProviderAdapterRegistry.getAdapter(ModelUtils.getAvailableProvider(project))
                    .adaptMessages(new ArrayList<>(List.of(systemMessage, userMessage))));
            chatContent.setModel(ChatUtils.getModelName(project));
            chatContent.setTemperature(0.2);

            // 添加到对话历史
            project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY).addAll(List.of(systemMessage, userMessage));

            // 清理界面
            AgentChatView textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
            if (textArea != null) {
                textArea.clearShowWindow();
            }

            // 获取commit文档
            Document commitDoc = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT);

            chatThread = new Thread(() -> {
                try {
                    GenerateAction.chat(chatContent, project, false, true, "", 0, commitDoc);
                } catch (Exception ex) {
                    // 记录异常日志
                    ex.printStackTrace();
                    // 在UI线程中显示错误信息
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showMessageDialog(project, 
                            "An error occurred during commit message generation: " + ex.getMessage(), 
                            "Error", 
                            Messages.getErrorIcon());
                    });
                } finally {
                    // 确保运行状态被重置
                    CommonUtil.stopRunningStatus(project);
                }
            });
            
            // 设置线程异常处理器
            chatThread.setUncaughtExceptionHandler((t, ex) -> {
                ex.printStackTrace();
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showMessageDialog(project, 
                        "An unexpected error occurred: " + ex.getMessage(), 
                        "Error", 
                        Messages.getErrorIcon());
                });
                CommonUtil.stopRunningStatus(project);
            });
            
            chatThread.start();
            
        } catch (Exception ex) {
            // 捕获主线程中的异常
            ex.printStackTrace();
            ApplicationManager.getApplication().invokeLater(() ->
                Messages.showMessageDialog(project, 
                    "Failed to initialize commit message generation: " + ex.getMessage(), 
                    "Error", 
                    Messages.getErrorIcon())
            );
        } finally {
            // 确保在主线程异常时也重置运行状态
            // 只有在没有成功启动聊天线程的情况下才重置状态
            if (chatThread == null || !chatThread.isAlive()) {
                CommonUtil.stopRunningStatus(project);
            }
        }
    }

    /**
     * 从 VcsDataKeys 获取用户高亮选中的变更（Selected Changes）
     */
    @NotNull
    private List<String> extractSelectedChanges(AnActionEvent e, VirtualFile root) {
        Change[] changes = e.getData(VcsDataKeys.SELECTED_CHANGES);
        if (changes == null || changes.length == 0) {
            changes = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION);
        }
        if (changes == null || changes.length == 0) {
            var selection = e.getData(VcsDataKeys.CHANGES_SELECTION);
            if (selection != null) {
                changes = selection.getList().toArray(Change[]::new);
            }
        }
        return changesToPaths(changes, root);
    }

    /**
     * 从 CommitWorkflowHandler 获取用户勾选的文件（Checked/Included Files）。
     * 通过 COMMIT_WORKFLOW_HANDLER → AbstractCommitWorkflowHandler → ui.getIncludedChanges()
     * 获取真正勾选的文件列表，适用于 non-modal、modal 和 Git Staging 三种 commit UI。
     * 如果无法通过 CommitWorkflowHandler 获取，回退到 VcsDataKeys.CHANGES。
     */
    @NotNull
    private List<String> extractCheckedFiles(AnActionEvent e, VirtualFile root) {
        // 优先通过 CommitWorkflowHandler 获取勾选文件
        CommitWorkflowHandler workflowHandler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER);
        System.out.println("[DEBUG] workflowHandler=" + workflowHandler);
        System.out.println("[DEBUG] workflowHandler class=" + (workflowHandler != null ? workflowHandler.getClass().getName() : "null"));
        if (workflowHandler instanceof AbstractCommitWorkflowHandler<?, ?> abstractHandler) {
            List<Change> includedChanges = abstractHandler.getUi().getIncludedChanges();
            System.out.println("[DEBUG] includedChanges.size=" + includedChanges.size());
            for (Change c : includedChanges) {
                System.out.println("[DEBUG]   includedChange: " + (c.getVirtualFile() != null ? c.getVirtualFile().getPath() : "null"));
            }
            if (!includedChanges.isEmpty()) {
                return changesToPathsFromList(includedChanges, root);
            }
        }
        // 回退到 VcsDataKeys.CHANGES
        Change[] changes = e.getData(VcsDataKeys.CHANGES);
        System.out.println("[DEBUG] fallback CHANGES=" + (changes != null ? changes.length : "null"));
        return changesToPaths(changes, root);
    }

    /**
     * 将 List<Change> 转换为相对路径列表
     */
    private List<String> changesToPathsFromList(List<Change> changes, VirtualFile root) {
        if (changes == null || changes.isEmpty()) return Collections.emptyList();
        List<String> paths = new ArrayList<>();
        for (Change change : changes) {
            VirtualFile file = change.getVirtualFile();
            if (file != null) {
                String rel = getRelativePath(file, root);
                if (rel != null) paths.add(rel);
            }
        }
        return paths;
    }

    /**
     * 将 Change[] 转换为相对路径列表
     */
    private List<String> changesToPaths(Change[] changes, VirtualFile root) {
        if (changes == null || changes.length == 0) return Collections.emptyList();
        List<String> paths = new ArrayList<>();
        for (Change change : changes) {
            VirtualFile file = change.getVirtualFile();
            if (file != null) {
                String rel = getRelativePath(file, root);
                if (rel != null) paths.add(rel);
            }
        }
        return paths;
    }

    private String getStagedDiff(Project project, AnActionEvent e) {
        try {
            VirtualFile root = ProjectLevelVcsManager.getInstance(project)
                    .getVcsRootFor(ProjectUtil.guessProjectDir(project));
            if (root == null) return "";

            // 使用 ChangeSourceResolver 解析变更来源
            List<String> selectedPaths = extractSelectedChanges(e, root);
            List<String> checkedPaths = extractCheckedFiles(e, root);
            List<String> resolvedPaths = changeSourceResolver.resolve(selectedPaths, checkedPaths);

            // DEBUG
            System.out.println("[ChangeSourceResolver] selectedPaths=" + selectedPaths);
            System.out.println("[ChangeSourceResolver] checkedPaths=" + checkedPaths);
            System.out.println("[ChangeSourceResolver] resolvedPaths=" + resolvedPaths);

            // 如果 resolver 返回了路径列表，直接用路径获取 diff
            if (!resolvedPaths.isEmpty()) {
                StringBuilder allDiff = new StringBuilder();
                for (String relativePath : resolvedPaths) {
                    // 先尝试 staged diff，如果为空再尝试 unstaged diff
                    String fileDiff = getSingleFileDiff(root, relativePath, false);
                    if (fileDiff.isEmpty()) {
                        fileDiff = getSingleFileDiff(root, relativePath, true);
                    }
                    if (!fileDiff.isEmpty()) {
                        allDiff.append("=== File: ").append(relativePath).append(" ===\n");
                        allDiff.append(fileDiff).append("\n");
                    }
                }
                return allDiff.toString();
            }

            // resolver 返回空 → 回退到 Default Change List
            Change[] changes = getChangesFromDefaultChangeList(project);
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
