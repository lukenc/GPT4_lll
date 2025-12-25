package com.wmsay.gpt4_lll.view;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 段落级翻译渲染器：支持自动换行，覆盖段落区域并避免与原文重叠
 */
public class ParagraphTranslationRenderer implements CustomHighlighterRenderer {
    private final String translatedText;

    public ParagraphTranslationRenderer(String translatedText) {
        this.translatedText = translatedText == null ? "" : translatedText.trim();
    }

    @Override
    public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
        if (translatedText.isEmpty()) return;

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int startOffset = highlighter.getStartOffset();
            int endOffset = highlighter.getEndOffset();

            int startLine = editor.getDocument().getLineNumber(startOffset);
            int endLine = editor.getDocument().getLineNumber(Math.max(startOffset, endOffset));

            EditorColorsScheme scheme = editor.getColorsScheme();
            Font font = scheme.getFont(EditorFontType.PLAIN);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            int lineHeight = editor.getLineHeight();

            // 逐行计算可用宽度，并进行软换行
            int currentLine = startLine;
            List<String> lines = wrapTextToRegion(editor, translatedText, startOffset, startLine, endLine, fm);

            // 背景与边框，使用不透明背景覆盖原文，避免重叠
            Color editorBg = scheme.getDefaultBackground();
            // 使用完全不透明的背景色确保盖住底下文本
            Color overlayBg = JBColor.namedColor("CommentTranslation.overlayBackgroundOpaque",
                    new JBColor(new Color(230, 255, 230, 255), new Color(30, 60, 30, 255)));
            Color border = JBColor.namedColor("CommentTranslation.overlayBorder",
                    new JBColor(new Color(120, 200, 120), new Color(80, 160, 80)));
            Color textColor = JBColor.namedColor("CommentTranslation.overlayText",
                    new JBColor(new Color(0, 80, 0), new Color(170, 255, 170)));

            for (int i = 0; i < lines.size(); i++) {
                if (currentLine > endLine) break;
                int docLineStart = editor.getDocument().getLineStartOffset(currentLine);
                int docLineEnd = editor.getDocument().getLineEndOffset(currentLine);
                Point pStart = editor.offsetToXY(docLineStart);
                Point pEnd = editor.offsetToXY(docLineEnd);

                int x = editor.offsetToXY(i == 0 ? startOffset : docLineStart).x;
                int y = pStart.y;
                int availableWidth = pEnd.x - x;
                int usedWidth = Math.min(availableWidth, fm.stringWidth(lines.get(i)) + 8);

                // 先用编辑器背景完全覆盖（不透明），再绘制我们的覆盖背景
                g2d.setComposite(AlphaComposite.SrcOver);
                g2d.setColor(editorBg);
                g2d.fillRect(x - 2, y, availableWidth + 4, lineHeight);

                g2d.setColor(overlayBg);
                g2d.fillRoundRect(x - 2, y, usedWidth, lineHeight, 4, 4);
                g2d.setColor(border);
                g2d.drawRoundRect(x - 2, y, usedWidth, lineHeight, 4, 4);

                g2d.setColor(textColor);
                int baseline = y + (lineHeight + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(lines.get(i), x + 4, baseline);

                currentLine++;
            }
        } finally {
            g2d.dispose();
        }
    }

    private List<String> wrapTextToRegion(Editor editor, String text, int startOffset, int startLine, int endLine, FontMetrics fm) {
        List<String> wrapped = new ArrayList<>();
        int idx = 0;
        int line = startLine;
        while (idx < text.length() && line <= endLine) {
            int docLineStart = editor.getDocument().getLineStartOffset(line);
            int docLineEnd = editor.getDocument().getLineEndOffset(line);
            int xStart = editor.offsetToXY(line == startLine ? startOffset : docLineStart).x;
            int xEnd = editor.offsetToXY(docLineEnd).x;
            int width = Math.max(0, xEnd - xStart);

            int endIdx = findFittingEnd(text, idx, width, fm);
            if (endIdx <= idx) {
                break;
            }
            wrapped.add(text.substring(idx, endIdx).trim());
            idx = endIdx;
            // 跳过一个空格以避免下一行以空格开头
            if (idx < text.length() && text.charAt(idx) == ' ') idx++;
            line++;
        }
        // 如果还有剩余文本但没有更多行，拼到最后一行尾部（避免丢失）
        if (idx < text.length() && !wrapped.isEmpty()) {
            String last = wrapped.remove(wrapped.size() - 1);
            wrapped.add((last + " " + text.substring(idx)).trim());
        } else if (idx < text.length() && wrapped.isEmpty()) {
            wrapped.add(text.substring(idx));
        }
        return wrapped;
    }

    private int findFittingEnd(String text, int start, int maxWidth, FontMetrics fm) {
        if (maxWidth <= 0) return start;
        int end = start;
        int lastBreak = -1;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isWhitespace(c) || c == '-') lastBreak = end;
            String sub = text.substring(start, end + 1);
            int w = fm.stringWidth(sub);
            if (w > maxWidth) {
                return lastBreak > start ? lastBreak : end;
            }
            end++;
        }
        return end;
    }
}


