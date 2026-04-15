package com.wmsay.gpt4_lll.fc.skill;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Skill 匹配器。
 * 以 sidecar 方式独立 LLM 调用（与 IntentRecognizer 模式一致）。
 * 支持关键词预过滤，减少 LLM 候选数量。
 */
public class SkillMatcher {

    private static final Logger LOG = Logger.getLogger(SkillMatcher.class.getName());

    private static final int PRE_FILTER_THRESHOLD = 20;
    private static final int MAX_CANDIDATES_AFTER_FILTER = 10;

    /** Minimum difference between top-1 and top-2 confidence to accept a match. */
    static final double AMBIGUITY_THRESHOLD = 0.1;

    private static final String SYSTEM_PROMPT =
        "You are a skill intent matcher. Given the user's input and a list of available skills, " +
        "determine which skill best matches the user's intent.\n\n" +
        "Available skills:\n%s\n\n" +
        "Respond with JSON only:\n" +
        "{\"intent\":\"skill_name_or_none\",\"confidence\":0.0-1.0," +
        "\"second_intent\":\"second_skill_name_or_none\",\"second_confidence\":0.0-1.0," +
        "\"reasoning\":\"...\"}\n\n" +
        "Rules:\n" +
        "- intent must be the exact skill name from the list, or \"none\" if no skill matches.\n" +
        "- confidence is a float between 0.0 and 1.0 indicating match certainty.\n" +
        "- second_intent is the second-best matching skill name, or \"none\" if there is no runner-up.\n" +
        "- second_confidence is the confidence for the second-best match (0.0 if none).\n" +
        "- If no skill clearly matches, set intent to \"none\" with low confidence.";

    /**
     * 根据用户输入匹配最佳 Skill。
     * <ol>
     *   <li>可用 Skill 数量为 0 时直接返回 unmatched</li>
     *   <li>Skill 数量 > 20 时通过 searchKeywords 关键词预过滤缩减到 10 个以内</li>
     *   <li>构建意图识别提示词（包含候选 Skill 的 name/purpose/trigger）</li>
     *   <li>sidecar LLM 调用，要求返回 JSON</li>
     *   <li>解析结果，confidence < 0.7 或 intent 为 none/空时返回 unmatched</li>
     * </ol>
     * LLM 调用失败或超时时返回 unmatched，确保主流程不被阻塞。
     */
    public SkillMatchResult match(String userInput, List<SkillDefinition> availableSkills,
                                   LlmCaller llmCaller, String modelName) {
        long startTime = System.currentTimeMillis();

        // 空 Skill 列表直接返回 unmatched，不执行 LLM 调用
        if (availableSkills == null || availableSkills.isEmpty()) {
            LOG.info("[Skill] No available skills, skipping match");
            return SkillMatchResult.unmatched("No available skills");
        }

        try {
            // 关键词预过滤
            List<SkillDefinition> candidates = preFilter(userInput, availableSkills);

            // 构建候选 Skill 描述
            String skillDescriptions = buildSkillDescriptions(candidates);
            String systemPrompt = String.format(SYSTEM_PROMPT, skillDescriptions);

            // 构建 sidecar 请求（与 IntentRecognizer 模式一致）
            List<Message> messages = new ArrayList<>();
            Message sysMsg = new Message();
            sysMsg.setRole("system");
            sysMsg.setContent(systemPrompt);
            messages.add(sysMsg);

            Message userMsg = new Message();
            userMsg.setRole("user");
            userMsg.setContent(userInput);
            messages.add(userMsg);

            ChatContent chatContent = new ChatContent();
            chatContent.setDirectMessages(messages);
            chatContent.setStream(false);
            if (modelName != null && !modelName.isBlank()) {
                chatContent.setModel(modelName);
            }

            FunctionCallRequest request = FunctionCallRequest.builder()
                    .chatContent(chatContent)
                    .maxRounds(1)
                    .config(FunctionCallConfig.builder().build())
                    .build();

            LOG.info("[Skill] Calling LLM for skill matching (" + candidates.size() + " candidates)...");
            System.out.println("[Skill] Calling LLM for skill matching (" + candidates.size() + " candidates)...");
            String response = llmCaller.call(request);
            System.out.println("[Skill] LLM raw response: " + (response != null ? response.substring(0, Math.min(response.length(), 500)) : "null"));
            LOG.info("[Skill] LLM raw response: " + (response != null ? response.substring(0, Math.min(response.length(), 500)) : "null"));
            SkillMatchResult result = parseResponse(response);

            long elapsed = System.currentTimeMillis() - startTime;
            if (result.isMatched()) {
                LOG.info("[Skill] Matched skill '" + result.getSkillName()
                        + "' with confidence " + result.getConfidence()
                        + " in " + elapsed + "ms");
            } else {
                LOG.info("[Skill] No skill matched (confidence="
                        + result.getConfidence() + ") in " + elapsed + "ms");
            }
            return result;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.log(Level.WARNING, "[Skill] Skill matching failed after " + elapsed + "ms: " + e.getMessage(), e);
            return SkillMatchResult.unmatched("LLM call failed: " + e.getMessage());
        }
    }

