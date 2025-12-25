package com.wmsay.gpt4_lll.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.wmsay.gpt4_lll.MyPluginSettings;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.CommentTranslation;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 注释翻译服务
 * Comment translation service
 */
public class CommentTranslationService {
    private static final Logger log = LoggerFactory.getLogger(CommentTranslationService.class);

    private static final String TRANSLATION_PROMPT = """
            请将以下英文代码注释翻译为 **${targetLanguage}**，并添加开发者理解辅助说明。

            ### 输出要求：
            1. **翻译部分**
               - 保持技术术语准确
               - 表达简洁、符合 ${targetLanguage} 的习惯

            2. **解释部分**
               - 在翻译后添加一段理解辅助说明
               - 用【解释：...】标记，与翻译内容区分开
               - 说明应帮助开发者更好理解注释的含义或使用场景

            3. **输出格式**
               - 每行对应一条输入注释
               - 格式为：
                 ```
                 翻译内容【解释：辅助说明】
                 ```

            ### 输入注释：
            ```
            ${comments}
            ```

            请严格按照上述格式输出，每行原文一个结果。
                        """;

    /**
     * 从PSI文件中提取所有注释
     * Extract all comments from PSI file
     */
    public List<CommentTranslation> extractComments(PsiFile psiFile, String targetLanguage) {
        List<CommentTranslation> comments = new ArrayList<>();

        if (psiFile == null) {
            log.warn("PSI file is null, cannot extract comments");
            return comments;
        }

        log.debug("Starting comment extraction from file: {}", psiFile.getName());

        Collection<PsiComment> psiComments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment.class);
        log.debug("Found {} raw PSI comments", psiComments.size());

        // 计算文件头部（package之前）的结束位置，用于跳过版权头注释
        int packageStartOffset = Integer.MAX_VALUE;
        if (psiFile instanceof PsiJavaFile) {
            PsiPackageStatement pkg = ((PsiJavaFile) psiFile).getPackageStatement();
            if (pkg != null && pkg.getTextRange() != null) {
                packageStartOffset = pkg.getTextRange().getStartOffset();
            }
        }

        // 按照起始位置排序，确保处理顺序正确
        List<PsiComment> sortedComments = new ArrayList<>(psiComments);
        sortedComments.sort(
                (c1, c2) -> Integer.compare(c1.getTextRange().getStartOffset(), c2.getTextRange().getStartOffset()));

        Set<PsiComment> processedComments = new HashSet<>();
        int validComments = 0;

        for (int idx = 0; idx < sortedComments.size(); idx++) {
            PsiComment psiComment = sortedComments.get(idx);
            // 如果这个注释已经被处理过（作为多行注释的一部分），跳过
            if (processedComments.contains(psiComment)) {
                continue;
            }

            String commentText = psiComment.getText();
            int startOffset = psiComment.getTextRange().getStartOffset();
            int endOffset = psiComment.getTextRange().getEndOffset();

            log.debug("Processing comment: {} (length: {})",
                    commentText.replaceAll("\n", "\\n").substring(0, Math.min(50, commentText.length())),
                    commentText.length());

            // 跳过位于 package 之前的头部版权注释
            if (psiComment.getTextRange().getEndOffset() <= packageStartOffset) {
                log.debug("Skip header comment before package statement");
                continue;
            }

            // 块/Javadoc注释：解析为段落
            if (commentText.startsWith("/**") || commentText.startsWith("/*")) {
                processedComments.add(psiComment);
                comments.addAll(extractParagraphsFromBlockComment(psiComment, targetLanguage));
                validComments = comments.size();
                continue;
            }

            // 单行注释：合并连续的 // 为一个段落
            if (commentText.startsWith("//")) {
                int groupStartOffset = startOffset;
                int groupEndOffset = endOffset;
                StringBuilder groupText = new StringBuilder();
                groupText.append(commentText);
                processedComments.add(psiComment);

                int j = idx + 1;
                while (j < sortedComments.size()) {
                    PsiComment next = sortedComments.get(j);
                    String nextText = next.getText();
                    if (!nextText.startsWith("//")) {
                        break;
                    }
                    int gap = next.getTextRange().getStartOffset() - groupEndOffset;
                    if (gap > 2) { // 超过换行或空格，认为不是同一段
                        break;
                    }
                    groupText.append("\n").append(nextText);
                    groupEndOffset = next.getTextRange().getEndOffset();
                    processedComments.add(next);
                    j++;
                }

                String groupString = groupText.toString();
                if (isValidComment(groupString)) {
                    CommentTranslation translation = new CommentTranslation(
                            groupString,
                            null,
                            groupStartOffset,
                            groupEndOffset,
                            targetLanguage);
                    comments.add(translation);
                    validComments++;
                    log.debug("Added merged // paragraph #{}: '{}' (offset {}-{})",
                            validComments, groupString.substring(0, Math.min(50, groupString.length())),
                            groupStartOffset, groupEndOffset);
                } else {
                    log.debug("Rejected merged // paragraph: '{}'",
                            groupString.substring(0, Math.min(50, groupString.length())));
                }
                continue;
            }

            // 其他情况（忽略碎片）
            processedComments.add(psiComment);
            log.debug("Skipped comment fragment: {}", commentText.replaceAll("\n", "\\n"));
        }

