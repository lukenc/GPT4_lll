package com.wmsay.gpt4_lll.fc.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.model.FunctionCallRequest;
import com.wmsay.gpt4_lll.fc.core.ChatContent;
import com.wmsay.gpt4_lll.fc.core.Message;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 知识库管理器 — 管理项目知识条目。
 * 由 AgentRuntime 持有（Project 级别共享），注入到各 AgentSession 的 ContextManager 中。
 * 持久化到 .gpt4lll/knowledge/ 目录，每个条目存储为独立的 JSON 文件。
 * <p>
 * 支持两种构造模式：
 * - KnowledgeBase(Path projectRoot)：带持久化，自动加载磁盘数据
 * - KnowledgeBase()：无持久化（knowledgePath=null），用于测试和无需持久化的场景
 */
public class KnowledgeBase {

    private static final Logger LOG = Logger.getLogger(KnowledgeBase.class.getName());

    private final ConcurrentHashMap<String, KnowledgeEntry> entries = new ConcurrentHashMap<>();
    private final Path knowledgePath;

    /**
     * 带持久化的构造函数。
     * 初始化时自动从 .gpt4lll/knowledge/ 加载已有条目。
     */
    public KnowledgeBase(Path projectRoot) {
        this.knowledgePath = projectRoot.resolve(".gpt4lll").resolve("knowledge");
        loadFromDisk();
    }

    /**
     * 无持久化构造函数（向后兼容）。
     * knowledgePath 为 null，所有持久化操作为 no-op。
     */
    public KnowledgeBase() {
        this.knowledgePath = null;
    }

    /**
     * 添加知识条目（需求 14.2）。
     * 同时持久化到磁盘（如果 knowledgePath 非 null）。
     */
    public void addEntry(KnowledgeEntry entry) {
        if (entry != null) {
            entries.put(entry.getId(), entry);
            persistEntry(entry);
        }
    }

    /**
     * 移除知识条目（需求 14.2）。
     * 同时删除磁盘文件（如果 knowledgePath 非 null）。
     */
    public void removeEntry(String entryId) {
        KnowledgeEntry removed = entries.remove(entryId);
        if (removed != null) {
            deleteEntryFile(entryId);
        }
    }

