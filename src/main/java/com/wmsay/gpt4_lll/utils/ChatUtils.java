package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.llm.LlmClient;
import com.wmsay.gpt4_lll.llm.LlmRequest;
import com.wmsay.gpt4_lll.llm.LlmStreamCallback;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapter;
import com.wmsay.gpt4_lll.llm.provider.ProviderAdapterRegistry;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.SelectModelOption;
import com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ChatUtils {

    private static final Logger log = LoggerFactory.getLogger(ChatUtils.class);

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
            log.debug("选中的模型是====={}",selectedModel.getModelName());
            return selectedModel.getModelName();
        }
        return "gpt-3.5-turbo";
    }



    private static JRadioButton findRadioButton(JComponent component, String radioButtonContent) {
        if (component instanceof JRadioButton jRadioButton && radioButtonContent.equals(jRadioButton.getText())) {
                return jRadioButton;
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
     * 获取 API URL。
     * 通过 ProviderAdapter 派发，统一处理所有供应商的 URL 获取逻辑。
     *
     * @param settings  插件设置
     * @param provider  供应商名称
     * @param modelName 模型名称
     * @return API URL
     */
    public static String getUrlByProvider(MyPluginSettings settings, String provider, String modelName) {
        ProviderAdapter adapter = ProviderAdapterRegistry.getAdapter(provider);
        return adapter.getApiUrl(settings, modelName);
    }

    /**
     * 获取 API URL（从 Project 获取供应商和模型信息）。
     *
     * @param settings 插件设置
     * @param project  当前项目
     * @return API URL
     */
    public static String getUrl(MyPluginSettings settings, Project project) {
        String provider = ModelUtils.getSelectedProvider(project);
        String modelName = ModelUtils.getModelNameByDisplay(ModelUtils.getSelectedModel(project).getDisplayName());
        return getUrlByProvider(settings, provider, modelName);
    }

    /**
     * 获取 API Key。
     * 通过 ProviderAdapter 派发，统一处理所有供应商的 API Key 获取逻辑。
     *
     * @param settings 插件设置
     * @param project  当前项目
     * @return API Key
     */
    public static String getApiKey(MyPluginSettings settings, Project project) {
        String provider = ModelUtils.getSelectedProvider(project);
        ProviderAdapter adapter = ProviderAdapterRegistry.getAdapter(provider);
        return adapter.getApiKey(settings);
    }

    // ==================== 供应商适配器便捷方法 ====================

    /**
     * 获取 system message 应使用的角色名。
     * 通过 ProviderAdapter 派发，替代各 Action 中散落的 if(BAIDU) setRole("user") 判断。
     *
     * @param provider 供应商名称
     * @return "system" 或 "user"
     */
    public static String getSystemRole(String provider) {
        return ProviderAdapterRegistry.getAdapter(provider).getSystemMessageRole();
    }

    /**
     * 判断当前供应商是否支持回复未完成时的续传重试。
     * 当前仅百度文心支持该特性。
     *
     * @param provider 供应商名称
     * @return true 支持续传重试
     */
    public static boolean supportsContinuationRetry(String provider) {
        return ProviderAdapterRegistry.getAdapter(provider).supportsContinuationRetry();
    }

    public static Boolean needsContinuation(String replyContent) {
        String cleanedReplyContent = replyContent.trim().replaceAll("\\n|\\r", "");
        return !cleanedReplyContent.endsWith(".")
                && !cleanedReplyContent.endsWith("。")
                && !cleanedReplyContent.endsWith("?")
                && !cleanedReplyContent.endsWith("？")
                && !cleanedReplyContent.endsWith("！")
                && !cleanedReplyContent.endsWith("!")
                && !cleanedReplyContent.endsWith("}")
                && !cleanedReplyContent.endsWith(";")
                && !cleanedReplyContent.endsWith("；")
                && !cleanedReplyContent.endsWith("```");
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
                CommonUtil.stopRunningStatus(project);
                return null;
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

    /**
     * 构建非流式 HTTP 请求（用于 Function Calling）。
     * 与 buildHttpRequest 相同，但 Accept 为 application/json。
     */
    public static HttpRequest buildHttpRequestJson(String url, String requestBody, String apiKey) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
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
        MyPluginSettings settings = MyPluginSettings.getInstance();
        String url = ChatUtils.getUrlByProvider( settings,provider,content.getModel());
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Input the correct api url/请输入正确api地址。");
        }
        if (apiKey==null ||apiKey.isBlank()){
            throw new IllegalArgumentException("Input the correct apikey/请输入正确apikey。");
        }
        String proxy = settings.getProxyAddress();

        LlmRequest request;
        try {
            request = LlmRequest.builder()
                    .url(url)
                    .chatContent(content)
                    .apiKey(apiKey)
                    .proxy(proxy)
                    .provider(provider)
                    .build();
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("URI with undefined scheme")) {
                throw new IllegalArgumentException("Input the correct api url/请输入正确api地址。");
            } else {
                throw new IllegalArgumentException("Request establishment failed, please check the relevant settings and input./建立请求失败，请检查相关设置与输入");
            }
        }

        StringBuilder stringBuffer = new StringBuilder();
        LlmClient.streamChat(request, new LlmStreamCallback() {
            @Override
            public void onContent(String contentDelta) {
                stringBuffer.append(contentDelta);
            }

            @Override
            public void onNonDataLine(String line) {
                stringBuffer.append(line);
            }
        });
        return stringBuffer.toString();
    }
}
