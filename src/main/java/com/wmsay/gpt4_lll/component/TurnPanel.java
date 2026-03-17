package com.wmsay.gpt4_lll.component;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.block.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 一轮对话的容器面板（user 或 assistant）。
 * 内部持有多个 ContentBlock，管理 activeBlock 指针。
 * 流式文本通过 StreamContentSplitter 自动拆分为 MarkdownBlock / CodeBlock。
 */
public class TurnPanel {

    private final String role;
    private final JPanel wrapper;
    private final JPanel contentContainer;
    private final List<ContentBlock> blocks = new ArrayList<>();
    private ContentBlock activeBlock;
    private final StreamContentSplitter splitter;
    private final Timer autoFlushTimer;
    private static final int AUTO_FLUSH_DELAY_MS = 200;

    private Runnable onContentChanged;

    public TurnPanel(String role) {
        this.role = role;

        wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(JBUI.Borders.empty(6, 4, 6, 4));

        // 气泡面板：圆角 + 背景色区分 user / assistant
        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        bubble.setOpaque(false);

        if ("user".equals(role)) {
            // 用户气泡：略深的蓝灰色调
            bubble.setBackground(new JBColor(new Color(0xE8EEF5), new Color(0x2D3548)));
            bubble.setBorder(JBUI.Borders.empty(8, 12));
        } else {
            // 助手气泡：浅灰 / 深灰底色
            bubble.setBackground(new JBColor(new Color(0xF5F5F5), new Color(0x2B2B2B)));
            bubble.setBorder(JBUI.Borders.empty(8, 12));
        }

        contentContainer = new JPanel();
        contentContainer.setLayout(new BoxLayout(contentContainer, BoxLayout.Y_AXIS));
        contentContainer.setOpaque(false);
        contentContainer.setBorder(JBUI.Borders.empty(0));
        bubble.add(contentContainer, BorderLayout.CENTER);

        wrapper.add(bubble, BorderLayout.CENTER);

        splitter = new StreamContentSplitter(new StreamContentSplitter.Sink() {
            @Override
            public void onMarkdownContent(String text) {
                ensureMarkdownBlock();
                activeBlock.appendContent(text);
            }

            @Override
            public void onCodeFenceStart(String language) {
                CodeBlock codeBlock = new CodeBlock(language);
                codeBlock.setOnContentChanged(TurnPanel.this::notifyContentChanged);
                addBlock(codeBlock);
                activeBlock = codeBlock;
            }

            @Override
            public void onCodeContent(String text) {
                if (activeBlock instanceof CodeBlock) {
                    activeBlock.appendContent(text);
                }
            }

            @Override
            public void onCodeFenceEnd() {
                activeBlock = null;
            }
        });

        autoFlushTimer = new Timer(AUTO_FLUSH_DELAY_MS, e -> splitter.flush());
        autoFlushTimer.setRepeats(false);
    }

    public String getRole() {
        return role;
    }

    public JComponent getComponent() {
        return wrapper;
    }

    public ContentBlock getActiveBlock() {
        return activeBlock;
    }

    public void setActiveBlock(ContentBlock block) {
        this.activeBlock = block;
    }

    public List<ContentBlock> getBlocks() {
        return blocks;
    }

    public void setOnContentChanged(Runnable callback) {
        this.onContentChanged = callback;
    }

    /**
     * 追加流式内容。ThinkingBlock 活跃时直接追加；
     * 否则通过 StreamContentSplitter 自动拆分为 MarkdownBlock / CodeBlock。
     * 每次追加后重置 autoFlushTimer，流停止 200ms 后自动 flush 剩余不完整行。
     */
    public void appendContent(String delta) {
        if (activeBlock instanceof ThinkingBlock) {
            activeBlock.appendContent(delta);
            return;
        }
        autoFlushTimer.restart();
        splitter.append(delta);
    }

    /**
     * 将 splitter 缓冲区中剩余的不完整行强制输出（生成结束时调用）。
     */
    public void flushContent() {
        splitter.flush();
    }

    /**
     * 开始思考过程块。先 flush splitter 缓冲再切换。
     */
    public ThinkingBlock startThinking() {
        splitter.flush();
        ThinkingBlock block = new ThinkingBlock();
        block.setOnContentChanged(this::notifyContentChanged);
        addBlock(block);
        activeBlock = block;
        return block;
    }

    /**
     * 结束思考过程：标记完成、折叠，清除 activeBlock 指针。
     */
    public void endThinking() {
        if (activeBlock instanceof ThinkingBlock thinkingBlock) {
            thinkingBlock.markComplete();
        }
        activeBlock = null;
    }

    /**
     * 添加一个 ContentBlock 并将其组件加入布局。
     */
    public void addBlock(ContentBlock block) {
        blocks.add(block);

        JComponent comp = block.getComponent();
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);

        contentContainer.add(comp);
        contentContainer.revalidate();
        contentContainer.repaint();
    }

    /**
     * 清空本轮所有 Block 并重置 splitter 状态。
     */
    public void clear() {
        autoFlushTimer.stop();
        for (ContentBlock block : blocks) {
            disposeBlock(block);
        }
        blocks.clear();
        activeBlock = null;
        splitter.reset();
        contentContainer.removeAll();
        contentContainer.revalidate();
        contentContainer.repaint();
    }

    private void ensureMarkdownBlock() {
        if (activeBlock == null || !(activeBlock instanceof MarkdownBlock)) {
            MarkdownBlock newBlock = createMarkdownBlock();
            addBlock(newBlock);
            activeBlock = newBlock;
        }
    }

    private MarkdownBlock createMarkdownBlock() {
        MarkdownBlock block = new MarkdownBlock();
        block.setOnContentChanged(this::notifyContentChanged);
        return block;
    }

    private void notifyContentChanged() {
        if (onContentChanged != null) {
            onContentChanged.run();
        }
    }

    private void disposeBlock(ContentBlock block) {
        if (block instanceof MarkdownBlock mb) {
            mb.dispose();
        } else if (block instanceof ThinkingBlock tb) {
            tb.dispose();
        } else if (block instanceof ToolUseBlock tub) {
            tub.dispose();
        }
    }
}
