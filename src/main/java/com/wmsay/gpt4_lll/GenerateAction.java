package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.wmsay.gpt4_lll.component.AgentChatView;
import com.wmsay.gpt4_lll.fc.FunctionCallOrchestrator;
import com.wmsay.gpt4_lll.fc.model.*;
import com.wmsay.gpt4_lll.languages.FileAnalysisManager;
import com.wmsay.gpt4_lll.llm.LlmClient;
import com.wmsay.gpt4_lll.llm.LlmRequest;
import com.wmsay.gpt4_lll.llm.LlmStreamCallback;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapter;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapterRegistry;
import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY;
import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC;
import static com.wmsay.gpt4_lll.utils.ChatUtils.getModelName;

public class GenerateAction extends AnAction {
    public static HashMap<String, String> languageMap = new HashMap<>();
    static {
        languageMap.put("ab", "Abkhazian");
        languageMap.put("aa", "Afar");
        languageMap.put("af", "Afrikaans");
        languageMap.put("ak", "Akan");
        languageMap.put("sq", "Albanian");
        languageMap.put("am", "Amharic");
        languageMap.put("ar", "Arabic");
        languageMap.put("an", "Aragonese");
        languageMap.put("hy", "Armenian");
        languageMap.put("as", "Assamese");
        languageMap.put("av", "Avaric");
        languageMap.put("ae", "Avestan");
        languageMap.put("ay", "Aymara");
        languageMap.put("az", "Azerbaijani");
        languageMap.put("bm", "Bambara");
        languageMap.put("ba", "Bashkir");
        languageMap.put("eu", "Basque");
        languageMap.put("be", "Belarusian");
        languageMap.put("bn", "Bengali");
        languageMap.put("bi", "Bislama");
        languageMap.put("bs", "Bosnian");
        languageMap.put("br", "Breton");
        languageMap.put("bg", "Bulgarian");
        languageMap.put("my", "Burmese");
        languageMap.put("ca", "Catalan, Valencian");
        languageMap.put("ch", "Chamorro");
        languageMap.put("ce", "Chechen");
        languageMap.put("ny", "Chichewa, Chewa, Nyanja");
        languageMap.put("zh", "Chinese");
        languageMap.put("cu", "Church Slavonic, Old Slavonic,Old Church Slavonic");
        languageMap.put("cv", "Chuvash");
        languageMap.put("kw", "Cornish");
        languageMap.put("co", "Corsican");
        languageMap.put("cr", "Cree");
        languageMap.put("hr", "Croatian");
        languageMap.put("cs", "Czech");
        languageMap.put("da", "Danish");
        languageMap.put("dv", "Divehi, Dhivehi, Maldivian");
        languageMap.put("nl", "Dutch,Flemish");
        languageMap.put("dz", "Dzongkha");
        languageMap.put("en", "English");
        languageMap.put("eo", "Esperanto");
        languageMap.put("et", "Estonian");
        languageMap.put("ee", "Ewe");
        languageMap.put("fo", "Faroese");
        languageMap.put("fj", "Fijian");
        languageMap.put("fi", "Finnish");
        languageMap.put("fr", "French");
        languageMap.put("fy", "Western Frisian");
        languageMap.put("ff", "Fulah");
        languageMap.put("gd", "Gaelic, Scottish Gaelic");
        languageMap.put("gl", "Galician");
        languageMap.put("lg", "Ganda");
        languageMap.put("ka", "Georgian");
        languageMap.put("de", "German");
        languageMap.put("el", "Greek, Modern (1453–)");
        languageMap.put("kl", "Kalaallisut, Greenlandic");
        languageMap.put("gn", "Guarani");
        languageMap.put("gu", "Gujarati");
        languageMap.put("ht", "Haitian, Haitian Creole");
        languageMap.put("ha", "Hausa");
        languageMap.put("he", "Hebrew");
        languageMap.put("hz", "Herero");
        languageMap.put("hi", "Hindi");
        languageMap.put("ho", "Hiri Motu");
        languageMap.put("hu", "Hungarian");
        languageMap.put("is", "Icelandic");
        languageMap.put("io", "Ido");
        languageMap.put("ig", "Igbo");
        languageMap.put("id", "Indonesian");
        languageMap.put("ia", "Interlingua(International Auxiliary Language Association)");
        languageMap.put("ie", "Interlingue, Occidental");
        languageMap.put("iu", "Inuktitut");
        languageMap.put("ik", "Inupiaq");
        languageMap.put("ga", "Irish");
        languageMap.put("it", "Italian");
        languageMap.put("ja", "Japanese");
        languageMap.put("jv", "Javanese");
        languageMap.put("kn", "Kannada");
        languageMap.put("kr", "Kanuri");
        languageMap.put("ks", "Kashmiri");
        languageMap.put("kk", "Kazakh");
        languageMap.put("km", "Central Khmer");
        languageMap.put("ki", "Kikuyu, Gikuyu");
        languageMap.put("rw", "Kinyarwanda");
        languageMap.put("ky", "Kirghiz, Kyrgyz");
        languageMap.put("kv", "Komi");
        languageMap.put("kg", "Kongo");
        languageMap.put("ko", "Korean");
        languageMap.put("kj", "Kuanyama, Kwanyama");
        languageMap.put("ku", "Kurdish");
        languageMap.put("lo", "Lao");
        languageMap.put("la", "Latin");
        languageMap.put("lv", "Latvian");
        languageMap.put("li", "Limburgan, Limburger, Limburgish");
        languageMap.put("ln", "Lingala");
        languageMap.put("lt", "Lithuanian");
        languageMap.put("lu", "Luba-Katanga");
        languageMap.put("lb", "Luxembourgish, Letzeburgesch");
        languageMap.put("mk", "Macedonian");
        languageMap.put("mg", "Malagasy");
        languageMap.put("ms", "Malay");
        languageMap.put("ml", "Malayalam");
        languageMap.put("mt", "Maltese");
        languageMap.put("gv", "Manx");
        languageMap.put("mi", "Maori");
        languageMap.put("mr", "Marathi");
        languageMap.put("mh", "Marshallese");
        languageMap.put("mn", "Mongolian");
        languageMap.put("na", "Nauru");
        languageMap.put("nv", "Navajo, Navaho");
        languageMap.put("nd", "North Ndebele");
        languageMap.put("nr", "South Ndebele");
        languageMap.put("ng", "Ndonga");
        languageMap.put("ne", "Nepali");
        languageMap.put("no", "Norwegian");
        languageMap.put("nb", "Norwegian Bokmål");
        languageMap.put("nn", "Norwegian Nynorsk");
        languageMap.put("ii", "Sichuan Yi, Nuosu");
        languageMap.put("oc", "Occitan");
        languageMap.put("oj", "Ojibwa");
        languageMap.put("or", "Oriya");
        languageMap.put("om", "Oromo");
        languageMap.put("os", "Ossetian, Ossetic");
        languageMap.put("pi", "Pali");
        languageMap.put("ps", "Pashto, Pushto");
        languageMap.put("fa", "Persian");
        languageMap.put("pl", "Polish");
        languageMap.put("pt", "Portuguese");
        languageMap.put("pa", "Punjabi, Panjabi");
        languageMap.put("qu", "Quechua");
        languageMap.put("ro", "Romanian,Moldavian, Moldovan");
        languageMap.put("rm", "Romansh");
        languageMap.put("rn", "Rundi");
        languageMap.put("ru", "Russian");
        languageMap.put("se", "Northern Sami");
        languageMap.put("sm", "Samoan");
        languageMap.put("sg", "Sango");
        languageMap.put("sa", "Sanskrit");
        languageMap.put("sc", "Sardinian");
        languageMap.put("sr", "Serbian");
        languageMap.put("sn", "Shona");
        languageMap.put("sd", "Sindhi");
        languageMap.put("si", "Sinhala, Sinhalese");
        languageMap.put("sk", "Slovak");
        languageMap.put("sl", "Slovenian");
        languageMap.put("so", "Somali");
        languageMap.put("st", "Southern Sotho");
        languageMap.put("es", "Spanish, Castilian");
        languageMap.put("su", "Sundanese");
        languageMap.put("sw", "Swahili");
        languageMap.put("ss", "Swati");
        languageMap.put("sv", "Swedish");
        languageMap.put("tl", "Tagalog");
        languageMap.put("ty", "Tahitian");
        languageMap.put("tg", "Tajik");
        languageMap.put("ta", "Tamil");
        languageMap.put("tt", "Tatar");
        languageMap.put("te", "Telugu");
        languageMap.put("th", "Thai");
        languageMap.put("bo", "Tibetan");
        languageMap.put("ti", "Tigrinya");
        languageMap.put("to", "Tonga(Tonga Islands)");
        languageMap.put("ts", "Tsonga");
        languageMap.put("tn", "Tswana");
        languageMap.put("tr", "Turkish");
        languageMap.put("tk", "Turkmen");
        languageMap.put("tw", "Twi");
        languageMap.put("ug", "Uighur, Uyghur");
        languageMap.put("uk", "Ukrainian");
        languageMap.put("ur", "Urdu");
        languageMap.put("uz", "Uzbek");
        languageMap.put("ve", "Venda");
        languageMap.put("vi", "Vietnamese");
        languageMap.put("vo", "Volapük");
        languageMap.put("wa", "Walloon");
        languageMap.put("cy", "Welsh");
        languageMap.put("wo", "Wolof");
        languageMap.put("xh", "Xhosa");
        languageMap.put("yi", "Yiddish");
        languageMap.put("yo", "Yoruba");
        languageMap.put("za", "Zhuang, Chuang");
        languageMap.put("zu", "Zulu");
    }

