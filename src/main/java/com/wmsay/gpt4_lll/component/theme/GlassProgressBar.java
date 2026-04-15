package com.wmsay.gpt4_lll.component.theme;

import javax.swing.*;
import java.awt.*;

/**
 * 玻璃质感进度条：半透明轨道背景 + 带高光的填充条。
 */
public class GlassProgressBar extends JPanel {

    private static final int BAR_HEIGHT = 6;
    private static final int CORNER = 3;

    private double progress = 0.0; // 0.0 ~ 1.0
    private Color fillColor;

    public GlassProgressBar() {
        this(LiquidGlassTheme.ACCENT);
    }

    public GlassProgressBar(Color fillColor) {
        this.fillColor = fillColor;
        setOpaque(false);
        setPreferredSize(new Dimension(0, BAR_HEIGHT));
    }

    public double getProgress() { return progress; }

    public void setProgress(double progress) {
        this.progress = Math.max(0.0, Math.min(1.0, progress));
        repaint();
    }

    public void setFillColor(Color fillColor) { this.fillColor = fillColor; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int barY = (h - BAR_HEIGHT) / 2;

            // 轨道背景（半透明）
            g2.setColor(new Color(128, 128, 128, 40));
            g2.fillRoundRect(0, barY, w, BAR_HEIGHT, CORNER, CORNER);

            // 填充条
            int fillW = (int) (w * progress);
            if (fillW > 0) {
                g2.setColor(fillColor);
                g2.fillRoundRect(0, barY, fillW, BAR_HEIGHT, CORNER, CORNER);

                // 高光（填充条顶部 1px）
                g2.setColor(LiquidGlassTheme.HIGHLIGHT);
                g2.fillRect(CORNER / 2, barY, fillW - CORNER, 1);
            }
        } finally {
            g2.dispose();
        }
    }
}
