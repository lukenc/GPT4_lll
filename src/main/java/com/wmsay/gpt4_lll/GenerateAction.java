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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SseResponse;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class GenerateAction extends AnAction {
    public static HashMap<String ,String > languageMap=new HashMap<>();
    {
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
    public static List<Message> chatHistory = new ArrayList<>();
    public static String nowTopic = "";

    //单机应用就是好，没有并发就是爽
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void actionPerformed(AnActionEvent e) {
        if (chatHistory!=null&&!chatHistory.isEmpty()&&!nowTopic.isEmpty()){
            JsonStorage.saveConservation(nowTopic,chatHistory);
        }
        chatHistory.clear();
        nowTopic="";
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
        String model="gpt-3.5-turbo";
        model = getModelName(toolWindow);
        String replyLanguage= getSystemLanguage();
        MyPluginSettings settings = MyPluginSettings.getInstance();
        Project project = e.getProject();
        if (project != null) {
            Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
            String fileType= getOpenFileType(project);
            SelectionModel selectionModel = editor.getSelectionModel();
            String selectedText = selectionModel.getSelectedText();
            Message systemMessage=new Message();
            systemMessage.setRole("system");
            systemMessage.setName("owner");
            systemMessage.setContent("你是一个有用的助手，同时也是一个计算机科学家，数据专家，有着多年的代码重构经验和多年的代码优化的架构师");

            Message message=new Message();
            Boolean coding=false;
            if (selectedText!=null) {
                selectedText = selectedText.trim();
                LocalDateTime now = LocalDateTime.now();
                nowTopic=formatter.format(now);
                if (Boolean.TRUE.equals(isSelectedTextAllComments(project))){
                    nowTopic=nowTopic+"--Generate:"+selectedText;
                    coding=true;
                    message.setRole("user");
                    message.setName("owner");
                    message.setContent("请帮我完成下面的功能，同时使用"+fileType+"，注释语言请使用iso为"+replyLanguage+"的语言,只要代码部分，代码部分要包含代码和注释，所有的返回代码应该在代码块中,请使用"+replyLanguage+"回复我，功能如下：" + selectedText);
                }else {
                    nowTopic=nowTopic+"--Optimize"+selectedText;
                    coding=false;
                    message.setRole("user");
                    message.setName("owner");
                    message.setContent("请帮我重构下面的代码，不局限于代码性能优化、命名优化、增加注释、简化代码、优化逻辑，请使用"+replyLanguage+"回复我，代码如下：" + selectedText);
                }
                ChatContent chatContent = new ChatContent();
                chatContent.setMessages(List.of(message, systemMessage));
                chatContent.setModel(model);
                chatHistory.addAll(List.of(message, systemMessage));
                Boolean finalCoding = coding;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        chat(chatContent, project, editor,finalCoding);
                    }
                }).start();
                //清理界面
                 WindowTool.clearShowWindow();
            }
        }
        // TODO: insert action logic here
    }


    public static String chat(ChatContent content,Project project,Editor editorPre,Boolean coding){
        MyPluginSettings settings = MyPluginSettings.getInstance();
        String apiKey = settings.getApiKey();
        String proxy = settings.getProxyAddress();
        if (StringUtils.isEmpty(apiKey)){
            Messages.showMessageDialog(project, "先去申请一个apikey。参考：https://blog.wmsay.com/article/60/", "ChatGpt", Messages.getInformationIcon());
            return "";
        }

        String requestBody= JSON.toJSONString(content);
        HttpClient.Builder clientBuilder=HttpClient.newBuilder();
        if (StringUtils.isNotEmpty(proxy)) {
            String[] addressAndPort = proxy.split(":");
            if (addressAndPort.length == 2 && isValidPort(addressAndPort[1])) {
                int port = Integer.parseInt(addressAndPort[1]);
                clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(addressAndPort[0], port)));
            } else {
                Messages.showMessageDialog(project, "格式错误，格式为ip:port", "科学冲浪失败", Messages.getInformationIcon());
            }
        }
        String url = settings.getGptUrl();
        HttpClient client = clientBuilder
                .build()
                ;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Authorization","Bearer "+apiKey)
                .header("Content-Type","application/json")
                .header("Accept","text/event-stream")
                .build();
        AtomicInteger lastInsertPosition = new AtomicInteger(-1);
        StringBuffer stringBuffer=new StringBuffer();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    response.body().forEach(line -> {
                        if (line.startsWith("data")) {

                            line = line.substring(5);
                            SseResponse sseResponse = null;
                            try {
                                sseResponse = JSON.parseObject(line, SseResponse.class);
                            } catch (Exception e) {
                                //// TODO: 2023/6/9
                            }
                            if (sseResponse != null){
                                String resContent = sseResponse.getChoices().get(0).getDelta().getContent();
                            if (resContent != null) {
                                WindowTool.appendContent(resContent);
                                if (Boolean.TRUE.equals(coding)) {

                                    ApplicationManager.getApplication().invokeLater(() -> {
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

                                            if (stringBuffer.indexOf("```") > 0 && stringBuffer.indexOf("```") == stringBuffer.lastIndexOf("```")&&stringBuffer.lastIndexOf("\n")>stringBuffer.indexOf("```")) {
                                                // Insert a newline and the data
                                                String textToInsert = resContent.replace("`", "");
                                                WriteCommandAction.runWriteCommandAction(project, () -> document.insertString(insertPosition, textToInsert));

                                                // Update the last insert position to the end of the inserted text
                                                lastInsertPosition.set(insertPosition + textToInsert.length());
                                            }
                                        }
                                    });
                                });
                                }
                                stringBuffer.append(resContent);
                            }
                        }
                        }
                    });
                }).join();
        String replyContent=stringBuffer.toString();
        Message message = new Message();
        message.setRole("assistant");
        message.setContent(replyContent);
        chatHistory.add(message);
        JsonStorage.saveConservation(nowTopic,chatHistory);
        return replyContent;
    }


    public static boolean isValidPort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            // 端口范围必须在0-65535之间
            return port >= 0 && port <= 65535;
        } catch (NumberFormatException e) {
            // 如果无法解析为整数，则返回false
            return false;
        }
    }


    private JRadioButton findRadioButton(JComponent component, String radioButtonContent) {
        if (component instanceof JRadioButton ) {
            if (radioButtonContent.equals(((JRadioButton) component).getText())) {
                return (JRadioButton) component;
            }
        }

        for (int i = 0; i < component.getComponentCount(); i++) {
            JComponent child = (JComponent) component.getComponent(i);
            JRadioButton radioButton = findRadioButton(child, radioButtonContent);
            if (radioButton != null) {
                return radioButton;
            }
        }

        return null;
    }


    private String getModelName(ToolWindow toolWindow) {
        if (toolWindow != null && toolWindow.isVisible()) {
            JPanel contentPanel = (JPanel) toolWindow.getContentManager().getContent(0).getComponent();

            JRadioButton gpt4Option = findRadioButton(contentPanel, "gpt-4");
            JRadioButton gpt35TurboOption = findRadioButton(contentPanel, "gpt-3.5-turbo");
            JRadioButton codeOption = findRadioButton(contentPanel, "code-davinci-002");

            if (gpt4Option != null) {
                boolean selected = gpt4Option.isSelected();
                if (selected) {
                    return "gpt-4";
                }
            }
            if (gpt35TurboOption != null) {
                boolean selected = gpt35TurboOption.isSelected();
                if (selected) {
                    return "gpt-3.5-turbo";
                }
            }
            if (codeOption != null) {
                boolean selected = codeOption.isSelected();
                if (selected) {
                    return "code-davinci-002";
                }
            }
        }
        return "gpt-3.5-turbo";
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
     *
     * 检查是否都是注释
    * @author liuchuan
    * @date 2023/6/8 2:06 PM
     * @param project
     * @return java.lang.Boolean
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

    public String  getSystemLanguage() {
        Locale locale = Locale.getDefault();
        String languageCode = locale.getLanguage();
        String language=languageMap.get(languageCode);
        if (language==null){
            return "中文";
        }
        return language;
    }
}

