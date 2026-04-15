package com.wmsay.gpt4_lll.component.theme;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlassPanel 单元测试。
 * Validates: Requirements 2.6, 2.7
 */
class GlassPanelUnitTest {

    /**
     * 验证 BlurLevel 枚举恰好有 3 个值：LOW, MEDIUM, HIGH。
     * Validates: Requirements 2.6
     */
    @Test
    void blurLevelEnumHasExactlyThreeValues() {
        GlassPanel.BlurLevel[] values = GlassPanel.BlurLevel.values();
        assertEquals(3, values.length, "BlurLevel should have exactly 3 values");
        assertEquals(GlassPanel.BlurLevel.LOW, values[0]);
        assertEquals(GlassPanel.BlurLevel.MEDIUM, values[1]);
        assertEquals(GlassPanel.BlurLevel.HIGH, values[2]);
    }

    /**
     * 验证 BlurLevel kernelSize 映射：LOW=3, MEDIUM=5, HIGH=7。
     * Validates: Requirements 2.6
     */
    @Test
    void blurLevelKernelSizes() {
        assertEquals(3, GlassPanel.BlurLevel.LOW.getKernelSize(), "LOW kernel size should be 3");
        assertEquals(5, GlassPanel.BlurLevel.MEDIUM.getKernelSize(), "MEDIUM kernel size should be 5");
        assertEquals(7, GlassPanel.BlurLevel.HIGH.getKernelSize(), "HIGH kernel size should be 7");
    }

    /**
     * 验证使用每种 BlurLevel 构造 GlassPanel 不抛异常。
     * Validates: Requirements 2.6
     */
    @Test
    void constructionWithEachBlurLevelDoesNotThrow() {
        for (GlassPanel.BlurLevel level : GlassPanel.BlurLevel.values()) {
            assertDoesNotThrow(() -> new GlassPanel(12, level),
                    "Constructing GlassPanel with BlurLevel." + level + " should not throw");
        }
    }

    /**
     * 验证降级模式：在 headless/无硬件加速环境中调用 paintComponent 不抛异常。
     * Validates: Requirements 2.7
     */
    @Test
    void paintComponentInDegradationModeDoesNotThrow() {
        for (GlassPanel.BlurLevel level : GlassPanel.BlurLevel.values()) {
            GlassPanel panel = new GlassPanel(12, level);
            panel.setSize(200, 100);

            // 使用 BufferedImage 的 Graphics2D 模拟绘制（headless 安全）
            BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            try {
                assertDoesNotThrow(() -> panel.paintComponent(g2),
                        "paintComponent should not throw in headless environment with BlurLevel." + level);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 验证 getBlurLevel 返回构造函数中设置的值。
     * Validates: Requirements 2.6
     */
    @Test
    void getBlurLevelReturnsConstructorValue() {
        for (GlassPanel.BlurLevel level : GlassPanel.BlurLevel.values()) {
            GlassPanel panel = new GlassPanel(12, level);
            assertEquals(level, panel.getBlurLevel(),
                    "getBlurLevel() should return " + level);
        }
    }
}
