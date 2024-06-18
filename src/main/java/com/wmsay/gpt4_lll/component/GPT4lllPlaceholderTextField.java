package com.wmsay.gpt4_lll.component;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class GPT4lllPlaceholderTextField extends JTextField {
    private String placeholder;
    private boolean isShowingPlaceholder = true;

    public GPT4lllPlaceholderTextField(String placeholder) {
        this.placeholder = placeholder;
        this.setText(placeholder);
        this.setForeground(Color.GRAY); // 设置占位符文本的颜色  

        // 添加焦点监听器以处理占位符的显示和隐藏  
        this.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (isShowingPlaceholder) {
                    GPT4lllPlaceholderTextField.this.setText("");
                    GPT4lllPlaceholderTextField.this.setForeground(UIManager.getColor("TextField.foreground"));
                }
                isShowingPlaceholder = false;
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (GPT4lllPlaceholderTextField.this.getText().trim().isEmpty()) {
                    GPT4lllPlaceholderTextField.this.setText(placeholder);
                    GPT4lllPlaceholderTextField.this.setForeground(Color.GRAY);
                    isShowingPlaceholder = true;
                }
            }
        });

        // 添加文档监听器以处理用户输入与占位符相同的情况  
        this.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                checkPlaceholder();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                checkPlaceholder();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Not used by PlainDocument  
            }

            private void checkPlaceholder() {
                if (!isShowingPlaceholder && GPT4lllPlaceholderTextField.this.getText().equals(placeholder)) {
                    isShowingPlaceholder = false; // 用户输入了与占位符相同的文本，仍视为用户输入  
                    GPT4lllPlaceholderTextField.this.setForeground(UIManager.getColor("TextField.foreground"));
                }
            }
        });
    }

    public String getUserInput() {
        return isShowingPlaceholder ? "" : this.getText();
    }
}