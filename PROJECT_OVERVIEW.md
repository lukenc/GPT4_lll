# Project Overview

## 1. 项目目标与核心定位

GPT4_lll 是一款 JetBrains IntelliJ IDEA 插件（Plugin ID: `com.wmsay.GPT4_lll`，当前版本 3.9.1），其核心定位是**将多家 AI/LLM 平台的能力无缝集成到 IDE 开发工作流中**，为开发者提供代码生成、代码优化、代码评估、注释生成、注释翻译、SQL 优化、单元测试生成、Git 提交信息生成、工作报告生成、Linter 修复建议等一站式 AI 辅助开发能力。

项目起源于 2023 年 OpenAI GPT 时代，最初仅支持 OpenAI 接口。随着国内 API 封号问题（代码注释中有明确记录：`plugin.xml` change-notes 中 3.2.0 版本说明），项目逐步拥抱国内大模型生态，目前已支持 7 个 AI 供应商平台。项目面向中国开发者群体为主，同时兼顾国际用户（UI 和提示信息均为中英双语）。

---

## 2. 项目整体架构

### 架构风格

本项目**没有采用分层架构**（如 MVC、DDD 等），而是采用**以 Action 为中心的扁平结构**。每个功能对应一个 `AnAction` 子类，Action 内部直接完成 UI 交互、Prompt 构建、HTTP 请求、结果解析和编辑器操作的全流程。

核心调用链路：

```
用户触发 Action → Action 构建 Prompt + ChatContent → 调用 GenerateAction.chat() 或 ChatUtils.pureChat()
→ SSE 流式请求 AI 平台 → 实时渲染到 ToolWindow 或编辑器
```

### 关键架构决策

1. **单一核心聊天方法**：`GenerateAction.chat()` 是几乎所有 Action 共用的 SSE 流式通信方法，支持实时写入编辑器（`coding=true`）、实时显示到 ToolWindow、多轮续写、commit 文档实时更新等模式。`ChatUtils.pureChat()` 是同步版本，用于不需要流式 UI 的场景（如批量评分、注释翻译）。

2. **供应商抽象层**：通过 `ProviderNameEnum` + `ModelUtils` + `ChatUtils` 三者协作，实现了对不同 AI 平台的统一抽象。URL 路由、API Key 获取、消息格式适配均在此层完成。

3. **运行时状态存储于 Project UserData**：利用 IntelliJ 的 `Project.putUserData()` / `getUserData()` 机制，将 ComboBox 引用、聊天历史、当前话题、运行状态等绑定到 Project 实例上，实现多项目隔离。

4. **单任务互斥**：通过 `GPT_4_LLL_RUNNING_STATUS` Key 实现每个 Project 同一时刻只能运行一个 AI 任务。


---

## 3. 目录结构说明

> **Agent 提示**：修改或新增功能时，请先根据此目录结构定位到正确的文件。文件职责边界清晰，不要在错误的位置添加代码。

```
src/main/java/com/wmsay/gpt4_lll/
├── *Action.java              # 功能入口，每个 Action 对应一个用户可触发的功能
│                              # ⚠️ 新增功能必须创建新的 Action 文件，不要往已有 Action 中塞无关逻辑
├── WindowTool.java           # ToolWindowFactory，创建右侧面板 UI（供应商/模型选择、聊天区、输入框）
│                              # ⚠️ 修改 UI 布局时改这里；不要在 Action 中创建 UI 组件
├── MyPluginSettings.java     # 应用级持久化设置（API Key、代理地址等），存储到 GptlllPluginSettings.xml
│                              # ⚠️ 新增供应商时必须在 State 内部类中添加对应的 API Key 字段
├── ProjectSettings.java      # 项目级持久化设置（上次选择的供应商和模型），存储到 gpt4lllProjectSettings.xml
├── MyPluginConfigurable.java # Settings 页面 UI（Other Settings > GPT4 lll Settings）
│                              # ⚠️ 新增供应商时必须在此添加对应的输入框，并修改 isModified/apply/reset 三个方法
├── JsonStorage.java          # 聊天历史的 JSON 文件持久化，文件路径由 PluginPathUtils 决定
├── component/                # UI 组件
│   ├── Gpt4lllTextArea.java          # 核心展示区域（支持 HTML/Markdown 渲染）
│   │                                  # ⚠️ 所有 AI 回复的展示都通过此组件的 appendContent() 方法
│   ├── Gpt4lllPlaceholderTextArea.java # 带 placeholder 的输入框
│   ├── LinterFixDialog.java          # Linter 修复确认对话框（展示 CodeChange 列表）
│   ├── VCSAuthorSelectionDialog.java  # 工作报告的作者/日期选择对话框
│   ├── ChatHistoryManager.java       # 聊天历史管理增强
│   ├── CommitResultViewer.java       # 提交结果查看器
│   └── GitCommitFetcher.java         # Git 提交记录获取
├── model/                    # 数据模型（POJO）
│   ├── ChatContent.java      # 请求体模型 ⚠️ 包含百度消息格式适配逻辑 adaptBaiduMessages()
│   ├── Message.java          # 消息模型（role/content/name），所有 AI 交互的基本单元
│   ├── SseResponse.java      # OpenAI 标准 SSE 响应模型（choices[].delta.content）
│   ├── SelectModelOption.java # 模型选项（modelName/displayName/provider/description）
│   ├── ModelProvider.java    # 供应商信息（name/url/description）
│   ├── CodeChange.java       # Linter 修复的变更操作模型（DELETE/INSERT/MODIFY）
│   ├── CommentTranslation.java # 注释翻译模型（原文/译文/偏移量）
│   ├── enums/ProviderNameEnum.java # 供应商枚举 ⚠️ 新增供应商第一步改这里
│   ├── key/                  # IntelliJ UserData Key 定义
│   │   ├── Gpt4lllChatKey.java    # GPT_4_LLL_RUNNING_STATUS / NOW_TOPIC / CONVERSATION_HISTORY
│   │   ├── Gpt4lllComboxKey.java  # PROVIDER_COMBO_BOX / MODEL_COMBO_BOX
│   │   ├── Gpt4lllTextAreaKey.java # GPT_4_LLL_TEXT_AREA
│   │   └── Gpt4lllHistoryButtonKey.java # GPT_4_LLL_HISTORY_BUTTON
│   ├── baidu/                # 百度特有响应模型（BaiduSseResponse 用 result 字段而非 choices）
│   ├── complete/             # 请求体模型
│   └── server/               # 服务端认证相关模型（ApiResponse/ApiToken/TokenResult）
├── utils/                    # 工具类
│   ├── ChatUtils.java        # ⚠️ 核心：HTTP 客户端构建、API URL 路由、API Key 路由、同步聊天方法
│   ├── ModelUtils.java       # ⚠️ 核心：模型/供应商注册表（硬编码）、选择逻辑
│   ├── CommonUtil.java       # 通用工具（语言检测、文件类型检测、运行状态管理）
│   ├── AuthUtils.java        # 百度 OAuth Token 获取与本地缓存（含免费 Token 从 blog.wmsay.com 获取）
│   ├── CodeUtils.java        # 代码评估标准 Prompt 定义（BACK_END_DEV_STD_PROMPT 等）
│   ├── SelectionUtils.java   # 编辑器选区工具（带行号的选区内容）
│   ├── FileTypeDetector.java # 文件类型检测（判断是否为外部库文件）
│   ├── CommentTranslationStorage.java # 翻译缓存持久化
│   ├── PluginPathUtils.java  # 插件临时文件路径工具
│   └── ...
├── languages/                # 可扩展文件分析框架
│   ├── FileAnalysisService.java      # 分析服务接口（canHandle/getPriority/analyzeInfoToMessage）
│   ├── FileAnalysisManager.java      # 服务管理器（优先级调度，@Service 注解）
│   └── extensionPoints/
│       ├── JavaFileAnalysisService.java    # Java PSI 分析（优先级 10），通过 java-support.xml 注册
│       └── GenericFileAnalysisService.java # 通用分析（优先级 0，兜底）
├── service/                  # 业务服务
│   ├── CommentTranslationService.java     # 注释翻译服务（提取、翻译、解析）
│   └── TranslationProgressCallback.java   # 翻译进度回调接口
└── view/                     # 编辑器渲染层
    ├── CommentTranslationRenderer.java    # 基于 FoldRegion 的翻译渲染，[GPT4LLL] 前缀标识
    └── ...TranslationRenderer.java        # 其他渲染器变体
```

