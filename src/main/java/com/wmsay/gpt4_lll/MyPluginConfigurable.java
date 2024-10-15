package com.wmsay.gpt4_lll;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MyPluginConfigurable implements Configurable {
    private JTextField apiKeyField;
    private JTextField proxyAddressField;
    private JTextField gptAddressField;
    private JTextField baiduAPIKeyField;
    private JTextField baiduSecretKeyField;
    private JTextField baiduUrlField;
    private JPanel panel;

    private Map<String, String> modelToUrlMap;
    private JComboBox<String> modelComboBox;


    public MyPluginConfigurable() {
        modelToUrlMap = new LinkedHashMap<>();
        modelToUrlMap.put("CodeLlama-7b-Instruct", "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/completions/codellama_7b_instruct");
        modelToUrlMap.put("ERNIE-4.0-8K", "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro");
        modelToUrlMap.put("Mixtral-8x7B-Instruct-v0.1【个人推荐】", "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/mixtral_8x7b_instruct");


        panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        {
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
        }
        {
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
        }
        {
            JLabel privateUrl = new JLabel("GPT address（maybe build by your company）");
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

        // 添加横向提示标签
        JLabel hintLabel = new JLabel("百度文心一言配置：");
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        panel.add(hintLabel, c);

        // 重置GridBagConstraints的gridwidth
        c.gridwidth = 1;

        {
            // 添加百度API Key输入框
            JLabel baiduAPIKeyLabel = new JLabel("百度API Key:");
            c.gridx = 0;
            c.gridy = 7;
            c.anchor = GridBagConstraints.EAST;
            panel.add(baiduAPIKeyLabel, c);

            baiduAPIKeyField = new JTextField();
            baiduAPIKeyField.setColumns(20);
            baiduAPIKeyField.setToolTipText("输入您的百度API Key");
            c.gridx = 1;
            c.gridy = 7;
            c.anchor = GridBagConstraints.WEST;
            panel.add(baiduAPIKeyField, c);
        }
        {
            // 添加百度Secret Key输入框
            JLabel baiduSecretKeyLabel = new JLabel("百度Secret Key:");
            c.gridx = 0;
            c.gridy = 8;
            c.anchor = GridBagConstraints.EAST;
            panel.add(baiduSecretKeyLabel, c);

            baiduSecretKeyField = new JTextField();
            baiduSecretKeyField.setColumns(20);
            baiduSecretKeyField.setToolTipText("输入您的百度Secret Key");
            c.gridx = 1;
            c.gridy = 8;
            c.anchor = GridBagConstraints.WEST;
            panel.add(baiduSecretKeyField, c);
        }
        {
            // 添加百度Api地址输入框
            JLabel baiduSecretKeyLabel = new JLabel("百度请求地址（百度根据地址选择不同模型）:");
            c.gridx = 0;
            c.gridy = 9;
            c.anchor = GridBagConstraints.EAST;
            panel.add(baiduSecretKeyLabel, c);

            modelComboBox = new ComboBox<>(modelToUrlMap.keySet().toArray(new String[0]));
            modelComboBox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String selectedModel = (String) modelComboBox.getSelectedItem();
                    baiduUrlField.setText(modelToUrlMap.get(selectedModel));
                }
            });
            c.gridx = 1;
            c.gridy = 9;
            c.anchor = GridBagConstraints.WEST;
            panel.add(modelComboBox,c);


            baiduUrlField = new JTextField();
            baiduUrlField.setColumns(20);
            baiduUrlField.setToolTipText("输入您的百度Api地址");
            c.gridx = 1;
            c.gridy = 10;
            c.anchor = GridBagConstraints.WEST;
            panel.add(baiduUrlField, c);
        }
        // 设置百度分割线与上一行之间的间距
        Insets insets = new Insets(20, 0, 10, 0); // 上、左、下、右的间距
        c.insets = insets;
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        c.gridx = 0;
        c.gridy = 5; // 设置分割线的位置
        c.gridwidth = 2; // 横跨两列
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(separator, c);
        // 重置GridBagConstraints的gridwidth
        c.gridwidth = 1;

        // 设置百度分割线与上一行之间的间距
        Insets commonInsets = new Insets(0, 0, 10, 0); // 上、左、下、右的间距
        c.insets = commonInsets;
        JSeparator commonSeparator = new JSeparator(SwingConstants.HORIZONTAL);
        c.gridx = 0;
        c.gridy = 5; // 设置分割线的位置
        c.gridwidth = 2; // 横跨两列
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(commonSeparator, c);
        // 重置GridBagConstraints的gridwidth
        c.gridwidth = 1;
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
        return
                !proxyAddressField.getText().equals(settings.getProxyAddress()) ||
                        !apiKeyField.getText().equals(settings.getApiKey()) ||
                        !baiduAPIKeyField.getText().equals(settings.getBaiduAPIKey()) ||
                        !baiduSecretKeyField.getText().equals(settings.getBaiduSecretKey()) ||
                        !tongyiApiKeyField.getText().equals(settings.getTongyiApiKey()) ||
                        !personalAddressField.getText().equals(settings.getPersonalApiUrl()) ||
                        !personalApiKeyField.getText().equals(settings.getPersonalApiKey()) ||
                        !personalModelField.getText().equals(settings.getPersonalModel());

    }

    @Override
    public void apply() throws ConfigurationException {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        settings.setApiKey(apiKeyField.getText());
        settings.setProxyAddress(proxyAddressField.getText());
        settings.setGptUrl(gptAddressField.getText());
        settings.setBaiduAPIKey(baiduAPIKeyField.getText());
        settings.setBaiduSecretKey(baiduSecretKeyField.getText());
        settings.setBaiduApiUrl(baiduUrlField.getText());
    }

    @Override
    public void reset() {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        apiKeyField.setText(settings.getApiKey());
        proxyAddressField.setText(settings.getProxyAddress());
        gptAddressField.setText(settings.getGptUrl());
        baiduAPIKeyField.setText(settings.getBaiduAPIKey());
        baiduSecretKeyField.setText(settings.getBaiduSecretKey());
        baiduUrlField.setText(settings.getBaiduApiUrl());
    }
}

