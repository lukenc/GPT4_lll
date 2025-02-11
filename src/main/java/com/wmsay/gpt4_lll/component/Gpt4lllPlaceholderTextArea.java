package com.wmsay.gpt4_lll.component;

import com.intellij.ui.Gray;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class Gpt4lllPlaceholderTextArea extends JTextArea {
    private String placeholder;
    private boolean isPlaceholderSet;
    private Color defaultTextColor;
    private Color placeholderColor = Gray._150; // 一种较淡的灰色

    public Gpt4lllPlaceholderTextArea(String placeholder) {
        this.placeholder = placeholder;
        this.defaultTextColor = getForeground();
        setPlaceholder();
        setLineWrap(true); // 启用折行
        setWrapStyleWord(true); // 按单词折行
        // 从 UIManager 中获取默认字体
        Font defaultFont = UIManager.getFont("TextArea.font");
        if (defaultFont == null) {
            // 如果 UIManager 中没有设置 TextArea.font，则使用系统默认字体
            defaultFont = new Font("Monospaced", Font.PLAIN, 12);
        }
        setFont(defaultFont);
        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (isPlaceholderSet) {
                    setText("");
                    setForeground(defaultTextColor);
                    isPlaceholderSet = false;
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (getText().trim().isEmpty()) {
                    setPlaceholder();
                }
            }
        });

        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                // 当文本变化时，确保移除 placeholder 状态
                isPlaceholderSet = false;
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                // 当文本被删除时，同样确保移除 placeholder 状态
                isPlaceholderSet = false;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // 对于普通文本组件不需要
            }
        });
    }

    private void setPlaceholder() {
        setText(placeholder);
        setForeground(placeholderColor);
        isPlaceholderSet = true;
    }

    public String getUserInput() {
        return isPlaceholderSet ? "" : getText();
    }

    @Override
    public String getText() {
        if (isPlaceholderSet){
            return "";
        }
            Document doc = getDocument();
        String txt;
        try {
            txt = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            txt = null;
        }
        return txt;
    }

}
