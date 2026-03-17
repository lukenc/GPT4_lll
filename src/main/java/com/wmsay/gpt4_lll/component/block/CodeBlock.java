package com.wmsay.gpt4_lll.component.block;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelListener;

/**
 * 代码块内容组件。
 * 等宽字体、带背景色、显示语言标签、支持流式追加。
 */
public class CodeBlock implements ContentBlock {

    private final JPanel wrapper;
    private final JTextArea codeArea;
    private final JLabel languageLabel;
    private final StringBuilder contentBuilder = new StringBuilder();

    private final Timer updateTimer;
    private static final int COALESCE_DELAY_MS = 80;
    private volatile boolean pendingUpdate = false;

    private Runnable onContentChanged;

    public CodeBlock(String language) {
        wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                // 宽度跟随父容器，高度自适应内容，确保内部 JScrollPane 能正确约束水平滚动
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    return new Dimension(p.getWidth(), super.getPreferredSize().height);
                }
                return super.getMaximumSize();
            }
        };
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.empty(4, 0),
                BorderFactory.createLineBorder(JBColor.border(), 1, true)
        ));
        wrapper.setBackground(new JBColor(new Color(245, 245, 245), new Color(43, 43, 43)));

        if (language != null && !language.isBlank()) {
            languageLabel = new JLabel(" " + language.trim());
            languageLabel.setFont(languageLabel.getFont().deriveFont(Font.BOLD, 10f));
            languageLabel.setForeground(JBColor.GRAY);
            languageLabel.setBorder(JBUI.Borders.empty(2, 6, 0, 0));
            languageLabel.setOpaque(true);
            languageLabel.setBackground(wrapper.getBackground());
            wrapper.add(languageLabel, BorderLayout.NORTH);
        } else {
            languageLabel = null;
        }

        codeArea = new JTextArea();
        codeArea.setEditable(false);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        codeArea.setLineWrap(false);
        codeArea.setTabSize(4);
        codeArea.setBackground(wrapper.getBackground());
        codeArea.setForeground(new JBColor(new Color(50, 50, 50), new Color(200, 200, 200)));
        codeArea.setBorder(JBUI.Borders.empty(4, 8, 4, 8));

        JScrollPane scrollPane = new JScrollPane(codeArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);

        // 保存原始监听器，用于处理水平滚动
        MouseWheelListener[] originalListeners = scrollPane.getMouseWheelListeners();
        for (MouseWheelListener mwl : originalListeners) {
            scrollPane.removeMouseWheelListener(mwl);
        }
        scrollPane.addMouseWheelListener(e -> {
            // macOS 触控板水平滑动在 Swing 中表现为 Shift + MouseWheel
            if (e.isShiftDown()) {
                // 水平滚动事件交给代码块自己的 JScrollPane 处理
                for (MouseWheelListener mwl : originalListeners) {
                    mwl.mouseWheelMoved(e);
                }
            } else {
                // 垂直滚动事件转发给父级容器
                Container ancestor = SwingUtilities.getAncestorOfClass(JScrollPane.class, scrollPane);
                if (ancestor != null) {
                    ancestor.dispatchEvent(SwingUtilities.convertMouseEvent(scrollPane, e, ancestor));
                }
            }
        });

        wrapper.add(scrollPane, BorderLayout.CENTER);

        updateTimer = new Timer(COALESCE_DELAY_MS, e -> flushPendingContent());
        updateTimer.setRepeats(false);
    }

    @Override
    public BlockType getType() {
        return BlockType.CODE;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    @Override
    public boolean isAppendable() {
        return true;
    }

    @Override
    public void appendContent(String delta) {
        contentBuilder.append(delta);
        pendingUpdate = true;
        if (!updateTimer.isRunning()) {
            updateTimer.restart();
        }
    }

    private void flushPendingContent() {
        if (!pendingUpdate) return;
        pendingUpdate = false;
        codeArea.setText(contentBuilder.toString());
        wrapper.revalidate();
        wrapper.repaint();
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    public void setContent(String fullText) {
        contentBuilder.setLength(0);
        contentBuilder.append(fullText);
        codeArea.setText(fullText);
        wrapper.revalidate();
        wrapper.repaint();
    }

    public String getContentText() {
        return contentBuilder.toString();
    }

    public void setOnContentChanged(Runnable callback) {
        this.onContentChanged = callback;
    }
}