### 配置文件

```
src/main/resources/META-INF/
├── plugin.xml          # 主配置：注册所有 Action、ToolWindow、Service、Configurable
│                       # ⚠️ 新增 Action 必须在此注册，否则用户看不到
└── java-support.xml    # 可选依赖配置：当 com.intellij.java 插件可用时加载 JavaFileAnalysisService
                        # ⚠️ 新增语言分析服务时参考此文件的模式
```


---

## 4. 技术栈与运行方式

| 维度 | 详情 |
|------|------|
| 编程语言 | Java 17（主要），Kotlin JVM 1.8.21（仅构建脚本） |
| 构建工具 | Gradle 8.13 + IntelliJ Platform Plugin 2.5.0 |
| 目标 IDE | IntelliJ IDEA Community 2025.1，兼容范围 222 ~ 253.* |
| JSON 序列化 | `com.alibaba:fastjson:1.2.83` — 全项目统一使用 fastjson，不要引入 gson/jackson |
| Markdown 渲染 | `com.vladsch.flexmark:flexmark:0.64.8` — Markdown→HTML 转换 |
| JavaScript 引擎 | `org.mozilla:rhino:1.7.15` |
| HTML 解析 | `org.jsoup:jsoup:1.17.2` |
| 捆绑插件依赖 | `com.intellij.java`（可选，用于 Java PSI 分析）、`Git4Idea`（必需，用于 Git 操作） |
| HTTP 通信 | Java 11 标准库 `java.net.http.HttpClient`，SSE 流式处理，**不要引入 OkHttp 等第三方 HTTP 库** |
| 持久化 | IntelliJ `PersistentStateComponent`（设置）+ 自定义 JSON 文件（聊天历史、Token 缓存） |

### 构建与运行

```bash
./gradlew buildPlugin    # 构建插件
./gradlew runIde         # 在沙箱 IDE 中运行
./gradlew publishPlugin  # 发布（需要环境变量 PUBLISH_TOKEN / CERTIFICATE_CHAIN / PRIVATE_KEY）
```

---

## 5. 核心模块解析

> **Agent 提示**：本节详细说明每个核心模块的职责、关键方法签名和调用关系。修改代码前务必理解这些模块之间的依赖。

### 5.1 GenerateAction — 核心聊天引擎

**文件**：`src/main/java/com/wmsay/gpt4_lll/GenerateAction.java`

承担双重职责：

**作为 Action**：处理代码生成/优化/TODO 补全。通过 PSI 分析判断选中内容的类型：
- `isSelectedTextAllComments()` 返回 true → 按注释描述生成代码
- `getCommentTODO()` 返回非空 → 按 TODO 描述补全实现（使用 `TODO_PROMPT` 模板）
- 其他 → 代码重构优化

**作为静态方法提供者**：`GenerateAction.chat()` 是全局共享的 SSE 流式通信方法。

关键方法签名：
```java
// 最常用的调用形式
public static String chat(ChatContent content, Project project, Boolean coding, 
                          Boolean replyShowInWindow, String loadingNotice)

// 完整形式（含重试和 commit 文档支持）
public static String chat(ChatContent content, Project project, Boolean coding, 
                          Boolean replyShowInWindow, String loadingNotice, 
                          Integer retryTime, Document commitDoc)
```

参数说明（**Agent 必须理解**）：
- `coding=true`：实时将 AI 返回的 Markdown 代码块内容写入编辑器（通过检测 ``` 标记的开闭）
- `coding=false`：不写入编辑器，仅展示在 ToolWindow
- `replyShowInWindow=true`：AI 回复实时显示在 ToolWindow 的 Gpt4lllTextArea 中
- `replyShowInWindow=false`：不实时显示，仅显示 loadingNotice 的进度提示（用于多轮对话的中间轮）
- `retryTime`：百度供应商自动续写的重试计数，初始传 0，最大 2 次
- `commitDoc`：Git commit 消息文档，非空时实时更新 commit 输入框

**被以下 Action 调用**：
- `CommentAction` → `chat(content, project, true, true, "")`
- `ScoreAction` → `chat(content, project, false, true, "")`
- `SqlAction` → `chat(content, project, false, true, "")`
- `UnitTestAction` → 两轮调用：第一轮 `chat(..., false, false, "")` 分析用例，第二轮 `chat(..., false, true, "")` 生成代码
- `GenerateVersionControlCommitMessage` → `chat(content, project, false, true, "", 0, commitDoc)`
- `LinterFixAction` → 使用自己的 `chatWithLinterFix()` 方法（类似逻辑但返回 JSON）
- `WindowTool` 中的发送按钮 → `chat(content, project, false, true, "")`

### 5.2 ChatUtils — API 路由与通信

**文件**：`src/main/java/com/wmsay/gpt4_lll/utils/ChatUtils.java`

> **Agent 提示**：新增供应商时，此文件的 `getUrl()` 和 `getApiKey()` 方法是必须修改的。

核心方法及其职责：

```java
// URL 路由 — 根据供应商返回正确的 API 端点
// ⚠️ 百度和 FREE 供应商需要拼接 access_token 参数
// ⚠️ PERSONAL 供应商使用用户自定义 URL
// ⚠️ 其他供应商从 ModelUtils.getUrlByProvider() 获取
public static String getUrl(MyPluginSettings settings, Project project)
public static String getUrlByProvider(MyPluginSettings settings, String provider, String modelName)

// API Key 路由 — 根据供应商从 MyPluginSettings 获取对应的 Key
// ⚠️ 新增供应商必须在此添加 if 分支
public static String getApiKey(MyPluginSettings settings, Project project)

// 同步聊天方法 — 用于不需要流式 UI 的场景
// 被 ScoreFilesAction2（批量评分）和 CommentTranslationService（注释翻译）使用
public static String pureChat(String provider, String apiKey, ChatContent content)

// HTTP 基础设施
public static HttpClient buildHttpClient(String proxy, Project project)  // 支持代理
public static HttpRequest buildHttpRequest(String url, String requestBody, String apiKey)  // Bearer Token + SSE

