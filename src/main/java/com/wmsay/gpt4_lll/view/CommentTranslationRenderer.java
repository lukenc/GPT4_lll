package com.wmsay.gpt4_lll.view;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.model.CommentTranslation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 注释翻译渲染器，负责在编辑器中显示翻译内容
 * Comment translation renderer responsible for displaying translations in the editor
 */
public class CommentTranslationRenderer {
    private static final Logger log = LoggerFactory.getLogger(CommentTranslationRenderer.class);
    private static final String FOLD_PLACEHOLDER_PREFIX = "[GPT4LLL] ";
    
    private final Editor editor;
    private final Map<RangeHighlighter, CommentTranslation> highlighterMap;
    private final List<FoldRegion> foldRegions = new java.util.ArrayList<>();
    private boolean translationMode = false;
    
    public CommentTranslationRenderer(Project project, Editor editor) {
        this.editor = editor;
        this.highlighterMap = new HashMap<>();
    }
    
    /**
     * 初始化翻译模式（为流式翻译做准备）
     * Initialize translation mode (prepare for streaming translation)
     */
    public void initializeTranslationMode() {
        clearTranslations();
        translationMode = true;
        log.info("Translation mode initialized for streaming");
    }
    
    /**
     * 添加单个翻译显示（用于流式翻译）
     * Add single translation display (for streaming translation)
     */
    public void addTranslation(CommentTranslation translation) {
        if (translation == null || 
            translation.getTranslatedComment() == null ||
            translation.getTranslatedComment().trim().isEmpty() ||
            translation.getTranslatedComment().equals(translation.getOriginalComment())) {
            log.debug("Skipping invalid translation: {}", 
                     translation != null ? translation.getTranslatedComment() : "null");
            return;
        }
        
        // 额外验证：跳过明显错误的翻译结果
        String translated = translation.getTranslatedComment().trim();
        if (translated.matches("^```+.*$") ||  // 代码块标记
            translated.length() < 2 ||  // 太短
            translated.startsWith("[翻译") ||  // 翻译失败标记
            translated.startsWith("[解析")) {  // 解析失败标记
            log.warn("Skipping problematic translation result: {}", translated);
            return;
        }
        
        try {
            FoldingModel foldingModel = editor.getFoldingModel();
            String original = translation.getOriginalComment();

            // 使用 FoldRegion 折叠整个段落区域，隐藏原文并以占位渲染译文
            String placeholder = formatTranslationWithExplanation(original, translated).replaceAll("\n", " ");
            // 标记占位以便后续识别与清理
            placeholder = FOLD_PLACEHOLDER_PREFIX + placeholder;
            if (placeholder.length() > 300) {
                placeholder = placeholder.substring(0, 300) + " …";
            }

            final String ph = placeholder;
            int startOffset = translation.getStartOffset();
            int endOffset = Math.max(startOffset, translation.getEndOffset());
            
            foldingModel.runBatchFoldingOperation(() -> {
                FoldRegion region = foldingModel.addFoldRegion(startOffset, endOffset, ph);
                if (region != null) {
                    region.setExpanded(false); // 折叠以显示占位（译文）
                    foldRegions.add(region);
                }
            });
            
            // 创建详细的工具提示，在悬停时显示完整对比
            final String tooltip = String.format(
                "<html><body style='max-width: 500px;'>" +
                "<div style='font-family: Arial, sans-serif; padding: 10px;'>" +
                "<div style='background-color: #4CAF50; color: white; padding: 8px; margin-bottom: 10px; text-align: center;'>" +
                "<b>🔄 注释翻译模式</b>" +
                "</div>" +
                
                "<div style='background-color: #f8f9fa; border-left: 4px solid #6c757d; padding: 8px; margin: 8px 0;'>" +
                "<div style='color: #495057; font-weight: bold; margin-bottom: 4px;'>📝 原始注释:</div>" +
                "<div style='font-family: Consolas, Monaco, monospace; color: #6c757d; font-size: 12px;'>%s</div>" +
                "</div>" +
                
                "<div style='background-color: #e8f5e8; border-left: 4px solid #28a745; padding: 8px; margin: 8px 0;'>" +
                "<div style='color: #155724; font-weight: bold; margin-bottom: 4px;'>✨ 中文翻译:</div>" +
                "<div style='font-family: Consolas, Monaco, monospace; color: #155724; font-size: 12px; font-weight: bold;'>%s</div>" +
                "</div>" +
                
                "<div style='text-align: center; margin-top: 10px; padding: 6px; background-color: #fff3cd;'>" +
                "<small style='color: #856404;'>💡 右键菜单选择 \"Show Original Comments\" 可切换回原文显示</small>" +
                "</div>" +
                "</div>" +
                "</body></html>",
                escapeHtml(translation.getOriginalComment()),
                escapeHtml(translated)
            );
            
            // 将提示绑定到最后一行的高亮上，避免重复
            if (!highlighterMap.isEmpty()) {
                RangeHighlighter last = null;
                for (RangeHighlighter rh : highlighterMap.keySet()) {
                    last = rh;
                }
                if (last != null) {
                    last.setErrorStripeTooltip(tooltip);
                }
            }
            
            log.info("Added streaming translation display for: '{}' -> '{}'", 
                     translation.getOriginalComment().replaceAll("\n", "\\n"), 
                     translation.getTranslatedComment().replaceAll("\n", "\\n"));
            
        } catch (Exception e) {
            log.error("Failed to add streaming translation display", e);
        }
    }
    
