# Project Overview

## 1. 项目目标与核心定位

GPT4_lll 是一款 JetBrains IntelliJ IDEA 插件（Plugin ID: `com.wmsay.GPT4_lll`，当前版本 3.9.1），其核心定位是**将多家 AI/LLM 平台的能力无缝集成到 IDE 开发工作流中**，为开发者提供代码生成、代码优化、代码评估、注释生成、注释翻译、SQL 优化、单元测试生成、Git 提交信息生成、工作报告生成、Linter 修复建议等一站式 AI 辅助开发能力。

项目起源于 2023 年 OpenAI GPT 时代，最初仅支持 OpenAI 接口。随着国内 API 封号问题（代码注释中有明确记录：`change-notes` 中 3.2.0 版本说明），项目逐步拥抱国内大模型生态，目前已支持 7 个 AI 供应商平台。项目面向中国开发者群体为主，同时兼顾国际用户（UI 和提示信息均为中英双语）。

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

4. **单任务互斥**：通过 `GPT_4_LLL_RUNNING_STATUS` Key 实现每个 Project 同一时刻只能运行一个 AI 任务（代码证据：`CommonUtil.isRunningStatus()` / `startRunningStatus()` / `stopRunningStatus()`）。

---

## 3. 目录结构说明

```
src/main/java/com/wmsay/gpt4_lll/
├── *Action.java              # 功能入口，每个 Action 对应一个用户可触发的功能
├── WindowTool.java           # ToolWindowFactory，创建右侧面板 UI（供应商/模型选择、聊天区、输入框）
├── MyPluginSettings.java     # 应用级持久化设置（API Key、代理地址等）
├── ProjectSettings.java      # 项目级持久化设置（上次选择的供应商和模型）
├── MyPluginConfigurable.java # Settings 页面 UI（Other Settings > GPT4 lll Settings）
├── JsonStorage.java          # 聊天历史的 JSON 文件持久化
├── PluginInfo.java           # 插件信息
├── component/                # UI 组件
│   ├── Gpt4lllTextArea.java          # 核心展示区域（支持 HTML/Markdown 渲染）
│   ├── Gpt4lllPlaceholderTextArea.java # 带 placeholder 的输入框
│   ├── LinterFixDialog.java          # Linter 修复确认对话框
│   ├── VCSAuthorSelectionDialog.java  # 工作报告的作者/日期选择对话框
│   ├── ChatHistoryManager.java       # 聊天历史管理
│   ├── CommitResultViewer.java       # 提交结果查看器
│   └── GitCommitFetcher.java         # Git 提交记录获取
├── model/                    # 数据模型（POJO）
│   ├── ChatContent.java      # 请求体，包含百度消息格式适配逻辑
│   ├── Message.java          # 消息模型（role/content/name）
│   ├── SseResponse.java      # OpenAI 标准 SSE 响应模型
│   ├── SelectModelOption.java # 模型选项（modelName/displayName/provider）
│   ├── ModelProvider.java    # 供应商信息（name/url/description）
│   ├── CodeChange.java       # Linter 修复的变更操作模型
│   ├── CommentTranslation.java # 注释翻译模型
│   ├── enums/ProviderNameEnum.java # 供应商枚举
│   ├── key/                  # IntelliJ UserData Key 定义
│   ├── baidu/                # 百度特有响应模型
│   └── server/               # 服务端认证相关模型
├── utils/                    # 工具类
│   ├── ChatUtils.java        # HTTP 客户端构建、API 路由、同步聊天方法
│   ├── ModelUtils.java       # 模型/供应商注册表、选择逻辑
│   ├── CommonUtil.java       # 通用工具（语言检测、文件类型、运行状态管理）
│   ├── AuthUtils.java        # 百度 OAuth Token 获取与缓存
│   ├── CodeUtils.java        # 代码评估标准 Prompt 定义
│   ├── SelectionUtils.java   # 编辑器选区工具
│   └── ...
├── languages/                # 可扩展文件分析框架
│   ├── FileAnalysisService.java      # 分析服务接口
│   ├── FileAnalysisManager.java      # 服务管理器（优先级调度）
│   └── extensionPoints/
│       ├── JavaFileAnalysisService.java    # Java PSI 分析（优先级 10）
│       └── GenericFileAnalysisService.java # 通用分析（优先级 0，兜底）
├── service/                  # 业务服务
│   ├── CommentTranslationService.java     # 注释翻译服务（提取、翻译、解析）
│   └── TranslationProgressCallback.java   # 翻译进度回调接口
└── view/                     # 编辑器渲染层
    ├── CommentTranslationRenderer.java    # 基于 FoldRegion 的翻译渲染
    └── ...TranslationRenderer.java        # 其他渲染器
```