// 续写判断 — 百度供应商专用
// 回复不以 . 。 ? ？ ！ ! } ; ； ``` 结尾时返回 true
public static Boolean needsContinuation(String replyContent)

// 聊天历史管理 — 基于 Project UserData
public static List<Message> getProjectChatHistory(Project project)
public static String getProjectTopic(Project project)
public static void setProjectTopic(Project project, String topic)
```

### 5.3 ModelUtils — 模型注册表

**文件**：`src/main/java/com/wmsay/gpt4_lll/utils/ModelUtils.java`

> **Agent 提示**：添加新模型只需修改此文件的静态初始化块。添加新供应商需要同时修改此文件和 `ProviderNameEnum`。

所有供应商和模型信息在静态初始化块中**硬编码注册**：

```java
// 供应商注册格式：
modelProviders.add(new ModelProvider(
    ProviderNameEnum.XXX.getProviderName(),  // 供应商显示名
    "https://api.xxx.com/v1/chat/completions",  // API 端点 URL
    "供应商描述"
));

// 模型注册格式：
modelOptions.add(new SelectModelOption(
    "model-identifier",           // API 请求中的 model 字段值
    "模型描述",                    // 描述信息
    ProviderNameEnum.XXX.getProviderName(),  // 所属供应商
    "显示名称"                     // UI 下拉框中显示的名称
));
```

当前已注册的供应商及其 URL：
| 供应商 | 枚举值 | API URL |
|--------|--------|---------|
| 百度文心一言 | `BAIDU` | `https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/` + modelName + `?access_token=` |
| 免费系列 | `FREE` | 硬编码 `ernie-speed-128k`，复用百度基础设施 |
| OpenAI | `OPEN_AI` | `https://api.openai.com/v1/chat/completions` |
| 阿里通义千问 | `ALI` | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` |
| X-GROK | `GROK` | `https://api.x.ai/v1/chat/completions` |
| DeepSeek | `DEEP_SEEK` | `https://api.deepseek.com/chat/completions` |
| 自定义 | `PERSONAL` | 用户在设置中填写的 URL |

关键辅助方法：
```java
// 三级优先级获取供应商/模型（当前选中 > 上次保存 > 系统默认）
// 系统默认供应商：ALI（阿里云），默认模型：qwen-turbo
public static String getAvailableProvider(Project project)
public static String getAvailableModelName(Project project)
```

### 5.4 ChatContent — 请求体与百度适配

**文件**：`src/main/java/com/wmsay/gpt4_lll/model/ChatContent.java`

> **Agent 提示**：这是一个看似简单但包含关键逻辑的模型类。`setMessages()` 方法会根据供应商自动调用 `adaptBaiduMessages()`。

```java
// ⚠️ 注意：setMessages 不是简单的 setter，它包含百度格式适配逻辑
public void setMessages(List<Message> messages, String providerName) {
    this.messages = messages;
    if (BAIDU 或 FREE 供应商) {
        adaptBaiduMessages();  // 自动调整消息格式
    }
}
```

`adaptBaiduMessages()` 的规则：
1. 第一条消息的 role 强制设为 `"user"`
2. 奇数索引位置如果是 `"user"` 角色，则在该位置插入一条 assistant 占位消息
3. 目的：确保百度 API 要求的 user/assistant 严格交替

### 5.5 FileAnalysisManager — 可扩展文件分析框架

**文件**：`src/main/java/com/wmsay/gpt4_lll/languages/FileAnalysisManager.java`

> **Agent 提示**：如果要为新语言（如 Kotlin、Python）添加专用分析服务，参考 `JavaFileAnalysisService` 的模式。

采用**策略模式 + 优先级调度**：
- `FileAnalysisService` 接口定义 `canHandle(VirtualFile)`、`getPriority()`、`analyzeInfoToMessage(Project, Editor)` 等方法
- `JavaFileAnalysisService`（优先级 10）：通过 Java PSI API 提取类信息、字段、方法签名，为 AI 提供丰富的代码上下文
- `GenericFileAnalysisService`（优先级 0）：通用兜底实现
- 通过 `java-support.xml` 可选依赖注册 Java 服务，`FileAnalysisManager` 使用反射 + IntelliJ 服务容器动态加载

新增语言分析服务的步骤：
1. 创建 `XxxFileAnalysisService implements FileAnalysisService`，设置更高优先级
2. 创建 `xxx-support.xml` 配置文件，在其中注册为 `applicationService`
3. 在 `plugin.xml` 中添加 `<depends optional="true" config-file="xxx-support.xml">对应插件ID</depends>`
4. 在 `FileAnalysisManager.createServices()` 中添加 `tryLoadServiceFromContainer()` 调用

### 5.6 CommentTranslationService — 注释翻译服务

**文件**：`src/main/java/com/wmsay/gpt4_lll/service/CommentTranslationService.java`

职责：从 PSI 文件中提取注释 → 构建翻译 Prompt → 调用 AI 翻译 → 解析结果。

关键设计：
- 注释提取基于 PSI 树遍历，支持单行注释（`//`）合并为段落、块注释（`/* */`、`/** */`）按段落拆分
- 跳过 package 声明之前的版权头注释（通过 `packageStartOffset` 判断）
- 支持批量翻译（`translateComments()`，每批 10 个）和逐个流式翻译（`translateCommentsWithCallback()`）两种模式
- 使用 `ChatUtils.pureChat()` 而非 `GenerateAction.chat()`，因为不需要流式 UI
- 使用 `ModelUtils.getAvailableProvider()` / `getAvailableModelName()` 获取供应商和模型（三级优先级）

### 5.7 CommentTranslationRenderer — 编辑器翻译渲染

**文件**：`src/main/java/com/wmsay/gpt4_lll/view/CommentTranslationRenderer.java`

使用 IntelliJ 编辑器的 `FoldRegion` 机制实现翻译显示：
- 将原始注释区域折叠，以 `[GPT4LLL] 翻译内容` 作为折叠占位文本
- `FOLD_PLACEHOLDER_PREFIX = "[GPT4LLL] "` 是识别翻译折叠的标识
- 支持翻译模式切换（`toggleTranslationMode()`）
- `clearTranslations()` 会清除本次会话的折叠和扫描残留的带前缀折叠

### 5.8 LinterFixAction — AI Linter 修复

**文件**：`src/main/java/com/wmsay/gpt4_lll/LinterFixAction.java`

> **Agent 提示**：这是一个独立性较强的 Action，有自己的 `chatWithLinterFix()` 方法而非复用 `GenerateAction.chat()`，因为它需要解析 JSON 格式的修复建议。

工作流程：
1. 通过 `DaemonCodeAnalyzerEx.processHighlights()` 获取选中区域的 ERROR/WARNING/WEAK_WARNING
2. 构建包含行号的代码和错误描述的 Prompt（`LINTER_FIX_PROMPT`）
3. AI 返回 JSON 数组格式的变更操作（DELETE/INSERT/MODIFY），对应 `CodeChange` 模型
4. 通过 `LinterFixDialog` 展示修复建议，用户确认后应用

### 5.9 GenerateVersionControlCommitMessage — Git 提交信息生成

**文件**：`src/main/java/com/wmsay/gpt4_lll/GenerateVersionControlCommitMessage.java`

