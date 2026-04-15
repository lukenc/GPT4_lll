package com.wmsay.gpt4_lll.component.theme;

import javax.swing.*;
import java.awt.*;

/**
 * 渐变透明度竖线组件。
 * 从上到下绘制 GradientPaint：lineColor(alpha=0.8) → lineColor(alpha=0.2)。
 */
public class GlassVerticalLine extends JPanel {

    private static final int LINE_WIDTH = 3;
    private Color lineColor;

    public GlassVerticalLine(Color lineColor) {
        this.lineColor = lineColor;
        setOpaque(false);
        setPreferredSize(new Dimension(LINE_WIDTH, 0));
    }

    public Color getLineColor() { return lineColor; }

    public void setLineColor(Color lineColor) {
        this.lineColor = lineColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (getHeight() <= 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color top = withAlpha(lineColor, 204);   // ~0.8
            Color bottom = withAlpha(lineColor, 51);  // ~0.2
            GradientPaint gradient = new GradientPaint(0, 0, top, 0, getHeight(), bottom);
            g2.setPaint(gradient);
            g2.fillRect(0, 0, LINE_WIDTH, getHeight());
        } finally {
            g2.dispose();
        }
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
