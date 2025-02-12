package com.wmsay.gpt4_lll;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;


public class MyPluginConfigurable implements Configurable {
    //代理 通用配置
    private JTextField proxyAddressField;

    //OpenAi配置
    private JTextField apiKeyField;

    //百度配置
    private JTextField baiduAPIKeyField;
    private JTextField baiduSecretKeyField;
    //千问配置
    private JTextField tongyiApiKeyField;
    //grok
    private JTextField grokApiKeyField;
    //deepseek
    private JTextField deepseekApiKeyField;
    //自定义的配置
    private JTextField personalApiKeyField;
    private JTextField personalAddressField;
    private JTextField personalModelField;

    private JPanel panel;


    public MyPluginConfigurable() {
        Font titleFont = UIManager.getFont("Label.font").deriveFont(Font.BOLD);
        panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        Insets insets = JBUI.insets(20, 0, 10, 0);
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);

        // 设置列宽
        panel.setLayout(new GridBagLayout());
        ((GridBagLayout)panel.getLayout()).columnWidths = new int[]{200, 0};
        ((GridBagLayout)panel.getLayout()).columnWeights = new double[]{0.0, 1.0};

        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);

        int gridy = 0;

        // 通用设置标题
        addTitleLabel(panel, c, gridy++, "通用设置/common configuration");

        // 代理地址设置
        addLabelAndField(panel, c, gridy++, "科学冲浪地址(格式 IP:port): ", proxyAddressField = new JTextField(20));
        proxyAddressField.setToolTipText("冲浪吧，格式IP:port");

        // 分隔线
        addSeparator(panel, c, gridy++);

        // OpenAI 设置标题
        addTitleLabel(panel, c, gridy++, "ChatGpt 配置/OpenAi(gpt) configuration");

        // OpenAI API Key 设置
        addLabelAndField(panel, c, gridy++, "OpenAi(gpt) API Key: ", apiKeyField = new JTextField(20));
        apiKeyField.setToolTipText("Enter your API Key here");

        // 分隔线
        addSeparator(panel, c, gridy++);

        // 百度文心一言设置标题
        addTitleLabel(panel, c, gridy++, "百度文心一言配置/Baidu ERNIE Configuration");

        // 百度 API Key 设置
        addLabelAndField(panel, c, gridy++, "百度API Key:", baiduAPIKeyField = new JTextField(20));
        baiduAPIKeyField.setToolTipText("输入您的百度API Key");

        // 百度 Secret Key 设置
        addLabelAndField(panel, c, gridy++, "百度Secret Key:", baiduSecretKeyField = new JTextField(20));
        baiduSecretKeyField.setToolTipText("输入您的百度Secret Key");

        // 分隔线
        addSeparator(panel, c, gridy++);

        // 通义千问设置标题
        addTitleLabel(panel, c, gridy++, "通义千问配置/Tongyi Qwen Configuration");

        // 通义千问 API Key 设置
        addLabelAndField(panel, c, gridy++, "通义千问Api Key:", tongyiApiKeyField = new JTextField(20));
        tongyiApiKeyField.setToolTipText("输入您的通义千问Api Key");

        // 分隔线
        addSeparator(panel, c, gridy++);

        addTitleLabel(panel, c, gridy++, "X-GROK配置/X-GROK Configuration");

        // Grok API Key 设置
        addLabelAndField(panel, c, gridy++, "GROK Api Key:", grokApiKeyField = new JTextField(20));
        tongyiApiKeyField.setToolTipText("输入您的GROK Api Key");

        // 分隔线
        addSeparator(panel, c, gridy++);
        addTitleLabel(panel, c, gridy++, "DeepSeek配置/DeepSeek Configuration");
        // DeepSeek API Key 设置
        addLabelAndField(panel, c, gridy++, "DeepSeek Api Key:", deepseekApiKeyField = new JTextField(20));
        tongyiApiKeyField.setToolTipText("输入您的DeepSeek Api Key");

        // 分隔线
        addSeparator(panel, c, gridy++);

        // 自定义配置标题
        addTitleLabel(panel, c, gridy++, "自定义Api配置（遵循OpenAI接口规范）/Custom API Configuration (Following OpenAI Interface Standards)");
        addTitleLabel(panel, c, gridy++, "可以是私有化部署的服务，例如ollama");

        // 自定义 API 地址设置
        addLabelAndField(panel, c, gridy++, "Api address:", personalAddressField = new JTextField(20));
        personalAddressField.setToolTipText("输入您的自定义的api地址");

        // 自定义 API 模型设置
        addLabelAndField(panel, c, gridy++, "Api model:", personalModelField = new JTextField(20));

        // 自定义 API Key 设置
        addLabelAndField(panel, c, gridy++, "personal Api Key:", personalApiKeyField = new JTextField(20));

        // 添加一个占位组件来吸收多余的垂直空间
        c.weighty = 1.0;
        c.gridwidth = 2;
        panel.add(new JPanel(), c);
    }

    private void addTitleLabel(JPanel panel, GridBagConstraints c, int gridy, String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        panel.add(label, c);
        c.gridwidth = 1;
    }

    private void addLabelAndField(JPanel panel, GridBagConstraints c, int gridy, String labelText, JTextField field) {
        JLabel label = new JLabel(labelText);
        c.gridx = 0;
        c.gridy = gridy;
        c.anchor = GridBagConstraints.EAST;
        panel.add(label, c);

        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        panel.add(field, c);
    }

    private void addSeparator(JPanel panel, GridBagConstraints c, int gridy) {
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 2;
        c.insets = JBUI.insets(20, 0, 10, 0);
        panel.add(separator, c);
        c.insets = JBUI.insets(5);
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
                        !personalModelField.getText().equals(settings.getPersonalModel())||
                        !grokApiKeyField.getText().equals(settings.getGrokApiKey())||
                        !deepseekApiKeyField.getText().equals(settings.getDeepSeekApiKey())
                ;

    }

    @Override
    public void apply() throws ConfigurationException {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        settings.setApiKey(apiKeyField.getText());
        settings.setProxyAddress(proxyAddressField.getText());
        settings.setBaiduAPIKey(baiduAPIKeyField.getText());
        settings.setBaiduSecretKey(baiduSecretKeyField.getText());
        settings.setTongyiApiKey(tongyiApiKeyField.getText());
        settings.setPersonalApiKey(personalApiKeyField.getText());
        settings.setPersonalApiUrl(personalAddressField.getText());
        settings.setPersonalModel(personalModelField.getText());
        settings.setGrokApiKey(grokApiKeyField.getText());
        settings.setDeepSeekApiKey(deepseekApiKeyField.getText());
    }

    @Override
    public void reset() {
        MyPluginSettings settings = MyPluginSettings.getInstance();
        proxyAddressField.setText(settings.getProxyAddress());
        apiKeyField.setText(settings.getApiKey());
        baiduAPIKeyField.setText(settings.getBaiduAPIKey());
        baiduSecretKeyField.setText(settings.getBaiduSecretKey());
        tongyiApiKeyField.setText(settings.getTongyiApiKey());
        personalAddressField.setText(settings.getPersonalApiUrl());
        personalModelField.setText(settings.getPersonalModel());
        personalApiKeyField.setText(settings.getPersonalApiKey());
        grokApiKeyField.setText(settings.getGrokApiKey());
        deepseekApiKeyField.setText(settings.getDeepSeekApiKey());
    }
}

