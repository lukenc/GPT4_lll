package com.wmsay.gpt4_lll.fc.skill;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Skill 加载器。
 * 负责扫描、解析、验证、注册的完整流程。
 * 支持 WatchService 热加载和自定义目录配置。
 */
public class SkillLoader {

    private static final Logger LOG = Logger.getLogger(SkillLoader.class.getName());
    private static final String LOG_PREFIX = "[Skill] ";

    private static final String DEFAULT_SKILL_DIR = ".lll/skill/";
    private static final String CONFIG_FILE = ".lll/skill-config.json";
    private static final String CONFIG_KEY_DIRECTORY = "skill.directory";

    private final SkillRegistry registry;
    private final SkillParser parser;
    private final SkillValidator validator;
    private final SkillFileWatcher watcher;
    private final List<String> allowedToolNames;

    private volatile Path skillDirectory;

    public SkillLoader(SkillRegistry registry, SkillParser parser,
                       SkillValidator validator, SkillFileWatcher watcher) {
        this(registry, parser, validator, watcher, Collections.emptyList());
    }

    public SkillLoader(SkillRegistry registry, SkillParser parser,
                       SkillValidator validator, SkillFileWatcher watcher,
                       List<String> allowedToolNames) {
        this.registry = registry;
        this.parser = parser;
        this.validator = validator;
        this.watcher = watcher;
        this.allowedToolNames = allowedToolNames != null
                ? Collections.unmodifiableList(new ArrayList<>(allowedToolNames))
                : Collections.emptyList();
    }

    // ── LoadResult 内部类 ───────────────────────────────────────────

    /**
     * 加载结果摘要 — 不可变对象。
     */
    public static class LoadResult {
        private final int totalFiles;
        private final int successCount;
        private final int validationFailCount;
        private final int parseErrorCount;

        public LoadResult(int totalFiles, int successCount,
                          int validationFailCount, int parseErrorCount) {
            this.totalFiles = totalFiles;
            this.successCount = successCount;
            this.validationFailCount = validationFailCount;
            this.parseErrorCount = parseErrorCount;
        }

        public int getTotalFiles() { return totalFiles; }
        public int getSuccessCount() { return successCount; }
        public int getValidationFailCount() { return validationFailCount; }
        public int getParseErrorCount() { return parseErrorCount; }

        @Override
        public String toString() {
            return "LoadResult{total=" + totalFiles
                    + ", success=" + successCount
                    + ", validationFail=" + validationFailCount
                    + ", parseError=" + parseErrorCount + "}";
        }
    }

    // ── 核心加载方法 ────────────────────────────────────────────────

    /**
     * 执行完整加载流程：解析配置 → 确保目录 → 清空注册表 → 扫描 → 解析 → 验证 → 注册。
     */
    public LoadResult loadAll() {
        Path directory = resolveSkillDirectory();
        this.skillDirectory = directory;
        System.out.println(LOG_PREFIX + "Loading skills from: " + directory);

        // 确保目录存在
        boolean directoryCreated = ensureDirectory(directory);

        // 检查目录是否为空（首次创建或用户清空）
        if (directoryCreated || isDirectoryEmpty(directory)) {
            System.out.println(LOG_PREFIX + "Skill directory is empty or newly created, generating templates: " + directory);
            generateTemplates(directory);
        }

        // 清空注册表
        registry.clear();

        // 扫描 .md 文件
        List<Path> mdFiles = scanMdFiles(directory);
        System.out.println(LOG_PREFIX + "Found " + mdFiles.size() + " .md file(s) in: " + directory);

        int totalFiles = mdFiles.size();
        int successCount = 0;
        int validationFailCount = 0;
        int parseErrorCount = 0;

        for (Path file : mdFiles) {
            // 解析
            SkillDefinition skill = parser.parse(file);
            if (skill == null) {
                parseErrorCount++;
                LOG.warning(LOG_PREFIX + "Parse failed for file: " + file.getFileName());
                continue;
            }

            // 验证
            SkillValidator.ValidationResult result = validator.validate(skill, allowedToolNames);
            if (!result.isValid()) {
                validationFailCount++;
                System.out.println(LOG_PREFIX + "Validation FAILED for skill '" + skill.getName()
                        + "': " + result.getViolations());
                continue;
            }

            // 注册
            registry.register(skill);
            successCount++;
        }

        LoadResult loadResult = new LoadResult(totalFiles, successCount, validationFailCount, parseErrorCount);
        System.out.println(LOG_PREFIX + "Load complete: " + loadResult);

        return loadResult;
    }

