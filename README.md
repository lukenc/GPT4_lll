# GPT4_lll Plugin for IntelliJ IDEA

![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/21935?logo=jetbrains&link=https%3A%2F%2Fplugins.jetbrains.com%2Fembeddable%2Finstall%2F21935)
![JetBrains Plugin Rating](https://img.shields.io/jetbrains/plugin/r/rating/21935?logo=intellijidea)


## Introduction

GPT4_lll是为IntelliJ IDEA用户设计的插件，旨在利用OpenAI的GPT-4模型提供实时的代码查询和分析服务。这个插件在你编写或阅读代码时充当一个强大的助手，帮助你更好地理解和改进你的代码。

![插件界面截图](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/7f3f8611-6fca-499a-a9c9-673cb2e3aae1)  <!-- 插入插件界面的截图 -->

## Features/特性

### Free AIGC Access/免费使用AIGC
The AIGC capability is now freely available to all users. Enjoy intelligent generation services at no extra cost. However, due to high demand, you may need to queue each time you use this feature.  
AIGC能力现已向所有用户免费开放。无需额外费用，即可享受智能化的生成服务。但由于使用人数过多，每次使用此功能时可能需要排队等待。

![AIGC功能截图](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/1cafa320-6f3b-4b0f-bfbb-db586a92fa3b)  <!-- 插入AIGC功能的截图 -->

### Multi-Platform Support/多平台支持
The plugin supports multiple platforms, including OpenAI's ChatGPT, Baidu's ERNIE Bot (Wenxin Yiyan), and Alibaba's Tongyi Qianwen. Future updates will expand compatibility further.  
插件现已支持多个平台，不仅支持OpenAI的ChatGPT，还支持百度的文心一言和阿里的通义千问。未来，我们计划进一步扩展兼容性，支持更多平台。

![多平台支持截图](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/5deb8ff8-0d79-487c-baee-27c70c7a0ed1)  <!-- 插入多平台支持的截图 -->

### Code Generation Based on TODO/根据TODO生成代码
Introduced the ability to generate code based on TODO comments within a project.  
在项目中根据TODO评论生成代码的能力已被引入。

[观看TODO功能自动生成演示](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/192f0812-f9c2-42c5-89b9-23e4e8c0861d)
[观看TODO功能自动生成演示2](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/29c80575-0c3b-4530-9ef4-dd764e5b7d4c)
[观看TODO功能自动生成演示3](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/5330c0ec-39c9-4207-aa10-a527238351f1)
[观看TODO功能自动生成演示4](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/bff2e642-c0e5-4cd2-afb5-af2a55832800)

### Natural Language Recognition/自然语言识别
Improved the logic for natural language recognition, enhancing accuracy and performance.  
改进了自然语言识别逻辑，提高了准确度和性能。

### Automatic Code Type Recognition/自动识别代码类型
Enhanced the code type recognition feature to automatically identify code types.  
提升了代码类型识别功能，自动识别代码类型。

### Generate Code On Demand/按需生成代码
Implemented the capability to generate code on demand, providing code snippets when needed.  
增加按需生成代码的功能，根据需要提供代码片段。

### Automatic Code Comments Generation/自动生成代码注释
Added a feature that allows you to auto-generate code comments using ChatGPT by simply highlighting the code.  
添加了使用ChatGPT自动生成代码注释的功能。只需简单地圈选代码，插件就会调用ChatGPT AI模型生成全面且符合上下文的代码注释。

[观看自动生成代码注释演示](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/1650c220-b7db-489d-8a82-e35e5882d201) <!-- 插入自动生成注释的截图 -->


### History Recording Feature/历史记录功能
Added a history recording feature to prevent data loss. Every conversation will now be recorded for easy access.  
增加了历史记录功能，防止数据丢失。每次对话都会被记录，方便查看之前的交互记录。

### Code Assessment/代码评估
Introduced a new Code Assessment feature, allowing team leaders to evaluate team members' code.  
新引入了代码评估功能，允许团队领导评估团队成员的代码。

[观看代码评估功能演示](https://dev-vroom-1311485584.cos.ap-beijing.myqcloud.com/71961e75-8bf8-488c-a430-aa243534ea2a)

### Git Commit Message Generation/Git提交信息生成
Added a feature to automatically generate Git commit messages in IDEA's commit dialog. The plugin analyzes changes in the commit dialog and uses AI to generate professional commit messages following conventional commit standards.  
新增了在IDEA提交对话框中自动生成Git提交信息的功能。插件会分析提交对话框中的变更并使用AI生成符合约定式提交规范的专业提交信息。

**使用方法**: 在Git提交对话框中点击 "Generate with AI" 按钮


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