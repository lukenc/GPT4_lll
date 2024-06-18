package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.wm.ToolWindow;
import com.wmsay.gpt4_lll.model.Message;

import javax.swing.*;

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



}
