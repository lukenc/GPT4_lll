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

}
