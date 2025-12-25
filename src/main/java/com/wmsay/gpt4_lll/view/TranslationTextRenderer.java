package com.wmsay.gpt4_lll.view;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 翻译文本的自定义渲染器
 * Custom renderer for translation text
 */
public class TranslationTextRenderer implements CustomHighlighterRenderer {
    private final String translatedText;
    private final String originalText;
    
    public TranslationTextRenderer(String translatedText, String originalText) {
        this.translatedText = translatedText;
        this.originalText = originalText;
    }
    
    @Override
    public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            // 设置抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 获取高亮区域的位置信息
            int startOffset = highlighter.getStartOffset();
            int endOffset = highlighter.getEndOffset();
            
            // 获取字符位置
            Point startPoint = editor.offsetToXY(startOffset);
            Point endPoint = editor.offsetToXY(endOffset);
            
            // 计算文本渲染区域
            int textX = startPoint.x;
            int textY = startPoint.y;
            int textWidth = endPoint.x - startPoint.x;
            int lineHeight = editor.getLineHeight();
            
            // 绘制背景
            Color backgroundColor = JBColor.namedColor("CommentTranslation.overlayBackground",
                new JBColor(new Color(230, 255, 230, 220), new Color(40, 80, 40, 220)));
            g2d.setColor(backgroundColor);
            g2d.fillRoundRect(textX - 2, textY, textWidth + 4, lineHeight, 4, 4);
            
            // 绘制边框
            Color borderColor = JBColor.namedColor("CommentTranslation.overlayBorder",
                new JBColor(new Color(120, 200, 120), new Color(80, 160, 80)));
            g2d.setColor(borderColor);
            g2d.drawRoundRect(textX - 2, textY, textWidth + 4, lineHeight, 4, 4);
            
            // 设置字体和颜色
            Font font = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
            g2d.setFont(font);
            
            Color textColor = JBColor.namedColor("CommentTranslation.overlayText",
                new JBColor(new Color(0, 100, 0), new Color(150, 255, 150)));
            g2d.setColor(textColor);
            
            // 准备翻译文本 - 保持注释格式
            String displayText = formatTranslatedComment(translatedText);
            
            // 绘制翻译文本
            FontMetrics fm = g2d.getFontMetrics();
            int textBaseline = textY + (lineHeight + fm.getAscent() - fm.getDescent()) / 2;
            
            // 如果是多行注释，需要分行显示
            String[] lines = displayText.split("\n");
            for (int i = 0; i < lines.length; i++) {
                g2d.drawString(lines[i], textX, textBaseline + i * lineHeight);
            }
            
        } finally {
            g2d.dispose();
        }
    }
    
    /**
     * 格式化翻译后的注释，保持注释符号
     */
    private String formatTranslatedComment(String translatedText) {
        if (translatedText == null || translatedText.trim().isEmpty()) {
            return "";
        }
        
        // 检查原文的注释格式
        if (originalText.trim().startsWith("//")) {
            // 单行注释
            return "// " + translatedText;
        } else if (originalText.trim().startsWith("/*")) {
            // 块注释
            if (originalText.contains("/**")) {
                // JavaDoc注释
                if (translatedText.contains("\n")) {
                    // 多行翻译
                    String[] lines = translatedText.split("\n");
                    StringBuilder formatted = new StringBuilder("/**\n");
                    for (String line : lines) {
                        formatted.append(" * ").append(line.trim()).append("\n");
                    }
                    formatted.append(" */");
                    return formatted.toString();
                } else {
                    // 单行翻译
                    return "/** " + translatedText + " */";
                }
            } else {
                // 普通块注释
                return "/* " + translatedText + " */";
            }
        }
        
        // 默认格式
        return "// " + translatedText;
    }
    
    public String getTranslatedText() {
        return translatedText;
    }
    
    public String getOriginalText() {
        return originalText;
    }
}
