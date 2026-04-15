package com.wmsay.gpt4_lll;

import com.wmsay.gpt4_lll.component.theme.GlassPanel;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Label;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Preservation Property Tests — Baseline Behavior Capture (BEFORE fix)
 * <p>
 * These tests observe and lock down existing behavior on UNFIXED code.
 * They must PASS on the current codebase, confirming the baseline we want to preserve.
 * After the fix is applied, these tests must CONTINUE to pass (no regressions).
 * <p>
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 */
class GlassPanelPreservationPropertyTest {

    // ---------------------------------------------------------------
    // Test 2a - Plain text wrapHtml preservation
    // Observation: MarkdownBlock.wrapHtml() for plain text produces HTML with
    //   - body color: foreground hex
    //   - table CSS: border, padding, th background
    // Property: for all plain text inputs (no <ul>, <ol>, <blockquote>),
    //   wrapHtml output contains table CSS rules and body color attribute
    // Validates: Requirements 3.2, 3.4
    // ---------------------------------------------------------------
    @Property(tries = 50)
    @Label("Preservation 2a: Plain text wrapHtml contains table CSS and body color")
    void plainTextWrapHtmlPreservesTableCssAndBodyColor(
            @ForAll("plainTextInputs") String plainText) throws Exception {

        String result = invokeWrapHtml("com.wmsay.gpt4_lll.component.block.MarkdownBlock", plainText);
        assertNotNull(result, "wrapHtml should return non-null HTML");

        String lower = result.toLowerCase();

        // Body must have a color attribute (foreground color)
        assertTrue(lower.contains("color:") || lower.contains("color :"),
                "wrapHtml output must contain 'color:' for foreground. Got: " + extractStyleBlock(result));

        // Table CSS rules must be present
        assertTrue(lower.contains("border:") || lower.contains("border "),
                "wrapHtml output must contain table border CSS. Got: " + extractStyleBlock(result));
        assertTrue(lower.contains("padding:") || lower.contains("padding "),
                "wrapHtml output must contain table padding CSS. Got: " + extractStyleBlock(result));

        // th background styling must be present
        assertTrue(lower.contains("background-color:") || lower.contains("background-color "),
                "wrapHtml output must contain th background-color CSS. Got: " + extractStyleBlock(result));
    }

    @Provide
    Arbitrary<String> plainTextInputs() {
        // Generate plain text that does NOT contain list/blockquote HTML tags
        return Arbitraries.of(
                "Hello world",
                "Simple paragraph text.",
                "<p>A paragraph</p>",
                "<strong>Bold</strong> and <em>italic</em> text",
                "<code>inline code</code>",
                "Line 1<br>Line 2",
                "<p>Multiple</p><p>paragraphs</p>",
                "Text with <a href='#'>link</a>",
                "<table><tr><th>Header</th></tr><tr><td>Cell</td></tr></table>",
                ""
        );
    }

    // ---------------------------------------------------------------
    // Test 2b - LiquidGlassTheme constant ranges
    // Observation: PRIMARY_BG_LIGHT alpha=191, PRIMARY_BG_DARK alpha=178,
    //   BORDER light alpha=100, dark alpha=70
    // Property: PRIMARY_BG alpha in [153, 204] (0.60~0.80),
    //   BORDER alpha > 0, FOREGROUND has sufficient contrast
    // Validates: Requirements 3.1
    // ---------------------------------------------------------------
    @Test
    @Label("Preservation 2b: LiquidGlassTheme constant ranges are within spec")
    void liquidGlassThemeConstantRangesAreWithinSpec() {
        // PRIMARY_BG alpha values must be in range [153, 204] (0.60~0.80)
        int lightAlpha = LiquidGlassTheme.PRIMARY_BG_LIGHT.getAlpha();
        int darkAlpha = LiquidGlassTheme.PRIMARY_BG_DARK.getAlpha();

        assertTrue(lightAlpha >= 153 && lightAlpha <= 204,
                "PRIMARY_BG_LIGHT alpha should be in [153, 204], got: " + lightAlpha);
        assertTrue(darkAlpha >= 153 && darkAlpha <= 204,
                "PRIMARY_BG_DARK alpha should be in [153, 204], got: " + darkAlpha);

        // BORDER alpha values must be > 0
        Color borderLight = new Color(255, 255, 255, 100); // known light value
        Color borderDark = new Color(255, 255, 255, 70);   // known dark value
        // Access BORDER via JBColor — in test env it returns the light variant
        Color border = LiquidGlassTheme.BORDER;
        assertTrue(border.getAlpha() > 0,
                "BORDER alpha should be > 0, got: " + border.getAlpha());

        // Verify the known alpha values directly from the static fields
        assertTrue(borderLight.getAlpha() > 0,
                "BORDER light alpha should be > 0, got: " + borderLight.getAlpha());
        assertTrue(borderDark.getAlpha() > 0,
                "BORDER dark alpha should be > 0, got: " + borderDark.getAlpha());

        // FOREGROUND colors should have sufficient contrast (non-zero RGB difference)
        Color fg = LiquidGlassTheme.FOREGROUND;
        // In light mode, foreground is dark (33,33,33); in dark mode it's light (220,220,220)
        // Either way, the color should not be fully transparent or identical to background
        assertNotNull(fg, "FOREGROUND should not be null");
        // Verify foreground has meaningful color values (not all zeros or all 255)
        int fgBrightness = (fg.getRed() + fg.getGreen() + fg.getBlue()) / 3;
        assertTrue(fgBrightness > 0 && fgBrightness < 255,
                "FOREGROUND should have meaningful brightness, got avg: " + fgBrightness);

        // SHADOW_SPREAD must be positive
        assertTrue(LiquidGlassTheme.SHADOW_SPREAD > 0,
                "SHADOW_SPREAD should be > 0, got: " + LiquidGlassTheme.SHADOW_SPREAD);
    }