---

## 4. 技术栈与运行方式

| 维度 | 详情 |
|------|------|
| 编程语言 | Java 17（主要），Kotlin JVM 1.8.21（构建脚本） |
| 构建工具 | Gradle 8.13 + IntelliJ Platform Plugin 2.5.0 |
| 目标 IDE | IntelliJ IDEA Community 2025.1，兼容范围 222 ~ 253.* |
| 核心依赖 | fastjson 1.2.83（JSON 序列化）、flexmark 0.64.8（Markdown→HTML）、rhino 1.7.15（JavaScript 引擎）、jsoup 1.17.2（HTML 解析） |
| 捆绑插件依赖 | `com.intellij.java`（可选，用于 Java PSI 分析）、`Git4Idea`（必需，用于 Git 操作） |
| HTTP 通信 | Java 11 标准库 `java.net.http.HttpClient`，SSE 流式处理 |
| 持久化 | IntelliJ `PersistentStateComponent`（设置）+ 自定义 JSON 文件（聊天历史） |
| 入口点 | `WindowTool`（ToolWindowFactory）创建 UI；各 Action 通过 `plugin.xml` 注册到右键菜单和快捷键 |

### 构建与运行

```bash
# 构建插件
./gradlew buildPlugin

# 在沙箱 IDE 中运行
./gradlew runIde

# 发布
./gradlew publishPlugin  # 需要环境变量 PUBLISH_TOKEN
```

---

## 5. 核心模块解析

### 5.1 GenerateAction — 核心聊天引擎

`GenerateAction` 承担双重职责：

**作为 Action**：处理代码生成/优化/TODO 补全。通过 PSI 分析判断选中内容的类型：
- 全部是注释 → 按注释描述生成代码
- 包含 TODO → 按 TODO 描述补全实现
- 其他 → 代码重构优化

**作为静态方法提供者**：`GenerateAction.chat()` 是全局共享的 SSE 流式通信方法，被 `CommentAction`、`ScoreAction`、`GenerateVersionControlCommitMessage`、`LinterFixAction` 等几乎所有 Action 调用。

关键行为：
- `coding=true` 时，实时将 AI 返回的代码块内容写入编辑器（通过检测 ``` 标记的开闭来提取代码）
- 支持推理模型的 `reasoningContent` 展示（思考过程）
- 百度供应商自动续写：当回复未以终止符结尾且重试次数 < 2 时，自动追加 "请继续完成" 消息
- 每次请求前保存当前会话，请求后保存响应到历史

### 5.2 ChatUtils — API 路由与通信

核心职责：
- `getUrl()` / `getUrlByProvider()`：根据供应商路由到正确的 API 端点。百度和免费供应商需要拼接 `access_token`，PERSONAL 供应商使用用户自定义 URL
- `getApiKey()`：根据供应商从 `MyPluginSettings` 获取对应的 API Key
- `buildHttpClient()`：构建 HTTP 客户端，支持代理配置
- `buildHttpRequest()`：构建标准 SSE 请求（Bearer Token 认证）
- `pureChat()`：同步版聊天方法，用于批量评分和注释翻译等不需要流式 UI 的场景
- `needsContinuation()`：判断回复是否需要续写（不以 `.` `。` `?` `}` `;` ``` 等结尾）

### 5.3 ModelUtils — 模型注册表

所有供应商和模型信息在静态初始化块中硬编码注册。核心数据结构：
- `modelProviders`：供应商列表（名称 → URL 映射）
- `modelOptions`：模型选项列表（模型标识符、描述、所属供应商、显示名称）
- `provider2ModelList`：供应商 → 模型列表的分组映射
- `getAvailableProvider()` / `getAvailableModelName()`：三级优先级获取（当前选中 > 上次保存 > 系统默认）