- 从 VCS 数据源获取 staged/unstaged 变更（`getSelectedChangesFromCommitDialog()` 有多个 fallback 路径）
- 通过 `git diff` 命令获取文件级别的 diff 内容
- 生成符合 Conventional Commits 规范的提交信息
- 通过 `commitDoc` 参数传递给 `GenerateAction.chat()`，实现实时更新 commit 输入框
- 在 `plugin.xml` 中注册到 `Vcs.MessageActionGroup`

### 5.10 ScoreFilesAction2 — 批量文件评分

**文件**：`src/main/java/com/wmsay/gpt4_lll/ScoreFilesAction2.java`

- 从项目树选中文件/文件夹，递归收集所有文件（排除 `score_gpt4lll` 输出目录）
- 使用 `ExecutorService` 线程池并行处理（`MAX_CONCURRENT_TASKS = 5`）
- 评分结果输出为 Markdown 文件到 `score_gpt4lll` 子目录
- 使用 `ChatUtils.pureChat()` 同步调用，不依赖 ToolWindow UI
- 使用 `ProgressManager` 显示后台进度

### 5.11 UnitTestAction — 单元测试生成

**文件**：`src/main/java/com/wmsay/gpt4_lll/UnitTestAction.java`

- **仅支持 Java**（通过 `java-support.xml` 注册）
- 采用**两轮对话**策略：第一轮分析测试用例和边界条件（`replyShowInWindow=false`），第二轮生成可执行的测试代码
- 自动在 test source root 下创建对应包结构和测试文件
- 从 AI 回复中提取 Markdown 代码块内容写入文件


---

## 6. 编码风格与命名约定

> **Agent 提示**：生成的代码必须严格遵循以下约定，否则会与现有代码风格不一致。

### 命名规范

| 类别 | 规范 | 示例 |
|------|------|------|
| Action 类 | `*Action` 后缀，继承 `AnAction` | `GenerateAction`、`CommentAction`、`LinterFixAction` |
| 工具类 | `*Utils` 后缀，全部静态方法 | `ChatUtils`、`ModelUtils`（例外：`CommonUtil` 未用 `Utils`） |
| UserData Key 类 | `Gpt4lll*Key` 模式 | `Gpt4lllChatKey`、`Gpt4lllComboxKey` |
| Key 常量 | `GPT_4_LLL_` 前缀 + 大写下划线 | `GPT_4_LLL_RUNNING_STATUS`、`GPT_4_LLL_CONVERSATION_HISTORY` |
| 模型类 | 纯 POJO，getter/setter 风格 | `ChatContent`、`Message`、`SseResponse` |
| 百度特有模型 | `Baidu*` 前缀 | `BaiduSseResponse` |
| 服务类 | `*Service` 后缀 | `CommentTranslationService`、`FileAnalysisService` |
| 渲染器 | `*Renderer` 后缀 | `CommentTranslationRenderer` |
| 设置类 | `*Settings` 后缀 | `MyPluginSettings`、`ProjectSettings` |
| Prompt 常量 | 大写下划线，`public static` 或 `public static final` | `LINTER_FIX_PROMPT`、`TODO_PROMPT`、`SQL_PROMPT` |
| 枚举 | 大写下划线枚举值 | `ProviderNameEnum.DEEP_SEEK` |

### 方法命名风格

```
获取类：get*        → getApiKey(), getModelName(), getSelectedProvider()
判断类：is*/needs*  → isRunningStatus(), needsContinuation(), isValidComment()
构建类：build*      → buildHttpClient(), buildHttpRequest()
处理类：process*    → processFiles(), processFileContent()
解析类：parse*/extract* → parseChangesFromResponse(), extractComments()
适配类：adapt*      → adaptBaiduMessages()
```

### Prompt 定义风格

所有 Prompt 使用 Java Text Block（`"""`）定义，通过 `String.replace()` 进行变量替换：

```java
// ✅ 正确的 Prompt 定义方式
public static String MY_PROMPT = """
        请根据以下信息完成任务：
        - 编程语言：${fileType}
        - 回复语言：${replyLanguage}
        
        代码内容：
        ```
        ${selectedText}
        ```
        """;

// ✅ 正确的变量替换方式
String prompt = MY_PROMPT
        .replace("${fileType}", fileType)
        .replace("${replyLanguage}", replyLanguage)
        .replace("${selectedText}", selectedText);
```

### 错误提示风格

所有用户可见的错误提示均为**中英双语**，用 `/` 分隔：

```java
// ✅ 正确
Messages.showMessageDialog(project, "请等待，另一个任务正在运行/Please wait, another task is running", "Error", Messages.getErrorIcon());

// ❌ 错误 — 只有一种语言
Messages.showMessageDialog(project, "Please wait", "Error", Messages.getErrorIcon());
```

### 线程模型

```java
// AI 请求在新线程中执行
new Thread(() -> {
    GenerateAction.chat(chatContent, project, false, true, "");
}).start();

// UI 更新必须回到 EDT
SwingUtilities.invokeLater(() -> Messages.showMessageDialog(...));
// 或
ApplicationManager.getApplication().invokeLater(() -> { ... });

// 编辑器写操作必须通过 WriteCommandAction
WriteCommandAction.runWriteCommandAction(project, () -> {
    document.insertString(position, text);
});
```

### temperature 参数约定

| 场景 | temperature 值 | 原因 |
|------|---------------|------|
| 代码评分 | 0.1 | 需要确定性高的评估结果 |
| 注释生成 | 0.2 | 需要准确但略有创造性 |
| SQL 评估 | 0.2 | 需要准确的分析 |
| Commit 消息 | 0.2 | 需要规范的格式 |
| 代码生成/优化/聊天 | 1.0（默认） | 需要创造性 |

---

## 7. 关键设计模式与工程实践

### 7.1 SSE 流式通信模式

所有 AI 交互均基于 SSE（Server-Sent Events）：

```java
client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
    .thenAccept(response -> {
        response.body().forEach(line -> {
            if (line.startsWith("data")) {
                line = line.substring(5);  // 去掉 "data:" 前缀
                // 根据供应商解析为 SseResponse 或 BaiduSseResponse
                // 提取 content 字段追加到 StringBuilder
            }
        });
    }).join();
```

> **Agent 提示**：百度/FREE 供应商使用 `BaiduSseResponse.getResult()`，其他供应商使用 `SseResponse.getChoices().get(0).getDelta().getContent()`。新增供应商如果响应格式不同，需要在 SSE 解析逻辑中添加分支。

### 7.2 供应商适配（隐式适配器模式）

虽然没有显式的适配器接口，但通过 `if-else` 分支在多个关键位置实现了供应商差异化处理。

> **Agent 提示**：以下是新增供应商时**必须检查和可能修改**的所有位置清单：