    /**
     * 关键词预过滤。Skill 数量 > 20 时，通过 searchKeywords 匹配缩减到 10 个以内。
     * Skill 数量 <= 20 时直接返回原列表。
     */
    List<SkillDefinition> preFilter(String userInput, List<SkillDefinition> skills) {
        if (skills.size() <= PRE_FILTER_THRESHOLD) {
            return skills;
        }

        String inputLower = userInput.toLowerCase();

        // 按关键词匹配数量排序，取前 MAX_CANDIDATES_AFTER_FILTER 个
        List<ScoredSkill> scored = new ArrayList<>();
        for (SkillDefinition skill : skills) {
            int score = 0;
            List<String> keywords = skill.getSearchKeywords();
            if (keywords != null) {
                for (String keyword : keywords) {
                    if (keyword != null && inputLower.contains(keyword.toLowerCase())) {
                        score++;
                    }
                }
            }
            // purpose 和 trigger 也参与匹配
            if (skill.getPurpose() != null && containsAnyWord(inputLower, skill.getPurpose().toLowerCase())) {
                score++;
            }
            if (skill.getTrigger() != null && containsAnyWord(inputLower, skill.getTrigger().toLowerCase())) {
                score++;
            }
            scored.add(new ScoredSkill(skill, score));
        }

        scored.sort(Comparator.comparingInt(ScoredSkill::score).reversed());

        return scored.stream()
                .limit(MAX_CANDIDATES_AFTER_FILTER)
                .map(ScoredSkill::skill)
                .collect(Collectors.toList());
    }

    private boolean containsAnyWord(String input, String text) {
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.length() >= 2 && input.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static class ScoredSkill {
        private final SkillDefinition skill;
        private final int score;

        ScoredSkill(SkillDefinition skill, int score) {
            this.skill = skill;
            this.score = score;
        }

        SkillDefinition skill() { return skill; }
        int score() { return score; }
    }

    /**
     * 构建候选 Skill 的描述文本，包含 name/purpose/trigger。
     */
    private String buildSkillDescriptions(List<SkillDefinition> candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            SkillDefinition skill = candidates.get(i);
            sb.append(i + 1).append(". name: ").append(skill.getName());
            sb.append("\n   purpose: ").append(skill.getPurpose());
            sb.append("\n   trigger: ").append(skill.getTrigger());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 响应为 SkillMatchResult。
     * 处理 LLM 可能返回的额外文本，提取 JSON 部分。
     * 包含模糊匹配防护：当 top-1 与 top-2 confidence 差值 < 0.1 时返回 unmatched。
     */
    SkillMatchResult parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return SkillMatchResult.unmatched("Empty LLM response");
        }

        try {
            // 提取 JSON 部分（处理 LLM 可能返回的额外文本）
            String json = response.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return SkillMatchResult.unmatched("No JSON found in LLM response");
            }
            json = json.substring(start, end + 1);

            JSONObject obj = JSON.parseObject(json);

            // 如果是 OpenAI API 格式的响应，提取 choices[0].message.content
            if (obj.containsKey("choices") && !obj.containsKey("intent")) {
                try {
                    String content = obj.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    if (content != null && !content.isBlank()) {
                        String innerJson = content.trim();
                        int innerStart = innerJson.indexOf('{');
                        int innerEnd = innerJson.lastIndexOf('}');
                        if (innerStart >= 0 && innerEnd > innerStart) {
                            innerJson = innerJson.substring(innerStart, innerEnd + 1);
                        }
                        obj = JSON.parseObject(innerJson);
                        System.out.println("[Skill] Extracted content from OpenAI response format");
                    }
                } catch (Exception e) {
                    System.out.println("[Skill] Failed to extract from OpenAI format: " + e.getMessage());
                }
            }

            String intent = obj.getString("intent");
            double confidence = obj.getDoubleValue("confidence");
            String reasoning = obj.getString("reasoning");

            LOG.info("[Skill] Parsed: intent=" + intent + ", confidence=" + confidence
                    + ", reasoning=" + (reasoning != null ? reasoning.substring(0, Math.min(reasoning.length(), 100)) : "null"));
            System.out.println("[Skill] Parsed: intent=" + intent + ", confidence=" + confidence
                    + ", reasoning=" + (reasoning != null ? reasoning.substring(0, Math.min(reasoning.length(), 100)) : "null"));

            // intent 为 "none" 或空时返回 unmatched
            if (intent == null || intent.isBlank() || "none".equalsIgnoreCase(intent)) {
                return SkillMatchResult.unmatched(reasoning != null ? reasoning : "No matching skill");
            }

            // confidence < 0.7 时返回 unmatched
            if (confidence < SkillMatchResult.getConfidenceThreshold()) {
                return SkillMatchResult.unmatched(reasoning != null ? reasoning : "Low confidence");
            }

            // 模糊匹配防护：检查 top-2 confidence 差值
            double secondConfidence = obj.getDoubleValue("second_confidence");
            if (secondConfidence >= SkillMatchResult.getConfidenceThreshold()
                    && (confidence - secondConfidence) < AMBIGUITY_THRESHOLD) {
                String secondIntent = obj.getString("second_intent");
                System.out.println("[Skill] Ambiguous match detected: '" + intent + "' (" + confidence
                        + ") vs '" + secondIntent + "' (" + secondConfidence
                        + "), difference=" + (confidence - secondConfidence));
                LOG.info("[Skill] Ambiguous match detected: '" + intent + "' (" + confidence
                        + ") vs '" + secondIntent + "' (" + secondConfidence
                        + "), difference=" + (confidence - secondConfidence));
                return SkillMatchResult.unmatched("Ambiguous match: top-2 confidence difference < "
                        + AMBIGUITY_THRESHOLD + " (" + intent + "=" + confidence
                        + " vs " + secondIntent + "=" + secondConfidence + ")");
            }

            return SkillMatchResult.matched(intent, confidence, reasoning != null ? reasoning : "");

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Skill] Failed to parse skill match response", e);
            return SkillMatchResult.unmatched("Failed to parse LLM response: " + e.getMessage());
        }
    }
}
