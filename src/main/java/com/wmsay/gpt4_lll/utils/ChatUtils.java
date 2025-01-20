package com.wmsay.gpt4_lll.utils;

import com.alibaba.fastjson.JSON;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SelectModelOption;
import com.wmsay.gpt4_lll.model.SseResponse;
import com.wmsay.gpt4_lll.model.baidu.BaiduSseResponse;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ChatUtils {

    public static Message getOddMessage4Baidu(){
        Message message = new Message();
        message.setRole("assistant");
        message.setContent("好的。还有更多内容需要提供么？以便让我更好解决您后面的问题。");
        return message;
    }

    public static Message getContinueMessage4Baidu(){
        Message message = new Message();
        message.setRole("user");
        message.setContent("请按照上面的要求，继续完成。");
        return message;
    }



    public static String getModelName(Project project) {
        SelectModelOption selectedModel=  ModelUtils.getSelectedModel(project);
        if (selectedModel!=null){
            System.out.printf("选中的模型是====="+selectedModel.getModelName());
            if ("".equals( selectedModel.getProvider())){

            }
            return selectedModel.getModelName();
        }
        return "gpt-3.5-turbo";
    }



    private static JRadioButton findRadioButton(JComponent component, String radioButtonContent) {
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


    /**
     * 获取API URL。
     *
     * @return API URL
     */
    public static String getUrlByProvider(MyPluginSettings settings,String  provider,String modelName) {
        if (ProviderNameEnum.BAIDU.getProviderName().equals(provider)) {
            String accessToken = AuthUtils.getBaiduAccessToken();
            String url = ModelUtils.getUrlByProvider(provider)+ modelName + "?access_token=" + accessToken;
            return url;
        }
        //todo 当前只有百度是免费的 所以先将免费的都写成百度的
        if (ProviderNameEnum.FREE.getProviderName().equals(provider)) {
            String accessToken = AuthUtils.getFreeBaiduAccessToken();
            String url = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-speed-128k" + "?access_token=" + accessToken;
            return url;
        }
        if (ProviderNameEnum.PERSONAL.getProviderName().equals(provider)){
            return settings.getPersonalApiUrl();
        }
        // 处理标准接口的公有平台url
        String url = ModelUtils.getUrlByProvider(provider);
        return url;
    }

    public static String getUrl(MyPluginSettings settings,Project project) {
        String provider = ModelUtils.getSelectedProvider(project);
        if (ProviderNameEnum.BAIDU.getProviderName().equals(provider)) {
            String accessToken = AuthUtils.getBaiduAccessToken();
            String url = ModelUtils.getUrlByProvider(provider)+ ModelUtils.getModelNameByDisplay(ModelUtils.getSelectedModel(project).getDisplayName()) + "?access_token=" + accessToken;
            return url;
        }
        //todo 当前只有百度是免费的 所以先将免费的都写成百度的
        if (ProviderNameEnum.FREE.getProviderName().equals(provider)) {
            String accessToken = AuthUtils.getFreeBaiduAccessToken();
            String url = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-speed-128k" + "?access_token=" + accessToken;
            return url;
        }
        if (ProviderNameEnum.PERSONAL.getProviderName().equals(provider)){
            return settings.getPersonalApiUrl();
        }
        // 处理标准接口的公有平台url
        String url = ModelUtils.getUrlByProvider(provider);
        return url;
    }



    public static String getApiKey(MyPluginSettings settings,Project project) {
        String provider = ModelUtils.getSelectedProvider(project);
        if (ProviderNameEnum.BAIDU.getProviderName().equals(provider)) {
            String accessToken = AuthUtils.getBaiduAccessToken();
            return accessToken;
        }
        //todo 当前只有百度是免费的 所以先将免费的都写成百度的
        if (ProviderNameEnum.FREE.getProviderName().equals(provider)) {
            String accessToken = AuthUtils.getFreeBaiduAccessToken();
            return accessToken;
        }
        if (ProviderNameEnum.PERSONAL.getProviderName().equals(provider)){
            return settings.getPersonalApiKey();
        }
        if (ProviderNameEnum.OPEN_AI.getProviderName().equals(provider)){
            return settings.getApiKey();
        }
        if (ProviderNameEnum.ALI.getProviderName().equals(provider)){
            return settings.getTongyiApiKey();
        }
        if (ProviderNameEnum.GROK.getProviderName().equals(provider)){
            return settings.getGrokApiKey();
        }
        return "";
    }

    public static Boolean needsContinuation(String replyContent) {
        String cleanedReplyContent = replyContent.trim().replaceAll("\\n|\\r", "");
        if (!cleanedReplyContent.endsWith(".")
                && !cleanedReplyContent.endsWith("。")
                && !cleanedReplyContent.endsWith("?")
                && !cleanedReplyContent.endsWith("？")
                && !cleanedReplyContent.endsWith("！")
                && !cleanedReplyContent.endsWith("!")
                && !cleanedReplyContent.endsWith("}")
                && !cleanedReplyContent.endsWith(";")
                && !cleanedReplyContent.endsWith("；")
                && !cleanedReplyContent.endsWith("```")){
            return true;
        }
        return false;
    }


    public static void showOpenaiApiKeyMessageDialog(Project project) {
        String noticeMessage = """
            尚未填写apikey。如果没有，先去申请一个apikey。参考：https://blog.wmsay.com/article/60/
            配置：
                    Settings >> Other Settings >> GPT4 lll Settings
            （如果你在国内使用，需要翻墙。参考：https://blog.wmsay.com/article/chatgpt-reg-1）
            """;
        if (!"Chinese".equals(CommonUtil.getSystemLanguage())) {
            noticeMessage = """
                Please apply for an API key first and then fill it in the settings.
                To configure it:
                        Settings >> Other Settings >> GPT4 lll Settings
                Please refer to the following link for reference:https://blog.wmsay.com/article/60
                """;
        }
        String finalNoticeMessage = noticeMessage;
        SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, finalNoticeMessage, "ChatGpt", Messages.getInformationIcon()));
    }



    public static HttpClient buildHttpClient(String proxy, Project project) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        if (StringUtils.isNotEmpty(proxy)) {
            String[] addressAndPort = proxy.split(":");
            if (addressAndPort.length == 2 && isValidPort(addressAndPort[1])) {
                int port = Integer.parseInt(addressAndPort[1]);
                clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(addressAndPort[0], port)));
            } else {
                showMessageDialog(project, "格式错误，格式为ip:port", "科学冲浪失败");
            }
        }
        return clientBuilder.build();
    }

    public static HttpClient buildHttpClient(String proxy) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        if (StringUtils.isNotEmpty(proxy)) {
            String[] addressAndPort = proxy.split(":");
            if (addressAndPort.length == 2 && isValidPort(addressAndPort[1])) {
                int port = Integer.parseInt(addressAndPort[1]);
                clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(addressAndPort[0], port)));
            } else {
                throw  new IllegalArgumentException("格式错误，格式为ip:port");
            }
        }
        return clientBuilder.build();
    }


    public static HttpRequest buildHttpRequest(String url, String requestBody, String apiKey) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofMinutes(2))
                .build();
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

    public static void showMessageDialog(Project project, String message, String title) {
        SwingUtilities.invokeLater(() -> Messages.showMessageDialog(project, message, title, Messages.getInformationIcon()));
    }

    public static List<Message> getProjectChatHistory(Project project){
        if (project.getUserData(Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY)==null){
            project.putUserData(Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY,new ArrayList<>());
        }
        return project.getUserData(Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY);
    }

    public static String getProjectTopic(Project project){
        if (project.getUserData(Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC)==null){
            project.putUserData(Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC,"");
        }
        return project.getUserData(Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC);
    }

    public static void setProjectTopic(Project project,String topic){
        project.putUserData(Gpt4lllChatKey.GPT_4_LLL_NOW_TOPIC,topic);
    }


    public static String pureChat(String provider,String apiKey,ChatContent content) throws IllegalArgumentException{
        System.out.println();
        MyPluginSettings settings = MyPluginSettings.getInstance();
        String url = ChatUtils.getUrlByProvider( settings,provider,content.getModel());
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Input the correct api url/请输入正确api地址。");
        }
        if (apiKey==null ||apiKey.isBlank()){
            throw new IllegalArgumentException("Input the correct apikey/请输入正确apikey。");
        }
        String proxy = settings.getProxyAddress();

        String requestBody = JSON.toJSONString(content);

        HttpClient client = ChatUtils.buildHttpClient(proxy);
        HttpRequest request;
        try {
            request = ChatUtils.buildHttpRequest(url, requestBody, apiKey);
        }catch (IllegalArgumentException exception){
            if (exception.getMessage().equals("URI with undefined scheme")) {
                throw new IllegalArgumentException("Input the correct api url/请输入正确api地址。");
            } else {
                throw new IllegalArgumentException("Request establishment failed, please check the relevant settings and input./建立请求失败，请检查相关设置与输入");
            }
        }
        StringBuilder stringBuffer = new StringBuilder();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    response.body().forEach(line -> {
                        if (line.startsWith("data")) {
//                            notExpected.set(false);
                            line = line.substring(5);
                            SseResponse sseResponse = null;
                            BaiduSseResponse baiduSseResponse = null;

                            if (ProviderNameEnum.BAIDU.getProviderName().equals(provider)||ProviderNameEnum.FREE.getProviderName().equals(provider)) {
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
                                if (ProviderNameEnum.BAIDU.getProviderName().equals(provider)||ProviderNameEnum.FREE.getProviderName().equals(provider)) {
                                    resContent = baiduSseResponse.getResult();
                                } else {
                                    resContent = sseResponse.getChoices().get(0).getDelta().getContent();
                                }
                                if (resContent != null) {
                                    stringBuffer.append(resContent);
                                }
                            }
                        } else {
                            stringBuffer.append(line);
                        }
                    });
                }).join();
        return stringBuffer.toString();


    }
}
