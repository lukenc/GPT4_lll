package com.wmsay.gpt4_lll.fc.memory;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.wmsay.gpt4_lll.fc.core.Message;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 LLM 的内容相似度分析器。
 * <p>
 * 通过 LLM 调用判断最近对话与之前对话的主题相似度，
 * 返回 0.0（完全不同）到 1.0（完全相同）的评分。
 * <p>
 * 仅分析 user/assistant 角色的 content，忽略 system/tool 消息。
 * null 或空列表输入返回 0.5（中性值）。
 * LLM 调用失败时返回 0.5 并记录日志。
 * 线程安全。
 */
public class SimilarityAnalyzer {

    private static final Logger LOG = Logger.getLogger(SimilarityAnalyzer.class.getName());

    private static final double DEFAULT_SCORE = 0.5;
    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+\\.\\d+|\\d+)");

    private final Function<String, String> summarizer;
    private final int recentRounds;

    /**
     * 构造 SimilarityAnalyzer。
     *
     * @param summarizer   LLM 调用函数，接收 prompt 返回 LLM 响应文本
     * @param recentRounds 最近对话轮数，用于分析范围控制
     */
    public SimilarityAnalyzer(Function<String, String> summarizer, int recentRounds) {
        this.summarizer = summarizer;
        this.recentRounds = recentRounds;
    }

    /**
     * 使用默认 recentRounds=3 构造。
     *
     * @param summarizer LLM 调用函数
     */
    public SimilarityAnalyzer(Function<String, String> summarizer) {
        this(summarizer, 3);
    }

    /**
     * 分析两段对话的主题相似度。
     * <p>
     * 仅分析 user/assistant 角色的 content，忽略 system/tool 消息。
     *
     * @param recentMessages  最近的消息列表
     * @param earlierMessages 之前的消息列表
     * @return 0.0（完全不同）到 1.0（完全相同），失败返回 0.5
     */
    public synchronized double analyzeSimilarity(List<Message> recentMessages, List<Message> earlierMessages) {
        if (isNullOrEmpty(recentMessages) || isNullOrEmpty(earlierMessages)) {
            return DEFAULT_SCORE;
        }

        String recentContent = extractContent(recentMessages);
        String earlierContent = extractContent(earlierMessages);

        if (recentContent.isEmpty() || earlierContent.isEmpty()) {
            return DEFAULT_SCORE;
        }

        String prompt = buildPrompt(recentContent, earlierContent);

        try {
            String response = summarizer.apply(prompt);
            return parseScore(response);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "SimilarityAnalyzer: LLM call failed, returning default score 0.5", e);
            return DEFAULT_SCORE;
        }
    }

    /**
     * 从消息列表中提取 user/assistant 角色的 content 文本。
     */
    private String extractContent(List<Message> messages) {
        return messages.stream()
                .filter(m -> m != null && isUserOrAssistant(m.getRole()))
                .map(Message::getContent)
                .filter(c -> c != null && !c.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 判断角色是否为 user 或 assistant。
     */
    private boolean isUserOrAssistant(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }

    /**
     * 构造专用 prompt，要求 LLM 返回 0.0-1.0 的相似度评分。
     */
    private String buildPrompt(String recentContent, String earlierContent) {
        return "Please analyze the topic similarity between the following two conversation segments. " +
                "Rate the similarity from 0.0 (completely different topics) to 1.0 (same topic). " +
                "Respond with ONLY a single decimal number between 0.0 and 1.0, nothing else.\n\n" +
                "--- Recent conversation ---\n" + recentContent + "\n\n" +
                "--- Earlier conversation ---\n" + earlierContent + "\n\n" +
                "Similarity score:";
    }

    /**
     * 从 LLM 响应中解析相似度评分，并 clamp 到 [0.0, 1.0]。
     */
    private double parseScore(String response) {
        if (response == null || response.trim().isEmpty()) {
            LOG.log(Level.WARNING, "SimilarityAnalyzer: LLM returned null or empty response, returning default score 0.5");
            return DEFAULT_SCORE;
        }

        Matcher matcher = SCORE_PATTERN.matcher(response.trim());
        if (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                return clamp(score);
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "SimilarityAnalyzer: Failed to parse score from LLM response: " + response);
                return DEFAULT_SCORE;
            }
        }

        LOG.log(Level.WARNING, "SimilarityAnalyzer: No numeric score found in LLM response: " + response);
        return DEFAULT_SCORE;
    }

    /**
     * 将值限制在 [0.0, 1.0] 范围内。
     */
    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private boolean isNullOrEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
