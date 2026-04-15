package com.wmsay.gpt4_lll.component.theme;

import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * Liquid Glass 设计语言主题常量集中管理。
 * 所有颜色使用 JBColor 封装，自动适配 IntelliJ 浅色/深色模式。
 */
public final class LiquidGlassTheme {
    private LiquidGlassTheme() {}

    // ── 主背景色：浅色 alpha≈0.75, 深色 alpha≈0.65 ──
    public static final Color PRIMARY_BG_LIGHT = new Color(255, 255, 255, 191);
    public static final Color PRIMARY_BG_DARK = new Color(50, 52, 68, 178);
    public static final JBColor PRIMARY_BG = new JBColor(PRIMARY_BG_LIGHT, PRIMARY_BG_DARK);

    // ── 次背景色：浅色 alpha≈0.70, 深色 alpha≈0.60 ──
    public static final Color SECONDARY_BG_LIGHT = new Color(245, 245, 245, 179);
    public static final Color SECONDARY_BG_DARK = new Color(60, 62, 78, 165);
    public static final JBColor SECONDARY_BG = new JBColor(SECONDARY_BG_LIGHT, SECONDARY_BG_DARK);

    // ── 前景色 ──
    public static final JBColor FOREGROUND = new JBColor(new Color(33, 33, 33), new Color(220, 220, 220));

    // ── 强调色 ──
    public static final JBColor ACCENT = new JBColor(new Color(0x2196F3), new Color(0x42A5F5));

    // ── 成功色 ──
    public static final JBColor SUCCESS = new JBColor(new Color(0x4CAF50), new Color(0x66BB6A));

    // ── 错误色 ──
    public static final JBColor ERROR = new JBColor(new Color(0xF44336), new Color(0xEF5350));

    // ── 边框色 ──
    public static final JBColor BORDER = new JBColor(new Color(255, 255, 255, 100), new Color(255, 255, 255, 70));

    // ── 高光反射条参数 ──
    public static final Color HIGHLIGHT_LIGHT = new Color(255, 255, 255, 80);   // alpha≈0.31
    public static final Color HIGHLIGHT_DARK = new Color(255, 255, 255, 30);    // alpha≈0.12
    public static final JBColor HIGHLIGHT = new JBColor(HIGHLIGHT_LIGHT, HIGHLIGHT_DARK);
    public static final int HIGHLIGHT_WIDTH = 1;

    // ── 圆角半径 ──
    public static final int RADIUS_SMALL = 8;
    public static final int RADIUS_MEDIUM = 12;
    public static final int RADIUS_LARGE = 16;

    // ── 阴影参数 ──
    public static final Color SHADOW_COLOR_LIGHT = new Color(0, 0, 0, 30);
    public static final Color SHADOW_COLOR_DARK = new Color(0, 0, 0, 55);
    public static final JBColor SHADOW_COLOR = new JBColor(SHADOW_COLOR_LIGHT, SHADOW_COLOR_DARK);
    public static final int SHADOW_OFFSET_X = 0;
    public static final int SHADOW_OFFSET_Y = 2;
    /** 阴影预留空间（每侧）。GlassPanel 在四周预留此空间用于绘制柔和阴影。 */
    public static final int SHADOW_SPREAD = 4;
    /**
     * @deprecated 使用 {@link #SHADOW_SPREAD} 代替。保留以兼容旧代码。
     */
    @Deprecated
    public static final int SHADOW_BLUR_RADIUS = SHADOW_SPREAD * 2;

    // ── 间距规范（玻璃组件之间的统一间距）──
    /** 玻璃面板与父容器边缘的间距 */
    public static final int PANEL_MARGIN = 10;
    /** 玻璃面板之间的间距 */
    public static final int PANEL_GAP = 6;
    /** 玻璃面板内部内容的内边距 */
    public static final int PANEL_PADDING = 10;

    // ── 气泡 tint ──
    public static final JBColor USER_BUBBLE_TINT = new JBColor(
            new Color(33, 150, 243, 20),   // alpha≈0.08
            new Color(33, 150, 243, 31));   // alpha≈0.12
    public static final JBColor ASSISTANT_BUBBLE_TINT = new JBColor(
            new Color(128, 128, 128, 15),   // alpha≈0.06
            new Color(128, 128, 128, 26));   // alpha≈0.10

    // ── 状态 tint ──
    public static final JBColor IN_PROGRESS_TINT = new JBColor(
            new Color(33, 150, 243, 20), new Color(33, 150, 243, 20));
    public static final JBColor SUCCESS_TINT = new JBColor(
            new Color(76, 175, 80, 20), new Color(76, 175, 80, 20));
    public static final JBColor ERROR_TINT = new JBColor(
            new Color(244, 67, 54, 20), new Color(244, 67, 54, 20));

    // ── Spring 动画预设 ──
    public static final SpringConfig SPRING_FAST = new SpringConfig(300.0, 0.7, 0.0);
    public static final SpringConfig SPRING_STANDARD = new SpringConfig(200.0, 0.8, 0.0);
    public static final SpringConfig SPRING_SLOW = new SpringConfig(120.0, 0.85, 0.0);

    /**
     * 弹簧动画配置参数。
     *
     * @param stiffness       刚度，值越大弹簧越硬，动画越快
     * @param dampingRatio    阻尼比，0~1 之间，1=临界阻尼（无回弹）
     * @param initialVelocity 初始速度，通常为 0
     */
    public record SpringConfig(double stiffness, double dampingRatio, double initialVelocity) {}
}