    public static String TODO_PROMPT= """
            根据以下代码中的todo注释，请实现注释中描述的每个功能。请遵循以下要求：

            - 编程语言：{FILE_TYPE}
            - 回复语言：{REPLY_LANGUAGE}
            - 返回的代码应放在Markdown格式的代码块中，且仅有一个代码块。
            - TODO后描述的每个功能点必须完整实现，且符合所需的逻辑和语法。
            - 仅实现与todo注释相关的功能，不要添加额外逻辑或修改其他部分。
            - 遵守{FILE_TYPE}编程的最佳实践和编码规范。
            - 提供必要的清晰的说明或提示。
            - 保持代码上下文不变，只修改或添加todo相关的代码。

            请按以上要求严格补全代码。需要补全实现的代码如下：
            ```
            {SELECTED_TEXT}
            ```
            """;

    //单机应用就是好，没有并发就是爽
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void actionPerformed(AnActionEvent e) {
        if (e.getProject()==null){
            Messages.showMessageDialog(e.getProject(), "不是一个项目/no project here", "Error", Messages.getErrorIcon());
            return;
        }
       if(Boolean.TRUE.equals(CommonUtil.isRunningStatus(e.getProject()))){
           Messages.showMessageDialog(e.getProject(), "Please wait, another task is running", "Error", Messages.getErrorIcon());
           return;
       }else {
           CommonUtil.startRunningStatus(e.getProject());
       }

        List<Message> chatHistory = e.getProject().getUserData(GPT_4_LLL_CONVERSATION_HISTORY);
        String nowTopic = e.getProject().getUserData(GPT_4_LLL_NOW_TOPIC);
        if (chatHistory != null && !chatHistory.isEmpty() && nowTopic!=null && !nowTopic.isEmpty()) {
            JsonStorage.saveConservation(nowTopic, chatHistory);
            chatHistory.clear();
        }
        nowTopic = "";
        e.getProject().putUserData(GPT_4_LLL_NOW_TOPIC,nowTopic);
        if (chatHistory==null){
            e.getProject().putUserData(GPT_4_LLL_CONVERSATION_HISTORY,new ArrayList<>());
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
                int tryTimes=0;
                while (!toolWindow.isVisible()&&tryTimes<3) {
                    tryTimes++;
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        String model = getModelName(e.getProject());
        String replyLanguage = getSystemLanguage();
        Project project = e.getProject();
        if (project != null) {
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor==null){
                Messages.showMessageDialog(project, "Editor is not open. Please open the file that you want to do something", "Error", Messages.getErrorIcon());
                CommonUtil.stopRunningStatus(project);
                return;
            }
            String fileType = getOpenFileType(project);
            SelectionModel selectionModel = editor.getSelectionModel();
            String selectedText = selectionModel.getSelectedText();
            if (selectedText == null || selectedText.isEmpty()) {
                Messages.showMessageDialog(project, "No text selected. Please select the code you want to do something", "Error", Messages.getErrorIcon());
                CommonUtil.stopRunningStatus(project);
                return;
            }
            Message systemMessage = new Message();
            ProviderAdapter adapter = ProviderAdapterRegistry.getAdapter(ModelUtils.getSelectedProvider(project));
            systemMessage.setRole(adapter.getSystemMessageRole());
            systemMessage.setContent("你是一个有用的助手，同时也是一个计算机科学家，数据专家，有着多年的代码开发和重构经验和多年的代码优化的架构师");

            Message message = new Message();
            List<Message> moreMessageList = new ArrayList<>();
            Boolean coding = false;
            if (selectedText != null) {
                selectedText = selectedText.trim();
                LocalDateTime now = LocalDateTime.now();
                nowTopic = formatter.format(now);
                if (isSelectedTextAllComments(project)) {
                    nowTopic = nowTopic + "--Generate:" + selectedText;
                    project.putUserData(GPT_4_LLL_NOW_TOPIC,nowTopic);
                    coding = true;
                    message.setRole("user");
                    message.setName("owner");
                    message.setContent("请帮我完成下面的功能，同时使用" + fileType + "，注释语言请使用" + replyLanguage + "的语言,代码部分要包含代码和注释，所有的返回代码应该在代码块中,请使用" + replyLanguage + "回复我，功能如下：" + selectedText);
                } else if (StringUtils.isNotEmpty(getCommentTODO(project, editor))) {
                    nowTopic = nowTopic + "--Complete:" + selectedText;
                    project.putUserData(GPT_4_LLL_NOW_TOPIC,nowTopic);
                    coding = true;
                    message.setRole("user");
                    message.setName("owner");
                    String prompt=TODO_PROMPT.replace("{FILE_TYPE}", fileType)
                            .replace("{REPLY_LANGUAGE}", replyLanguage)
                            .replace("{SELECTED_TEXT}", selectedText);
//                    message.setContent("todo后的文字是需要完成的功能，请帮我实现这些描述的功能，同时使用" + fileType + "。代码要严格按照描述，实现所有todo后的功能，所有的返回代码应该在一个Markdown的代码块中，非todo后的描述的需求不要出现在代码块中，请使用" + replyLanguage + "回复我，需要实现的代码如下：" + selectedText);
                    message.setContent(prompt);
                    if ("java".equalsIgnoreCase(fileType)) {
                        FileAnalysisManager analysisManager= ApplicationManager.getApplication().getService(FileAnalysisManager.class);
                        List<Message> messageList = analysisManager.analyzeFile(project, editor);
                        if (!messageList.isEmpty()) {
                            moreMessageList.addAll(messageList);
                        }
                        // 通过PsiDocumentManager获取当前编辑器的PsiFile对象信息 提供给gpt
                        List<Message> currentFileMessages = analysisManager.analyzeCurrentFile(project, editor);
                        moreMessageList.addAll(currentFileMessages);
                    }
                } else {
                    nowTopic = nowTopic + "--Optimize" + selectedText;
                    project.putUserData(GPT_4_LLL_NOW_TOPIC,nowTopic);
                    coding = false;
                    message.setRole("user");
                    message.setName("owner");
                    message.setContent("请帮我重构下面的代码，不局限于代码性能优化、命名优化、增加注释、简化代码、优化逻辑，请使用" + replyLanguage + "回复我，代码如下：" + selectedText);
                    if ("java".equalsIgnoreCase(fileType)) {
                        FileAnalysisManager analysisManager= ApplicationManager.getApplication().getService(FileAnalysisManager.class);
                        List<Message> messageList = analysisManager.analyzeFile(project, editor);

                        if (!messageList.isEmpty()) {
                            moreMessageList.addAll(messageList);
                        }
                        // 通过PsiDocumentManager获取当前编辑器的PsiFile对象信息 提供给gpt
                        List<Message> currentFileMessages = analysisManager.analyzeCurrentFile(project, editor);
                        moreMessageList.addAll(currentFileMessages);
                    }
                }
                ChatContent chatContent = new ChatContent();
                List<Message> sendMessageList = new ArrayList<>(List.of(systemMessage, message));
                if (!moreMessageList.isEmpty()) {
                    sendMessageList.addAll(1, moreMessageList);
                    chatContent.setMessages(sendMessageList,ModelUtils.getSelectedProvider(project));
                }
                chatContent.setMessages(sendMessageList,ModelUtils.getSelectedProvider(project));
                chatContent.setModel(model);
                if (project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY)==null){
                    project.putUserData(GPT_4_LLL_CONVERSATION_HISTORY,new ArrayList<>());
                }
                project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY).addAll(List.of(systemMessage, message));
                Boolean finalCoding = coding;
                //清理界面
                AgentChatView textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
                if (textArea != null) {
                    textArea.clearShowWindow();
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        chat(chatContent, project, finalCoding, true, "");
                    }
                }).start();

            }
        }
        // TODO: insert action logic here
    }

    public static String chat(ChatContent content, Project project, Boolean coding, Boolean replyShowInWindow, String loadingNotice) {
        return chat(content, project, coding, replyShowInWindow, loadingNotice, 0, null);
    }

    public static String chat(ChatContent content, Project project, Boolean coding, Boolean replyShowInWindow, String loadingNotice, Integer retryTime) {
        return chat(content, project, coding, replyShowInWindow, loadingNotice, retryTime, null);
    }

    public static String chat(ChatContent content, Project project, Boolean coding, Boolean replyShowInWindow, String loadingNotice, Integer retryTime, Document commitDoc) {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        String url = ChatUtils.getUrl(settings,project);
        if (url == null || url.isBlank()) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, "Input the correct api url/请输入正确api地址。", "GPT4_LLL", Messages.getInformationIcon()));
            CommonUtil.stopRunningStatus(project);
            return "";
        }
        String apiKey = ChatUtils.getApiKey(settings,project);
        if (apiKey==null ||apiKey.isBlank()){
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, "Input the correct apikey/请输入正确apikey。", "GPT4_LLL", Messages.getInformationIcon()));
            CommonUtil.stopRunningStatus(project);
            return "";
        }
        String proxy = settings.getProxyAddress();
        String currentProvider = ModelUtils.getSelectedProvider(project);

        LlmRequest llmRequest = LlmRequest.builder()
                .url(url)
                .chatContent(content)
                .apiKey(apiKey)
                .proxy(proxy)
                .provider(currentProvider)
                .build();

        AgentChatView textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
        if (Boolean.FALSE.equals(replyShowInWindow) && StringUtils.isEmpty(loadingNotice)) {
            loadingNotice = "正在进行多层问题分析...\nConducting multi-step problem analysis...";
        }
        if (Boolean.FALSE.equals(replyShowInWindow)) {
            textArea.appendContent(loadingNotice);
        }

        GenerateChatCallback chatCallback = new GenerateChatCallback(
                project, textArea, replyShowInWindow, coding, commitDoc);

        try {
            LlmClient.streamChat(llmRequest, chatCallback);
        } catch (ProcessCanceledException exception){
            throw exception;
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, e.getMessage(), "ChatGpt", Messages.getInformationIcon()));
            e.printStackTrace();
        } finally {
            CommonUtil.stopRunningStatus(project);
        }

        String replyContent = chatCallback.getCollectedContent();
        
        // 最终更新commit消息框（如果提供了commitDoc）
        if (commitDoc != null) {
            String finalCommitMessage = extractCommitMessageFromResponse(replyContent);
            if (!finalCommitMessage.isEmpty()) {
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    commitDoc.setText(finalCommitMessage);
                });
            }
        }
        
        Message message = new Message();
        message.setRole("assistant");
        message.setContent(replyContent);
        message.setThinkingContent(chatCallback.getCollectedThinkingContent());
        project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY).add(message);
        JsonStorage.saveConservation(project.getUserData(GPT_4_LLL_NOW_TOPIC), project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY));
        if (chatCallback.isNotExpected()) {
            SwingUtilities.invokeLater(
                    () -> Messages.showMessageDialog(project, replyContent, "ChatGpt", Messages.getInformationIcon())
            );
           CommonUtil.stopRunningStatus(project);
        }

        
        ProviderAdapter continuationAdapter = ProviderAdapterRegistry.getAdapter(currentProvider);
        if (continuationAdapter.supportsContinuationRetry()) {
            //判断是否需要继续未完成的内容
            if (ChatUtils.needsContinuation(replyContent) && retryTime < 2) {
                project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY).add(continuationAdapter.getContinuationMessage());
                content.setMessages(project.getUserData(GPT_4_LLL_CONVERSATION_HISTORY),ModelUtils.getSelectedProvider(project));
                chat(content, project, coding, replyShowInWindow, loadingNotice, retryTime + 1, commitDoc);
            }
        }
        return replyContent;
    }

    // ==================== Function Calling 集成 ====================

    /**
     * 检查指定项目是否启用了 function calling。
     * 通过 WindowTool 实例获取 FC 框架状态。
     *
     * @param project 当前项目
     * @return true 如果 FC 已初始化且启用
     */
    public static boolean isFunctionCallingEnabled(Project project) {
        if (project == null) return false;
        WindowTool windowTool = WindowTool.getInstance(project);
        if (windowTool == null) return false;
        FunctionCallOrchestrator orchestrator = windowTool.getFunctionCallOrchestrator();
        FunctionCallConfig config = windowTool.getFunctionCallConfig();
        return orchestrator != null && config != null && config.isEnableFunctionCalling();
    }

    /**
     * 使用 function calling 执行对话。
     * 通过 WindowTool 的 FunctionCallOrchestrator 执行带工具调用的对话流程。
     * 如果 FC 不可用或执行失败，返回 null 表示应回退到传统 chat 流程。
     *
     * @param content 对话内容
     * @param project 当前项目
     * @return FC 结果，或 null 表示应回退到传统流程
     */
    public static FunctionCallResult chatWithFunctionCalling(ChatContent content, Project project) {
        WindowTool windowTool = WindowTool.getInstance(project);
        if (windowTool == null) return null;

        FunctionCallOrchestrator orchestrator = windowTool.getFunctionCallOrchestrator();
        FunctionCallConfig config = windowTool.getFunctionCallConfig();
        if (orchestrator == null || config == null) return null;

        try {
            FunctionCallRequest request = FunctionCallRequest.builder()
                    .chatContent(content)
                    .availableTools(McpToolRegistry.getAllTools())
                    .maxRounds(config.getMaxRounds())
                    .config(config)
                    .build();

            McpContext context = McpContext.fromIdeState(project, null);

            FunctionCallOrchestrator.LlmCaller llmCaller = (req) -> {
                MyPluginSettings settings = MyPluginSettings.getInstance();
                String url = ChatUtils.getUrl(settings, project);
                String apiKey = ChatUtils.getApiKey(settings, project);
                String proxy = settings.getProxyAddress();
                String currentProvider = ModelUtils.getSelectedProvider(project);

                LlmRequest llmRequest = LlmRequest.builder()
                        .url(url)
                        .chatContent(req.getChatContent())
                        .apiKey(apiKey)
                        .proxy(proxy)
                        .provider(currentProvider)
                        .build();

                return LlmClient.syncChatRaw(llmRequest);
            };

            // 设置流式 LlmCaller（reasoning/content 实时展示）
            orchestrator.setStreamingLlmCaller((req, displayCb) -> {
                MyPluginSettings settings = MyPluginSettings.getInstance();
                String url = ChatUtils.getUrl(settings, project);
                String apiKey = ChatUtils.getApiKey(settings, project);
                String proxy = settings.getProxyAddress();
                String currentProvider = ModelUtils.getSelectedProvider(project);

                LlmRequest llmRequest = LlmRequest.builder()
                        .url(url)
                        .chatContent(req.getChatContent())
                        .apiKey(apiKey)
                        .proxy(proxy)
                        .provider(currentProvider)
                        .build();

                com.wmsay.gpt4_lll.llm.StreamingFcCollector collector =
                        new com.wmsay.gpt4_lll.llm.StreamingFcCollector(displayCb);
                LlmClient.streamChat(llmRequest, collector);
                return collector.reconstructResponse();
            });

            // 创建进度回调，实时更新 UI
            AgentChatView textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
            FunctionCallOrchestrator.ProgressCallback progressCallback =
                    createFcProgressCallback(textArea);

            FunctionCallResult result = orchestrator.execute(
                    request, context, llmCaller, progressCallback);

            return result;
        } catch (Exception e) {
            System.err.println("[FC] Function calling failed in GenerateAction: " + e.getMessage());
            return null;
        }
    }

    /**
     * 创建 FC 进度回调，实时在 AgentChatView 中显示工具调用状态。
     */
    private static FunctionCallOrchestrator.ProgressCallback createFcProgressCallback(
            AgentChatView area) {
        if (area == null) {
            return new FunctionCallOrchestrator.ProgressCallback() {};
        }
        return new FunctionCallOrchestrator.ProgressCallback() {
            private final java.util.concurrent.atomic.AtomicReference<com.wmsay.gpt4_lll.component.block.ToolUseBlock>
                    currentToolBlock = new java.util.concurrent.atomic.AtomicReference<>();

            @Override
            public void onLlmCallStarting(int round) {
            }

            // ---- 非流式路径回调 ----
            @Override
            public void onReasoningContent(int round, String reasoningContent) {
                SwingUtilities.invokeLater(() -> {
                    area.appendThingkingTitle();
                    area.appendContent(reasoningContent);
                    area.appendThingkingEnd();
                });
            }

            @Override
            public void onTextContent(int round, String content) {
                SwingUtilities.invokeLater(() -> area.appendContent(content));
            }

            // ---- 流式路径回调：reasoning/content 实时增量展示 ----
            @Override
            public void onReasoningStarted(int round) {
                SwingUtilities.invokeLater(() -> area.appendThingkingTitle());
            }

            @Override
            public void onReasoningDelta(int round, String delta) {
                SwingUtilities.invokeLater(() -> area.appendContent(delta));
            }

            @Override
            public void onReasoningComplete(int round) {
                SwingUtilities.invokeLater(() -> area.appendThingkingEnd());
            }

            @Override
            public void onTextDelta(int round, String delta) {
                SwingUtilities.invokeLater(() -> area.appendContent(delta));
            }

            @Override
            public void onLlmCallCompleted(int round, int toolCallCount) {
            }

            @Override
            public void onToolExecutionStarting(String toolName, java.util.Map<String, Object> params) {
                SwingUtilities.invokeLater(() -> {
                    var block = area.addToolUseBlock(toolName, params);
                    currentToolBlock.set(block);
                });
            }

            @Override
            public void onToolExecutionCompleted(ToolCallResult result) {
                SwingUtilities.invokeLater(() -> {
                    var block = currentToolBlock.getAndSet(null);
                    if (block != null) {
                        block.markCompleted(result.isSuccess(), result.getDurationMs());
                    }
                    String toolName = result.getToolName();
                    if (result.isSuccess()) {
                        String resultText = result.getResult() != null
                                ? result.getResult().getDisplayText() : "(no output)";
                        if (resultText == null || resultText.isEmpty()) {
                            resultText = "(no output)";
                        }
                        area.addToolResultBlock(toolName, resultText);
                    } else {
                        String errorText = result.getError() != null
                                ? result.getError().getMessage()
                                + (result.getError().getSuggestion() != null
                                    ? "\n" + result.getError().getSuggestion() : "")
                                : "Unknown error";
                        area.addToolResultBlock(toolName + " (ERROR)", errorText);
                    }
                });
            }
        };
    }

    /**
     * 在 AgentChatView 中显示 FC 工具调用历史。
     */
    // displayFcToolCallHistory removed — replaced by real-time ProgressCallback

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





    /**
     * 检查是否都是注释
     *
     * @param project
     * @return java.lang.Boolean
     * @author liuchuan
     * @date 2023/6/8 2:06 PM
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

    public String getSystemLanguage() {
        Locale locale = Locale.getDefault();
        String languageCode = locale.getLanguage();
        String language = languageMap.get(languageCode);
        if (language == null) {
            return "中文";
        }
        return language;
    }



    public String getCommentTODO(Project project, Editor editor) {
        SelectionModel selectionModel = editor.getSelectionModel();
        int start = selectionModel.getSelectionStart();
        int end = selectionModel.getSelectionEnd();

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) return null;

        PsiElement startElement = psiFile.findElementAt(start);
        PsiElement endElement = psiFile.findElementAt(end);

        if (startElement == null || endElement == null) return null;

        PsiElement[] comments = PsiTreeUtil.collectElements(psiFile, el ->
                (el instanceof PsiComment && el.getTextRange().getStartOffset() >= start && el.getTextRange().getEndOffset() <= end)
        );

        Pattern todoPattern = Pattern.compile("TODO:?(.*)", Pattern.CASE_INSENSITIVE);

        for (PsiElement comment : comments) {
            Matcher matcher = todoPattern.matcher(comment.getText());
            if (matcher.find()) {
                String todoContent = matcher.group(1).trim();
                if (!todoContent.isEmpty()) {
                    return todoContent;
                }
            } else {
                continue;
            }
        }
        return null;
    }

    /**
     * 从AI响应中提取commit消息
     */
    private static String extractCommitMessageFromResponse(String response) {
        // 查找markdown代码块
        int startIndex = response.indexOf("```");
        if (startIndex != -1) {
            int endIndex = response.indexOf("```", startIndex + 3);
            if (endIndex != -1) {
                return response.substring(startIndex + 3, endIndex).trim();
            }
        }
        
        // 如果没有找到代码块，返回整个响应
        return response.trim();
    }

    /**
     * GenerateAction.chat() 的流式回调实现。
     * 将原匿名回调提取为命名类，封装编辑器写入状态机和 UI 展示逻辑。
     * <p>
     * 状态机逻辑（coding=true 时的编辑器实时写入）保持原有行为不变，
     * 仅从匿名代码块提取为命名类以提升可读性和可测试性。
     *
     * @see GenerateAction#chat(ChatContent, Project, Boolean, Boolean, String, Integer, Document)
     */
    static class GenerateChatCallback implements LlmStreamCallback {
        // ---- 外部依赖（构造时注入） ----
        private final Project project;
        private final AgentChatView textArea;
        private final Boolean replyShowInWindow;
        private final Boolean coding;
        private final Document commitDoc;

        // ---- 流式处理状态 ----
        private final StringBuilder stringBuffer = new StringBuilder();
        private final StringBuilder thinkingBuffer = new StringBuilder();
        private final AtomicBoolean notExpected = new AtomicBoolean(true);
        private final AtomicBoolean firstThinkingAnswer = new AtomicBoolean(false);
        private final AtomicBoolean startReasonedAnswer = new AtomicBoolean(false);
        private final AtomicBoolean isWriting = new AtomicBoolean(false);
        private final AtomicInteger lastInsertPosition = new AtomicInteger(-1);
        private final AtomicInteger countDot = new AtomicInteger(0);
        private final AtomicInteger commitUpdateCounter = new AtomicInteger(0);
        private final AtomicReference<String> preEndString = new AtomicReference<>("");

        GenerateChatCallback(Project project, AgentChatView textArea,
                             Boolean replyShowInWindow, Boolean coding, Document commitDoc) {
            this.project = project;
            this.textArea = textArea;
            this.replyShowInWindow = replyShowInWindow;
            this.coding = coding;
            this.commitDoc = commitDoc;
        }

        /** 获取已收集的完整回复内容（含 data 行内容和非 data 行文本）。 */
        public String getCollectedContent() {
            return stringBuffer.toString();
        }

        /** 获取已收集的思考过程内容（reasoning_content），为空时返回 null。 */
        public String getCollectedThinkingContent() {
            return thinkingBuffer.length() > 0 ? thinkingBuffer.toString() : null;
        }

        /** 是否未收到任何 data 行（即响应不符合预期的 SSE 格式）。 */
        public boolean isNotExpected() {
            return notExpected.get();
        }

        @Override
        public void onDataLineReceived() {
            notExpected.set(false);
        }

        @Override
        public void onNonDataLine(String line) {
            stringBuffer.append(line);
        }

        @Override
        public void onReasoningContent(String reasoningDelta) {
            thinkingBuffer.append(reasoningDelta);
            if (!firstThinkingAnswer.get()) {
                firstThinkingAnswer.set(true);
                if (textArea != null) {
                    textArea.appendThingkingTitle();
                }
            }
            if (textArea != null) {
                textArea.appendContent(reasoningDelta);
            }
        }

        @Override
        public void onContent(String resContent) {
            // ---- 思考→正式回复的过渡 ----
            if (firstThinkingAnswer.get() && !startReasonedAnswer.get()) {
                startReasonedAnswer.set(true);
                if (textArea != null) {
                    textArea.appendThingkingEnd();
                }
            }

            // ---- ToolWindow 展示 ----
            if (textArea != null) {
                if (Boolean.TRUE.equals(replyShowInWindow)) {
                    textArea.appendContent(resContent);
                } else {
                    if (countDot.get() % 4 == 0 && countDot.get() != 0) {
                        textArea.setText(textArea.getText().substring(0, textArea.getText().length() - 3));
                    } else {
                        textArea.appendContent(".");
                    }
                }
            }

            // ---- 收集内容 ----
            stringBuffer.append(resContent);

            // ---- 实时更新 commit 消息框 ----
            if (commitDoc != null) {
                if (commitUpdateCounter.incrementAndGet() % 5 == 0) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        ApplicationManager.getApplication().runWriteAction(() -> {
                            String currentCommitMessage = extractCommitMessageFromResponse(stringBuffer.toString());
                            if (!currentCommitMessage.isEmpty()) {
                                commitDoc.setText(currentCommitMessage);
                            }
                        });
                    }, ModalityState.defaultModalityState());
                }
            }

            // ---- 编辑器实时写入状态机（coding=true） ----
            if (Boolean.TRUE.equals(coding)) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                        if (editor != null) {
                            insertContentToEditor(editor, resContent);
                        }
                    });
                }, ModalityState.defaultModalityState());
            }
        }

        /**
         * 编辑器写入状态机核心逻辑。
         * 根据代码块标记（```）控制写入开始和结束。
         * ⚠️ 此方法内部逻辑与原 GenerateAction.chat() 完全一致，不做任何改动。
         */
        private void insertContentToEditor(Editor editor, String resContent) {
            Document document = editor.getDocument();
            SelectionModel selectionModel = editor.getSelectionModel();

            int insertPosition;
            if (lastInsertPosition.get() == -1) {
                if (selectionModel.hasSelection()) {
                    int selectionEnd = selectionModel.getSelectionEnd();
                    int endLine = document.getLineNumber(selectionEnd);
                    int lastLine = document.getLineCount() - 1;
                    if (endLine == lastLine) {
                        int tmpInsertPosition = document.getTextLength();
                        WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
                            document.insertString(tmpInsertPosition, "\n");
                        });
                        insertPosition = tmpInsertPosition + 1;
                    } else {
                        insertPosition = document.getLineEndOffset(endLine);
                    }
                } else {
                    insertPosition = document.getTextLength();
                }
            } else {
                insertPosition = lastInsertPosition.get();
            }

            if (isWriting.get() && resContent.endsWith("`") && !resContent.startsWith("`")) {
                preEndString.set(resContent);
                System.out.println(preEndString.get());
            }

            if (isWriting.get() && stringBuffer.indexOf("```") != stringBuffer.lastIndexOf("```")) {
                isWriting.set(false);
                if (resContent.contains("```")) {
                    String textToInsert;
                    String[] parts = resContent.split("```");
                    if (parts.length > 0) {
                        textToInsert = parts[0];
                    } else {
                        textToInsert = null;
                    }
                    if (!StringUtils.isEmpty(textToInsert)) {
                        WriteCommandAction.runWriteCommandAction(project, () ->
                                document.insertString(insertPosition, textToInsert));
                        lastInsertPosition.set(insertPosition + textToInsert.length());
                    }
                } else {
                    String textToInsert = preEndString.get().replace("`", "");
                    WriteCommandAction.runWriteCommandAction(project, () ->
                            document.insertString(insertPosition, textToInsert));
                    lastInsertPosition.set(insertPosition + textToInsert.length());
                }
            }

            if (stringBuffer.indexOf("```") >= 0
                    && stringBuffer.indexOf("```") == stringBuffer.lastIndexOf("```")
                    && stringBuffer.lastIndexOf("\n") > stringBuffer.indexOf("```")) {
                isWriting.set(true);
                String textToInsert;
                if (resContent.contains("```") && !resContent.endsWith("```")) {
                    textToInsert = resContent.split("```")[1];
                } else {
                    textToInsert = resContent.replace("`", "");
                }
                WriteCommandAction.runWriteCommandAction(project, () ->
                        document.insertString(insertPosition, textToInsert));
                lastInsertPosition.set(insertPosition + textToInsert.length());
            }
        }
    }
}

