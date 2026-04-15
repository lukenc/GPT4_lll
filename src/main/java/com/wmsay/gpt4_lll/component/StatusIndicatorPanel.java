package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.Disposable;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.model.AgentStatusContext;
import com.wmsay.gpt4_lll.utils.RuntimeStatusManager;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * 悬浮状态指示条 — 显示在对话滚动区域底部。
 * 实现 AgentPhaseListener，自动响应阶段变化。
 * 半透明背景，不占用对话内容布局空间。
 */
public class StatusIndicatorPanel extends JPanel
        implements RuntimeStatusManager.AgentPhaseListener, Disposable {

    private final JLabel iconLabel;
    private final JLabel textLabel;
    private final JLabel detailLabel;
    private Timer fadeOutTimer;
    private SpinnerIconAnimator spinnerAnimator;

    static final int COMPLETED_HIDE_DELAY_MS = 3000;
    static final int ERROR_HIDE_DELAY_MS = 5000;
    static final int STOPPED_HIDE_DELAY_MS = 3000;

    public StatusIndicatorPanel() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 6, 4));
        setOpaque(false);
        setVisible(false);

        iconLabel = new JLabel();
        textLabel = new JLabel();
        detailLabel = new JLabel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                // 限制 detail 标签最大宽度，超长文本由 JLabel 自动省略号截断
                d.width = Math.min(d.width, 200);
                return d;
            }
        };
        detailLabel.setForeground(Color.GRAY);

        add(iconLabel);
        add(textLabel);
        add(detailLabel);

        // SpinnerIconAnimator handles HierarchyListener internally,
        // so no need for a manual HierarchyListener here for spinner pause/resume.
    }

    private static final int CORNER_RADIUS = LiquidGlassTheme.RADIUS_MEDIUM; // 12px

    /**
     * 为阴影区域预留 insets，防止子组件（文字标签）渲染到玻璃框外部。
     * 与 GlassPanel / TurnPanel bubble 保持一致的处理方式。
     */
    @Override
    public Insets getInsets() {
        Insets base = super.getInsets();
        int s = LiquidGlassTheme.SHADOW_SPREAD;
        return new Insets(base.top + s, base.left + s, base.bottom + s, base.right + s);
    }

    /**
     * 限制首选宽度不超过父容器宽度，防止长文本撑破玻璃框。
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        Container p = getParent();
        if (p != null && p.getWidth() > 0) {
            d.width = Math.min(d.width, p.getWidth() - 20); // 20px 左右留白
        }
        return d;
    }

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
            if (drawW <= 0 || drawH <= 0) {
                return;
            }

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
                        CORNER_RADIUS + i, CORNER_RADIUS + i);
            }

            RoundRectangle2D clip = new RoundRectangle2D.Float(drawX, drawY, drawW, drawH, CORNER_RADIUS, CORNER_RADIUS);

            // 2. 半透明背景填充（圆角裁剪）
            g2.setClip(clip);
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(LiquidGlassTheme.PRIMARY_BG);
            g2.fillRoundRect(drawX, drawY, drawW, drawH, CORNER_RADIUS, CORNER_RADIUS);

            // 3. 高光反射条（顶部渐变淡出）
            Color hlColor = LiquidGlassTheme.HIGHLIGHT;
            int fadeH = 4;
            g2.setPaint(new GradientPaint(0, drawY, hlColor,
                    0, drawY + fadeH, new Color(hlColor.getRed(), hlColor.getGreen(), hlColor.getBlue(), 0)));
            g2.fillRect(drawX + CORNER_RADIUS / 2, drawY, drawW - CORNER_RADIUS, fadeH);

            g2.setClip(null);

            // 4. 边框描边（1px 半透明）
            g2.setColor(LiquidGlassTheme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(drawX, drawY, drawW - 1, drawH - 1, CORNER_RADIUS, CORNER_RADIUS);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    @Override
    public void onPhaseChanged(AgentStatusContext oldCtx, AgentStatusContext newCtx) {
        SwingUtilities.invokeLater(() -> updateDisplay(newCtx));
    }

    private void updateDisplay(AgentStatusContext ctx) {
        stopTimers();

        switch (ctx.getPhase()) {
            case IDLE -> setVisible(false);
            case RUNNING -> showRunning(ctx);
            case COMPLETED -> showTerminal(PluginIcons.SUCCESS, "已完成", COMPLETED_HIDE_DELAY_MS);
            case ERROR -> showTerminal(PluginIcons.ERROR,
                    ctx.getDetail() != null ? ctx.getDetail() : "出错",
                    ERROR_HIDE_DELAY_MS);
            case STOPPED -> showTerminal(PluginIcons.STOPPED, "已停止", STOPPED_HIDE_DELAY_MS);
        }
    }

    private void showRunning(AgentStatusContext ctx) {
        setVisible(true);
        iconLabel.setText("");
        textLabel.setText("运行中");
        detailLabel.setText(ctx.getDetail() != null ? ctx.getDetail() : "");
        startSpinner();
    }

    private void showTerminal(Icon icon, String text, int delayMs) {
        setVisible(true);
        stopSpinner();
        iconLabel.setText("");
        iconLabel.setIcon(icon);
        textLabel.setText(text);
        detailLabel.setText("");
        fadeOutTimer = new Timer(delayMs, e -> setVisible(false));
        fadeOutTimer.setRepeats(false);
        fadeOutTimer.start();
    }

    private void startSpinner() {
        spinnerAnimator = new SpinnerIconAnimator(PluginIcons.SPINNER, iconLabel);
        spinnerAnimator.start();
    }

    private void stopSpinner() {
        if (spinnerAnimator != null) {
            spinnerAnimator.stop();
            spinnerAnimator = null;
        }
        iconLabel.setIcon(null);
    }

    private void stopTimers() {
        stopSpinner();
        if (fadeOutTimer != null) {
            fadeOutTimer.stop();
            fadeOutTimer = null;
        }
    }

    @Override
    public void dispose() {
        stopTimers();
    }

    // Package-private accessors for testing
    Timer getFadeOutTimer() { return fadeOutTimer; }
    SpinnerIconAnimator getSpinnerAnimator() { return spinnerAnimator; }
    JLabel getIconLabel() { return iconLabel; }
    JLabel getTextLabel() { return textLabel; }
    JLabel getDetailLabel() { return detailLabel; }
}
