package com.wmsay.gpt4_lll.fc.skill;

import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * In-context skill 加载器工具。
 * <p>
 * <strong>架构定位</strong>:本工具是<em>纯内容加载器</em>。被调用时它做且仅做一件事——
 * 返回 skill 的 systemPrompt + additionalNotes + 填了 user_input 的 promptTemplate
 * 作为 {@link ToolResult#text}。下一轮 LLM 把这段 tool_result 当作"刚加载的 skill 指南"
 * 读进上下文,<strong>当前正在跑的这个 agent</strong>(无论它是主 agent 还是已经在执行的
 * 某个 sub-agent)继续按 skill 指南干活。
 *
 * <p><strong>本工具不开 sub-agent</strong>。Skill 与 sub-agent 是两种独立机制,可以结合
 * 但不能混合:
 * <ul>
 *   <li><strong>用法 1(本工具)</strong>:把 skill <em>内容</em>加载到当前 agent 的对话
 *       上下文。当前 agent 自己照着指南干活,使用常规工具完成任务。同步、ms 级返回。</li>
 *   <li><strong>用法 2(sidecar SkillMatcher 路径,不在本工具职责内)</strong>:在 main agent
 *       收到 user message 之前,旁路 LLM 匹配并决定:某些 skill(complexity 较高的)以
 *       skill systemPrompt 为人设直接开 sub-agent 在隔离 session 里跑;调用方阻塞等返回。
 *       这条路径见 {@code AgentRuntime.send} 里的 sidecar 分支,它使用 {@link SubAgentFactory}。</li>
 * </ul>
 * 关键不变量:在同一个 user turn 内,主 agent <strong>不会</strong>"自己干一半,又派 sub-agent
 * 跑一遍"——因为本工具不派 sub-agent,而 sidecar 路径在主 agent 的 ReAct 循环之前就已经分流。
 *
 * <p><strong>fc/ 包纯度</strong>:本类不依赖任何 IntelliJ Platform API。完全无状态——只持
 * {@link SkillRegistry} 一个引用,{@code execute} 是字符串拼接,无 ToolContext 写入,无
 * sub-agent 派发,无运行时引用注入。
 *
 * <p><strong>幻觉调用处理</strong>:若模型传入 registry 中不存在的 {@code skill_name},
 * 返回 {@link ToolResult#error} 并附带可用列表,让模型自行纠错或换 skill。
 */
public class InvokeSkillTool implements Tool {

    private static final Logger LOG = Logger.getLogger(InvokeSkillTool.class.getName());

    public static final String TOOL_NAME = "invoke_skill";

    private final SkillRegistry skillRegistry;

    public InvokeSkillTool(SkillRegistry skillRegistry) {
        if (skillRegistry == null) throw new IllegalArgumentException("skillRegistry must not be null");
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        List<SkillDefinition> skills = skillRegistry.getAllSkills();
        StringBuilder sb = new StringBuilder();
        sb.append("Load a specialized skill's guidance into your context. ")
          .append("Calling this tool returns the skill's instructions; you (this same agent) ")
          .append("then follow the loaded instructions yourself, using your regular tools ")
          .append("(read_file, write_file, grep, etc.) to perform the work.\n\n");
        if (skills.isEmpty()) {
            sb.append("Available skills: (none currently registered)\n\n");
        } else {
            sb.append("Available skills:\n");
            int idx = 1;
            for (SkillDefinition skill : skills) {
                sb.append("  ").append(idx++).append(". ").append(skill.getName());
                if (skill.getPurpose() != null && !skill.getPurpose().isBlank()) {
                    sb.append(" — ").append(skill.getPurpose());
                }
                sb.append("\n");
                if (skill.getTrigger() != null && !skill.getTrigger().isBlank()) {
                    sb.append("     Trigger: ").append(skill.getTrigger()).append("\n");
                }
            }
            sb.append("\n");
        }
        sb.append("Use the exact skill_name from the list above. ")
          .append("If no skill clearly fits, don't call this tool — handle the request with regular tools.\n\n")
          .append("**Important**: this tool does NOT delegate to a sub-agent and does NOT re-execute ")
          .append("work for you. If you have already started solving the task with regular tools, do ")
          .append("not call invoke_skill afterwards as a 'second-opinion' or 'review pass' — its only ")
          .append("purpose is to provide guidance up front. Each skill needs to be loaded at most once ")
          .append("per conversation.");
        return sb.toString();
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();

        Map<String, Object> skillNameProp = new LinkedHashMap<>();
        skillNameProp.put("type", "string");
        skillNameProp.put("required", true);
        skillNameProp.put("description", "Exact name of the skill to load. Must match one of the names listed in this tool's description.");
        schema.put("skill_name", skillNameProp);

        Map<String, Object> userInputProp = new LinkedHashMap<>();
        userInputProp.put("type", "string");
        userInputProp.put("required", true);
        userInputProp.put("description", "The user's task to fill into the skill's prompt template (replaces {{user_input}} in the skill).");
        schema.put("user_input", userInputProp);

        return schema;
    }

    @Override
    public String category() {
        return "skill";
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> params) {
        if (params == null) {
            return ToolResult.error("invoke_skill called with null params");
        }
        Object skillNameObj = params.get("skill_name");
        Object userInputObj = params.get("user_input");
        if (!(skillNameObj instanceof String) || ((String) skillNameObj).isBlank()) {
            return ToolResult.error("invoke_skill requires a non-empty 'skill_name' string parameter");
        }
        if (!(userInputObj instanceof String) || ((String) userInputObj).isBlank()) {
            return ToolResult.error("invoke_skill requires a non-empty 'user_input' string parameter");
        }
        String skillName = ((String) skillNameObj).trim();
        String userInput = (String) userInputObj;

        SkillDefinition skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            StringBuilder avail = new StringBuilder();
            List<SkillDefinition> all = skillRegistry.getAllSkills();
            for (int i = 0; i < all.size(); i++) {
                if (i > 0) avail.append(", ");
                avail.append(all.get(i).getName());
            }
            return ToolResult.error("Skill not found: '" + skillName + "'. Available skills: ["
                    + avail.toString() + "]");
        }

        // 内容加载 = systemPrompt + additionalNotes + 填了 user_input 的 promptTemplate。
        // 不分 complexity——complexity 字段是 sidecar SkillMatcher 路径用来决定"是否在 main
        // agent 之前就开 sub-agent"的,本工具是 in-context 路径,只做加载、永远不派 sub-agent。
        StringBuilder content = new StringBuilder();
        content.append("Skill '").append(skillName).append("' loaded — follow the guidance below using your regular tools.\n\n");

        content.append("## Role\n");
        content.append(skill.getSystemPrompt() != null ? skill.getSystemPrompt() : "(no system prompt)");

        if (skill.getAdditionalNotes() != null && !skill.getAdditionalNotes().isBlank()) {
            content.append("\n\n## Additional Notes\n").append(skill.getAdditionalNotes());
        }

        if (skill.getPromptTemplate() != null && !skill.getPromptTemplate().isBlank()) {
            content.append("\n\n## Task\n");
            content.append(skill.getPromptTemplate().replace("{{user_input}}", userInput));
        }

        content.append("\n\n---\nNow follow the above guidance. Use the regular tools available to ")
               .append("you (read_file, write_file, grep, etc.) to perform any actions needed. ")
               .append("Don't call invoke_skill('").append(skillName).append("') again — its guidance ")
               .append("is already in this conversation.");

        LOG.info("[InvokeSkillTool] Loaded skill '" + skillName + "' content ("
                + content.length() + " chars)");
        return ToolResult.text(content.toString());
    }

    @Override
    public boolean isConcurrentSafe() {
        // 完全无状态(SkillRegistry 读取 + 字符串拼接),并发安全。
        return true;
    }
}
