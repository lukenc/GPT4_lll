package com.wmsay.gpt4_lll.component.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 玻璃质感按钮：半透明圆角背景 + 高光反射条 + 柔和阴影 + 悬停反馈。
 * 按钮使用全部可用空间绘制（无阴影预留），阴影通过 inset 负偏移实现微妙的底部发光。
 */
public class GlassButton extends JButton {

    private final int cornerRadius;
    private final Color tintColor;
    private boolean hovered = false;

    public GlassButton(String text, int cornerRadius, Color tintColor) {
        super(text);
        this.cornerRadius = cornerRadius;
        this.tintColor = tintColor;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override
            public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
        });
    }

    public GlassButton(String text, int cornerRadius) {
        this(text, cornerRadius, LiquidGlassTheme.ACCENT);
    }

    public boolean isHovered() { return hovered; }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // 柔和底部阴影（2层渐变）
            int shadowAlpha = LiquidGlassTheme.SHADOW_COLOR.getAlpha();
            for (int i = 2; i >= 1; i--) {
                float ratio = (float) i / 2;
                int alpha = Math.max(0, (int)(shadowAlpha * (1f - ratio) * 0.5f));
                g2.setColor(new Color(0, 0, 0, alpha));
                g2.drawRoundRect(i, LiquidGlassTheme.SHADOW_OFFSET_Y + i,
                        w - 1 - i, h - 1 - i, cornerRadius + i, cornerRadius + i);
            }

            // 半透明背景
            int baseAlpha = hovered ? 65 : 25;
            Color bg = new Color(tintColor.getRed(), tintColor.getGreen(), tintColor.getBlue(), baseAlpha);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);

            // 高光反射条（顶部渐变淡出）
            Color hlColor = LiquidGlassTheme.HIGHLIGHT;
            int fadeH = 4;
            g2.setPaint(new GradientPaint(0, 0, hlColor,
                    0, fadeH, new Color(hlColor.getRed(), hlColor.getGreen(), hlColor.getBlue(), 0)));
            g2.fillRect(cornerRadius / 2, 0, w - cornerRadius, fadeH);

            // 边框
            g2.setColor(LiquidGlassTheme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