### 5.4 FileAnalysisManager — 可扩展文件分析框架

采用**策略模式 + 优先级调度**：
- `FileAnalysisService` 接口定义 `canHandle()`、`getPriority()`、`analyzeInfoToMessage()` 等方法
- `JavaFileAnalysisService`（优先级 10）：通过 Java PSI API 提取类信息、字段、方法签名，为 AI 提供丰富的代码上下文
- `GenericFileAnalysisService`（优先级 0）：通用兜底实现
- 通过 `java-support.xml` 可选依赖注册 Java 服务，`FileAnalysisManager` 使用反射 + IntelliJ 服务容器动态加载，实现了对 Java 插件的优雅降级

### 5.5 CommentTranslationService — 注释翻译服务

职责：从 PSI 文件中提取注释 → 构建翻译 Prompt → 调用 AI 翻译 → 解析结果。

关键设计：
- 注释提取：基于 PSI 树遍历，支持单行注释（`//`）合并为段落、块注释（`/* */`、`/** */`）按段落拆分
- 跳过 package 声明之前的版权头注释
- 支持批量翻译（每批 10 个）和逐个流式翻译两种模式
- 翻译结果包含辅助解释（`【解释：...】` 标记）

### 5.6 CommentTranslationRenderer — 编辑器翻译渲染

使用 IntelliJ 编辑器的 `FoldRegion` 机制实现翻译显示：
- 将原始注释区域折叠，以 `[GPT4LLL] 翻译内容` 作为折叠占位文本
- 支持翻译模式切换（显示/隐藏）
- 通过扫描 `FOLD_PLACEHOLDER_PREFIX` 前缀检测持久化的翻译折叠

### 5.7 LinterFixAction — AI Linter 修复

工作流程：
1. 通过 `DaemonCodeAnalyzerEx.processHighlights()` 获取选中区域的 ERROR/WARNING/WEAK_WARNING
2. 构建包含行号的代码和错误描述的 Prompt
3. AI 返回 JSON 数组格式的变更操作（DELETE/INSERT/MODIFY）
4. 通过 `LinterFixDialog` 展示修复建议，用户确认后应用

### 5.8 GenerateVersionControlCommitMessage — Git 提交信息生成

- 从 VCS 数据源获取 staged/unstaged 变更（支持多种数据获取路径作为 fallback）
- 通过 `git diff` 命令获取文件级别的 diff 内容
- 生成符合 Conventional Commits 规范的提交信息
- 支持实时更新 commit 文档（通过 `commitDoc` 参数传递给 `GenerateAction.chat()`）

### 5.9 ScoreFilesAction2 — 批量文件评分

- 从项目树选中文件/文件夹，递归收集所有文件
- 使用 `ExecutorService` 线程池并行处理（最大 5 个并发）
- 评分结果输出为 Markdown 文件到 `score_gpt4lll` 子目录
- 使用 `ChatUtils.pureChat()` 同步调用，不依赖 ToolWindow UI

---

## 6. 编码风格与命名约定

### 命名规范

| 类别 | 规范 | 代码证据 |
|------|------|----------|
| Action 类 | `*Action` 后缀 | `GenerateAction`、`CommentAction`、`ScoreAction`、`LinterFixAction` |
| 工具类 | `*Utils` 后缀，静态方法为主 | `ChatUtils`、`ModelUtils`、`CommonUtil`（注意：此处不一致，`CommonUtil` 未用 `Utils` 后缀） |
| UserData Key | `Gpt4lll*Key` 模式，内部常量以 `GPT_4_LLL_` 前缀 | `Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY` |
| 模型类 | 纯 POJO，getter/setter 风格 | `ChatContent`、`Message`、`SseResponse` |
| 百度特有模型 | `Baidu*` 前缀 | `BaiduSseResponse` |
| 服务类 | `*Service` 后缀 | `CommentTranslationService`、`FileAnalysisService` |
| 渲染器 | `*Renderer` 后缀 | `CommentTranslationRenderer` |
| 设置类 | `*Settings` 后缀 | `MyPluginSettings`、`ProjectSettings` |
| Prompt 常量 | 大写下划线，定义为类的 `static final String` | `LINTER_FIX_PROMPT`、`TODO_PROMPT`、`PROMPT`、`TRANSLATION_PROMPT` |

