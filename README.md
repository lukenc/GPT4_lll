# GPT4_lll Plugin for IntelliJ IDEA

![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/21935?logo=jetbrains&link=https%3A%2F%2Fplugins.jetbrains.com%2Fembeddable%2Finstall%2F21935)
![JetBrains Plugin Rating](https://img.shields.io/jetbrains/plugin/r/rating/21935?logo=intellijidea)

## Introduction

GPT4_lll是为IntelliJ IDEA用户设计的插件，提供实时代码查询、分析和AI辅助开发服务。借助 Function Calling 引擎和 MCP 工具系统（包含文件读写、搜索、目录树和本地命令执行），插件可以自主操作项目文件并执行构建/测试命令，成为 IDE 内的强大 AI 编程助手。

![插件界面截图](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/7f3f8611-6fca-499a-a9c9-673cb2e3aae1)  <!-- 插入插件界面的截图 -->

## Features/特性

### Agent Chat View with Block Rendering
全新设计的聊天界面，支持六种内容块类型的分块渲染：Markdown、代码块、思考过程、工具调用、工具使用和工具结果。提供流畅的流式显示效果，智能自动滚动，按对话轮次分气泡展示用户和助手消息。
- 支持分块渲染（Markdown、代码块、思考过程、工具调用与结果）
- 提供更流畅的流式对话体验和智能自动滚动
- 全新Agent Chat View替代旧版文本区域

### Function Calling (Tool Use) Engine
内置 Function Calling 编排引擎，使 AI 模型能够在 IDE 中自主调用工具。支持多轮工具调用循环（最多20轮），自动参数验证、用户审批执行和结果反馈。兼容 OpenAI、Anthropic 和 Markdown 三种协议格式，内置降级容错机制。
- 支持多轮工具调用循环（最多20轮）
- 自动解析、验证、执行工具调用并将结果回传LLM
- 兼容OpenAI、Anthropic和Markdown协议格式
- 内置降级容错机制

### MCP Tool System
Model Context Protocol（MCP）工具系统，提供五个内置工具供 AI Agent 自主操作项目文件和执行命令：

| 工具 | 说明 |
|------|------|
| `read_file` | 文件读取，支持行范围指定 |
| `write_file` | 文件写入（overwrite / patch / append / insert_after_line），原子写入 |
| `grep` | 关键词搜索，支持上下文行数、文件大小限制、目录黑名单、二进制过滤 |
| `tree` | 项目目录树，支持自定义忽略目录 |
| `shell_exec` | 本地命令执行，四层安全防御，结构化输出 |

- 所有工具沙箱化限制在项目工作区内

### Shell Exec Tool (v4.0.1 新增)
AI Agent 可在项目工作区内安全地执行非交互式本地命令（构建、测试、版本控制查询等），获取包含 stdout、stderr、退出码、执行时长和风险等级的结构化结果。

**四层纵深安全防御：**
1. **Schema 约束**：强制使用 argv 数组传参（如 `["git", "status"]`），避免 Shell 注入
2. **Validator 语义校验**：可执行文件白名单、deny 模式匹配、元字符检测、symlink 边界校验、环境变量白名单
3. **Policy 风险策略**：四级风险评估（READ_ONLY / WORKSPACE_MUTATING / NETWORKED / SYSTEM_MUTATING），根据风险等级自动决定是否需要用户审批
4. **Runtime 运行时限制**：超时控制（默认 30s，最大 300s）、输出截断（默认 64KB）、进程树递归终止

**策略配置**：通过 `.gpt4lll/shell-policy.json` 自定义白名单、黑名单、环境变量白名单和审批策略。

### Unified LLM Client
集中管理与多个供应商的 HTTP 和 SSE 通信的 LLM 客户端抽象层。支持流式聊天、同步聊天和原始 JSON 三种模式。通过适配器模式透明处理供应商差异（URL、API Key 格式、消息结构、SSE 解析）。

### Conversation Memory Management
智能对话记忆系统，提供多种策略：滑动窗口记忆（基于 Token 截断）、摘要记忆（超出阈值时 LLM 自动摘要）、自适应记忆（基于相似度）和复合记忆（多层级）。包含 API 返回的真实 Token 用量追踪。
- 滑动窗口记忆（SlidingWindowMemory）
- 摘要记忆（SummarizingMemory）
- 自适应记忆（AdaptiveMemory）
- 复合记忆（CompositeMemory）

### Runtime Status Indicator
通过动画边框展示任务状态：空闲（无边框）、运行中（绿色扫光动画）、完成（绿色实线）、错误（红色）。执行时显示 Stop 按钮支持任务取消，状态3秒后自动复位。

### Free AIGC Access/免费使用AIGC
The AIGC capability is now freely available to all users. Enjoy intelligent generation services at no extra cost. However, due to high demand, you may need to queue each time you use this feature.  
AIGC能力现已向所有用户免费开放。无需额外费用，即可享受智能化的生成服务。但由于使用人数过多，每次使用此功能时可能需要排队等待。

![AIGC功能截图](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/1cafa320-6f3b-4b0f-bfbb-db586a92fa3b)  <!-- 插入AIGC功能的截图 -->

### Multi-Platform Support/多平台支持
插件支持多个 AI 平台，提供统一接口和无缝切换。
#### 支持列表
- OpenAI (GPT-4, GPT-4o 等)
- Grok
- 通义千问 (Qwen3-Coder-Plus, Qwen3-Coder-Flash 等)
- 百度文心一言
- 深度求索 (V3, R1)
- OpenAI 标准的自定义接口

插件现已支持多个平台，不仅支持OpenAI的ChatGPT，还支持百度的文心一言和阿里的通义千问。未来，我们计划进一步扩展兼容性，支持更多平台。

![多平台支持截图](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/5deb8ff8-0d79-487c-baee-27c70c7a0ed1)  <!-- 插入多平台支持的截图 -->

### Code Generation Based on TODO/根据TODO生成代码
在项目中根据TODO评论生成代码，通过将可操作的TODO转化为功能性代码段实现快速开发。
Introduced the ability to generate code based on TODO comments within a project.  
在项目中根据TODO评论生成代码的能力已被引入。

[观看TODO功能自动生成演示](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/192f0812-f9c2-42c5-89b9-23e4e8c0861d)
[观看TODO功能自动生成演示2](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/29c80575-0c3b-4530-9ef4-dd764e5b7d4c)
[观看TODO功能自动生成演示3](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/5330c0ec-39c9-4207-aa10-a527238351f1)
[观看TODO功能自动生成演示4](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/bff2e642-c0e5-4cd2-afb5-af2a55832800)

### Natural Language Recognition/自然语言识别
支持100多种语言，自动检测用户系统语言，以用户偏好的语言提供回复。
Improved the logic for natural language recognition, enhancing accuracy and performance.  
改进了自然语言识别逻辑，提高了准确度和性能。

### Automatic Code Type Recognition/自动识别代码类型
自动识别代码类型，无需手动配置。
Enhanced the code type recognition feature to automatically identify code types.  
提升了代码类型识别功能，自动识别代码类型。

### Generate Code On Demand/按需生成代码
根据需要生成代码片段。
Implemented the capability to generate code on demand, providing code snippets when needed.  
增加按需生成代码的功能，根据需要提供代码片段。

### Automatic Code Comments Generation/自动生成代码注释
只需圈选代码，插件即可调用 AI 模型生成全面且符合上下文的代码注释。
Added a feature that allows you to auto-generate code comments using ChatGPT by simply highlighting the code.  
添加了使用ChatGPT自动生成代码注释的功能。只需简单地圈选代码，插件就会调用ChatGPT AI模型生成全面且符合上下文的代码注释。

[观看自动生成代码注释演示](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/1650c220-b7db-489d-8a82-e35e5882d201) <!-- 插入自动生成注释的截图 -->

### Intelligent Comment Translation/智能注释翻译
对外部库文件中的注释进行实时流式翻译，支持在原文和翻译之间无缝切换，并可重新生成翻译内容，显著提升代码阅读和学习效率。

### History Recording Feature/历史记录功能
增加了历史记录功能，防止数据丢失。每次对话都会被记录，方便查看之前的交互记录。
Added a history recording feature to prevent data loss. Every conversation will now be recorded for easy access.  
增加了历史记录功能，防止数据丢失。每次对话都会被记录，方便查看之前的交互记录。

### Code Assessment/代码评估
代码评估功能，允许团队领导评估团队成员的代码，促进代码审查和持续学习。
Introduced a new Code Assessment feature, allowing team leaders to evaluate team members' code.  
新引入了代码评估功能，允许团队领导评估团队成员的代码。

[观看代码评估功能演示](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/71961e75-8bf8-488c-a430-aa243534ea2a)

### Unit Test Generation/单元测试生成
一键生成单元测试功能，从选定代码块自动生成可执行的单元测试文件。

### SQL Statement Evaluation and Optimization/SQL语句评估和优化
全面的SQL语句评估和优化功能，提供语法检查、性能优化建议、安全性评估、索引使用建议、查询效率分析和最佳实践建议。

### AI-Powered Commit Message Generation/AI智能提交信息生成
基于Git变更内容使用AI技术自动生成符合规范的提交信息。
Added a feature to automatically generate Git commit messages in IDEA's commit dialog. The plugin analyzes changes in the commit dialog and uses AI to generate professional commit messages following conventional commit standards.  
新增了在IDEA提交对话框中自动生成Git提交信息的功能。插件会分析提交对话框中的变更并使用AI生成符合约定式提交规范的专业提交信息。

**使用方法**: 在Git提交对话框中点击 "Generate with AI" 按钮

### Git Work Report Generation/Git工作报告生成
通过按作者和时间范围筛选Git提交记录来生成详细的工作报告，支持团队协作和项目管理。

### Batch File Assessment/批量文件评估
在项目文件列表中选择多个代码文件或文件夹同时评估。

### AI-Powered Linter Fix/AI智能Linter修复
基于 JetBrains IDE Linter 错误的智能代码修复建议。选中有问题的代码，右键选择"GPT4lll: Fix Linter Errors"或使用快捷键 Ctrl+Alt+L 获取 AI 生成的修复建议。并排对比对话框显示原始代码和修复后的代码，按回车键即可立即应用修复。

## Installation/安装
1. Open IntelliJ IDEA.
2. Go to `File` -> `Settings` -> `Plugins`.
3. Search for "GPT4_lll" and install it.
4. Restart IntelliJ IDEA.

![安装过程截图](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/cd5c3e21-d8b9-485d-8861-931fd729e5d9)  <!-- 插入安装过程的截图 -->

## Usage/使用
After installation, you can access the plugin from the toolbar. Highlight the code or TODO comments you want to work with, and select the appropriate action from the plugin menu.

# AI Platform Integration Guide

## 添加新平台

当需要添加一个全新的AI平台时，需要按照以下步骤进行：

1. **添加平台枚举**
    - 在`ProviderNameEnum`中添加新平台的枚举值
    - 示例：`DEEP_SEEK("DeepSeek")`

2. **配置API密钥管理**
    - 在`ChatUtils`的`getApiKey`方法中添加对应平台的信息
    - 在`MyPluginSettings.State`类中添加平台API密钥字段
    - 示例：`public String deepSeekApiKey;`

3. **添加模型厂商信息**
    - 在`ModelUtils`的静态块中添加模型厂商数据
    - 格式：`modelProviders.add(new ModelProvider(厂商枚举名.getProviderName(), "API地址", "厂商描述"));`

4. **配置UI界面**
   在`MyPluginConfigurable`类中进行以下修改：

   a. 添加API密钥输入框字段
   ```java
   private JTextField deepseekApiKeyField;
   ```

   b. 在构造方法中添加UI布局代码
   ```java
   addSeparator(panel, c, gridy++);
   addTitleLabel(panel, c, gridy++, "平台名称配置");
   addLabelAndField(panel, c, gridy++, "平台 Api Key:", apiKeyField);
   ```

   c. 修改`isModified()`方法，添加配置修改检测

   d. 修改`apply()`方法，处理配置保存逻辑

   e. 修改`reset()`方法，处理配置重置逻辑

## 为现有平台添加新模型

当需要在已有平台中添加新的模型时，只需执行以下步骤：

1. **在ModelUtils中添加模型信息**
   ```java
   modelOptions.add(new SelectModelOption(
       "模型标识符",
       "模型描述信息",
       ProviderNameEnum.平台名称.getProviderName(),
       "模型显示名称"
   ));
   ```

2. **验证模型配置**
    - 确保新模型的API请求格式与平台现有格式一致
    - 测试模型是否能正常工作

## 注意事项

1. 添加新平台时，确保所有配置项都已正确设置
2. API密钥的存储要考虑安全性
3. UI界面的设计要保持一致性
4. 新增模型时要确保与平台的API规范相符
5. 所有新增的配置项都要考虑持久化存储

## 测试检查清单

- [ ] API密钥配置是否正常保存和读取
- [ ] UI界面是否正常显示
- [ ] 新模型是否能正常调用API
- [ ] 配置的修改和重置功能是否正常
- [ ] 与已有功能是否有冲突