| 文件 | 方法/位置 | 需要做什么 |
|------|-----------|-----------|
| `ProviderNameEnum.java` | 枚举定义 | 添加新枚举值 |
| `ModelUtils.java` | 静态初始化块 | 添加 `modelProviders.add(...)` 和 `modelOptions.add(...)` |
| `ChatUtils.getUrl()` | if-else 分支 | 如果 URL 构建方式特殊（如需要 token 拼接），添加分支 |
| `ChatUtils.getUrlByProvider()` | if-else 分支 | 同上 |
| `ChatUtils.getApiKey()` | if-else 分支 | 添加从 settings 获取 Key 的分支 |
| `MyPluginSettings.State` | 字段定义 | 添加 `public String xxxApiKey;` |
| `MyPluginSettings` | getter/setter | 添加对应的 getter/setter |
| `MyPluginConfigurable` | 构造方法 + isModified + apply + reset | 添加 UI 输入框和持久化逻辑 |
| `ChatContent.setMessages()` | 条件判断 | 如果新供应商有特殊消息格式要求，添加适配逻辑 |
| 所有 Action 中的 system 消息构建 | role 判断 | 如果新供应商不支持 system role，添加判断 |
| `GenerateAction.chat()` | SSE 解析分支 | 如果响应格式不同于 OpenAI 标准，添加解析分支 |
| `ChatUtils.pureChat()` | SSE 解析分支 | 同上 |

### 7.3 策略模式 — 文件分析框架

`FileAnalysisManager` 通过优先级调度选择最佳的 `FileAnalysisService` 实现：

```java
// 选择优先级最高的能处理该文件的服务
private FileAnalysisService findBestService(VirtualFile file) {
    return services.stream()
            .filter(service -> service.canHandle(file))
            .max(Comparator.comparingInt(FileAnalysisService::getPriority))
            .orElse(null);
}
```

### 7.4 Project UserData 状态管理

> **Agent 提示**：不要使用静态变量存储项目相关状态，必须使用 UserData 机制。

```java
// ✅ 正确 — 使用 UserData
project.putUserData(Gpt4lllChatKey.GPT_4_LLL_RUNNING_STATUS, true);
Boolean running = project.getUserData(Gpt4lllChatKey.GPT_4_LLL_RUNNING_STATUS);

// ❌ 错误 — 使用静态变量（多项目时会冲突）
private static boolean isRunning = false;
```

当前已定义的 UserData Key 及其用途：

| Key | 类型 | 用途 | 定义位置 |
|-----|------|------|----------|
| `GPT_4_LLL_RUNNING_STATUS` | `Key<Boolean>` | 任务运行状态互斥锁 | `Gpt4lllChatKey` |
| `GPT_4_LLL_NOW_TOPIC` | `Key<String>` | 当前会话主题（用于历史记录索引） | `Gpt4lllChatKey` |
| `GPT_4_LLL_CONVERSATION_HISTORY` | `Key<List<Message>>` | 当前会话的消息历史 | `Gpt4lllChatKey` |
| `GPT_4_LLL_PROVIDER_COMBO_BOX` | `Key<ComboBox<String>>` | 供应商下拉框引用 | `Gpt4lllComboxKey` |
| `GPT_4_LLL_MODEL_COMBO_BOX` | `Key<ComboBox<SelectModelOption>>` | 模型下拉框引用 | `Gpt4lllComboxKey` |
| `GPT_4_LLL_TEXT_AREA` | `Key<Gpt4lllTextArea>` | ToolWindow 展示区域引用 | `Gpt4lllTextAreaKey` |
| `GPT_4_LLL_HISTORY_BUTTON` | `Key<JButton>` | 历史记录按钮引用 | `Gpt4lllHistoryButtonKey` |

### 7.5 编辑器实时写入状态机

`GenerateAction.chat()` 在 `coding=true` 模式下的代码写入逻辑是一个状态机：

```
初始状态 → 检测到第一个 ``` → 进入 isWriting=true 状态 → 逐块写入编辑器
→ 检测到第二个 ``` → 退出 isWriting=false 状态 → 停止写入
```

关键状态变量：`isWriting`、`lastInsertPosition`、`preEndString`

> **Agent 提示**：这段逻辑非常脆弱，不建议修改。如果需要改变代码写入行为，建议在 `chat()` 方法外部处理返回值。



---

## 8. 隐含约定与重要注意事项

> **Agent 提示**：本节列出的约定大多**没有在代码注释中明确说明**，但违反它们会导致功能异常或运行时错误。执行任何修改任务前，请逐条检查是否涉及以下约定。

### 8.1 百度供应商的特殊消息格式约束

百度文心一言 API 要求消息列表中 `user` 和 `assistant` 角色**严格交替**，且第一条消息必须是 `user` 角色。这一约束通过 `ChatContent.adaptBaiduMessages()` 自动处理。

**关键规则**：
- `ChatContent.setMessages(messages, providerName)` **不是简单的 setter**。当 `providerName` 为 `BAIDU` 或 `FREE` 时，它会自动调用 `adaptBaiduMessages()`
- `adaptBaiduMessages()` 会将第一条消息的 role 强制改为 `"user"`（即使原本是 `"system"`）
- 如果奇数索引位置出现 `"user"` 角色，会自动插入一条 assistant 占位消息（`"好的。还有更多内容需要提供么？"`）

**Agent 操作约束**：
- ⚠️ 构建消息列表时，**不要手动处理百度的消息交替逻辑**，交给 `setMessages()` 自动处理
- ⚠️ 如果你直接操作 `this.messages` 字段而绕过 `setMessages()`，百度供应商将收到格式错误的请求
- ⚠️ 不要在 `adaptBaiduMessages()` 之后再修改消息列表的顺序或角色

### 8.2 FREE 供应商复用百度基础设施

代码中有明确的 TODO 注释：`//todo 当前只有百度是免费的 所以先将免费的都写成百度的`

**实际含义**：
- `FREE` 供应商在 URL 路由、API Key 获取、SSE 响应解析、消息格式适配等**所有环节**都走百度的代码路径
- `FREE` 使用硬编码模型 `ernie-speed-128k`，不受用户模型选择影响
- `FREE` 的 access_token 通过 `AuthUtils.getFreeBaiduAccessToken()` 从 `blog.wmsay.com` 远程获取（而非用户自己的百度 Key）

**Agent 操作约束**：
- ⚠️ 修改百度相关逻辑时，必须同时考虑对 `FREE` 供应商的影响
- ⚠️ 所有 `if (BAIDU)` 的判断条件，几乎都需要同时包含 `|| FREE`，代码中已有此模式，新增代码必须遵循
- ⚠️ 判断模式为：`ProviderNameEnum.BAIDU.getProviderName().equals(provider) || ProviderNameEnum.FREE.getProviderName().equals(provider)`

### 8.3 单任务互斥约束

每个 Project 同一时刻只能运行一个 AI 任务。通过 `CommonUtil` 的三个方法管理：

```java
CommonUtil.isRunningStatus(project)   // 检查是否有任务在运行
CommonUtil.startRunningStatus(project) // 标记任务开始
CommonUtil.stopRunningStatus(project)  // 标记任务结束
```

**所有 Action 必须遵循的模式**：

```java
// ✅ 正确的模式
@Override
public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    // 1. 检查互斥
    if (CommonUtil.isRunningStatus(project)) {
        Messages.showMessageDialog(project, "请等待，另一个任务正在运行/Please wait, another task is running", "Error", Messages.getErrorIcon());
        return;
    }
    // 2. 标记开始
    CommonUtil.startRunningStatus(project);
    
    // 3. 在 finally 中标记结束（或在线程结束时）
    new Thread(() -> {
        try {
            // ... 执行 AI 任务
        } finally {
            CommonUtil.stopRunningStatus(project);
        }
    }).start();
}
```

