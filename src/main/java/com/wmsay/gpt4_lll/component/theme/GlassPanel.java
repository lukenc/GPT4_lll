package com.wmsay.gpt4_lll.component.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * 磨砂玻璃材质基础面板。
 * 渲染管线：柔和阴影 → 半透明背景 → 圆角裁剪 → 可选高斯模糊 → 高光反射条 → 边框描边。
 * 当硬件加速不可用时自动降级为纯半透明填充。
 *
 * 阴影使用四周均匀的 {@link LiquidGlassTheme#SHADOW_SPREAD} 预留空间，
 * 通过多层渐变 alpha 实现柔和扩散效果，避免硬线条。
 */
public class GlassPanel extends JPanel {

    /** 模糊强度等级 */
    public enum BlurLevel {
        LOW(3), MEDIUM(5), HIGH(7);

        private final int kernelSize;
        BlurLevel(int kernelSize) { this.kernelSize = kernelSize; }
        public int getKernelSize() { return kernelSize; }
    }

    private static final boolean BLUR_AVAILABLE;
    static {
        boolean available;
        try {
            var ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            var gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
            available = gc.isTranslucencyCapable();
        } catch (Exception e) {
            available = false;
        }
        BLUR_AVAILABLE = available;
    }

    private int cornerRadius;
    private BlurLevel blurLevel;
    private Color bgColor;

    public GlassPanel(int cornerRadius, BlurLevel blurLevel) {
        this.cornerRadius = cornerRadius;
        this.blurLevel = blurLevel;
        this.bgColor = LiquidGlassTheme.PRIMARY_BG;
        setOpaque(false);
    }

    public GlassPanel(int cornerRadius) {
        this(cornerRadius, BlurLevel.MEDIUM);
    }

    public BlurLevel getBlurLevel() { return blurLevel; }

    public void setBlurLevel(BlurLevel blurLevel) { this.blurLevel = blurLevel; repaint(); }

    public int getCornerRadius() { return cornerRadius; }

    public void setCornerRadius(int cornerRadius) { this.cornerRadius = cornerRadius; repaint(); }

    public Color getBgColor() { return bgColor; }

    public void setBgColor(Color bgColor) { this.bgColor = bgColor; repaint(); }

    @Override
    public Insets getInsets() {
        Insets base = super.getInsets();
        int s = LiquidGlassTheme.SHADOW_SPREAD;
        return new Insets(base.top + s, base.left + s, base.bottom + s, base.right + s);
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

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int s = LiquidGlassTheme.SHADOW_SPREAD;

            // 内容绘制区域（去掉四周阴影预留）
            int drawX = s;
            int drawY = s;
            int drawW = w - s * 2;
            int drawH = h - s * 2;
            if (drawW <= 0 || drawH <= 0) return;

            // 1. 柔和阴影（在内容区域外围绘制渐变）
            paintSoftShadow(g2, drawX, drawY, drawW, drawH);

            RoundRectangle2D clip = new RoundRectangle2D.Float(drawX, drawY, drawW, drawH, cornerRadius, cornerRadius);

            // 2. 半透明背景填充（圆角裁剪，与 viewport clip 取交集）
            g2.clip(clip);
            g2.setComposite(AlphaComposite.SrcOver);

            if (BLUR_AVAILABLE && blurLevel != null) {
                paintBlurredBackground(g2, drawX, drawY, drawW, drawH);
            } else {
                g2.setColor(bgColor);
                g2.fillRoundRect(drawX, drawY, drawW, drawH, cornerRadius, cornerRadius);
            }

            // 3. 高光反射条（顶部渐变淡出，避免硬线条）
            int hlX = drawX + cornerRadius / 2;
            int hlW = drawW - cornerRadius;
            int fadeHeight = 4; // 渐变过渡高度（px）
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

    /**
     * 绘制柔和扩散阴影。使用多层渐变 alpha 的圆角矩形，
     * 从内到外 alpha 递减，模拟真实的玻璃投影效果。
     */
    private void paintSoftShadow(Graphics2D g2, int x, int y, int w, int h) {
        int spread = LiquidGlassTheme.SHADOW_SPREAD;
        int baseAlpha = LiquidGlassTheme.SHADOW_COLOR.getAlpha();
        int oy = LiquidGlassTheme.SHADOW_OFFSET_Y;

        for (int i = spread; i >= 1; i--) {
            float ratio = (float) i / spread;
            int alpha = Math.max(0, (int)(baseAlpha * (1f - ratio) * 0.6f));
            g2.setColor(new Color(0, 0, 0, alpha));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x - i, y - i + oy, w + i * 2 - 1, h + i * 2 - 1,
                    cornerRadius + i, cornerRadius + i);
        }
    }

    private void paintBlurredBackground(Graphics2D g2, int x, int y, int w, int h) {
        try {
            BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = buf.createGraphics();
            bg.setColor(bgColor);
            bg.fillRect(0, 0, w, h);
            bg.dispose();

            int ks = blurLevel.getKernelSize();
            float[] data = new float[ks * ks];
            float val = 1.0f / (ks * ks);
            java.util.Arrays.fill(data, val);
            ConvolveOp op = new ConvolveOp(new Kernel(ks, ks, data), ConvolveOp.EDGE_NO_OP, null);
            BufferedImage blurred = op.filter(buf, null);
            g2.drawImage(blurred, x, y, null);
        } catch (Exception e) {
            g2.setColor(bgColor);
            g2.fillRoundRect(x, y, w, h, cornerRadius, cornerRadius);
        }
    }
}