### 方法命名风格

- 获取类：`get*`（`getApiKey`、`getModelName`、`getSelectedProvider`、`getAvailableProvider`）
- 判断类：`is*` / `needs*`（`isRunningStatus`、`needsContinuation`、`isValidComment`）
- 构建类：`build*`（`buildHttpClient`、`buildHttpRequest`）
- 处理类：`process*`（`processFiles`、`processFileContent`、`processHighlights`）
- 解析类：`parse*` / `extract*`（`parseChangesFromResponse`、`extractJsonArray`、`extractComments`）

### 代码组织风格

- **方法长度偏长**：`GenerateAction.chat()` 约 200 行，`actionPerformed()` 方法普遍 80-150 行。Action 类倾向于在单个方法中完成完整流程。
- **注释风格**：中英双语注释贯穿全项目。Javadoc 注释较少，更多使用行内注释说明关键逻辑。
- **异常处理**：倾向于 catch-and-show-dialog 模式，通过 `SwingUtilities.invokeLater()` 在 EDT 中弹出错误提示。部分异常被静默忽略（如 SSE 解析中的 `catch (Exception e) {}`）。
- **线程模型**：AI 请求在 `new Thread()` 中执行，UI 更新通过 `SwingUtilities.invokeLater()` 或 `ApplicationManager.getApplication().invokeLater()` 回到 EDT。
- **Prompt 定义**：使用 Java Text Block（`"""`）定义多行 Prompt，通过 `String.replace()` 进行变量替换。

---

## 7. 关键设计模式与工程实践

### 7.1 SSE 流式通信模式

所有 AI 交互均基于 SSE（Server-Sent Events）。`HttpClient.sendAsync()` + `HttpResponse.BodyHandlers.ofLines()` 逐行处理响应流。每行以 `data:` 开头，解析为 `SseResponse`（标准接口）或 `BaiduSseResponse`（百度接口）。

### 7.2 供应商适配器模式（隐式）

虽然没有显式的适配器接口，但通过 `if-else` 分支在多个关键位置实现了供应商差异化处理：
- `ChatContent.adaptBaiduMessages()`：百度要求 user/assistant 严格交替，第一条必须是 user
- `ChatUtils.getUrl()`：百度和免费供应商使用 access_token 拼接 URL
- `ChatUtils.getApiKey()`：每个供应商从不同的设置字段获取 Key
- 百度供应商的 `system` 角色消息被转换为 `user` 角色

### 7.3 策略模式 — 文件分析框架

`FileAnalysisManager` 通过优先级调度选择最佳的 `FileAnalysisService` 实现。Java 服务通过可选依赖 XML（`java-support.xml`）注册，运行时通过反射和 IntelliJ 服务容器加载，实现了对 Java 插件的优雅降级。

### 7.4 Project UserData 状态管理

利用 IntelliJ 的 `Key<T>` + `Project.putUserData()` 机制替代传统的单例或静态变量，实现了：
- 多项目实例隔离（每个 Project 有独立的聊天历史、UI 组件引用、运行状态）
- 无需自行管理生命周期（随 Project 关闭自动释放）

### 7.5 编辑器实时写入

`GenerateAction.chat()` 在 `coding=true` 模式下，通过检测 Markdown 代码块的 ``` 开闭标记，实时将 AI 返回的代码内容通过 `WriteCommandAction.runWriteCommandAction()` 写入编辑器。这是一个复杂的状态机逻辑（`isWriting`、`lastInsertPosition`、`preEndString` 等状态变量）。

### 7.6 多轮续写机制

百度供应商特有：当 AI 回复未以终止符结尾（`.` `。` `?` `}` `;` ``` 等）且重试次数 < 2 时，自动追加 "请按照上面的要求，继续完成" 消息并递归调用 `chat()`。

---

## 8. 隐含约定与重要注意事项

### 8.1 百度供应商的特殊消息格式（关键）

百度文心一言 API 要求消息列表中 user 和 assistant 角色严格交替出现，且第一条消息必须是 user 角色。`ChatContent.adaptBaiduMessages()` 方法在设置消息时自动处理此约束。**任何修改消息构建逻辑的代码都必须考虑此约束**。

### 8.2 FREE 供应商复用百度基础设施

