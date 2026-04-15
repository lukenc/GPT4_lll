package com.wmsay.gpt4_lll.component;

import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * 带 placeholder 的单行文本输入框。
 * placeholder 通过 paintComponent 绘制，不作为实际文本内容，
 * 避免文本溢出边框等布局问题。
 */
public class GPT4lllPlaceholderTextField extends JTextField {
    private final String placeholder;

    public GPT4lllPlaceholderTextField(String placeholder) {
        this.placeholder = placeholder;
        setMargin(JBUI.insets(4, 6, 4, 6));
        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) { repaint(); }
            @Override
            public void focusLost(FocusEvent e) { repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getDocument().getLength() == 0 && !isFocusOwner()) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Color.GRAY);
                g2.setFont(getFont());
                Insets insets = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, insets.left, insets.top + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    public String getUserInput() {
        return getText();
    }
}