**Agent 操作约束**：
- ⚠️ `startRunningStatus` 必须在主线程（EDT）中调用，在启动新线程之前
- ⚠️ `stopRunningStatus` 必须在 `finally` 块中调用，确保异常情况下也能释放锁
- ⚠️ `GenerateAction.chat()` 内部也会调用 `stopRunningStatus`，所以如果你的 Action 在 `chat()` 之后还有逻辑，需要注意状态已被释放
- ⚠️ 忘记调用 `stopRunningStatus` 会导致该 Project 的所有 AI 功能永久卡死，只能重启 IDE

### 8.4 会话保存时序约定

在开始新任务前，必须**先保存当前会话，再清空历史**。这个顺序不能颠倒：

```java
// ✅ 正确顺序
List<Message> chatHistory = ChatUtils.getProjectChatHistory(project);
String nowTopic = ChatUtils.getProjectTopic(project);
if (chatHistory != null && !chatHistory.isEmpty() && nowTopic != null && !nowTopic.isEmpty()) {
    JsonStorage.saveConservation(nowTopic, chatHistory);  // 先保存
    chatHistory.clear();                                    // 再清空
}
```

**Agent 操作约束**：
- ⚠️ 如果先清空再保存，会丢失用户的聊天历史
- ⚠️ 必须同时检查 `chatHistory` 非空且 `nowTopic` 非空，否则会保存空数据或覆盖有效数据
- ⚠️ `JsonStorage.saveConservation` 的方法名拼写是 `Conservation`（不是 `Conversation`），这是项目中的历史拼写，不要"修正"它

### 8.5 system 角色的供应商差异

百度 API 不支持 `system` 角色。项目中通过以下模式处理：

```java
Message systemMessage = new Message();
if (ProviderNameEnum.BAIDU.getProviderName().equals(ModelUtils.getSelectedProvider(project))) {
    systemMessage.setRole("user");      // 百度：用 user 代替 system
} else {
    systemMessage.setRole("system");    // 其他供应商：正常使用 system
}
```

**Agent 操作约束**：
- ⚠️ 每个新 Action 中构建 system 消息时，都必须包含这个 `if-else` 判断
- ⚠️ 注意这里只判断了 `BAIDU`，没有判断 `FREE`。这是因为 `adaptBaiduMessages()` 会将第一条消息的 role 强制改为 `"user"`，所以 `FREE` 供应商的 system 消息会被自动处理
- ⚠️ 如果新增的供应商也不支持 system role，需要在此 `if` 条件中添加

### 8.6 模型和供应商的硬编码注册

所有供应商和模型信息在 `ModelUtils.java` 的静态初始化块中**硬编码注册**，没有配置文件或数据库。

**Agent 操作约束**：
- ⚠️ 添加新模型只需修改 `ModelUtils.java` 的静态初始化块，不需要修改其他文件
- ⚠️ 添加新供应商需要修改多个文件（参见 7.2 节的完整清单）
- ⚠️ 模型的 `modelName` 字段是发送给 API 的实际值，必须与供应商 API 文档一致
- ⚠️ 模型的 `displayName` 是 UI 下拉框中显示的名称，可以自定义
- ⚠️ 默认供应商是 `ALI`（阿里云），默认模型是 `qwen-turbo`，定义在 `ModelUtils.getAvailableProvider()` 和 `getAvailableModelName()` 中

### 8.7 languageMap 重复定义问题

`languageMap`（语言代码到语言名称的映射）在两个地方**完全重复定义**：
- `GenerateAction.java` — `public static HashMap<String, String> languageMap`
- `CommonUtil.java` — `public static HashMap<String, String> languageMap`

**Agent 操作约束**：
- ⚠️ 这是已知的代码重复问题，但**不要擅自合并**，除非用户明确要求重构
- ⚠️ `GenerateAction` 内部使用自己的 `languageMap`（通过 `getSystemLanguage()` 实例方法）
- ⚠️ 其他 Action（如 `CommentAction`）使用 `CommonUtil.getSystemLanguage()`（通过 `CommonUtil.languageMap`）
- ⚠️ 如果需要修改语言映射，**两处都要改**，否则会导致不同 Action 的语言检测结果不一致

### 8.8 敏感模块列表

以下文件和方法包含复杂的状态管理或脆弱的逻辑，**不应轻易修改**，除非用户明确要求：

| 文件/方法 | 风险说明 |
|-----------|----------|
| `GenerateAction.chat()` 的 `coding=true` 写入逻辑 | 基于 ``` 标记的状态机，逻辑脆弱，修改容易导致代码写入位置错误或丢失 |
| `ChatContent.adaptBaiduMessages()` | 百度消息格式的核心适配，修改会影响所有百度/FREE 供应商的请求 |
| `AuthUtils.getBaiduAccessToken()` / `getFreeBaiduAccessToken()` | Token 获取和缓存逻辑，涉及外部 HTTP 请求和本地文件缓存 |
| `WindowTool.java` 的 UI 初始化 | 复杂的 Swing 布局和 UserData 绑定，修改容易导致 NPE |
| `ChatUtils.needsContinuation()` | 百度续写判断逻辑，修改会影响百度供应商的回复完整性 |
| `GenerateAction.actionPerformed()` 的 PSI 分析分支 | `isSelectedTextAllComments()` 和 `getCommentTODO()` 的判断顺序决定了功能路由 |

### 8.9 Agent 操作时的常见陷阱

1. **不要在 Action 的 `actionPerformed()` 中直接执行耗时操作**：所有 AI 请求必须在新线程中执行，否则会冻结 IDE UI
2. **不要忘记打开 ToolWindow**：大多数 Action 在执行前会检查并打开 `GPT4_lll` ToolWindow，否则 `textArea` 为 null 会导致 NPE
3. **不要使用 `gson` 或 `jackson`**：项目统一使用 `fastjson`（`com.alibaba:fastjson:1.2.83`），引入其他 JSON 库会增加包体积且风格不一致
4. **不要使用 `OkHttp` 等第三方 HTTP 库**：项目使用 Java 11 标准库 `java.net.http.HttpClient`
5. **不要在非 EDT 线程中操作 Swing 组件**：UI 更新必须通过 `SwingUtilities.invokeLater()` 或 `ApplicationManager.getApplication().invokeLater()`
6. **不要在非 WriteAction 中修改 Document**：编辑器文档修改必须通过 `WriteCommandAction.runWriteCommandAction()`
7. **不要"修正" `saveConservation` 的拼写**：这是项目中的历史命名，修改会导致已保存的历史记录无法读取
8. **不要假设 `getUserData()` 返回非 null**：所有 UserData 获取都应做 null 检查
9. **不要在 `chat()` 返回后假设 running status 仍为 true**：`chat()` 内部的 `finally` 块会调用 `stopRunningStatus()`


---

## 9. 新开发者 / 新 Agent 上手建议

### 9.1 快速理解项目的阅读路径

建议按以下顺序阅读代码，每一步都建立在前一步的理解之上：

```
第 1 步：ProviderNameEnum.java → 了解支持哪些供应商
第 2 步：ModelUtils.java → 了解模型注册机制和供应商-模型映射
第 3 步：MyPluginSettings.java → 了解持久化设置的结构
第 4 步：ChatUtils.java → 了解 URL 路由、API Key 路由、HTTP 通信
第 5 步：ChatContent.java + Message.java → 了解请求体结构和百度适配
第 6 步：GenerateAction.chat() → 了解核心 SSE 流式通信（只看 chat 方法，跳过 actionPerformed）
第 7 步：CommentAction.java → 了解一个典型 Action 的完整生命周期（最佳模板）
第 8 步：WindowTool.java → 了解 UI 结构和 UserData 绑定
第 9 步：plugin.xml → 了解所有组件的注册方式
```

### 9.2 新增 Action 的完整代码模板

> **Agent 提示**：以下模板可直接复制使用。基于 `CommentAction` 提炼，包含所有必要的样板代码。

```java
package com.wmsay.gpt4_lll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.wmsay.gpt4_lll.component.Gpt4lllTextArea;
import com.wmsay.gpt4_lll.model.ChatContent;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import com.wmsay.gpt4_lll.utils.CommonUtil;
import com.wmsay.gpt4_lll.utils.ModelUtils;

