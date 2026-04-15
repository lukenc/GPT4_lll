package com.wmsay.gpt4_lll.component.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LiquidGlassTheme 单元测试。
 * Validates: Requirements 1.2, 1.3, 1.4, 1.5, 15.1, 15.2
 */
class LiquidGlassThemeUnitTest {

    /**
     * 验证浅色模式主背景 alpha 在 [0.65, 0.85] 范围内（即 alpha 值 166-217）。
     * Validates: Requirements 1.2
     */
    @Test
    void primaryBgLightAlphaInRange() {
        int alpha = LiquidGlassTheme.PRIMARY_BG_LIGHT.getAlpha();
        assertTrue(alpha >= 166 && alpha <= 217,
                "Light mode PRIMARY_BG alpha should be in [166, 217] (0.65-0.85), but was " + alpha);
    }

    /**
     * 验证深色模式主背景 alpha 在 [0.55, 0.75] 范围内（即 alpha 值 140-191）。
     * Validates: Requirements 1.2
     */
    @Test
    void primaryBgDarkAlphaInRange() {
        int alpha = LiquidGlassTheme.PRIMARY_BG_DARK.getAlpha();
        assertTrue(alpha >= 140 && alpha <= 191,
                "Dark mode PRIMARY_BG alpha should be in [140, 191] (0.55-0.75), but was " + alpha);
    }

    /**
     * 验证圆角半径常量：RADIUS_SMALL=8, RADIUS_MEDIUM=12, RADIUS_LARGE=16。
     * Validates: Requirements 1.3
     */
    @Test
    void radiusConstants() {
        assertEquals(8, LiquidGlassTheme.RADIUS_SMALL, "RADIUS_SMALL should be 8");
        assertEquals(12, LiquidGlassTheme.RADIUS_MEDIUM, "RADIUS_MEDIUM should be 12");
        assertEquals(16, LiquidGlassTheme.RADIUS_LARGE, "RADIUS_LARGE should be 16");
    }

    /**
     * 验证阴影常量存在且非 null。
     * Validates: Requirements 1.4
     */
    @Test
    void shadowConstantsExistAndNonNull() {
        assertNotNull(LiquidGlassTheme.SHADOW_COLOR, "SHADOW_COLOR should not be null");
        // SHADOW_OFFSET_X, SHADOW_OFFSET_Y, SHADOW_BLUR_RADIUS are primitive ints — just verify they are accessible
        assertEquals(0, LiquidGlassTheme.SHADOW_OFFSET_X, "SHADOW_OFFSET_X should be defined");
        assertEquals(2, LiquidGlassTheme.SHADOW_OFFSET_Y, "SHADOW_OFFSET_Y should be defined");
        assertTrue(LiquidGlassTheme.SHADOW_BLUR_RADIUS > 0,
                "SHADOW_BLUR_RADIUS should be positive, was " + LiquidGlassTheme.SHADOW_BLUR_RADIUS);
    }

    /**
     * 验证高光常量非 null 且 HIGHLIGHT_WIDTH=1。
     * Validates: Requirements 1.5
     */
    @Test
    void highlightConstantsValid() {
        assertNotNull(LiquidGlassTheme.HIGHLIGHT, "HIGHLIGHT should not be null");
        assertEquals(1, LiquidGlassTheme.HIGHLIGHT_WIDTH, "HIGHLIGHT_WIDTH should be 1");
    }

    /**
     * 验证 3 种 SpringConfig 预设存在且参数有效（stiffness > 0, 0 < dampingRatio <= 1）。
     * Validates: Requirements 15.1, 15.2
     */
    @Test
    void springConfigPresetsValid() {
        LiquidGlassTheme.SpringConfig[] presets = {
                LiquidGlassTheme.SPRING_FAST,
                LiquidGlassTheme.SPRING_STANDARD,
                LiquidGlassTheme.SPRING_SLOW
        };

        for (LiquidGlassTheme.SpringConfig cfg : presets) {
            assertNotNull(cfg, "SpringConfig preset should not be null");
            assertTrue(cfg.stiffness() > 0,
                    "stiffness should be > 0, was " + cfg.stiffness());
            assertTrue(cfg.dampingRatio() > 0 && cfg.dampingRatio() <= 1,
                    "dampingRatio should be in (0, 1], was " + cfg.dampingRatio());
        }
    }
}
