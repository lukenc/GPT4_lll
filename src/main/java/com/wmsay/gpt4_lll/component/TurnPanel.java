package com.wmsay.gpt4_lll.component;

import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.block.*;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.geom.RoundRectangle2D;
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

        wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    d.width = Math.min(d.width, p.getWidth());
                }
                return d;
            }

            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    return new Dimension(p.getWidth(), pref.height);
                }
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        wrapper.setOpaque(false);
        wrapper.setBorder(JBUI.Borders.empty(6, 4, 6, 4));

        // 气泡面板：GlassPanel 渲染（12px 圆角）+ 角色 tint 区分 user / assistant
        final Color bubbleTint = "user".equals(role)
                ? LiquidGlassTheme.USER_BUBBLE_TINT
                : LiquidGlassTheme.ASSISTANT_BUBBLE_TINT;
        final int cornerRadius = LiquidGlassTheme.RADIUS_MEDIUM;

        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int w = getWidth();
                    int h = getHeight();
                    int s = LiquidGlassTheme.SHADOW_SPREAD;
                    int drawX = s;
                    int drawY = s;
                    int drawW = w - s * 2;
                    int drawH = h - s * 2;
                    if (drawW <= 0 || drawH <= 0) return;

                    // 1. 柔和阴影
                    int baseAlpha = LiquidGlassTheme.SHADOW_COLOR.getAlpha();
                    int oy = LiquidGlassTheme.SHADOW_OFFSET_Y;
                    for (int i = s; i >= 1; i--) {
                        float ratio = (float) i / s;
                        int alpha = Math.max(0, (int)(baseAlpha * (1f - ratio) * 0.6f));
                        g2.setColor(new Color(0, 0, 0, alpha));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(drawX - i, drawY - i + oy,
                                drawW + i * 2 - 1, drawH + i * 2 - 1,
                                cornerRadius + i, cornerRadius + i);
                    }

                    RoundRectangle2D clip = new RoundRectangle2D.Float(
                            drawX, drawY, drawW, drawH, cornerRadius, cornerRadius);

                    // 2. 半透明背景填充（圆角裁剪，与 viewport clip 取交集）
                    g2.clip(clip);
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(bubbleTint);
                    g2.fillRoundRect(drawX, drawY, drawW, drawH, cornerRadius, cornerRadius);

                    // 3. 高光反射条（顶部渐变淡出，避免硬线条）
                    int hlX = drawX + cornerRadius / 2;
                    int hlW = drawW - cornerRadius;
                    int fadeHeight = 4;
                    Color hlColor = LiquidGlassTheme.HIGHLIGHT;
                    GradientPaint hlGradient = new GradientPaint(
                            0, drawY, hlColor,
                            0, drawY + fadeHeight, new Color(hlColor.getRed(), hlColor.getGreen(), hlColor.getBlue(), 0));
                    g2.setPaint(hlGradient);
                    g2.fillRect(hlX, drawY, hlW, fadeHeight);

                    // 恢复原始 clip（继承自 JViewport 的 viewport clip），
                    // 不能用 setClip(null)——那会清除 viewport clip，
                    // 导致边框绘制到 scrollPane 可见区域之外
                    g2.setClip(g.getClip());

                    // 4. 边框描边（1px 半透明）
                    g2.setColor(LiquidGlassTheme.BORDER);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(drawX, drawY, drawW - 1, drawH - 1, cornerRadius, cornerRadius);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public Insets getInsets() {
                Insets base = super.getInsets();
                int s = LiquidGlassTheme.SHADOW_SPREAD;
                return new Insets(base.top + s, base.left + s,
                        base.bottom + s, base.right + s);
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    d.width = Math.min(d.width, p.getWidth());
                }
                return d;
            }

            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    return new Dimension(p.getWidth(), pref.height);
                }
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(JBUI.Borders.empty(8, 12));

        contentContainer = new JPanel();
        contentContainer.setLayout(new BoxLayout(contentContainer, BoxLayout.Y_AXIS));
        contentContainer.setOpaque(false);
        contentContainer.setBorder(JBUI.Borders.empty(0));
        bubble.add(contentContainer, BorderLayout.CENTER);

        // assistant 角色：在气泡左上角显示 Agent 头像 (24x24)，与内容区域保持 8px 水平间距
        if ("assistant".equals(role)) {
            JLabel avatarLabel = new JLabel(PluginIcons.AGENT_AVATAR);
            avatarLabel.setName("Agent_Avatar");
            avatarLabel.setVerticalAlignment(SwingConstants.TOP);
            avatarLabel.setBorder(JBUI.Borders.emptyRight(8));
            bubble.add(avatarLabel, BorderLayout.WEST);
        }

        wrapper.add(bubble, BorderLayout.CENTER);

        splitter = new StreamContentSplitter(new StreamContentSplitter.Sink() {
            @Override
            public void onMarkdownContent(String text) {
                ensureMarkdownBlock();
                activeBlock.appendContent(text);
            }

            @Override
            public void onCodeFenceStart(String language) {
                if (language != null && language.trim().equalsIgnoreCase("mermaid")) {
                    MermaidBlock mermaidBlock = new MermaidBlock();
                    mermaidBlock.setOnContentChanged(TurnPanel.this::notifyContentChanged);
                    addBlock(mermaidBlock);
                    activeBlock = mermaidBlock;
                } else {
                    CodeBlock codeBlock = new CodeBlock(language);
                    codeBlock.setOnContentChanged(TurnPanel.this::notifyContentChanged);
                    addBlock(codeBlock);
                    activeBlock = codeBlock;
                }
            }

            @Override
            public void onCodeContent(String text) {
                if (activeBlock instanceof CodeBlock || activeBlock instanceof MermaidBlock) {
                    activeBlock.appendContent(text);
                }
            }

            @Override
            public void onCodeFenceEnd() {
                if (activeBlock instanceof MermaidBlock mb) {
                    mb.triggerRender();
                }
                activeBlock = null;
            }
        });

        autoFlushTimer = new Timer(AUTO_FLUSH_DELAY_MS, e -> splitter.flush());
        autoFlushTimer.setRepeats(false);

        // 窗口切走时暂停 flush timer，切回时恢复，避免 EDT 队列积压导致假死
        wrapper.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (wrapper.isShowing()) {
                    if (autoFlushSuspended) {
                        autoFlushTimer.restart();
                        autoFlushSuspended = false;
                    }
                } else {
                    if (autoFlushTimer.isRunning()) {
                        autoFlushTimer.stop();
                        autoFlushSuspended = true;
                    }
                }
            }
        });
    }

    /** 当组件不可见时，记住 autoFlushTimer 是否应该在恢复可见时重启 */
    private boolean autoFlushSuspended = false;

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
        } else if (block instanceof CodeBlock cb) {
            cb.dispose();
        } else if (block instanceof ThinkingBlock tb) {
            tb.dispose();
        } else if (block instanceof ToolUseBlock tub) {
            tub.dispose();
        } else if (block instanceof MermaidBlock meb) {
            meb.dispose();
        }
    }

    /**
     * 释放本轮所有资源（timer、block）。用于插件动态卸载时的清理。
     */
    public void dispose() {
        clear();
    }
}