import java.util.ArrayList;
import java.util.List;

public class MyNewAction extends AnAction {

    // 1. 定义 Prompt（使用 Text Block + ${变量} 占位符）
    public static String MY_PROMPT = """
            请根据以下信息完成任务：
            - 编程语言：${fileType}
            - 回复语言：${replyLanguage}
            
            代码内容：
            ```
            ${selectedText}
            ```
            """;

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        
        // 2. 基础校验
        if (project == null) {
            Messages.showMessageDialog(e.getProject(), "不是一个项目/no project here", "Error", Messages.getErrorIcon());
            return;
        }
        
        // 3. 单任务互斥检查
        if (CommonUtil.isRunningStatus(project)) {
            Messages.showMessageDialog(project, "请等待，另一个任务正在运行/Please wait, another task is running", "Error", Messages.getErrorIcon());
            return;
        }
        CommonUtil.startRunningStatus(project);

        // 4. 保存并清空当前会话
        if (ChatUtils.getProjectChatHistory(project) != null && !ChatUtils.getProjectChatHistory(project).isEmpty()
                && ChatUtils.getProjectTopic(project) != null && !ChatUtils.getProjectTopic(project).isEmpty()) {
            JsonStorage.saveConservation(ChatUtils.getProjectTopic(project), ChatUtils.getProjectChatHistory(project));
            ChatUtils.getProjectChatHistory(project).clear();
        }

        // 5. 确保 ToolWindow 打开
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("GPT4_lll");
        if (toolWindow != null && !toolWindow.isVisible()) {
            toolWindow.show();
        }

        // 6. 获取编辑器和选中文本
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            Messages.showMessageDialog(project, "Editor is not open. Please open the file that you want to do something", "Error", Messages.getErrorIcon());
            CommonUtil.stopRunningStatus(project);
            return;
        }
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            Messages.showMessageDialog(project, "No text selected. Please select the code you want to do something", "Error", Messages.getErrorIcon());
            CommonUtil.stopRunningStatus(project);
            return;
        }

        // 7. 获取上下文信息
        String model = ChatUtils.getModelName(project);
        String replyLanguage = CommonUtil.getSystemLanguage();
        String fileType = CommonUtil.getOpenFileType(project);

        // 8. 设置会话主题
        ChatUtils.setProjectTopic(project, CommonUtil.generateTopicByMethodAndTime(selectedText, "MyNewAction"));

        // 9. 构建 system 消息（注意百度供应商差异）
        Message systemMessage = new Message();
        if (ProviderNameEnum.BAIDU.getProviderName().equals(ModelUtils.getSelectedProvider(project))) {
            systemMessage.setRole("user");
        } else {
            systemMessage.setRole("system");
        }
        systemMessage.setContent("你是一个有用的助手，同时也是一个资深的软件开发工程师");

        // 10. 构建用户消息
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setName("owner");
        String prompt = MY_PROMPT
                .replace("${fileType}", fileType)
                .replace("${replyLanguage}", replyLanguage)
                .replace("${selectedText}", selectedText.trim());
        userMessage.setContent(prompt);

        // 11. 组装 ChatContent
        ChatContent chatContent = new ChatContent();
        List<Message> sendMessageList = new ArrayList<>(List.of(systemMessage, userMessage));
        chatContent.setMessages(sendMessageList, ModelUtils.getSelectedProvider(project));
        chatContent.setModel(model);
        chatContent.setTemperature(0.2);  // 根据场景调整

        // 12. 保存到会话历史
        ChatUtils.getProjectChatHistory(project).addAll(chatContent.getMessages());

        // 13. 清理展示区域
        Gpt4lllTextArea textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
        if (textArea != null) {
            textArea.clearShowWindow();
        }

        // 14. 在新线程中执行 AI 请求
        //     coding=false 表示不写入编辑器，replyShowInWindow=true 表示实时显示在 ToolWindow
        new Thread(() -> {
            try {
                GenerateAction.chat(chatContent, project, false, true, "");
            } finally {
                CommonUtil.stopRunningStatus(project);
            }
        }).start();
    }
}
```

**创建新 Action 后，还需要在 `plugin.xml` 中注册**：

```xml
<!-- 在 <actions> 节点内添加 -->
<action id="MyNewActionId"
        class="com.wmsay.gpt4_lll.MyNewAction"
        text="GPT4lll: My New Feature"
        description="功能描述/Feature description">
    <add-to-group group-id="EditorPopupMenu" anchor="last"/>
    <!-- 可选：添加快捷键 -->
    <!-- <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt N"/> -->
</action>
```

**常用的 `group-id` 值**：
| group-id | 出现位置 | 适用场景 |
|----------|----------|----------|
| `EditorPopupMenu` | 编辑器右键菜单 | 需要选中代码的功能 |
| `ProjectViewPopupMenu` | 项目树右键菜单 | 操作文件/文件夹的功能（如批量评分） |
| `ToolsMenu` | 顶部 Tools 菜单 | 全局工具功能 |
| `ChangesViewPopupMenu` | VCS 变更视图右键菜单 | Git 相关功能（如 commit 消息生成） |

### 9.3 新增供应商的步骤清单

> **Agent 提示**：按以下顺序逐步修改，每步都标注了文件路径。遗漏任何一步都会导致新供应商无法正常工作。

**假设要添加一个名为 "MoonShot" 的供应商**：

**第 1 步：添加枚举值**
文件：`src/main/java/com/wmsay/gpt4_lll/model/enums/ProviderNameEnum.java`
```java
MOON_SHOT("MoonShot"),  // 在枚举列表末尾添加（逗号分隔）
```

**第 2 步：注册供应商和模型**
文件：`src/main/java/com/wmsay/gpt4_lll/utils/ModelUtils.java`
```java
// 在静态初始化块中添加供应商
modelProviders.add(new ModelProvider(
    ProviderNameEnum.MOON_SHOT.getProviderName(),
    "https://api.moonshot.cn/v1/chat/completions",
    "MoonShot AI"
));

