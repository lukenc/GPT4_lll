package com.wmsay.gpt4_lll;

import com.alibaba.fastjson.JSON;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.Reply;
import org.apache.commons.lang3.StringUtils;



import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class GenerateAction extends AnAction {
    public static List<Message> chatHistory = new ArrayList<>();

    @Override
    public void actionPerformed(AnActionEvent e) {
        chatHistory.clear();
        MyPluginSettings settings = MyPluginSettings.getInstance();
        Project project = e.getProject();
        if (project != null) {
            Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
            SelectionModel selectionModel = editor.getSelectionModel();
            String selectedText = selectionModel.getSelectedText();
            Message systemMessage=new Message();
            systemMessage.setRole("system");
            systemMessage.setName("owner");
            systemMessage.setContent("你是一个有用的助手，同时也是一个计算机科学家，数据专家，有着多年的代码重构经验和多年的代码优化的架构师");

            Message message=new Message();
            message.setRole("user");
            message.setName("owner");
            message.setContent("请帮我重构下面的代码，不局限于代码性能优化，命名优化，增加注释，简化代码，优化逻辑，同时可以代码如下："+selectedText);

            ChatContent chatContent= new ChatContent();
            chatContent.setMessages(List.of(message,systemMessage));
            chatContent.setModel("gpt-3.5-turbo");
            chatHistory.addAll(List.of(message,systemMessage));
            String res= chat(chatContent,project);
            WindowTool.updateShowText(res);
        }
        // TODO: insert action logic here
    }


    public static String chat(ChatContent content,Project project){
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
        HttpClient client = clientBuilder
                .build()
                ;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Authorization","Bearer "+apiKey)
                .header("Content-Type","application/json")
                .build();
        CompletableFuture<HttpResponse<String>> futureResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            HttpResponse<String> response = futureResponse.get();
            if (response.statusCode()==200) {
                String reply = response.body();
                Reply replyDto = JSON.parseObject(reply, Reply.class);
                chatHistory.add(replyDto.getChoices().get(0).getMessage());
                return replyDto.getChoices().get(0).getMessage().getContent();
            }else {
                return response.body();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

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
}
