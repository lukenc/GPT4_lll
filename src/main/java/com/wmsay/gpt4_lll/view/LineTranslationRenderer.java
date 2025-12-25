package com.wmsay.gpt4_lll.view;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 单行翻译覆盖渲染器：仅在所属物理行内绘制翻译文本，避免跨行溢出
 */
public class LineTranslationRenderer implements CustomHighlighterRenderer {
    private final String lineTextToDisplay;

    public LineTranslationRenderer(String lineTextToDisplay) {
        this.lineTextToDisplay = lineTextToDisplay == null ? "" : lineTextToDisplay;
    }

    @Override
    public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
        if (lineTextToDisplay.isEmpty()) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int startOffset = highlighter.getStartOffset();

            Point startPoint = editor.offsetToXY(startOffset);
            int x = startPoint.x;
            int y = startPoint.y;
            int lineHeight = editor.getLineHeight();

            // 背景与边框（淡绿色覆盖）
            Color background = JBColor.namedColor("CommentTranslation.overlayBackground",
                new JBColor(new Color(230, 255, 230, 220), new Color(40, 80, 40, 220)));
            Color border = JBColor.namedColor("CommentTranslation.overlayBorder",
                new JBColor(new Color(120, 200, 120), new Color(80, 160, 80)));

            // 计算文本宽度以确定背景宽度
            Font font = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(lineTextToDisplay) + 8;

            g2d.setColor(background);
            g2d.fillRoundRect(x - 2, y, textWidth, lineHeight, 4, 4);
            g2d.setColor(border);
            g2d.drawRoundRect(x - 2, y, textWidth, lineHeight, 4, 4);

            // 绘制文字
            Color textColor = JBColor.namedColor("CommentTranslation.overlayText",
                new JBColor(new Color(0, 100, 0), new Color(150, 255, 150)));
            g2d.setColor(textColor);

            int baseline = y + (lineHeight + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(lineTextToDisplay, x + 4, baseline);
        } finally {
            g2d.dispose();
        }
    }
}


