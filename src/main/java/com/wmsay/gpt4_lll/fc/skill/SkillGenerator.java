package com.wmsay.gpt4_lll.fc.skill;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.core.Message;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Skill 生成器。
 * 在招募模式下通过 Sidecar LLM 调用自动生成 SkillDefinition。
 * <p>
 * 所有 LLM 调用均为独立的 Sidecar 调用，不写入主 Agent 的 ConversationMemory。
 */
public class SkillGenerator {

    private static final Logger LOG = Logger.getLogger(SkillGenerator.class.getName());

    private static final String SYSTEM_PROMPT =
        "You are a skill definition generator. Based on the user's request and context, " +
        "generate a new skill definition that can handle the task.\n\n" +
        "Existing skills (avoid duplicates):\n%s\n\n" +
        "Evaluate the task complexity along these dimensions:\n" +
        "- Number of tool call steps required\n" +
        "- Whether a specialized systemPrompt is needed\n" +
        "- Whether independent context isolation is needed\n\n" +
        "Respond with JSON only:\n" +
        "{\n" +
        "  \"name\": \"skill-name-kebab-case\",\n" +
        "  \"systemPrompt\": \"Detailed system prompt for the sub-agent\",\n" +
        "  \"purpose\": \"Brief description of what this skill does\",\n" +
        "  \"trigger\": \"When this skill should be activated\",\n" +
        "  \"promptTemplate\": \"Template with {{user_input}} placeholder\",\n" +
        "  \"complexity\": \"simple|moderate|complex\",\n" +
        "  \"reasoning\": \"Why this complexity level was chosen\"\n" +
        "}\n\n" +
        "Rules:\n" +
        "- name must be unique and not duplicate any existing skill name.\n" +
        "- systemPrompt should be detailed and specific to the task.\n" +
        "- promptTemplate MUST contain the {{user_input}} placeholder.\n" +
        "- complexity: 'simple' for single-step tool calls, 'moderate' for multi-step coordination, " +
        "'complex' for deep reasoning chains requiring context isolation.";

    /**
     * 根据用户输入和当前上下文生成新的 SkillDefinition。
     * <p>
     * 通过 Sidecar LLM 调用生成，不写入主 Agent 的 ConversationMemory。
     * 生成的 SkillDefinition 标记 generated=true。
     *
     * @param userInput      用户输入
     * @param contextSummary 对话上下文摘要
     * @param existingSkills 已有 Skill 列表（用于避免重复）
     * @param llmCaller      LLM 调用器
     * @param modelName      模型名称
     * @return 生成的 SkillDefinition（generated=true）
     * @throws RuntimeException 当 LLM 调用失败或生成结果无效时
     */
    public SkillDefinition generate(String userInput,
                                     String contextSummary,
                                     List<SkillDefinition> existingSkills,
                                     LlmCaller llmCaller,
                                     String modelName) {
        LOG.info("[SkillGenerator] Generating skill for input: "
                + (userInput != null && userInput.length() > 80
                   ? userInput.substring(0, 80) + "..." : userInput));

        String existingDesc = buildExistingSkillsDescription(existingSkills);
        String systemPrompt = String.format(SYSTEM_PROMPT, existingDesc);

        // Build user message with input and context
        StringBuilder userContent = new StringBuilder();
        userContent.append("User request: ").append(userInput != null ? userInput : "");
        if (contextSummary != null && !contextSummary.isBlank()) {
            userContent.append("\n\nContext summary:\n").append(contextSummary);
        }

        // Build sidecar messages
        List<Message> messages = new ArrayList<>();

        Message sysMsg = new Message();
        sysMsg.setRole("system");
        sysMsg.setContent(systemPrompt);
        messages.add(sysMsg);

        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent(userContent.toString());
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

        LOG.info("[SkillGenerator] Calling LLM for skill generation...");
        String response = llmCaller.call(request);

        SkillDefinition generated = parseResponse(response, existingSkills);
        LOG.info("[SkillGenerator] Generated skill: " + generated.getName()
                + " (complexity=" + generated.getComplexity() + ")");
        return generated;
    }

    /**
     * 解析 LLM 响应为 SkillDefinition。
     * 验证所有必需字段非空，标记 generated=true。
     */
    SkillDefinition parseResponse(String response, List<SkillDefinition> existingSkills) {
        if (response == null || response.isBlank()) {
            throw new RuntimeException("Empty LLM response during skill generation");
        }

        // Extract JSON from response
        String json = response.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new RuntimeException("No JSON found in skill generation response");
        }
        json = json.substring(start, end + 1);

        JSONObject obj = JSON.parseObject(json);

        String name = obj.getString("name");
        String systemPrompt = obj.getString("systemPrompt");
        String purpose = obj.getString("purpose");
        String trigger = obj.getString("trigger");
        String promptTemplate = obj.getString("promptTemplate");
        String complexityStr = obj.getString("complexity");

        // Validate required fields
        validateField(name, "name");
        validateField(systemPrompt, "systemPrompt");
        validateField(purpose, "purpose");
        validateField(trigger, "trigger");
        validateField(promptTemplate, "promptTemplate");

        // Check for duplicate names
        if (existingSkills != null) {
            for (SkillDefinition existing : existingSkills) {
                if (existing.getName().equalsIgnoreCase(name)) {
                    // Append suffix to avoid collision
                    name = name + "-gen-" + System.currentTimeMillis() % 10000;
                    LOG.info("[SkillGenerator] Renamed to avoid duplicate: " + name);
                    break;
                }
            }
        }

        SkillComplexity complexity = SkillComplexity.MODERATE;
        try {
            complexity = SkillComplexity.fromString(complexityStr);
        } catch (Exception e) {
            LOG.warning("[SkillGenerator] Invalid complexity '" + complexityStr
                    + "', defaulting to MODERATE");
        }

        return SkillDefinition.builder()
                .name(name)
                .systemPrompt(systemPrompt)
                .purpose(purpose)
                .trigger(trigger)
                .promptTemplate(promptTemplate)
                .complexity(complexity)
                .generated(true)
                .build();
    }

    private void validateField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException("Generated SkillDefinition missing required field: " + fieldName);
        }
    }

    private String buildExistingSkillsDescription(List<SkillDefinition> existingSkills) {
        if (existingSkills == null || existingSkills.isEmpty()) {
            return "(none)";
        }
        return existingSkills.stream()
                .map(s -> "- " + s.getName() + ": " + s.getPurpose())
                .collect(Collectors.joining("\n"));
    }
}
