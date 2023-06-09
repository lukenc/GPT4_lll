package com.wmsay.gpt4_lll;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MyPluginConfigurable implements Configurable {
    private JTextField apiKeyField;
    private JTextField proxyAddressField;

    private JTextField gptAddressField;
    private JPanel panel;

    public MyPluginConfigurable() {
        panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        JLabel apiKeyLabel = new JLabel("API Key: ");
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(apiKeyLabel, c);

        apiKeyField = new JTextField();
        apiKeyField.setColumns(20);
        apiKeyField.setToolTipText("Enter your API Key here");
        c.gridx = 1;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        panel.add(apiKeyField, c);

        JLabel proxyAddressLabel = new JLabel("科学冲浪地址(格式 IP:port): ");
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.EAST;
        panel.add(proxyAddressLabel, c);

        proxyAddressField = new JTextField();
        proxyAddressField.setColumns(20);
        proxyAddressField.setToolTipText("冲浪吧，格式IP:port");
        c.gridx = 1;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        panel.add(proxyAddressField, c);



        JLabel privateUrl = new JLabel("ChatGPT address（maybe build by your company）");
        c.gridx = 0;
        c.gridy = 2;
        c.anchor = GridBagConstraints.EAST;
        panel.add(privateUrl, c);

        gptAddressField = new JTextField();
        gptAddressField.setColumns(20);
        gptAddressField.setText("https://api.openai.com/v1/chat/completions");
        gptAddressField.setToolTipText("default：https://api.openai.com/v1/chat/completions");
        c.gridx = 1;
        c.gridy = 2;
        c.anchor = GridBagConstraints.WEST;
        panel.add(gptAddressField, c);
    }

    @Override
    public String getDisplayName() {
        return "GPT4 lll Settings";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return panel;
    }

    @Override
    public boolean isModified() {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        return !apiKeyField.getText().equals(settings.getApiKey()) ||
                !gptAddressField.getText().equals(settings.getGptUrl())||
                !proxyAddressField.getText().equals(settings.getProxyAddress());
    }

    @Override
    public void apply() throws ConfigurationException {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        settings.setApiKey(apiKeyField.getText());
        settings.setProxyAddress(proxyAddressField.getText());
        settings.setGptUrl(gptAddressField.getText());
    }

    @Override
    public void reset() {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        apiKeyField.setText(settings.getApiKey());
        proxyAddressField.setText(settings.getProxyAddress());
        gptAddressField.setText(settings.getGptUrl());
    }
}

