package com.wmsay.gpt4_lll.fc.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Skill 注册表 — 线程安全。
 * 使用 ConcurrentHashMap 存储，保证多线程读写安全。
 */
public class SkillRegistry {

    private static final Logger LOG = Logger.getLogger(SkillRegistry.class.getName());

    private final ConcurrentHashMap<String, SkillDefinition> skills = new ConcurrentHashMap<>();

    /**
     * 注册 Skill。重复名称时用新版本替换旧版本并记录信息日志。
     */
    public void register(SkillDefinition skill) {
        if (skill == null) {
            LOG.warning("[Skill] Cannot register null SkillDefinition");
            return;
        }
        SkillDefinition previous = skills.put(skill.getName(), skill);
        if (previous != null) {
            LOG.info("[Skill] Replaced existing skill: " + skill.getName()
                    + " (version " + previous.getVersion() + " -> " + skill.getVersion() + ")");
        } else {
            LOG.info("[Skill] Registered skill: " + skill.getName() + " (version " + skill.getVersion() + ")");
        }
    }

    /**
     * 从注册表中移除指定 Skill。
     */
    public void unregister(String skillName) {
        if (skillName == null) {
            return;
        }
        SkillDefinition removed = skills.remove(skillName);
        if (removed != null) {
            LOG.info("[Skill] Unregistered skill: " + skillName);
        }
    }

    /**
     * 按名称查询 Skill。
     * @return 对应的 SkillDefinition，不存在时返回 null
     */
    public SkillDefinition getSkill(String skillName) {
        if (skillName == null) {
            return null;
        }
        return skills.get(skillName);
    }

    /**
     * 返回所有已注册 Skill 的不可变列表。
     */
    public List<SkillDefinition> getAllSkills() {
        return Collections.unmodifiableList(new ArrayList<>(skills.values()));
    }

    /**
     * 返回当前已注册的 Skill 数量。
     */
    public int getSkillCount() {
        return skills.size();
    }

    /**
     * 返回所有 generated=true 的 Skill 的不可变列表。
     */
    public List<SkillDefinition> getGeneratedSkills() {
        List<SkillDefinition> result = new ArrayList<>();
        for (SkillDefinition skill : skills.values()) {
            if (skill.isGenerated()) {
                result.add(skill);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 查询指定名称的 Skill 是否为动态生成。
     *
     * @param skillName Skill 名称
     * @return 如果 Skill 存在且 generated=true 返回 true，否则返回 false
     */
    public boolean isGenerated(String skillName) {
        if (skillName == null) {
            return false;
        }
        SkillDefinition skill = skills.get(skillName);
        return skill != null && skill.isGenerated();
    }

    /**
     * 清空所有已注册的 Skill。
     */
    public void clear() {
        skills.clear();
        LOG.info("[Skill] Registry cleared");
    }
}