    /**
     * 热加载：重新执行完整加载流程。
     * 失败时保留上一次成功的 SkillRegistry 状态。
     */
    public LoadResult reload() {
        LOG.info(LOG_PREFIX + "Reload triggered");
        try {
            return loadAll();
        } catch (Exception e) {
            LOG.log(Level.WARNING, LOG_PREFIX + "Reload failed, preserving previous registry state", e);
            return new LoadResult(0, 0, 0, 0);
        }
    }

    /**
     * 停止文件监听并释放资源。
     */
    public void stopWatching() {
        watcher.stop();
        LOG.info(LOG_PREFIX + "File watching stopped");
    }

    /**
     * 启动文件监听，变更时自动触发 reload()。
     */
    public void startWatching() {
        Path directory = this.skillDirectory;
        if (directory == null) {
            directory = resolveSkillDirectory();
        }
        watcher.start(directory, this::reload);
        LOG.info(LOG_PREFIX + "File watching started for: " + directory);
    }

    /**
     * 获取当前 Skill 目录路径。
     */
    public Path getSkillDirectory() {
        return skillDirectory;
    }

    // ── 目录与配置解析 ──────────────────────────────────────────────

    /**
     * 解析 Skill 目录路径：优先从配置文件读取，无效时回退默认路径。
     */
    Path resolveSkillDirectory() {
        Path configPath = Paths.get(System.getProperty("user.home"), CONFIG_FILE);

        if (Files.exists(configPath)) {
            try {
                String configContent = Files.readString(configPath, StandardCharsets.UTF_8);
                JSONObject config = JSON.parseObject(configContent);
                if (config != null && config.containsKey(CONFIG_KEY_DIRECTORY)) {
                    String customDir = config.getString(CONFIG_KEY_DIRECTORY);
                    if (customDir != null && !customDir.isEmpty()) {
                        // 展开 ~ 为用户主目录
                        if (customDir.startsWith("~")) {
                            customDir = System.getProperty("user.home") + customDir.substring(1);
                        }
                        Path customPath = Paths.get(customDir);
                        // 验证路径有效性：如果父目录存在或路径本身存在，则使用
                        if (Files.exists(customPath) || (customPath.getParent() != null && Files.exists(customPath.getParent()))) {
                            LOG.info(LOG_PREFIX + "Using custom skill directory from config: " + customPath);
                            return customPath;
                        } else {
                            LOG.warning(LOG_PREFIX + "Custom skill directory is invalid or inaccessible: "
                                    + customDir + ", falling back to default");
                        }
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, LOG_PREFIX + "Failed to read config file: " + configPath, e);
            }
        }

        return getDefaultSkillDirectory();
    }

    /**
     * 获取默认 Skill 目录路径：~/.lll/skill/
     */
    static Path getDefaultSkillDirectory() {
        return Paths.get(System.getProperty("user.home"), DEFAULT_SKILL_DIR);
    }

    /**
     * 确保目录存在，不存在则创建。
     * @return true 如果目录是新创建的
     */
    boolean ensureDirectory(Path directory) {
        if (Files.exists(directory)) {
            return false;
        }
        try {
            Files.createDirectories(directory);
            LOG.info(LOG_PREFIX + "Created skill directory: " + directory);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, LOG_PREFIX + "Failed to create skill directory: " + directory, e);
            return false;
        }
    }

