package com.wmsay.gpt4_lll;

import com.alibaba.fastjson.JSON;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.wmsay.gpt4_lll.model.ChatModel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class PluginInfo extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        // TODO: insert action logic here
        MyPluginSettings settings = MyPluginSettings.getInstance();
        String apiKey = settings.getApiKey();
        Project project = e.getData(PlatformDataKeys.PROJECT);
        HttpClient client = HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", 7890)))
                .build()
                ;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/models"))
                .header("Authorization","Bearer "+apiKey)
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body =response.body();
            String data= JSON.parseObject(body).getObject("data",String.class);
            List<ChatModel> chatModels= JSON.parseArray(data,ChatModel.class);
            StringBuilder sb=new StringBuilder();
            chatModels.stream().map(ChatModel::getId).forEachOrdered(id->sb.append(id).append("\n"));

            Messages.showMessageDialog(project, sb.toString(), "info", Messages.getInformationIcon());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        Messages.showMessageDialog(project, "0.01", "info", Messages.getInformationIcon());

    }
}
