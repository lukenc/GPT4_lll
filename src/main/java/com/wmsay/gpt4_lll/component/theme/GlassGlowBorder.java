package com.wmsay.gpt4_lll.component.theme;

import java.awt.*;

/**
 * 发光边框绘制工具。
 * 通过多层递减 alpha 的圆角矩形实现柔和发光效果。
 */
public final class GlassGlowBorder {
    private GlassGlowBorder() {}

    private static final int GLOW_LAYERS = 4;
    private static final float BORDER_THICKNESS = 1.5f;

    /**
     * 在指定 Graphics2D 上绘制发光边框。
     *
     * @param g2        目标 Graphics2D
     * @param glowColor 发光颜色（不含 alpha，alpha 由层级自动计算）
     * @param x         左上角 x
     * @param y         左上角 y
     * @param width     宽度
     * @param height    高度
     * @param radius    圆角半径
     */
    public static void paint(Graphics2D g2, Color glowColor, int x, int y, int width, int height, int radius) {
        Graphics2D gc = (Graphics2D) g2.create();
        try {
            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = GLOW_LAYERS - 1; i >= 0; i--) {
                int alpha = (int) (255 * 0.1f * (GLOW_LAYERS - i));
                alpha = Math.min(255, Math.max(0, alpha));
                gc.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), alpha));
                gc.setStroke(new BasicStroke(BORDER_THICKNESS + i * 2));
                gc.drawRoundRect(x + i, y + i, width - i * 2, height - i * 2, radius, radius);
            }
        } finally {
            gc.dispose();
        }
    }
}