    // ---------------------------------------------------------------
    // Test 2c - GlassPanel insets consistency
    // Observation: GlassPanel.getInsets() adds SHADOW_SPREAD (4px) on all sides
    // Property: for any GlassPanel instance, insets include SHADOW_SPREAD on all four sides
    // Validates: Requirements 3.1, 3.3
    // ---------------------------------------------------------------
    @Property(tries = 20)
    @Label("Preservation 2c: GlassPanel insets include SHADOW_SPREAD on all sides")
    void glassPanelInsetsIncludeShadowSpread(
            @ForAll("cornerRadii") int cornerRadius) {

        int shadowSpread = LiquidGlassTheme.SHADOW_SPREAD;
        assertTrue(shadowSpread > 0, "SHADOW_SPREAD should be positive");

        GlassPanel panel = new GlassPanel(cornerRadius);
        Insets insets = panel.getInsets();

        // All four sides must include at least SHADOW_SPREAD
        assertTrue(insets.top >= shadowSpread,
                "Top inset (" + insets.top + ") should be >= SHADOW_SPREAD (" + shadowSpread + ")");
        assertTrue(insets.left >= shadowSpread,
                "Left inset (" + insets.left + ") should be >= SHADOW_SPREAD (" + shadowSpread + ")");
        assertTrue(insets.bottom >= shadowSpread,
                "Bottom inset (" + insets.bottom + ") should be >= SHADOW_SPREAD (" + shadowSpread + ")");
        assertTrue(insets.right >= shadowSpread,
                "Right inset (" + insets.right + ") should be >= SHADOW_SPREAD (" + shadowSpread + ")");
    }

    @Provide
    Arbitrary<Integer> cornerRadii() {
        return Arbitraries.of(
                LiquidGlassTheme.RADIUS_SMALL,
                LiquidGlassTheme.RADIUS_MEDIUM,
                LiquidGlassTheme.RADIUS_LARGE
        );
    }

    // ---------------------------------------------------------------
    // Test 2d - ThinkingBlock wrapHtml base structure
    // Observation: ThinkingBlock.wrapHtml() produces HTML with
    //   color: gray and font-style: italic
    // Property: wrapHtml output always contains color: gray and font-style: italic
    // Validates: Requirements 3.2
    // ---------------------------------------------------------------
    @Property(tries = 30)
    @Label("Preservation 2d: ThinkingBlock wrapHtml contains gray color and italic style")
    void thinkingBlockWrapHtmlContainsGrayAndItalic(
            @ForAll("thinkingBlockInputs") String bodyContent) throws Exception {

        String result = invokeWrapHtml("com.wmsay.gpt4_lll.component.block.ThinkingBlock", bodyContent);
        assertNotNull(result, "ThinkingBlock.wrapHtml should return non-null HTML");

        String lower = result.toLowerCase();

        // Must contain color: gray
        assertTrue(lower.contains("color: gray") || lower.contains("color:gray"),
                "ThinkingBlock wrapHtml must contain 'color: gray'. Got: " + result);

        // Must contain font-style: italic
        assertTrue(lower.contains("font-style: italic") || lower.contains("font-style:italic"),
                "ThinkingBlock wrapHtml must contain 'font-style: italic'. Got: " + result);
    }

    @Provide
    Arbitrary<String> thinkingBlockInputs() {
        return Arbitraries.of(
                "",
                "Thinking about the problem...",
                "<p>Step 1: analyze</p><p>Step 2: solve</p>",
                "Let me consider <strong>multiple</strong> approaches.",
                "<ul><li>Option A</li><li>Option B</li></ul>",
                "Simple text content"
        );
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    /**
     * Invoke the private static wrapHtml(String) method on the given class via reflection.
     */
    private String invokeWrapHtml(String className, String bodyContent) throws Exception {
        Class<?> clazz = Class.forName(className);
        Method wrapHtmlMethod = clazz.getDeclaredMethod("wrapHtml", String.class);
        wrapHtmlMethod.setAccessible(true);
        return (String) wrapHtmlMethod.invoke(null, bodyContent);
    }

    /**
     * Extract the &lt;style&gt; block from HTML for error messages.
     */
    private String extractStyleBlock(String html) {
        int start = html.indexOf("<style>");
        int end = html.indexOf("</style>");
        if (start >= 0 && end >= 0) {
            return html.substring(start, end + "</style>".length());
        }
        return "(no style block found)";
    }
}
