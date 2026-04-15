package com.wmsay.gpt4_lll.component;

import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.im.InputMethodRequests;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 带 placeholder 的文本输入框。
 * <p>
 * placeholder 通过 paintComponent 绘制，不作为实际文本内容，
 * 避免文本溢出圆角边框等布局问题。
 * <p>
 * macOS 冻结防护：覆写 getInputMethodRequests()，窗口失焦时返回 null。
 * 这样 macOS 在窗口切换时查询此组件的输入法支持时得到 null，
 * 不会触发 TSM/CoreText 交互，避免死锁。
 * 窗口有焦点时返回正常的 InputMethodRequests，中文输入不受影响。
 */
public class Gpt4lllPlaceholderTextArea extends JTextArea {
    private final String placeholder;
    private final Color placeholderColor = Gray._150;

    /** 跟踪窗口是否有焦点，用于 getInputMethodRequests() 判断 */
    private final AtomicBoolean windowFocused = new AtomicBoolean(true);
    private WindowFocusListener windowFocusListener;

    public Gpt4lllPlaceholderTextArea(String placeholder) {
        this.placeholder = placeholder;
        setLineWrap(true);
        setWrapStyleWord(true);
        setMargin(JBUI.insets(6, 8, 6, 8));

        Font defaultFont = UIManager.getFont("TextArea.font");
        if (defaultFont == null) {
            defaultFont = new Font("Monospaced", Font.PLAIN, 12);
        }
        setFont(defaultFont);

        // 焦点变化时重绘，确保 placeholder 正确显示/隐藏
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
                g2.setColor(placeholderColor);
                g2.setFont(getFont());
                Insets insets = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, insets.left, insets.top + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 窗口失焦时返回 null，阻止 macOS TSM 在窗口切换时与此组件交互。
     * 窗口有焦点时返回正常的 InputMethodRequests，中文输入正常工作。
     */
    @Override
    public InputMethodRequests getInputMethodRequests() {
        if (!windowFocused.get()) {
            return null;
        }
        return super.getInputMethodRequests();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            windowFocusListener = new WindowFocusListener() {
                @Override
                public void windowGainedFocus(WindowEvent e) {
                    windowFocused.set(true);
                }
                @Override
                public void windowLostFocus(WindowEvent e) {
                    windowFocused.set(false);
                }
            };
            window.addWindowFocusListener(windowFocusListener);
            windowFocused.set(window.isFocused());
        }
    }

    @Override
    public void removeNotify() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null && windowFocusListener != null) {
            window.removeWindowFocusListener(windowFocusListener);
            windowFocusListener = null;
        }
        super.removeNotify();
    }

    public String getUserInput() {
        return getText();
    }
}
