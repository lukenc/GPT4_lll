package com.wmsay.gpt4_lll;

import com.alibaba.fastjson.JSON;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SseResponse;
import com.wmsay.gpt4_lll.model.baidu.BaiduSseResponse;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import javax.swing.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey.*;
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
            {SELECTED_TEXT}
            """;

    //单机应用就是好，没有并发就是爽
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void actionPerformed(AnActionEvent e) {
        if (e.getProject()==null){
            Messages.showMessageDialog(e.getProject(), "不是一个项目/no project here", "Error", Messages.getErrorIcon());
            return;
        }
       if(CommonUtil.isRunningStatus(e.getProject())){
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
            if (ProviderNameEnum.BAIDU.getProviderName().equals(ModelUtils.getSelectedProvider(project))  ) {
                systemMessage.setRole("user");
            } else {
                systemMessage.setRole("system");
            }
            systemMessage.setContent("你是一个有用的助手，同时也是一个计算机科学家，数据专家，有着多年的代码开发和重构经验和多年的代码优化的架构师");

            Message message = new Message();
            List<Message> moreMessageList = new ArrayList<>();
            Boolean coding = false;
            if (selectedText != null) {
                selectedText = selectedText.trim();
                LocalDateTime now = LocalDateTime.now();
                nowTopic = formatter.format(now);
                if (Boolean.TRUE.equals(isSelectedTextAllComments(project))) {
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
                        List<Message> messageList = getClassInfoToMessageType(project, editor);
                        if (!messageList.isEmpty()) {
                            moreMessageList.addAll(messageList);
                        }
                        // 通过PsiDocumentManager获取当前编辑器的PsiFile对象信息 提供给gpt
                        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                        if (psiFile != null) {
                            int startOffset = editor.getSelectionModel().getSelectionStart();
                            int endOffset = editor.getSelectionModel().getSelectionEnd();
                            while (startOffset < endOffset && Character.isWhitespace(psiFile.getText().charAt(startOffset))) {
                                startOffset++;
                            }
                            // 获取当前选中元素的PsiElement对象
                            PsiElement selectedElement = psiFile.findElementAt(startOffset);
                            // 通过PsiTreeUtil获取当前选中元素所在的PsiClass对象
                            PsiClass containingClass = PsiTreeUtil.getParentOfType(selectedElement, PsiClass.class, true);
                            if (containingClass != null) {
                                // 将当前选中的PsiClass对象转换为Message对象
                                Message currentClassMessage = processCurentClass2Message(containingClass);
                                // 将转换后的Message对象添加到moreMessageList中
                                moreMessageList.add(currentClassMessage);
                            }
                        }
                    }
                } else {
                    nowTopic = nowTopic + "--Optimize" + selectedText;
                    project.putUserData(GPT_4_LLL_NOW_TOPIC,nowTopic);
                    coding = false;
                    message.setRole("user");
                    message.setName("owner");
                    message.setContent("请帮我重构下面的代码，不局限于代码性能优化、命名优化、增加注释、简化代码、优化逻辑，请使用" + replyLanguage + "回复我，代码如下：" + selectedText);
                    if ("java".equalsIgnoreCase(fileType)) {
                        List<Message> messageList = getClassInfoToMessageType(project, editor);
                        if (!messageList.isEmpty()) {
                            moreMessageList.addAll(messageList);
                        }
                        // 通过PsiDocumentManager获取当前编辑器的PsiFile对象信息 提供给gpt
                        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                        if (psiFile != null) {
                            // 获取当前选中元素的PsiElement对象
                            PsiElement selectedElement = psiFile.findElementAt(editor.getSelectionModel().getSelectionStart());
                            // 通过PsiTreeUtil获取当前选中元素所在的PsiClass对象
                            PsiClass containingClass = PsiTreeUtil.getParentOfType(selectedElement, PsiClass.class);
                            if (containingClass != null) {
                                // 将当前选中的PsiClass对象转换为Message对象
                                Message currentClassMessage = processCurentClass2Message(containingClass);
                                // 将转换后的Message对象添加到moreMessageList中
                                moreMessageList.add(currentClassMessage);
                            }
                        }
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
                Gpt4lllTextArea textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
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
        return chat(content, project, coding, replyShowInWindow, loadingNotice, 0);
    }

    public static String chat(ChatContent content, Project project, Boolean coding, Boolean replyShowInWindow, String loadingNotice, Integer retryTime) {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        String url = ChatUtils.getUrl(content, settings);
        if (url == null || url.isBlank()) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, "Input the correct api url/请输入正确api地址。", "GPT4_LLL", Messages.getInformationIcon()));
            return "";
        }
        String apiKey = ChatUtils.getApiKey(settings);
        if (apiKey==null ||apiKey.isBlank()){
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, "Input the correct apikey/请输入正确apikey。", "GPT4_LLL", Messages.getInformationIcon()));
            return "";
        }
        String proxy = settings.getProxyAddress();

        String requestBody = JSON.toJSONString(content);

        HttpClient client = ChatUtils.buildHttpClient(proxy, project);
        HttpRequest request;
        try {
             request = ChatUtils.buildHttpRequest(url, requestBody, apiKey);
        }catch (IllegalArgumentException exception){
            if (exception.getMessage().equals("URI with undefined scheme")) {
                SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, "Input the correct api url/请输入正确api地址。", "GPT4_LLL", Messages.getInformationIcon()));
                return "";
            } else {
                SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, "Request establishment failed, please check the relevant settings and input./建立请求失败，请检查相关设置与输入", "GPT4_LLL", Messages.getInformationIcon()));
                return "";
            }
        }


        Gpt4lllTextArea textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
        if (Boolean.FALSE.equals(replyShowInWindow) && StringUtils.isEmpty(loadingNotice)) {
            loadingNotice = "正在进行多层问题分析...\nConducting multi-step problem analysis...";
        }
        if (Boolean.FALSE.equals(replyShowInWindow)) {
            textArea.appendContent(loadingNotice);
        }

        AtomicInteger lastInsertPosition = new AtomicInteger(-1);
        StringBuilder stringBuffer = new StringBuilder();
        final AtomicBoolean notExpected = new AtomicBoolean(true);
        final AtomicBoolean isWriting = new AtomicBoolean(false);
        final AtomicInteger countDot = new AtomicInteger(0);
        try {
            AtomicReference<String> preEndString = new AtomicReference<>("");
            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        response.body().forEach(line -> {
                            if (line.startsWith("data")) {
                                notExpected.set(false);
                                line = line.substring(5);
                                SseResponse sseResponse = null;
                                BaiduSseResponse baiduSseResponse = null;

                                if (ProviderNameEnum.BAIDU.getProviderName().equals(WindowTool.getSelectedProvider())||ProviderNameEnum.FREE.getProviderName().equals(WindowTool.getSelectedProvider())) {
                                    try {
                                        baiduSseResponse = JSON.parseObject(line, BaiduSseResponse.class);
                                    } catch (Exception e) {
                                        //// TODO: 2023/6/9
                                    }
                                } else {
                                    try {
                                        sseResponse = JSON.parseObject(line, SseResponse.class);
                                    } catch (Exception e) {
                                        //// TODO: 2023/6/9
                                    }
                                }
                                if (sseResponse != null || baiduSseResponse != null) {
                                    String resContent;
                                    if (ProviderNameEnum.BAIDU.getProviderName().equals(WindowTool.getSelectedProvider())||ProviderNameEnum.FREE.getProviderName().equals(WindowTool.getSelectedProvider())) {
                                        resContent = baiduSseResponse.getResult();
                                    } else {
                                        resContent = sseResponse.getChoices().get(0).getDelta().getContent();
                                    }
                                    if (resContent != null) {
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
                                        stringBuffer.append(resContent);
                                        if (Boolean.TRUE.equals(coding)) {
                                            ApplicationManager.getApplication().invokeAndWait(() -> {
                                                ApplicationManager.getApplication().runWriteAction(() -> {
                                                    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                                                    if (editor != null) {
                                                        Document document = editor.getDocument();
                                                        SelectionModel selectionModel = editor.getSelectionModel();

                                                        int insertPosition;
                                                        if (lastInsertPosition.get() == -1) { // This means it's the first time to insert
                                                            if (selectionModel.hasSelection()) {
                                                                // If there's a selection, find the end line of the selection
                                                                int selectionEnd = selectionModel.getSelectionEnd();
                                                                int endLine = document.getLineNumber(selectionEnd);
                                                                // Insert at the end of the line where the selection ends
                                                                insertPosition = document.getLineEndOffset(endLine);
                                                            } else {
                                                                // If there's no selection, insert at the end of the document
                                                                insertPosition = document.getTextLength();
                                                            }
                                                        } else { // This is not the first time, so we insert at the last insert position
                                                            insertPosition = lastInsertPosition.get();
                                                        }
                                                        if (isWriting.get() && resContent.endsWith("`") && !resContent.startsWith("`")) {
                                                            preEndString.set(resContent);
                                                            System.out.println(preEndString.get());
                                                        }
                                                        if (isWriting.get() && stringBuffer.indexOf("```") != stringBuffer.lastIndexOf("```")) {
                                                            isWriting.set(false);
                                                            if (resContent.contains("```")) {
                                                                String textToInsert = resContent.split("```")[0];
                                                                WriteCommandAction.runWriteCommandAction(project, () ->
                                                                        document.insertString(insertPosition, textToInsert));
                                                                lastInsertPosition.set(insertPosition + textToInsert.length());
                                                            } else {
                                                                String textToInsert = preEndString.get().replace("`", "");
                                                                WriteCommandAction.runWriteCommandAction(project, () ->
                                                                        document.insertString(insertPosition, textToInsert));
                                                                lastInsertPosition.set(insertPosition + textToInsert.length());
                                                            }
                                                        }

                                                        if (stringBuffer.indexOf("```") >= 0 && stringBuffer.indexOf("```") == stringBuffer.lastIndexOf("```") && stringBuffer.lastIndexOf("\n") > stringBuffer.indexOf("```")) {
                                                            // Insert a newline and the data
                                                            isWriting.set(true);
                                                            String textToInsert;
                                                            if (resContent.contains("```") && !resContent.endsWith("```")) {
                                                                textToInsert = resContent.split("```")[1];
                                                            } else {
                                                                textToInsert = resContent.replace("`", "");
                                                            }
                                                            WriteCommandAction.runWriteCommandAction(project, () ->
                                                                    document.insertString(insertPosition, textToInsert));

                                                            // Update the last insert position to the end of the inserted text
                                                            lastInsertPosition.set(insertPosition + textToInsert.length());
                                                        }
                                                    }
                                                });
                                            });
                                        }

                                    }
                                }
                            } else {
                                stringBuffer.append(line);
                            }
                        });
                    }).join();
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, e.getMessage(), "ChatGpt", Messages.getInformationIcon()));
            e.printStackTrace();
        }
//        if (textArea != null) {
//            textArea.highlight();
//        }
        String replyContent = stringBuffer.toString();
        Message message = new Message();
        message.setRole("assistant");
        message.setContent(replyContent);
        chatHistory.add(message);
        JsonStorage.saveConservation(nowTopic, chatHistory);
        if (notExpected.get()) {
            SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, replyContent, "ChatGpt", Messages.getInformationIcon()));
        }
        if (ProviderNameEnum.BAIDU.getProviderName().equals(WindowTool.getSelectedProvider())) {
            //判断是否需要继续未完成的内容
            if (Boolean.TRUE.equals(ChatUtils.needsContinuation(replyContent)) && retryTime < 2) {
                chatHistory.add(ChatUtils.getContinueMessage4Baidu());
                content.setMessages(chatHistory);
                chat(content, project, coding, replyShowInWindow, loadingNotice, retryTime + 1);
            }
        }
        return replyContent;
    }





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

    public int countSelectedLines(Project project) {
        // 获取当前活动的编辑器
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        if (editor != null) {
            SelectionModel selectionModel = editor.getSelectionModel();
            if (selectionModel.hasSelection()) {
                // 获取选中文本的起始和结束位置
                int startLine = editor.getDocument().getLineNumber(selectionModel.getSelectionStart());
                int endLine = editor.getDocument().getLineNumber(selectionModel.getSelectionEnd());

                // 计算选中的行数
                int lineCount = endLine - startLine + 1;
                return lineCount;
            }
        }
        return 0;
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


    public static List<Message> getClassInfoToMessageType(Project project, Editor editor) {
        List<Message> res = new ArrayList<>();

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        int selectionStart = editor.getSelectionModel().getSelectionStart();
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();

        // 获取圈选范围内的所有PsiElement
        PsiElement[] selectedElements = PsiTreeUtil.collectElements(psiFile,
                element -> element.getTextRange().getStartOffset() >= selectionStart &&
                        element.getTextRange().getEndOffset() <= selectionEnd);

        Set<PsiClass> involvedClasses = new HashSet<>();
        for (PsiElement element : selectedElements) {
            if (element instanceof PsiReference psireference) {
                PsiElement resolvedElement = psireference.resolve();
                if (resolvedElement instanceof PsiClass psiClass) {
                    involvedClasses.add(psiClass);
                }
            }
        }

        for (PsiClass psiClass : involvedClasses) {
            VirtualFile classFile = psiClass.getContainingFile().getVirtualFile();
            // 检查类是否属于当前项目
            if (ProjectRootManager.getInstance(project).getFileIndex().isInSourceContent(classFile)) {
                Message message = processClass2Message(psiClass, project);
                if (message != null) {
                    res.add(message);
                }
            }
        }
        return res;
    }

    /**
     * 将PsiClass对象转换为Message对象
     *
     * @param psiClass 要转换的PsiClass对象
     * @param project  当前的项目对象
     * @return 转换后的Message对象，如果PsiClass对象不在源代码中则返回null
     */
    private static Message processClass2Message(PsiClass psiClass, Project project) {
        // 获取PsiClass对象所在的虚拟文件
        VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
        // 获取项目文件索引
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        // 判断虚拟文件是否在源代码中
        if (fileIndex.isInSourceContent(virtualFile)) {
            // 获取PsiClass对象的所有字段
            PsiField[] fields = psiClass.getFields();
            PsiMethod[] methods = psiClass.getMethods();
            // 创建一个新的Message对象
            Message classMessage = new Message();
            classMessage.setRole("user");
            classMessage.setName("owner");
            // 创建一个StringBuffer对象，用于存储类的信息
            StringBuffer classInfoSb = new StringBuffer();
            // 添加类名和属性信息到StringBuffer对象中
            classInfoSb.append("已知").append(psiClass.getName()).append("类包含以下属性：");
            for (PsiField field : fields) {
                // 添加字段类型和字段名到StringBuffer对象中
                classInfoSb.append(field.getType().getPresentableText()).append(" ").append(field.getName());
                // 如果字段有备注，则把备注也append进去
                PsiDocComment docComment = field.getDocComment();
                if (docComment != null) {
                    String commentText = extractContentFromDocComment(docComment);
                    classInfoSb.append("，描述为:").append(commentText);
                } else {
                    PsiAnnotation[] annotations = field.getAnnotations();
                    // 遍历注解数组
                    for (PsiAnnotation annotation : annotations) {
                        PsiAnnotationMemberValue value = annotation.findAttributeValue("description");
                        if (value != null) {
                            classInfoSb.append("，描述为:").append(value.getText());
                            break;
                        }
                    }
                }
                //每个字段需要分隔开
                classInfoSb.append(" \n");
            }
            if (methods.length > 0) {
                Set<String> getterAndSetterNames = new HashSet<>();
                for (PsiField field : psiClass.getFields()) {
                    String fieldName = field.getName();
                    String capitalizedFieldName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    getterAndSetterNames.add("get" + capitalizedFieldName);
                    getterAndSetterNames.add("set" + capitalizedFieldName);
                }

                List<PsiMethod> usefulMethod = Arrays.stream(psiClass.getMethods()).filter(psiMethod -> !getterAndSetterNames.contains(psiMethod.getName())).toList();
                if (CollectionUtils.isNotEmpty(usefulMethod)) {
                    classInfoSb.append(",包含如下方法：");
                    for (PsiMethod method : psiClass.getMethods()) {
                        String methodName = method.getName();
                        classInfoSb.append("\n方法名: ").append(methodName);
                        // 输出方法的注释
                        PsiDocComment docComment = method.getDocComment();
                        if (docComment != null) {
                            String extractedContent = extractContentFromDocComment(docComment);
                            classInfoSb.append(" 用途描述: ").append(extractedContent);
                        }
                        // 输出方法的入参
                        PsiParameter[] parameters = method.getParameterList().getParameters();
                        if (parameters.length > 0) {
                            classInfoSb.append(" 调用参数:");
                            for (PsiParameter parameter : parameters) {
                                classInfoSb.append("  ").append(parameter.getType().getPresentableText()).append(" ").append(parameter.getName());
                            }
                        }
                        // 输出方法的出参
                        PsiType returnType = method.getReturnType();
                        if (returnType != null) {
                            classInfoSb.append("返回类型: ").append(returnType.getPresentableText());
                        }
                    }
                }
            }
            classInfoSb.append("上面这个类提供给你，有助于你更了解情况，但是如果上面这个类用不上，可忽略。");
            // 将StringBuffer对象的内容设置为Message对象的内容
            classMessage.setContent(classInfoSb.toString());
            // 返回转换后的Message对象
            return classMessage;
        }
        // 如果PsiClass对象不在源代码中，则返回null
        return null;
    }


    private static Message processCurentClass2Message(PsiClass psiClass) {
        // 获取PsiClass对象的所有字段
        PsiField[] fields = psiClass.getFields();
        PsiMethod[] methods = psiClass.getMethods();
        // 创建一个新的Message对象
        Message classMessage = new Message();
        classMessage.setRole("user");
        classMessage.setName("owner");
        // 创建一个StringBuffer对象，用于存储类的信息
        StringBuffer classInfoSb = new StringBuffer();
        // 添加类名和属性信息到StringBuffer对象中
        classInfoSb.append("已知").append(psiClass.getName()).append("类包含以下属性：");
        for (PsiField field : fields) {
            // 添加字段类型和字段名到StringBuffer对象中
            classInfoSb.append(field.getType().getPresentableText()).append(" ").append(field.getName());
            // 如果字段有备注，则把备注也append进去
            PsiDocComment docComment = field.getDocComment();
            if (docComment != null) {
                String commentText = extractContentFromDocComment(docComment);
                classInfoSb.append("，描述为:").append(commentText);
            } else {
                PsiAnnotation[] annotations = field.getAnnotations();
                // 遍历注解数组
                for (PsiAnnotation annotation : annotations) {
                    PsiAnnotationMemberValue value = annotation.findAttributeValue("description");
                    if (value != null) {
                        classInfoSb.append("，描述为:").append(value.getText());
                        break;
                    }
                }
            }
            //每个字段需要分隔开
            classInfoSb.append(" \n");
        }
        if (methods.length > 0) {
            Set<String> getterAndSetterNames = new HashSet<>();
            for (PsiField field : psiClass.getFields()) {
                String fieldName = field.getName();
                String capitalizedFieldName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                getterAndSetterNames.add("get" + capitalizedFieldName);
                getterAndSetterNames.add("set" + capitalizedFieldName);
            }

            List<PsiMethod> usefulMethod = Arrays.stream(psiClass.getMethods()).filter(psiMethod -> !getterAndSetterNames.contains(psiMethod.getName())).toList();
            if (CollectionUtils.isNotEmpty(usefulMethod)) {
                classInfoSb.append(",包含如下方法：");
                for (PsiMethod method : psiClass.getMethods()) {
                    String methodName = method.getName();
                    classInfoSb.append("\n方法名: ").append(methodName);
                    // 输出方法的注释
                    PsiDocComment docComment = method.getDocComment();
                    if (docComment != null) {
                        String extractedContent = extractContentFromDocComment(docComment);
                        classInfoSb.append(" 用途描述: ").append(extractedContent);
                    }
                    // 输出方法的入参
                    PsiParameter[] parameters = method.getParameterList().getParameters();
                    if (parameters.length > 0) {
                        classInfoSb.append(" 调用参数:");
                        for (PsiParameter parameter : parameters) {
                            classInfoSb.append("  ").append(parameter.getType().getPresentableText()).append(" ").append(parameter.getName());
                        }
                    }
                    // 输出方法的出参
                    PsiType returnType = method.getReturnType();
                    if (returnType != null) {
                        classInfoSb.append("返回类型: ").append(returnType.getPresentableText());
                    }
                }
            }
        }
        classInfoSb.append("。\n圈选的代码所在的类信息提供给你，有助于你更了解情况，但是如果上面这个类的信息用不上，可忽略。");
        // 将StringBuffer对象的内容设置为Message对象的内容
        classMessage.setContent(classInfoSb.toString());
        // 返回转换后的Message对象
        return classMessage;
    }


    private PsiElement getNextElement(PsiElement element, PsiElement stopElement) {
        if (element.getFirstChild() != null) {
            return element.getFirstChild();
        }
        if (element.getNextSibling() != null) {
            return element.getNextSibling();
        }
        while (element.getParent() != null) {
            element = element.getParent();
            if (element.isEquivalentTo(stopElement) || element.getNextSibling() == null) {
                return null;
            }
            if (element.getNextSibling() != null) {
                return element.getNextSibling();
            }
        }
        return null;
    }

    private static String extractContentFromDocComment(PsiDocComment docComment) {
        String fullText = docComment.getText();
        String content = fullText.replace("/**", "").replace("*/", "").trim();
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmedLine = line.trim().replaceFirst("^\\*", "").trim();
            if (!trimmedLine.startsWith("@")) {  // 过滤掉所有以 @ 开头的 Javadoc 标签
                sb.append(trimmedLine).append(" ");
            }
        }
        return sb.toString().trim();
    }
}

