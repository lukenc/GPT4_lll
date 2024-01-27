package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.component.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.wmsay.gpt4_lll.GenerateAction.*;
import static com.wmsay.gpt4_lll.GenerateAction.nowTopic;
import static com.wmsay.gpt4_lll.utils.ChatUtils.getModelName;
import static com.wmsay.gpt4_lll.utils.CommonUtil.getSystemLanguage;

public class UnitTestAction extends AnAction {
    public static String nowTopic = "";
    //1、创建文件
    //2、多次对话 最后输出功能
    //3、写入文件

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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




        Project project = e.getProject();
        // 获取当前活动编辑器的文件
        VirtualFile currentFile = FileEditorManager.getInstance(project).getSelectedFiles()[0];
        String packageName = "";
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        String fileName = "Test" + currentFile.getName();
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        String replyLanguage= getSystemLanguage();
        String model="gpt-3.5-turbo";
        model = getModelName(toolWindow);

        //生成文件所需要的变量赋值
        String targetPackageName="";
        //开始准备创建测试文件
        if (psiFile != null) {
            PsiElement[] children = psiFile.getChildren();
            for (PsiElement child : children) {
                if (child instanceof PsiPackageStatement) {
                    targetPackageName = ((PsiPackageStatement) child).getPackageName();
                    break;
                }
            }
        }
        List<SourceFolder> testSourceFolderList = new ArrayList<>();
        // 获取模块管理器
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        // 遍历项目中所有的模块
        for (Module module : moduleManager.getModules()) {
            // 获取每个模块的root model
            ModuleRootModel rootModel = ModuleRootManager.getInstance(module);
            // 从root model中获取内容条目并遍历
            for (ContentEntry contentEntry : rootModel.getContentEntries()) {
                // 获取测试源文件夹列表
                List<SourceFolder> testSourceFolders = contentEntry.getSourceFolders(JavaSourceRootType.TEST_SOURCE);
                if (testSourceFolders.isEmpty()) {
                    continue;
                }
                testSourceFolderList.addAll(testSourceFolders);
            }
        }
        if (testSourceFolderList.isEmpty()) {
            Messages.showMessageDialog(project, "请先设置好测试文件夹/Mark a Directory As Test Sources Root", "help", Messages.getInformationIcon());
            return;
        }
        SourceFolder nearestSourceFolder = findNearestTestFolder(e, testSourceFolderList);
        // 将targetPackageName转换为路径格式
        String packagePath = targetPackageName.replace('.', '/');
        VirtualFile nearestSourceFolderVirtualFile = nearestSourceFolder.getFile();
        VirtualFile packageDirectory = nearestSourceFolderVirtualFile.findChild(packagePath);
        VirtualFile targetDirectory = null;
        String[] directories;
        if (packageDirectory == null) {
            directories = targetPackageName.replace('.', '/').split("/");
        } else {
            directories = new String[0];
        }
        String testFileName;
        PsiDirectory psiDirectory;
        if (packageDirectory == null) {
            VirtualFile currentDirectory = nearestSourceFolderVirtualFile;
            // 逐级创建目录结构
            for (String directory : directories) {
                VirtualFile nextDirectory = currentDirectory.findChild(directory);
                if (nextDirectory == null || !nextDirectory.exists()) {
                    try {
                        nextDirectory = currentDirectory.createChildDirectory(null, directory);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                currentDirectory = nextDirectory;
            }
            psiDirectory = PsiManager.getInstance(project).findDirectory(currentDirectory);
            PsiFile existingFile = psiDirectory.findFile(fileName);
            if (existingFile == null) {
                // 创建文件内容和文件类型
//                                newFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, "");
                testFileName=fileName;
            } else {
                // 文件已存在，则新建一个新的文件
                // （避免污染其他文件，以后可以解已经存在的文件的文件结构，往里面加肉。但是目前这个版本先新建一个新的，降低一些难度）
                // 获取当前时间戳
                long timestamp = System.currentTimeMillis();
                // 找到最后一个点的位置（用于分隔文件名和扩展名）
                int dotIndex = fileName.lastIndexOf('.');
                // 分割文件名和扩展名
                String name = fileName.substring(0, dotIndex);
                String extension = fileName.substring(dotIndex);
                // 创建并返回新的文件名
                testFileName = name + "_" + timestamp + extension;
            }
        } else {
            testFileName = null;
            psiDirectory = null;
        }

        if (selectedText == null || selectedText.isEmpty()) {
            Messages.showMessageDialog(project, "No text selected. Please select the code you want to do something", "Error", Messages.getErrorIcon());
            return;
        }
        // 如果聊天历史不为空且当前主题不为空
        if (chatHistory != null && !chatHistory.isEmpty() && !nowTopic.isEmpty()) {
            // 调用JsonStorage类的saveConservation方法，保存当前主题的聊天历史
            JsonStorage.saveConservation(nowTopic, chatHistory);
            // 清空聊天历史
            chatHistory.clear();
        }


        Message systemMessage=new Message();
        systemMessage.setRole("system");
        systemMessage.setName("owner");
        systemMessage.setContent("你是一个有用的助手，同时也是一个资深的测试专家，会对每一个方法内部的逻辑进行分析，找到方法的边界条件和用例。");
        List<Message> moreMessageList=new ArrayList<>();
        Message messageFirst=new Message();
        nowTopic=formatter.format(LocalDateTime.now())+"--UnitTest:"+selectedText;
        messageFirst.setRole("user");
        messageFirst.setContent("请你根据代码逻辑，及逻辑内的各种边界条件,仔细思考，给出全面的所有的测试用例和用例的期望，要包括成功的，失败的，请使用"+replyLanguage+"回复我，代码如下：" + selectedText);

        ChatContent chatContent = new ChatContent();
        List<Message> sendMessageList= new ArrayList<>(List.of(systemMessage,messageFirst));
        chatHistory.addAll(sendMessageList);

        chatContent.setMessages(sendMessageList);
        chatContent.setModel(model);

        Gpt4lllTextArea textArea= project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
        if (textArea != null) {
            textArea.appendContent("analyzing,pls wait a moment./分析中，请稍等");
        }

        AtomicReference<String> secondResponse= new AtomicReference<>("");

        Thread chating = new Thread(() -> {
            String firstAnswer = chat(chatContent, project, false, false, "");
            if (textArea != null) {
                textArea.clearShowWindow();
            }
            Message replyFirst=new Message();
            replyFirst.setRole("assistant");
            replyFirst.setContent(firstAnswer);
            sendMessageList.add(replyFirst);
            Message askSecond=new Message();
            askSecond.setRole("user");
            askSecond.setContent("请根据这些测试用例，编写成可用的单元测试代码，要求使用"+fileType+",确保代码可以执行,所以要包含一个可以执行的代码的所有部分,其文件名为"+fileName);
            if ("java".equalsIgnoreCase(fileType.getName())){
                askSecond.setContent(askSecond.getContent()+",使用junit测试框架。返回的代码要在md的代码块中");
            }
            sendMessageList.add(askSecond);
            chatContent.setMessages(sendMessageList);
            secondResponse.set(chat(chatContent, project, false, true, ""));






            if (packageDirectory == null) {
                // 将packageName转换为路径格式，并拆分为子目录
                WriteCommandAction.runWriteCommandAction(e.getProject(), () -> {

                    if (psiDirectory != null) {
                        //创建文件完成，开始写入内容。
                        String[] contentList = secondResponse.get().split("```");
                        if (contentList.length < 3) {
                            Messages.showMessageDialog(project, "gpt连接出问题，请重试/Please retry as the GPT connection is experiencing issues.", "help", Messages.getInformationIcon());
                            return;
                        }
                        String fileContent = contentList[contentList.length - 2];
                        PsiFile newFile;
                        newFile = PsiFileFactory.getInstance(project).createFileFromText(testFileName, fileType, "\n"+fileContent);
                        psiDirectory.add(newFile);
                    }

                    // 调用其他方法对文件进行进一步处理
                });
            }

        });
        chating.start();


    }


    private VirtualFile getTestFile(){

        return null;
    }


    private void appendNewLineToFile(VirtualFile virtualFile) throws IOException {
        if (virtualFile != null) {
            virtualFile.refresh(false, false);
            String content = new String(virtualFile.contentsToByteArray()) + "\n";
            virtualFile.setBinaryContent(content.getBytes());
        }
    }

    private SourceFolder findNearestTestFolder(AnActionEvent e, List<SourceFolder> testSourceFolderList) {
        VirtualFile currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        SourceFolder nearestSourceFolder = null;
        int minDepth = Integer.MAX_VALUE;
        if (currentFile != null) {
            // 获取当前文件的路径
            Path currentFilePath = Paths.get(currentFile.getPath());
            for (SourceFolder sourceFolder : testSourceFolderList) {
                VirtualFile testRoot = sourceFolder.getFile();
                if (testRoot != null) {
                    // 获取测试源文件夹的路径
                    Path testRootPath = Paths.get(testRoot.getPath());
                    // 计算当前文件与测试源文件夹的相对深度
                    int depth = currentFilePath.relativize(testRootPath).getNameCount();
                    if (depth < minDepth) {
                        minDepth = depth;
                        nearestSourceFolder = sourceFolder;
                    }
                }
            }
        }

        // 如果找到了最近的sourceFolder
        if (nearestSourceFolder != null) {
            // 打印最近的测试源文件夹路径
            System.out.println("Nearest Test Source Folder: " + nearestSourceFolder.getUrl());
            return nearestSourceFolder;
        }
        return null;
    }

    public static String addTimestampToFileName(String fileName) {
        // 获取当前时间戳
        long timestamp = System.currentTimeMillis();
        // 找到最后一个点的位置（用于分隔文件名和扩展名）
        int dotIndex = fileName.lastIndexOf('.');
        // 分割文件名和扩展名
        String name = fileName.substring(0, dotIndex);
        String extension = fileName.substring(dotIndex);
        // 创建并返回新的文件名
        return name + "-" + timestamp + extension;
    }
}