    /**
     * 按类型查询知识条目列表（需求 14.3）。
     */
    public List<KnowledgeEntry> getEntriesByType(KnowledgeType type) {
        return entries.values().stream()
            .filter(e -> e.getType() == type)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 将所有知识条目格式化为可注入系统提示词的文本片段（需求 14.4）。
     * 按 ARCHITECTURE → GENERAL → ERROR_PATTERN 优先级排序（需求 14.5）。
     */
    public String buildKnowledgePrompt() {
        StringBuilder sb = new StringBuilder();
        appendEntries(sb, KnowledgeType.ARCHITECTURE, "## 项目架构");
        appendEntries(sb, KnowledgeType.GENERAL, "## 通用知识");
        appendEntries(sb, KnowledgeType.ERROR_PATTERN, "## 错误避免模式");
        return sb.toString();
    }

    /**
     * 通过 LLM 分析项目结构自动生成 ARCHITECTURE 类型的知识条目（需求 14.9）。
     * LLM 调用失败时记录错误日志并返回 null（需求 14.10）。
     */
    public KnowledgeEntry generateArchitectureEntry(
            LlmCaller llmCaller, String projectRoot) {
        try {
            // 构建简单的分析请求
            ChatContent chatContent = new ChatContent();
            Message systemMsg = new Message();
            systemMsg.setRole("system");
            systemMsg.setContent("你是一个项目架构分析专家。请分析以下项目根目录的结构，" +
                "生成一段简洁的项目架构描述，包括项目类型、主要模块、技术栈和关键设计模式。" +
                "只输出架构描述文本，不要输出其他内容。");

            Message userMsg = new Message();
            userMsg.setRole("user");
            userMsg.setContent("请分析项目根目录: " + projectRoot);

            chatContent.setDirectMessages(List.of(systemMsg, userMsg));
            chatContent.setStream(false);

            FunctionCallRequest request = FunctionCallRequest.builder()
                .chatContent(chatContent)
                .maxRounds(1)
                .build();

            String response = llmCaller.call(request);
            if (response == null || response.isBlank()) {
                LOG.warning("LLM returned empty response for architecture generation");
                return null;
            }

            String entryId = "arch-" + UUID.randomUUID().toString().substring(0, 8);
            KnowledgeEntry entry = new KnowledgeEntry(
                entryId,
                KnowledgeType.ARCHITECTURE,
                "项目架构描述",
                response.trim(),
                System.currentTimeMillis()
            );
            addEntry(entry);
            return entry;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to generate architecture entry via LLM", e);
            return null;
        }
    }

    public int size() {
        return entries.size();
    }

    // ---- Persistence methods ----

    /**
     * 从 .gpt4lll/knowledge/ 目录加载所有 JSON 文件恢复知识条目（需求 14.8）。
     * IO 错误时记录日志，不影响启动。
     */
    private void loadFromDisk() {
        if (knowledgePath == null) return;
        if (!Files.exists(knowledgePath)) return;

        try (Stream<Path> files = Files.list(knowledgePath)) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadEntryFile);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list knowledge directory: " + knowledgePath, e);
        }
    }

    private void loadEntryFile(Path file) {
        try {
            String content = Files.readString(file);
            JSONObject json = JSON.parseObject(content);
            if (json == null) return;

            String id = json.getString("id");
            String typeStr = json.getString("type");
            String title = json.getString("title");
            String entryContent = json.getString("content");
            Long createdAt = json.getLong("createdAt");

            if (id == null || typeStr == null || title == null || entryContent == null) {
                LOG.warning("Skipping malformed knowledge entry file: " + file);
                return;
            }

            KnowledgeType type;
            try {
                type = KnowledgeType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                LOG.warning("Unknown knowledge type '" + typeStr + "' in file: " + file);
                return;
            }

            KnowledgeEntry entry = new KnowledgeEntry(
                id, type, title, entryContent,
                createdAt != null ? createdAt : 0L
            );
            entries.put(id, entry);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load knowledge entry from file: " + file, e);
        }
    }

    /**
     * 将知识条目持久化为 JSON 文件（需求 14.7）。
     * knowledgePath 为 null 时为 no-op。
     */
    private void persistEntry(KnowledgeEntry entry) {
        if (knowledgePath == null) return;

        try {
            Files.createDirectories(knowledgePath);
            Path file = knowledgePath.resolve(entry.getId() + ".json");
            JSONObject json = new JSONObject();
            json.put("id", entry.getId());
            json.put("type", entry.getType().name());
            json.put("title", entry.getTitle());
            json.put("content", entry.getContent());
            json.put("createdAt", entry.getCreatedAt());
            Files.writeString(file, json.toJSONString());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to persist knowledge entry: " + entry.getId(), e);
        }
    }

    /**
     * 删除知识条目的 JSON 文件。
     * knowledgePath 为 null 时为 no-op。
     */
    private void deleteEntryFile(String entryId) {
        if (knowledgePath == null) return;

        try {
            Path file = knowledgePath.resolve(entryId + ".json");
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete knowledge entry file: " + entryId, e);
        }
    }

    // ---- Helper methods ----

    private void appendEntries(StringBuilder sb, KnowledgeType type, String header) {
        List<KnowledgeEntry> list = getEntriesByType(type);
        if (!list.isEmpty()) {
            sb.append(header).append("\n\n");
            for (KnowledgeEntry e : list) {
                sb.append("### ").append(e.getTitle()).append("\n");
                sb.append(e.getContent()).append("\n\n");
            }
        }
    }
}