// 添加模型
modelOptions.add(new SelectModelOption(
    "moonshot-v1-8k",
    "MoonShot 8K 上下文",
    ProviderNameEnum.MOON_SHOT.getProviderName(),
    "moonshot-v1-8k"
));
```

**第 3 步：添加 URL 路由**
文件：`src/main/java/com/wmsay/gpt4_lll/utils/ChatUtils.java`
- 如果新供应商遵循 OpenAI 标准接口（URL 不需要特殊处理），则 `getUrl()` 和 `getUrlByProvider()` **不需要修改**，会走默认的 `ModelUtils.getUrlByProvider(provider)` 路径
- 如果 URL 需要特殊处理（如拼接 token），需要在 `getUrl()` 和 `getUrlByProvider()` 中添加 `if` 分支

**第 4 步：添加 API Key 路由**
文件：`src/main/java/com/wmsay/gpt4_lll/utils/ChatUtils.java`
```java
// 在 getApiKey() 方法中添加
if (ProviderNameEnum.MOON_SHOT.getProviderName().equals(provider)) {
    return settings.getMoonShotApiKey();
}
```

**第 5 步：添加设置字段**
文件：`src/main/java/com/wmsay/gpt4_lll/MyPluginSettings.java`
```java
// 在 State 内部类中添加
public String moonShotApiKey;

// 在外部类中添加 getter/setter
public String getMoonShotApiKey() { return state.moonShotApiKey; }
public void setMoonShotApiKey(String key) { state.moonShotApiKey = key; }
```

**第 6 步：添加设置 UI**
文件：`src/main/java/com/wmsay/gpt4_lll/MyPluginConfigurable.java`
```java
// 1. 添加字段声明
private JTextField moonShotApiKeyField;

// 2. 在构造方法中添加 UI 组件（参考已有供应商的模式）
addSeparator(panel, c, gridy++);
addTitleLabel(panel, c, gridy++, "MoonShot配置/MoonShot Configuration");
addLabelAndField(panel, c, gridy++, "MoonShot Api Key:", moonShotApiKeyField = new JTextField(20));

// 3. 在 isModified() 中添加比较
|| !moonShotApiKeyField.getText().equals(settings.getMoonShotApiKey())

// 4. 在 apply() 中添加保存
settings.setMoonShotApiKey(moonShotApiKeyField.getText());

// 5. 在 reset() 中添加加载
moonShotApiKeyField.setText(settings.getMoonShotApiKey());
```

**第 7 步：检查 SSE 响应格式**
- 如果新供应商的 SSE 响应格式与 OpenAI 标准一致（`choices[0].delta.content`），则 `GenerateAction.chat()` 和 `ChatUtils.pureChat()` **不需要修改**
- 如果响应格式不同（如百度使用 `result` 字段），需要：
  1. 创建新的响应模型类（参考 `BaiduSseResponse`）
  2. 在 `GenerateAction.chat()` 的 SSE 解析分支中添加新的 `if` 条件
  3. 在 `ChatUtils.pureChat()` 的 SSE 解析分支中添加同样的条件

**第 8 步：检查消息格式**
- 如果新供应商不支持 `system` 角色，需要在所有 Action 的 system 消息构建处添加判断
- 如果新供应商有特殊的消息格式要求（如百度的交替规则），需要在 `ChatContent.setMessages()` 中添加适配逻辑

### 9.4 新增语言分析服务的步骤

> **Agent 提示**：以添加 Kotlin 分析服务为例。

**第 1 步：创建服务实现类**
文件：`src/main/java/com/wmsay/gpt4_lll/languages/extensionPoints/KotlinFileAnalysisService.java`
```java
public class KotlinFileAnalysisService implements FileAnalysisService {
    @Override
    public boolean canHandle(VirtualFile file) {
        return "kt".equals(file.getExtension()) || "kts".equals(file.getExtension());
    }
    
    @Override
    public int getPriority() {
        return 10;  // 与 Java 同优先级，高于 Generic（0）
    }
    
    @Override
    public List<Message> analyzeInfoToMessage(Project project, Editor editor) {
        // 使用 Kotlin PSI API 提取类信息
        // ...
    }
}
```

**第 2 步：创建可选依赖配置**
文件：`src/main/resources/META-INF/kotlin-support.xml`
```xml
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <applicationService
            serviceInterface="com.wmsay.gpt4_lll.languages.extensionPoints.KotlinFileAnalysisService"
            serviceImplementation="com.wmsay.gpt4_lll.languages.extensionPoints.KotlinFileAnalysisService"/>
    </extensions>
</idea-plugin>
```

**第 3 步：在 plugin.xml 中注册可选依赖**
```xml
<depends optional="true" config-file="kotlin-support.xml">org.jetbrains.kotlin</depends>
```

**第 4 步：在 FileAnalysisManager 中加载**
文件：`src/main/java/com/wmsay/gpt4_lll/languages/FileAnalysisManager.java`
```java
// 在 createServices() 方法中添加
tryLoadServiceFromContainer(services, KotlinFileAnalysisService.class);
```

### 9.5 常见任务操作指南

#### 修改 Prompt

1. 找到对应 Action 文件中的 `public static String XXX_PROMPT` 常量
2. 修改 Text Block 内容，保持 `${变量}` 占位符格式
3. 确保 `actionPerformed()` 中的 `.replace()` 调用覆盖了所有占位符
4. 不要修改 Prompt 常量的变量名（其他地方可能引用）

#### 添加新模型（已有供应商）

只需修改 `ModelUtils.java` 的静态初始化块：
```java
modelOptions.add(new SelectModelOption(
    "new-model-id",                              // API 请求中的 model 值
    "模型描述",                                    // 描述
    ProviderNameEnum.XXX.getProviderName(),       // 所属供应商
    "UI 显示名称"                                  // 下拉框显示
));
```

#### 修改 UI 布局

- ToolWindow 右侧面板：修改 `WindowTool.java`
- Settings 页面：修改 `MyPluginConfigurable.java`
- 对话框：修改对应的 `component/` 下的类

#### 修改 SSE 响应解析

需要同时修改两处：
1. `GenerateAction.chat()` — 流式 UI 场景
2. `ChatUtils.pureChat()` — 同步调用场景

### 9.6 Agent 执行任务时的检查清单

> **Agent 提示**：每次完成代码修改后，按此清单逐项检查。

- [ ] **编译检查**：代码是否能通过编译（import 是否完整、类型是否匹配）
- [ ] **互斥模式**：新 Action 是否遵循 `isRunningStatus → startRunningStatus → try/finally stopRunningStatus` 模式
- [ ] **线程模型**：AI 请求是否在新线程中执行、UI 更新是否在 EDT 中执行、Document 修改是否在 WriteAction 中
- [ ] **百度兼容**：涉及供应商判断时，是否同时考虑了 `BAIDU` 和 `FREE`
- [ ] **system 角色**：构建 system 消息时，是否处理了百度不支持 system role 的情况
- [ ] **双语提示**：用户可见的错误提示是否为中英双语（用 `/` 分隔）
- [ ] **会话管理**：是否在任务开始前保存并清空了当前会话
- [ ] **ToolWindow**：是否确保 ToolWindow 已打开（避免 textArea 为 null）
- [ ] **plugin.xml**：新 Action 是否已在 `plugin.xml` 的 `<actions>` 节点中注册
- [ ] **null 安全**：`getUserData()` 返回值是否做了 null 检查
- [ ] **JSON 库**：是否使用了 `fastjson`（而非 gson/jackson）
- [ ] **HTTP 库**：是否使用了 `java.net.http.HttpClient`（而非 OkHttp 等）
- [ ] **命名规范**：类名、方法名、常量名是否遵循第 6 节的命名约定
- [ ] **temperature**：是否根据场景设置了合适的 temperature 值（参见 6 节的约定表）