代码中多处 `if (BAIDU || FREE)` 的条件判断表明，免费供应商实际上使用百度的 `ernie-speed-128k` 模型和百度的 access_token 机制。代码注释明确标注：`//todo 当前只有百度是免费的 所以先将免费的都写成百度的`。

### 8.3 单任务互斥约束

每个 Project 同一时刻只能运行一个 AI 任务。所有 Action 在 `actionPerformed()` 开头检查 `isRunningStatus()`，结束时调用 `stopRunningStatus()`。**新增 Action 必须遵循此模式**，否则会导致任务冲突。

### 8.4 会话保存时序

所有 Action 在启动新任务前，先保存当前会话历史到 `JsonStorage`，然后清空历史。这是一个隐含的"先保存后清空"约定。

### 8.5 system 角色的供应商差异

百度供应商不支持 `system` 角色，所有 Action 在构建 system 消息时都有如下判断：
```java
if (ProviderNameEnum.BAIDU.getProviderName().equals(provider)) {
    systemMessage.setRole("user");
} else {
    systemMessage.setRole("system");
}
```
**新增 Action 必须包含此判断**。

### 8.6 模型和供应商的硬编码注册

所有模型和供应商信息在 `ModelUtils` 的静态初始化块中硬编码。添加新平台或新模型需要修改此文件。README 中有详细的添加指南。

### 8.7 languageMap 重复定义

`GenerateAction` 和 `CommonUtil` 中各自维护了一份完整的 `languageMap`（ISO 639-1 语言代码映射）。这是代码冗余，但两处均在使用中。修改时需同步更新，或考虑统一到 `CommonUtil`。

### 8.8 敏感模块（不应轻易修改）

- `GenerateAction.chat()`：核心聊天方法，几乎所有功能依赖它。修改需全面回归测试。
- `ChatContent.adaptBaiduMessages()`：百度消息格式适配，逻辑微妙。
- `ChatUtils.getUrl()` / `getApiKey()`：供应商路由核心，新增供应商必须在此处添加分支。
- `ModelUtils` 静态初始化块：模型注册表，是 UI 下拉框数据源。

---

## 9. 新开发者 / 新 Agent 上手建议

### 快速理解项目的路径

1. 从 `plugin.xml` 开始，了解所有注册的 Action、ToolWindow、Service
2. 阅读 `ModelUtils.java` 了解支持的供应商和模型
3. 阅读 `GenerateAction.chat()` 理解核心通信流程
4. 阅读 `ChatUtils.java` 理解 API 路由和供应商差异
5. 选择一个简单的 Action（如 `CommentAction`）作为模板，理解 Action 的标准流程

### 新增功能的标准模板

新增一个 AI 功能 Action 需要：

1. 创建 `XxxAction extends AnAction`
2. `actionPerformed()` 中遵循标准流程：
   - 检查 `isRunningStatus()` → `startRunningStatus()`
   - 保存并清空当前会话
   - 打开 ToolWindow
   - 构建 system 消息（注意百度的 role 差异）
   - 构建 user 消息（Prompt）
   - 如果是 Java 文件，通过 `FileAnalysisManager` 获取上下文
   - 构建 `ChatContent`，调用 `GenerateAction.chat()` 或 `ChatUtils.pureChat()`
   - 在 `finally` 中调用 `stopRunningStatus()`
3. 在 `plugin.xml` 中注册 Action

### 新增 AI 供应商的步骤

README 中有详细指南，核心步骤：
1. `ProviderNameEnum` 添加枚举值
2. `ModelUtils` 静态块添加供应商和模型
3. `ChatUtils.getApiKey()` 添加 Key 获取分支
4. `MyPluginSettings.State` 添加 API Key 字段
5. `MyPluginConfigurable` 添加配置 UI
6. 如果新供应商的消息格式有特殊要求，需要在 `ChatContent` 中添加适配逻辑

### 注意事项

- 所有 UI 操作必须在 EDT（Event Dispatch Thread）中执行
- 编辑器写操作必须通过 `WriteCommandAction.runWriteCommandAction()` 执行
- 百度供应商的消息格式约束是最容易踩的坑
- `temperature` 参数：评分类 Action 使用 0.1（确定性高），注释类使用 0.2，生成类使用默认 1.0
