package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

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

    public static String getModelName(ToolWindow toolWindow) {
        if (toolWindow != null && toolWindow.isVisible()) {
            JPanel contentPanel = (JPanel) toolWindow.getContentManager().getContent(0).getComponent();

            JRadioButton gpt4Option = findRadioButton(contentPanel, "gpt-4");
            JRadioButton gpt35TurboOption = findRadioButton(contentPanel, "gpt-3.5-turbo");
            JRadioButton codeOption = findRadioButton(contentPanel, "code-davinci-002");
            JRadioButton gpt40TurboOption = findRadioButton(contentPanel, "gpt-4-turbo");
            JRadioButton baiduOption = findRadioButton(contentPanel, "文心一言-baidu");
            JRadioButton freeBaiduOption = findRadioButton(contentPanel,"Free-免费");

            if (freeBaiduOption != null) {
                boolean selected = freeBaiduOption.isSelected();
                if (selected) {
                    return "baidu-free";
                }
            }

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
            if (gpt40TurboOption != null) {
                boolean selected = gpt40TurboOption.isSelected();
                if (selected) {
                    return "gpt-4-turbo-preview";
                }
            }
            if (baiduOption!=null){
                boolean selected = baiduOption.isSelected();
                if (selected) {
                    return "baidu";
                }
            }
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
     * @param content 聊天内容
     * @return API URL
     */
    public static String getUrl(ChatContent content, MyPluginSettings settings) {
        if (content.getModel().contains("baidu")) {
            String accessToken = AuthUtils.getBaiduAccessToken();
            String url = settings.getBaiduApiUrl() + "?access_token=" + accessToken;
            if (content.getModel().contains("free")) {
                accessToken = AuthUtils.getFreeBaiduAccessToken();
                url = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-speed-128k" + "?access_token=" + accessToken;
            }
            return url;
        } else {
            return settings.getGptUrl();
        }
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