    /**
     * 检查目录是否为空（不含任何 .md 文件）。
     */
    boolean isDirectoryEmpty(Path directory) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.md")) {
            return !stream.iterator().hasNext();
        } catch (IOException e) {
            LOG.log(Level.FINE, LOG_PREFIX + "Could not check if directory is empty: " + directory, e);
            return true;
        }
    }

    /**
     * 扫描目录下所有 .md 文件（忽略子目录和非 .md 文件）。
     */
    List<Path> scanMdFiles(Path directory) {
        List<Path> mdFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.md")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    mdFiles.add(entry);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, LOG_PREFIX + "Failed to scan directory: " + directory, e);
        }
        return mdFiles;
    }

    // ── 示例模板生成 ─────────────────────────────────────────────────

    /**
     * 生成示例 Skill 模板文件。
     * 当目录为空或首次创建时调用。
     * 生成 6 个示例模板，覆盖常见开发场景。
     */
    void generateTemplates(Path directory) {
        int count = 0;

        // 模板 1：代码审查
        if (writeTemplate(directory, "code-review.md",
                "<!-- version: 1.0 -->\n"
                + "<!-- Skill 名称: 代码审查 -->\n"
                + "<!-- 说明: 这是一个示例 Skill 模板，您可以根据需要修改 -->\n\n"
                + "## System\n"
                + "<!-- 系统提示词：定义 Agent 的角色和行为 -->\n\n"
                + "你是一个专业的代码审查专家。请对用户提供的代码进行全面审查，发现潜在问题并提供改进建议。\n\n"
                + "## Description\n\n"
                + "### Purpose\n"
                + "<!-- 用途描述：说明这个 Skill 的功能 -->\n\n"
                + "对代码进行专业审查，发现潜在问题并提供改进建议。\n\n"
                + "### Trigger\n"
                + "<!-- 触发时机：描述何时应该匹配到这个 Skill -->\n\n"
                + "当用户请求代码审查、代码评审、review 代码时触发。\n\n"
                + "## Prompt\n"
                + "<!-- 执行提示模板：{{user_input}} 会被替换为用户的实际输入 -->\n\n"
                + "请对以下代码进行全面审查：\n\n"
                + "{{user_input}}\n\n"
                + "请从以下维度进行分析：\n"
                + "1. 代码质量\n"
                + "2. 潜在 Bug\n"
                + "3. 性能问题\n"
                + "4. 安全风险\n\n"
                + "## Search Keywords\n"
                + "<!-- 搜索关键词：用于快速预过滤匹配 -->\n\n"
                + "- 代码审查\n"
                + "- code review\n"
                + "- 审查\n"
                + "- review\n")) {
            count++;
        }

        // 模板 2：单元测试生成
        if (writeTemplate(directory, "unit-test.md",
                "<!-- version: 1.0 -->\n"
                + "<!-- Skill 名称: 单元测试生成 -->\n"
                + "<!-- 说明: 这是一个示例 Skill 模板，您可以根据需要修改 -->\n\n"
                + "## System\n"
                + "<!-- 系统提示词：定义 Agent 的角色和行为 -->\n\n"
                + "你是一个单元测试专家。请根据用户提供的代码生成高质量的单元测试。\n\n"
                + "## Description\n\n"
                + "### Purpose\n"
                + "<!-- 用途描述：说明这个 Skill 的功能 -->\n\n"
                + "根据代码自动生成单元测试，覆盖主要逻辑分支和边界条件。\n\n"
                + "### Trigger\n"
                + "<!-- 触发时机：描述何时应该匹配到这个 Skill -->\n\n"
                + "当用户请求生成单元测试、写测试、test 时触发。\n\n"
                + "## Prompt\n"
                + "<!-- 执行提示模板：{{user_input}} 会被替换为用户的实际输入 -->\n\n"
                + "请为以下代码生成单元测试：\n\n"
                + "{{user_input}}\n\n"
                + "要求：\n"
                + "1. 覆盖主要逻辑分支\n"
                + "2. 包含边界条件测试\n"
                + "3. 使用 JUnit 5 框架\n"
                + "4. 添加清晰的测试方法命名\n\n"
                + "## Search Keywords\n"
                + "<!-- 搜索关键词：用于快速预过滤匹配 -->\n\n"
                + "- 单元测试\n"
                + "- unit test\n"
                + "- 测试\n"
                + "- test\n")) {
            count++;
        }

        // 模板 3：代码重构建议
        if (writeTemplate(directory, "refactor.md",
                "<!-- version: 1.0 -->\n"
                + "<!-- Skill 名称: 代码重构建议 -->\n"
                + "<!-- 说明: 这是一个示例 Skill 模板，您可以根据需要修改 -->\n\n"
                + "## System\n"
                + "<!-- 系统提示词：定义 Agent 的角色和行为 -->\n\n"
                + "你是一个代码重构专家。请分析用户提供的代码并给出重构建议。\n\n"
                + "## Description\n\n"
                + "### Purpose\n"
                + "<!-- 用途描述：说明这个 Skill 的功能 -->\n\n"
                + "分析代码结构，提供重构建议以提高代码质量和可维护性。\n\n"
                + "### Trigger\n"
                + "<!-- 触发时机：描述何时应该匹配到这个 Skill -->\n\n"
                + "当用户请求代码重构、优化代码结构、refactor 时触发。\n\n"
                + "## Prompt\n"
                + "<!-- 执行提示模板：{{user_input}} 会被替换为用户的实际输入 -->\n\n"
                + "请分析以下代码并提供重构建议：\n\n"
                + "{{user_input}}\n\n"
                + "请从以下角度给出建议：\n"
                + "1. 设计模式应用\n"
                + "2. 代码复用性\n"
                + "3. 可读性改进\n"
                + "4. 复杂度降低\n\n"
                + "## Search Keywords\n"
                + "<!-- 搜索关键词：用于快速预过滤匹配 -->\n\n"
                + "- 重构\n"
                + "- refactor\n"
                + "- 优化\n"
                + "- 代码优化\n")) {
            count++;
        }

        // 模板 4：技术文档撰写
        if (writeTemplate(directory, "tech-doc.md",
                "<!-- version: 1.0 -->\n"
                + "<!-- Skill 名称: 技术文档撰写 -->\n"
                + "<!-- 说明: 这是一个示例 Skill 模板，您可以根据需要修改 -->\n\n"
                + "## System\n"
                + "<!-- 系统提示词：定义 Agent 的角色和行为 -->\n\n"
                + "你是一个技术文档撰写专家。请根据用户提供的代码或需求，生成清晰、规范的技术文档。\n\n"
                + "## Description\n\n"
                + "### Purpose\n"
                + "<!-- 用途描述：说明这个 Skill 的功能 -->\n\n"
                + "根据代码或需求描述，自动生成结构化的技术文档，包括 API 文档、设计文档、使用说明等。\n\n"
                + "### Trigger\n"
                + "<!-- 触发时机：描述何时应该匹配到这个 Skill -->\n\n"
                + "当用户请求撰写技术文档、生成 API 文档、写使用说明时触发。\n\n"
                + "## Prompt\n"
                + "<!-- 执行提示模板：{{user_input}} 会被替换为用户的实际输入 -->\n\n"
                + "请根据以下内容生成技术文档：\n\n"
                + "{{user_input}}\n\n"
                + "文档应包含：\n"
                + "1. 概述\n"
                + "2. 接口/方法说明\n"
                + "3. 参数描述\n"
                + "4. 使用示例\n"
                + "5. 注意事项\n\n"
                + "## Search Keywords\n"
                + "<!-- 搜索关键词：用于快速预过滤匹配 -->\n\n"
                + "- 技术文档\n"
                + "- 文档\n"
                + "- API 文档\n"
                + "- documentation\n"
                + "- doc\n")) {
            count++;
        }

        // 模板 5：SQL 优化
        if (writeTemplate(directory, "sql-optimize.md",
                "<!-- version: 1.0 -->\n"
                + "<!-- Skill 名称: SQL 优化 -->\n"
                + "<!-- 说明: 这是一个示例 Skill 模板，您可以根据需要修改 -->\n\n"
                + "## System\n"
                + "<!-- 系统提示词：定义 Agent 的角色和行为 -->\n\n"
                + "你是一个数据库和 SQL 优化专家。请分析用户提供的 SQL 语句，找出性能瓶颈并给出优化方案。\n\n"
                + "## Description\n\n"
                + "### Purpose\n"
                + "<!-- 用途描述：说明这个 Skill 的功能 -->\n\n"
                + "分析 SQL 语句的执行效率，提供索引建议、查询重写等优化方案。\n\n"
                + "### Trigger\n"
                + "<!-- 触发时机：描述何时应该匹配到这个 Skill -->\n\n"
                + "当用户请求优化 SQL、分析慢查询、SQL 调优时触发。\n\n"
                + "## Prompt\n"
                + "<!-- 执行提示模板：{{user_input}} 会被替换为用户的实际输入 -->\n\n"
                + "请分析并优化以下 SQL 语句：\n\n"
                + "{{user_input}}\n\n"
                + "请从以下方面进行优化分析：\n"
                + "1. 索引使用情况\n"
                + "2. 查询计划分析\n"
                + "3. JOIN 优化\n"
                + "4. 子查询改写\n"
                + "5. 分页优化建议\n\n"
                + "## Search Keywords\n"
                + "<!-- 搜索关键词：用于快速预过滤匹配 -->\n\n"
                + "- SQL 优化\n"
                + "- SQL\n"
                + "- 慢查询\n"
                + "- 数据库优化\n"
                + "- sql optimize\n")) {
            count++;
        }

        // 模板 6：Git Commit 消息生成
        if (writeTemplate(directory, "git-commit.md",
                "<!-- version: 1.0 -->\n"
                + "<!-- Skill 名称: Git Commit 消息生成 -->\n"
                + "<!-- 说明: 这是一个示例 Skill 模板，您可以根据需要修改 -->\n\n"
                + "## System\n"
                + "<!-- 系统提示词：定义 Agent 的角色和行为 -->\n\n"
                + "你是一个 Git 提交规范专家。请根据用户提供的代码变更内容，生成符合 Conventional Commits 规范的提交消息。\n\n"
                + "## Description\n\n"
                + "### Purpose\n"
                + "<!-- 用途描述：说明这个 Skill 的功能 -->\n\n"
                + "根据代码变更内容自动生成规范的 Git Commit 消息，遵循 Conventional Commits 格式。\n\n"
                + "### Trigger\n"
                + "<!-- 触发时机：描述何时应该匹配到这个 Skill -->\n\n"
                + "当用户请求生成 commit 消息、写提交说明、git commit message 时触发。\n\n"
                + "## Prompt\n"
                + "<!-- 执行提示模板：{{user_input}} 会被替换为用户的实际输入 -->\n\n"
                + "请根据以下代码变更生成 Git Commit 消息：\n\n"
                + "{{user_input}}\n\n"
                + "要求：\n"
                + "1. 遵循 Conventional Commits 格式：type(scope): description\n"
                + "2. type 包括：feat, fix, docs, style, refactor, test, chore\n"
                + "3. 描述简洁明了，不超过 72 个字符\n"
                + "4. 如有必要，添加详细的 body 说明\n\n"
                + "## Search Keywords\n"
                + "<!-- 搜索关键词：用于快速预过滤匹配 -->\n\n"
                + "- git commit\n"
                + "- commit message\n"
                + "- 提交消息\n"
                + "- 提交说明\n"
                + "- git\n")) {
            count++;
        }

        LOG.info(LOG_PREFIX + "Generated " + count + " template files in: " + directory);
    }

    /**
     * 写入单个模板文件。已存在则跳过。
     * @return true 如果文件成功写入，false 如果已存在或写入失败
     */
    private boolean writeTemplate(Path directory, String fileName, String content) {
        Path filePath = directory.resolve(fileName);
        if (Files.exists(filePath)) {
            return false;
        }
        try {
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            LOG.fine(LOG_PREFIX + "Generated template: " + fileName);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, LOG_PREFIX + "Failed to write template: " + fileName, e);
            return false;
        }
    }
}
