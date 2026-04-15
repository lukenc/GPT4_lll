package com.wmsay.gpt4_lll.fc.skill;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * /skill 命令处理器。
 * 纯 Java 实现，不依赖 IntelliJ API。
 * 处理 /skill list、/skill reload、/skill info 命令。
 */
public class SkillCommandHandler {

    private static final Logger LOG = Logger.getLogger(SkillCommandHandler.class.getName());

    private final SkillRegistry registry;
    private final SkillLoader loader;

    public SkillCommandHandler(SkillRegistry registry, SkillLoader loader) {
        this.registry = registry;
        this.loader = loader;
    }

    /**
     * 判断消息是否为 /skill 命令。
     */
    public boolean isSkillCommand(String message) {
        return message != null && message.trim().startsWith("/skill");
    }

    /**
     * 处理 /skill 命令并返回响应文本。
     */
    public String handleCommand(String message) {
        if (message == null) {
            return getHelpText();
        }
        String trimmed = message.trim();

        if (trimmed.equals("/skill list")) {
            return handleList();
        }
        if (trimmed.equals("/skill reload")) {
            return handleReload();
        }
        if (trimmed.equals("/skill info")) {
            return handleInfo("");
        }
        if (trimmed.startsWith("/skill info ")) {
            String skillName = trimmed.substring("/skill info ".length()).trim();
            return handleInfo(skillName);
        }
        return getHelpText();
    }

    /**
     * 处理 /skill list 命令：显示所有已加载 Skill 的名称、用途描述和版本号。
     */
    String handleList() {
        List<SkillDefinition> skills = registry.getAllSkills();
        if (skills.isEmpty()) {
            return "📋 当前没有已加载的 Skill。\n\n"
                    + "请在 ~/.lll/skill/ 目录下创建 .md 文件定义 Skill，"
                    + "或使用 /skill reload 重新加载。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 已加载的 Skill 列表 (").append(skills.size()).append(" 个):\n");

        for (SkillDefinition skill : skills) {
            sb.append("\n  名称: ").append(skill.getName())
              .append(" | 版本: ").append(skill.getVersion())
              .append("\n  用途: ").append(skill.getPurpose())
              .append("\n");
        }

        return sb.toString();
    }

    /**
     * 处理 /skill reload 命令：触发重载并显示结果摘要。
     */
    String handleReload() {
        LOG.info("[Skill] User triggered reload via /skill reload command");
        SkillLoader.LoadResult result = loader.reload();

        StringBuilder sb = new StringBuilder();
        sb.append("🔄 Skill 重载完成\n\n");
        sb.append("  总文件数: ").append(result.getTotalFiles()).append("\n");
        sb.append("  成功加载: ").append(result.getSuccessCount()).append("\n");
        sb.append("  验证失败: ").append(result.getValidationFailCount()).append("\n");
        sb.append("  解析错误: ").append(result.getParseErrorCount()).append("\n");

        return sb.toString();
    }

    /**
     * 处理 /skill info <skill_name> 命令：显示指定 Skill 的详细信息。
     */
    String handleInfo(String skillName) {
        if (skillName.isEmpty()) {
            return "⚠️ 请指定 Skill 名称。用法: /skill info <skill_name>";
        }

        SkillDefinition skill = registry.getSkill(skillName);
        if (skill == null) {
            return "⚠️ 未找到名为 \"" + skillName + "\" 的 Skill。\n\n"
                    + "使用 /skill list 查看所有已加载的 Skill。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📖 Skill 详情: ").append(skill.getName()).append("\n");
        sb.append("\n  版本: ").append(skill.getVersion());
        sb.append("\n  用途: ").append(skill.getPurpose());
        sb.append("\n  触发: ").append(skill.getTrigger());

        if (skill.getSearchKeywords() != null && !skill.getSearchKeywords().isEmpty()) {
            sb.append("\n  关键词: ").append(String.join(", ", skill.getSearchKeywords()));
        }

        Path filePath = skill.getFilePath();
        if (filePath != null) {
            sb.append("\n  文件: ").append(filePath.toAbsolutePath());
        }

        if (skill.getLastModified() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sb.append("\n  最后修改: ").append(sdf.format(new Date(skill.getLastModified())));
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * 返回使用帮助信息。
     */
    String getHelpText() {
        return "📌 Skill 命令帮助\n\n"
                + "  /skill list          — 查看所有已加载的 Skill\n"
                + "  /skill reload        — 重新加载 Skill 文件\n"
                + "  /skill info <name>   — 查看指定 Skill 的详细信息\n";
    }
}