        log.info("Extracted {} valid comments from {} total PSI comments in file: {}",
                validComments, psiComments.size(), psiFile.getName());
        return comments;
    }

    /**
     * 检查注释是否有效（包含实际文本内容）
     * Check if comment is valid (contains actual text content)
     */
    private boolean isValidComment(String commentText) {
        if (commentText == null || commentText.trim().isEmpty()) {
            log.debug("Comment rejected: null or empty");
            return false;
        }

        // 移除注释符号，检查是否有实际内容
        String cleanText = commentText
                .replaceAll("^//+\\s*", "") // 移除单行注释符号
                .replaceAll("^/\\*+\\s*", "") // 移除块注释开始符号
                .replaceAll("\\s*\\*+/$", "") // 移除块注释结束符号
                .replaceAll("^\\s*\\*+\\s*", "") // 移除块注释中间的星号
                .trim();

        log.debug("Comment validation - Original: '{}', Clean: '{}', Length: {}",
                commentText.replaceAll("\n", "\\n"), cleanText, cleanText.length());

        // 更宽松的验证条件：
        // 1. 长度至少2个字符
        // 2. 包含字母或中文字符
        // 3. 不只是符号和空白
        boolean isValid = cleanText.length() >= 2 &&
        // cleanText.matches(".*[a-zA-Z\\u4e00-\\u9fff].*") &&
                !cleanText.matches("^[\\s\\p{Punct}]*$");

        log.debug("Comment validation result: {}", isValid);
        return isValid;
    }

    /**
     * 清理注释文本，移除注释符号
     * Clean comment text, remove comment symbols
     */
    private String cleanCommentText(String commentText) {
        if (commentText == null) {
            return "";
        }
        // 仅移除注释符号，保留 @param/@return/@throws 以及 <p> 等标签行用于段落翻译
        return commentText
                .replaceAll("^//+\\s*", "")
                .replaceAll("^/\\*\\*?\\s*", "")
                .replaceAll("\\s*\\*+/$", "")
                .replaceAll("(?m)^\\s*\\*+\\s?", "")
                .trim();
    }

    /**
     * 将一个块注释（Javadoc/普通块注释）解析为段落列表
     * 每个段落会生成一个 CommentTranslation，并锚定到该段落第一行的起始文本位置
     */
    private List<CommentTranslation> extractParagraphsFromBlockComment(PsiComment blockComment, String targetLanguage) {
        List<CommentTranslation> list = new ArrayList<>();
        try {
            PsiFile file = blockComment.getContainingFile();
            Document document = file.getViewProvider().getDocument();
            if (document == null) {
                // 无法获取文档，退化为整体注释
                list.add(new CommentTranslation(blockComment.getText(), null,
                        blockComment.getTextRange().getStartOffset(),
                        blockComment.getTextRange().getEndOffset(), targetLanguage));
                return list;
            }

            String raw = blockComment.getText();
            int commentStart = blockComment.getTextRange().getStartOffset();

            // 按行拆分并记录原始行相对偏移
            List<String> rawLines = new ArrayList<>();
            List<Integer> rawLineStartRelOffsets = new ArrayList<>();
            int rel = 0;
            for (String line : raw.split("\n", -1)) {
                rawLines.add(line);
                rawLineStartRelOffsets.add(rel);
                rel += line.length() + 1; // +1 for \n
            }

            // 生成清洗后的逻辑行（去掉* 前缀等），用于段落划分
            List<String> logicalLines = new ArrayList<>();
            for (int i = 0; i < rawLines.size(); i++) {
                String line = rawLines.get(i);
                if (i == 0) {
                    line = line.replaceFirst("^/\\*\\*?\\s*", "");
                }
                if (i == rawLines.size() - 1) {
                    line = line.replaceFirst("\\s*\\*/\\s*$", "");
                }
                line = line.replaceFirst("^\\s*\\*\\s?", "");
                logicalLines.add(line);
            }

            // 按段落归并：空行分段；遇到 <p> 另起一段；以 @tag 开头的为独立段，可跨多行直到下个 @ 或空行
            int i = 0;
            while (i < logicalLines.size()) {
                // 跳过空行
                while (i < logicalLines.size() && logicalLines.get(i).trim().isEmpty()) {
                    i++;
                }
                if (i >= logicalLines.size())
                    break;

                int paraStartIdx = i;
                String first = logicalLines.get(i).trim();
                boolean isTag = first.startsWith("@");
                boolean isParagraphTag = first.startsWith("<p>");

                StringBuilder paraText = new StringBuilder();
                int paraEndIdx = paraStartIdx;
                while (i < logicalLines.size()) {
                    String current = logicalLines.get(i);
                    String trimmed = current.trim();
                    if (i > paraStartIdx) {
                        // 结束条件
                        if (trimmed.startsWith("<p>"))
                            break;
                        if (trimmed.startsWith("@"))
                            break;
                        if (trimmed.isEmpty())
                            break;
                    }
                    paraText.append(paraText.length() == 0 ? trimmed : (" " + trimmed));
                    paraEndIdx = i;
                    i++;
                    if (isParagraphTag) {
                        // <p> 段落仅包含本行
                        break;
                    }
                    if (isTag) {
                        // tag 段落可包含后续非@的描述行，直到空行或下一个@
                        if (i < logicalLines.size()) {
                            String nextTrim = logicalLines.get(i).trim();
                            if (nextTrim.startsWith("@") || nextTrim.isEmpty())
                                break;
                        }
                    }
                }

                // 定位段落第一行在文档中的可视起点
                int rawLineStartRel = rawLineStartRelOffsets.get(paraStartIdx);
                int absOffsetInComment = rawLineStartRel;
                int absOffset = commentStart + absOffsetInComment;

                int lineNumber = document.getLineNumber(absOffset);
                int docLineStart = document.getLineStartOffset(lineNumber);
                int docLineEnd = document.getLineEndOffset(lineNumber);
                CharSequence docLineSeq = document.getCharsSequence().subSequence(docLineStart, docLineEnd);
                String docLineStr = docLineSeq.toString();
                String search = logicalLines.get(paraStartIdx).trim();
                int idxInLine = Math.max(0, docLineStr.indexOf(search));
                int start = docLineStart + idxInLine;

                // 计算段落结束偏移：使用段落最后一行的文档行尾，覆盖完整段落
                int lastRawLineStartRel = rawLineStartRelOffsets.get(Math.min(paraEndIdx, rawLineStartRelOffsets.size() - 1));
                int lastAbsOffsetInComment = lastRawLineStartRel;
                int lastAbsOffset = commentStart + lastAbsOffsetInComment;
                int lastLineNumber = document.getLineNumber(lastAbsOffset);
                int lastDocLineEnd = document.getLineEndOffset(lastLineNumber);
                int end = Math.max(start, lastDocLineEnd);

                if (paraText.length() > 0) {
                    String paragraphText = paraText.toString();
                    if (isValidComment(paragraphText)) {
                        list.add(new CommentTranslation(paragraphText, null, start, end, targetLanguage));
                        log.debug("Added block comment paragraph: '{}' (offset {}-{})",
                                paragraphText.substring(0, Math.min(50, paragraphText.length())), start, end);
                    } else {
                        log.debug("Rejected block comment paragraph: '{}'",
                                paragraphText.substring(0, Math.min(50, paragraphText.length())));
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to extract paragraphs from block comment", e);
            list.add(new CommentTranslation(blockComment.getText(), null,
                    blockComment.getTextRange().getStartOffset(),
                    blockComment.getTextRange().getEndOffset(), targetLanguage));
        }
        return list;
    }

    /**
     * 异步翻译注释列表
     * Asynchronously translate comment list
     */
    public CompletableFuture<List<CommentTranslation>> translateCommentsAsync(
            Project project, List<CommentTranslation> comments, String targetLanguage) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return translateComments(project, comments, targetLanguage);
            } catch (Exception e) {
                log.error("Failed to translate comments", e);
                return comments; // 返回原始注释列表，翻译失败不影响功能
            }
        });
    }

    /**
     * 翻译注释列表（流式回调版本）
     * Translate comment list (streaming callback version)
     */
    public void translateCommentsWithCallback(
            Project project, List<CommentTranslation> comments, String targetLanguage, 
            TranslationProgressCallback callback) {

        if (comments.isEmpty()) {
            if (callback != null) {
                callback.onAllTranslationsCompleted();
            }
            return;
        }

        log.info("Starting streaming translation of {} comments", comments.size());

        // 逐个翻译注释以实现流式显示
        for (int i = 0; i < comments.size(); i++) {
            CommentTranslation comment = comments.get(i);
            try {
                // 翻译单个注释
                CommentTranslation translated = translateSingleComment(project, comment, targetLanguage);
                
                // 通知回调
                if (callback != null) {
                    callback.onTranslationCompleted(translated, i, comments.size());
                }
                
                log.debug("Completed translation {}/{}: '{}'", 
                         i + 1, comments.size(), 
                         translated.getTranslatedComment().substring(0, Math.min(50, translated.getTranslatedComment().length())));
                
            } catch (Exception e) {
                log.error("Failed to translate comment #{}: {}", i + 1, e.getMessage(), e);
                if (callback != null) {
                    callback.onTranslationError("翻译失败: " + e.getMessage(), i, comments.size());
                }
            }
        }

        // 通知所有翻译完成
        if (callback != null) {
            callback.onAllTranslationsCompleted();
        }
        
        log.info("Completed streaming translation of all {} comments", comments.size());
    }

    /**
     * 翻译注释列表（批量版本，保留向后兼容性）
     * Translate comment list (batch version, retain backward compatibility)
     */
    public List<CommentTranslation> translateComments(
            Project project, List<CommentTranslation> comments, String targetLanguage) {

        if (comments.isEmpty()) {
            return comments;
        }

        // 批量处理注释以提高效率
        int batchSize = 10; // 每批处理10个注释
        List<CommentTranslation> results = new ArrayList<>();

        for (int i = 0; i < comments.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, comments.size());
            List<CommentTranslation> batch = comments.subList(i, endIndex);

            List<CommentTranslation> translatedBatch = translateCommentBatch(project, batch, targetLanguage);
            results.addAll(translatedBatch);
        }

        return results;
    }

    /**
     * 翻译单个注释（用于流式处理）
     * Translate single comment (for streaming processing)
     */
    private CommentTranslation translateSingleComment(
            Project project, CommentTranslation comment, String targetLanguage) {
        
        try {
            // 构建输入文本 - 清理注释符号，只发送纯文本
            String originalComment = comment.getOriginalComment();
            String cleanComment = cleanCommentText(originalComment);
            
            // 准备翻译请求
            String prompt = TRANSLATION_PROMPT
                    .replace("${targetLanguage}", targetLanguage)
                    .replace("${comments}", "1. " + cleanComment);

            Message systemMessage = new Message();
            String provider = ModelUtils.getAvailableProvider(project);
            if (ProviderNameEnum.BAIDU.getProviderName().equals(provider)) {
                systemMessage.setRole("user");
            } else {
                systemMessage.setRole("system");
            }
            systemMessage.setContent("你是一个专业的代码注释翻译助手，能够准确翻译各种编程语言的注释内容。");

            Message userMessage = new Message();
            userMessage.setRole("user");
            userMessage.setContent(prompt);

            List<Message> messages = new ArrayList<>();
            messages.add(systemMessage);
            messages.add(userMessage);

            ChatContent chatContent = new ChatContent();
            chatContent.setMessages(messages, provider);
            chatContent.setModel(ModelUtils.getAvailableModelName(project));

            // 调用LLM进行翻译
            String apiKey = ChatUtils.getApiKey(MyPluginSettings.getInstance(), project);
            log.debug("Starting single comment translation with provider: {}, model: {}", provider, chatContent.getModel());

            String translationResult = ChatUtils.pureChat(provider, apiKey, chatContent);
            log.debug("Single comment translation result: {}",
                    translationResult.substring(0, Math.min(200, translationResult.length())));

            // 解析翻译结果
            String translatedText = parseSingleTranslationResult(translationResult);
            comment.setTranslatedComment(translatedText);
            
            return comment;

        } catch (Exception e) {
            log.error("Failed to translate single comment: {}", e.getMessage(), e);
            // 返回原始注释，标记为翻译失败
            comment.setTranslatedComment("[翻译失败] " + comment.getOriginalComment());
            return comment;
        }
    }

    /**
     * 解析单个翻译结果
     * Parse single translation result
     */
    private String parseSingleTranslationResult(String translationResult) {
        try {
            log.debug("Parsing single translation result: {}", translationResult);
            
            if (translationResult == null || translationResult.trim().isEmpty()) {
                log.warn("Translation result is empty");
                return "[翻译结果为空]";
            }
            
            String[] lines = translationResult.split("\n");
            List<String> candidateTranslations = new ArrayList<>();
            
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    // 移除可能的编号前缀，支持多种格式
                    String translation = line.replaceAll("^\\d+[.):]\\s*", "").trim();
                    
                    // 更严格的验证：不是纯数字/符号，且长度合理
                    if (!translation.isEmpty() && 
                        !translation.matches("^[\\d\\s\\.\\)\\:]+$") &&
                        !translation.matches("^```+.*$") &&  // 过滤代码块标记
                        translation.length() > 1) {  // 至少2个字符
                        candidateTranslations.add(translation);
                        log.debug("Found candidate translation: {}", translation);
                    }
                }
            }
            
            // 返回第一个有效的翻译候选
            if (!candidateTranslations.isEmpty()) {
                String result = candidateTranslations.get(0);
                log.debug("Selected translation: {}", result);
                return result;
            }
            
            // 如果没有找到有效的翻译，但原始结果不为空，尝试直接使用
            String cleanResult = translationResult.trim();
            if (!cleanResult.isEmpty() && !cleanResult.matches("^```+.*$")) {
                log.warn("Using raw translation result as fallback: {}", cleanResult);
                return cleanResult;
            }
            
            // 最后的fallback
            log.error("No valid translation found in result: {}", translationResult);
            return "[翻译解析失败] " + translationResult.substring(0, Math.min(50, translationResult.length()));
            
        } catch (Exception e) {
            log.error("Failed to parse single translation result: {}", translationResult, e);
            return "[解析异常] " + e.getMessage();
        }
    }

    /**
     * 翻译一批注释
     * Translate a batch of comments
     */
    private List<CommentTranslation> translateCommentBatch(
            Project project, List<CommentTranslation> batch, String targetLanguage) {

        try {
            // 构建输入文本 - 清理注释符号，只发送纯文本
            StringBuilder commentsText = new StringBuilder();
            for (int i = 0; i < batch.size(); i++) {
                String originalComment = batch.get(i).getOriginalComment();
                String cleanComment = cleanCommentText(originalComment);
                commentsText.append(i + 1).append(". ").append(cleanComment);
                if (i < batch.size() - 1) {
                    commentsText.append("\n");
                }
            }

            // 准备翻译请求
            String prompt = TRANSLATION_PROMPT
                    .replace("${targetLanguage}", targetLanguage)
                    .replace("${comments}", commentsText.toString());

            Message systemMessage = new Message();
            String provider = ModelUtils.getAvailableProvider(project);
            if (ProviderNameEnum.BAIDU.getProviderName().equals(provider)) {
                systemMessage.setRole("user");
            } else {
                systemMessage.setRole("system");
            }
            systemMessage.setContent("你是一个专业的代码注释翻译助手，能够准确翻译各种编程语言的注释内容。");

            Message userMessage = new Message();
            userMessage.setRole("user");
            userMessage.setContent(prompt);

            List<Message> messages = new ArrayList<>();
            messages.add(systemMessage);
            messages.add(userMessage);

            ChatContent chatContent = new ChatContent();
            chatContent.setMessages(messages, provider);
            chatContent.setModel(ModelUtils.getAvailableModelName(project));

            // 调用LLM进行翻译
            String apiKey = ChatUtils.getApiKey(MyPluginSettings.getInstance(), project);
            log.debug("Starting LLM translation with provider: {}, model: {}", provider, chatContent.getModel());
            log.debug("Translation prompt: {}", prompt.substring(0, Math.min(200, prompt.length())));

            String translationResult = ChatUtils.pureChat(provider, apiKey, chatContent);
            log.debug("LLM translation result: {}",
                    translationResult.substring(0, Math.min(200, translationResult.length())));

            // 解析翻译结果
            return parseTranslationResult(batch, translationResult);

        } catch (Exception e) {
            log.error("Failed to translate comment batch: {}", e.getMessage(), e);
            // 返回原始注释，标记为翻译失败
            for (int i = 0; i < batch.size(); i++) {
                CommentTranslation comment = batch.get(i);
                comment.setTranslatedComment("[翻译失败] " + comment.getOriginalComment());
                log.warn("Marked comment #{} as translation failed: {}",
                        i + 1,
                        comment.getOriginalComment().substring(0, Math.min(30, comment.getOriginalComment().length())));
            }
            return batch;
        }
    }

    /**
     * 解析翻译结果
     * Parse translation result
     */
    private List<CommentTranslation> parseTranslationResult(
            List<CommentTranslation> originalComments, String translationResult) {

        log.debug("Parsing translation result for {} comments", originalComments.size());
        log.debug("Raw translation result: {}", translationResult);

        try {
            String[] translatedLines = translationResult.split("\n");
            List<String> translations = new ArrayList<>();

            // 提取翻译内容，跳过编号和空行
            for (String line : translatedLines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    // 移除可能的编号前缀，支持多种格式
                    String translation = line.replaceAll("^\\d+[.):]\\s*", "").trim();
                    if (!translation.isEmpty() && !translation.matches("^[\\d\\s\\.\\)\\:]+$")) {
                        translations.add(translation);
                        log.debug("Extracted translation: {}", translation);
                    }
                }
            }

            log.debug("Extracted {} translations for {} original comments", translations.size(),
                    originalComments.size());

            // 将翻译结果匹配到原始注释
            for (int i = 0; i < originalComments.size(); i++) {
                CommentTranslation comment = originalComments.get(i);
                if (i < translations.size()) {
                    String translatedText = translations.get(i);
                    comment.setTranslatedComment(translatedText);
                    log.debug("Matched translation #{}: '{}' -> '{}'",
                            i + 1,
                            comment.getOriginalComment().substring(0,
                                    Math.min(50, comment.getOriginalComment().length())),
                            translatedText.substring(0, Math.min(50, translatedText.length())));
                } else {
                    // 如果翻译结果不够，使用原始注释并添加标记
                    String fallback = "[未翻译] " + comment.getOriginalComment();
                    comment.setTranslatedComment(fallback);
                    log.warn("No translation found for comment #{}, using fallback: {}",
                            i + 1, comment.getOriginalComment().substring(0,
                                    Math.min(30, comment.getOriginalComment().length())));
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse translation result", e);
            // 解析失败时，使用原始注释作为翻译结果并添加错误标记
            for (int i = 0; i < originalComments.size(); i++) {
                CommentTranslation comment = originalComments.get(i);
                comment.setTranslatedComment("[解析失败] " + comment.getOriginalComment());
            }
        }

        return originalComments;
    }

}
