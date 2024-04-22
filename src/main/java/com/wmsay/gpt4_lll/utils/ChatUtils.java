package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.wm.ToolWindow;

import javax.swing.*;

public class ChatUtils {

    public static String getModelName(ToolWindow toolWindow) {
        if (toolWindow != null && toolWindow.isVisible()) {
            JPanel contentPanel = (JPanel) toolWindow.getContentManager().getContent(0).getComponent();

            JRadioButton gpt4Option = findRadioButton(contentPanel, "gpt-4");
            JRadioButton gpt35TurboOption = findRadioButton(contentPanel, "gpt-3.5-turbo");
            JRadioButton codeOption = findRadioButton(contentPanel, "code-davinci-002");
            JRadioButton gpt40TurboOption = findRadioButton(contentPanel, "gpt-4-turbo");
            JRadioButton baiduOption = findRadioButton(contentPanel, "文心一言-baidu");

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
