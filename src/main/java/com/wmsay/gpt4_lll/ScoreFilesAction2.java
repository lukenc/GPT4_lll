package com.wmsay.gpt4_lll;


import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CodeUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ScoreFilesAction2 extends AnAction {
    private static final String OUTPUT_FOLDER_NAME = "score_gpt4lll";
    private static final int MAX_CONCURRENT_TASKS = 5; // 限制并发数量

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        VirtualFile[] selectedFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        if (selectedFiles == null || selectedFiles.length == 0) return;
        String replyLanguage = CommonUtil.getSystemLanguage();
        String providerName = ModelUtils.getSelectedProvider(project);
        String modelName = ChatUtils.getModelName(event.getProject());
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing Files") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    processFiles(selectedFiles, project, indicator, providerName, modelName, replyLanguage);
                } catch (Exception e) {
                    showError(project, "Error processing files: " + e.getMessage());
                }
            }
        });
    }

    private void processFiles(VirtualFile[] files, Project project, ProgressIndicator indicator,
                              String providerName, String modelName, String replyLanguage) {
        AtomicInteger processedFiles = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        List<Path> createdPaths = Collections.synchronizedList(new ArrayList<>());

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_TASKS);

        try {
            // 收集所有需要处理的文件
            List<VirtualFile> filesToProcess = new ArrayList<>();
            for (VirtualFile file : files) {
                if (file.isDirectory()) {
                    collectFilesFromDirectory(file, filesToProcess);
                } else if (!isInScoreFolder(file)) {
                    filesToProcess.add(file);
                }
            }

            // 创建所有必要的输出目录
            Map<String, Path> outputPaths = new ConcurrentHashMap<>();
            for (VirtualFile file : filesToProcess) {
                Path outputPath = Paths.get(file.getParent().getPath(), OUTPUT_FOLDER_NAME);
                outputPaths.put(file.getPath(), outputPath);
                Files.createDirectories(outputPath);
                createdPaths.add(outputPath);
            }

            // 使用 CompletableFuture 并行处理文件
            List<CompletableFuture<Void>> futures = filesToProcess.stream()
                    .map(file -> CompletableFuture.runAsync(() -> {
                        try {
                            if (indicator.isCanceled()) return;
                            indicator.setText2("Processing: " + file.getPath());

                            Path outputPath = outputPaths.get(file.getPath());
                            processFileContent(project, file, outputPath, processedFiles, errors, createdPaths,
                                    providerName, modelName, replyLanguage);

                            indicator.setFraction((double) processedFiles.get() / filesToProcess.size());
                        } catch (Exception e) {
                            errors.add("Error processing " + file.getPath() + ": " + e.getMessage());
                        }
                    }, executor))
                    .collect(Collectors.toList());

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } catch (Exception e) {
            errors.add("Error in parallel processing: " + e.getMessage());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }

        // 刷新创建的文件和文件夹
        refreshFiles(createdPaths);

        // Show completion message on UI thread
        ApplicationManager.getApplication().invokeLater(() -> {
            String message = String.format("Processed %d files", processedFiles.get());
            if (!errors.isEmpty()) {
                message += "\nErrors occurred:\n" + String.join("\n", errors);
            }
            Messages.showInfoMessage(project, message, "Process Complete");
        });
    }

    private void collectFilesFromDirectory(VirtualFile directory, List<VirtualFile> files) {
        if (isScoreFolder(directory)) return;

        for (VirtualFile child : directory.getChildren()) {
            if (child.isDirectory()) {
                if (!isScoreFolder(child)) {
                    collectFilesFromDirectory(child, files);
                }
            } else if (!isInScoreFolder(child)) {
                files.add(child);
            }
        }
    }

    private void processFileContent(Project project, VirtualFile file, Path outputPath, AtomicInteger processedFiles,
                                    List<String> errors, List<Path> createdPaths,
                                    String providerName, String modelName, String replyLanguage) throws IOException {
        try {
            String content = new String(file.contentsToByteArray());
            if (content.isEmpty())
                throw new Exception("File is empty");

            String fileType = file.getFileType().getName();
            Message systemMessage = new Message();
            if (ProviderNameEnum.BAIDU.getProviderName().equals(providerName)) {
                systemMessage.setRole("user");
            } else {
                systemMessage.setRole("system");
            }
            systemMessage.setContent(CodeUtils.SCORE_AI_PROMPT);

            Message message = new Message();
            message.setRole("user");
            if ("Vue".equalsIgnoreCase(fileType) || "TypeScript".equalsIgnoreCase(fileType)
                    || "JavaScript".equalsIgnoreCase(fileType)
                    || (fileType != null && fileType.toLowerCase().contains("javascript"))
                    || (fileType != null && fileType.toLowerCase().contains("typescript"))) {
                message.setContent("评估不限于以下方面：" + CodeUtils.WEB_DEV_STD + "。如果该评估总分是100，帮忙使用"
                        + replyLanguage + "语言，评估下面的" + fileType + "代码的得分，一定要确保评估的准确性，接下来我将给你需要评估的代码。");
            } else {
                message.setContent("评估不限于以下方面：" + CodeUtils.BACK_END_DEV_STD_PROMPT + "。如果该评估总分是100，帮忙使用"
                        + replyLanguage + "语言，评估下面的" + fileType + "代码的得分，一定要确保评估的准确性，接下来我将给你需要评估的代码。");
            }

            Message codeMessage = new Message();
            codeMessage.setRole("user");
            codeMessage.setName("owner");
            codeMessage.setContent("认真对每一项打分，以及总体得分，请开始评估以下代码，：\n" + content);

            ChatContent chatContent = new ChatContent();
            chatContent.setMessages(new ArrayList<>(List.of(systemMessage, message, codeMessage)), providerName);
            chatContent.setModel(modelName);
            chatContent.setTemperature(0.1);
            MyPluginSettings settings = MyPluginSettings.getInstance();

            String replyContent = ChatUtils.pureChat(providerName, ChatUtils.getApiKey(settings, project), chatContent);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HH:mm:ss").format(new Date());
            String outputFileName = file.getNameWithoutExtension() + "Score_" + timestamp + ".md";

            Path outputFile = outputPath.resolve(outputFileName);
            synchronized (createdPaths) {
                Files.write(outputFile, replyContent.getBytes());
                createdPaths.add(outputFile);
            }

            processedFiles.incrementAndGet();
        } catch (Exception e) {
            synchronized (errors) {
                errors.add("Error processing " + file.getPath() + ": " + e.getMessage());
            }
        }
    }

    // 其他辅助方法保持不变...
    private void refreshFiles(List<Path> paths) {
        ApplicationManager.getApplication().invokeLater(() -> {
            LocalFileSystem fileSystem = LocalFileSystem.getInstance();
            for (Path path : paths) {
                File file = path.toFile();
                if (file.exists()) {
                    fileSystem.refreshAndFindFileByIoFile(file);
                }

                if (file.isDirectory()) {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        VirtualFile parentVFile = fileSystem.refreshAndFindFileByIoFile(parent);
                        if (parentVFile != null) {
                            parentVFile.refresh(false, true);
                        }
                    }
                }
            }
        });
    }

    private void showError(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(project, message, "Error")
        );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        e.getPresentation().setEnabledAndVisible(files != null && files.length > 0);
    }

    private boolean isInScoreFolder(VirtualFile file) {
        VirtualFile parent = file.getParent();
        while (parent != null) {
            if (isScoreFolder(parent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isScoreFolder(VirtualFile directory) {
        return directory.getName().equals(OUTPUT_FOLDER_NAME);
    }
}