    /**
     * 显示翻译模式（批量显示所有翻译）
     * Show translation mode (batch display all translations)
     */
    public void showTranslations(List<CommentTranslation> translations) {
        initializeTranslationMode();
        
        if (translations == null || translations.isEmpty()) {
            return;
        }
        
        for (CommentTranslation translation : translations) {
            addTranslation(translation);
        }
        
        log.info("Successfully displayed {} translation highlights", foldRegions.size());
    }

    private String formatAsComment(String original, String translatedLine) {
        if (original.trim().startsWith("//")) {
            return "// " + translatedLine;
        }
        if (original.trim().startsWith("/**")) {
            return " * " + translatedLine;
        }
        if (original.trim().startsWith("/*")) {
            return "/* " + translatedLine + " */";
        }
        return "// " + translatedLine;
    }
    
    /**
     * 格式化翻译内容，支持解释标记的样式处理
     */
    private String formatTranslationWithExplanation(String original, String translated) {
        if (translated == null || translated.trim().isEmpty()) {
            log.warn("Translation content is empty, using placeholder");
            return formatAsComment(original, "[翻译内容为空]");
        }
        
        String cleanTranslated = translated.trim();
        
        // 额外验证：防止显示明显错误的翻译
        if (cleanTranslated.matches("^```+.*$") ||
            cleanTranslated.startsWith("[翻译") ||
            cleanTranslated.startsWith("[解析") ||
            cleanTranslated.length() < 2) {
            log.warn("Invalid translation content detected: {}", cleanTranslated);
            return formatAsComment(original, "[翻译无效]");
        }
        
        // 检查是否包含解释标记【解释：...】
        if (cleanTranslated.contains("【解释：")) {
            String[] parts = cleanTranslated.split("【解释：", 2);
            String mainTranslation = parts[0].trim();
            String explanation = parts.length > 1 ? parts[1].replaceAll("】$", "").trim() : "";
            
            String formattedMain = formatAsComment(original, mainTranslation);
            if (!explanation.isEmpty()) {
                // 用不同样式标记解释部分
                return formattedMain + " 💡" + explanation;
            }
            return formattedMain;
        }
        
        return formatAsComment(original, cleanTranslated);
    }
    
    /**
     * 转义HTML字符
     * Escape HTML characters
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#x27;");
    }
    
    /**
     * 清除翻译显示
     * Clear translation display
     */
    public void clearTranslations() {
        // 清除高亮
        if (!highlighterMap.isEmpty()) {
            MarkupModel markupModel = editor.getMarkupModel();
            
            for (RangeHighlighter highlighter : highlighterMap.keySet()) {
                try {
                    markupModel.removeHighlighter(highlighter);
                } catch (Exception e) {
                    log.error("Failed to remove translation highlighter", e);
                }
            }
            
            highlighterMap.clear();
        }
        // 清除折叠区域
        FoldingModel foldingModel = editor.getFoldingModel();
        foldingModel.runBatchFoldingOperation(() -> {
            // 1) 先移除本次会话追踪的折叠
            if (!foldRegions.isEmpty()) {
                for (FoldRegion region : new java.util.ArrayList<>(foldRegions)) {
                    try {
                        if (region != null && region.isValid()) {
                            foldingModel.removeFoldRegion(region);
                        }
                    } catch (Exception e) {
                        log.error("Failed to remove translation fold region", e);
                    }
                }
                foldRegions.clear();
            }
            // 2) 再扫描并移除任何残留的、带有我们前缀的折叠（跨会话持久化的情况）
            for (FoldRegion region : foldingModel.getAllFoldRegions()) {
                try {
                    if (region != null && region.isValid()) {
                        String placeholder = region.getPlaceholderText();
                        if (placeholder != null && placeholder.startsWith(FOLD_PLACEHOLDER_PREFIX)) {
                            foldingModel.removeFoldRegion(region);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to remove persisted translation fold region", e);
                }
            }
        });
        

        
        translationMode = false;
        log.info("Cleared all translation displays");
    }
    
    /**
     * 切换翻译模式
     * Toggle translation mode
     */
    public void toggleTranslationMode(List<CommentTranslation> translations) {
        if (translationMode) {
            clearTranslations();
        } else {
            showTranslations(translations);
        }
    }
    
    /**
     * 检查是否处于翻译模式
     * Check if in translation mode
     */
    public boolean isTranslationMode() {
        // 若当前布尔标志为false，也尝试从折叠区域检测（处理编辑器重开后的持久化场景）
        if (!translationMode) {
            try {
                for (FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
                    String ph = region.getPlaceholderText();
                    if (ph != null && ph.startsWith(FOLD_PLACEHOLDER_PREFIX)) {
                        translationMode = true;
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to probe translation mode from fold regions", e);
            }
        }
        return translationMode;
    }
    

    
    /**
     * 获取指定位置的翻译信息
     * Get translation info at specified position
     */
    public CommentTranslation getTranslationAt(int offset) {
        for (Map.Entry<RangeHighlighter, CommentTranslation> entry : highlighterMap.entrySet()) {
            RangeHighlighter highlighter = entry.getKey();
            if (highlighter.getStartOffset() <= offset && offset <= highlighter.getEndOffset()) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * 更新翻译显示
     * Update translation display
     */
    public void updateTranslations(List<CommentTranslation> translations) {
        if (translationMode) {
            showTranslations(translations);
        }
    }
    
    /**
     * 释放资源
     * Release resources
     */
    public void dispose() {
        clearTranslations();
    }
}
