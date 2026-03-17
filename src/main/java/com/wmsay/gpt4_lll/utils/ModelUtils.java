package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.ProjectSettings;
import com.wmsay.gpt4_lll.model.ModelProvider;
import com.wmsay.gpt4_lll.model.SelectModelOption;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;
import com.wmsay.gpt4_lll.model.key.Gpt4lllComboxKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModelUtils {

    public final static List<SelectModelOption> modelOptions;
    public final static Map<String, List<SelectModelOption>> provider2ModelList;
    public final static List<ModelProvider> modelProviders;
    private final static Map<String, String> displayName2Model;

    private final static Map<String, String> model2Provider;
    private final static Map<String, String> provider2Url;

    static {
        modelProviders = new ArrayList<>(15);
        modelOptions = new ArrayList<>(100);

        //模型厂商
        modelProviders.add(new ModelProvider(ProviderNameEnum.BAIDU.getProviderName(), "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/", "一个特立独行的哨兵供应商，瞎立什么规范"));
        modelProviders.add(new ModelProvider(ProviderNameEnum.OPEN_AI.getProviderName(), "https://api.openai.com/v1/chat/completions", ""));
        modelProviders.add(new ModelProvider(ProviderNameEnum.ALI.getProviderName(), "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "国内厂商中头部水平了"));
        modelProviders.add(new ModelProvider(ProviderNameEnum.GROK.getProviderName(), "https://api.x.ai/v1/chat/completions", "X出的的平台，马斯克早的应该不错。"));
        modelProviders.add(new ModelProvider(ProviderNameEnum.DEEP_SEEK.getProviderName(), "https://api.deepseek.com/chat/completions", "国内头部厂商"));
        modelProviders.add(new ModelProvider(ProviderNameEnum.VOLC_ENGINE.getProviderName(), "https://ark.cn-beijing.volces.com/api/v3/chat/completions", "字节跳动火山引擎豆包系列"));

        provider2Url = modelProviders.stream().collect(Collectors.toMap(ModelProvider::getName, ModelProvider::getUrl));
        // 免费模型
        modelOptions.add(new SelectModelOption("baidu-free", "免费的模型", ProviderNameEnum.FREE.getProviderName(), "免费/Free"));
        // OpenAI 模型
        modelOptions.add(new SelectModelOption("gpt-3.5-turbo", "A powerful general-purpose model", ProviderNameEnum.OPEN_AI.getProviderName(), "GPT-3.5 Turbo"));
        modelOptions.add(new SelectModelOption("gpt-4", "An even more powerful general-purpose model", ProviderNameEnum.OPEN_AI.getProviderName(), "GPT-4"));
        modelOptions.add(new SelectModelOption("gpt-4-turbo", "A high-efficiency version of GPT-4", ProviderNameEnum.OPEN_AI.getProviderName(), "GPT-4 Turbo"));
        // Baidu 模型
        modelOptions.add(new SelectModelOption("ernie-4.0-8k-latest", "支持自动对接百度搜索插件，保障问答信息时效，支持5K tokens输入+2K tokens输出。", ProviderNameEnum.BAIDU.getProviderName(), "ERNIE-4.0-8K-Latest"));
        modelOptions.add(new SelectModelOption("ernie-4.0-turbo-8k", "ERNIE 4.0 Turbo是百度自研的旗舰级超大规模⼤语⾔模型，综合效果表现出色，广泛适用于各领域复杂任务场景；支持自动对接百度搜索插件，保障问答信息时效。", ProviderNameEnum.BAIDU.getProviderName(), "ERNIE-4.0-Turbo-8K"));
        modelOptions.add(new SelectModelOption("mixtral_8x7b_instruct", "由Mistral AI发布。在代码生成任务中表现尤为优异", ProviderNameEnum.BAIDU.getProviderName(), "Mixtral-8x7B-Instruct 👍"));
        modelOptions.add(new SelectModelOption("llama_3_70b", "Meta AI于2024年4月18日发布的Meta Llama 3系列70B参数大语言模型", ProviderNameEnum.BAIDU.getProviderName(), "Meta-Llama-3-70B-Instruct"));

        // Alibaba 模型
        modelOptions.add(new SelectModelOption("qwen3.5-plus", "千问3.5-Plus：397B参数MoE架构，100万token上下文，自适应推理，性价比极高", ProviderNameEnum.ALI.getProviderName(), "Qwen3.5 Plus"));
        modelOptions.add(new SelectModelOption("qwen3-coder-plus", "推荐： Qwen3-Coder-Plus 系列模型是基于 Qwen3 的代码生成模型", ProviderNameEnum.ALI.getProviderName(), "Qwen3 Coder Plus"));
        modelOptions.add(new SelectModelOption("qwen3-coder-flash", "基于 Qwen3 的代码生成模型", ProviderNameEnum.ALI.getProviderName(), "Qwen Coder Flash"));
        modelOptions.add(new SelectModelOption("qwen-coder-plus", "稳定版的专门搞代码的模型。好于turbo。", ProviderNameEnum.ALI.getProviderName(), "Qwen Coder Plus(better then turbo)"));
        modelOptions.add(new SelectModelOption("qwen-coder-turbo", "稳定版的专门搞代码的模型，便宜。", ProviderNameEnum.ALI.getProviderName(), "Qwen Coder Turbo"));
        modelOptions.add(new SelectModelOption("qwen-coder-turbo-latest", "最新版本的专门搞代码的模型，便宜。", ProviderNameEnum.ALI.getProviderName(), "Qwen Coder Turbo Latest"));
        modelOptions.add(new SelectModelOption("qwen-max", "Max系列是阿里系最高规格的模型，这个是稳定版", ProviderNameEnum.ALI.getProviderName(), "Qwen Max"));
        modelOptions.add(new SelectModelOption("qwen-max-latest", "Max系列是阿里系最高规格的模型，这个是最新版", ProviderNameEnum.ALI.getProviderName(), "Qwen Max Latest"));
        modelOptions.add(new SelectModelOption("qwen-max-longcontext", "Max系列是阿里系最高规格的模型，这个是长上下文版本", ProviderNameEnum.ALI.getProviderName(), "Qwen Max Long Context"));
        modelOptions.add(new SelectModelOption("qwen-plus", "稳定的Plus适合中等复杂任务", ProviderNameEnum.ALI.getProviderName(), "Qwen Plus"));
        modelOptions.add(new SelectModelOption("qwen-plus-latest", "最新的Plus适合中等复杂任务", ProviderNameEnum.ALI.getProviderName(), "Qwen Plus Latest"));
        modelOptions.add(new SelectModelOption("qwen-turbo", "turbo稳定版。速度最快、成本很低的模型，适合简单任务", ProviderNameEnum.ALI.getProviderName(), "Qwen Turbo"));
        modelOptions.add(new SelectModelOption("qwen-turbo-latest", "turbo最新版。速度最快、成本很低的模型，适合简单任务", ProviderNameEnum.ALI.getProviderName(), "Qwen Turbo Latest"));
        modelOptions.add(new SelectModelOption("qwen-long", "Long-context handling version of Qwen", ProviderNameEnum.ALI.getProviderName(), "Qwen Long"));
        modelOptions.add(new SelectModelOption("deepseek-r1", "671B 满血版模型。由阿里云部署。", ProviderNameEnum.ALI.getProviderName(), "deepseek-r1 deploy by aliyun"));
        modelOptions.add(new SelectModelOption("deepseek-v3", "参数量为 671B。由阿里云部署。", ProviderNameEnum.ALI.getProviderName(), "deepseek-v3 deploy by aliyun"));

        //X 的GROK 系列
        modelOptions.add(new SelectModelOption("grok-beta", "X打造的大模型，提供简洁、有效的代码解决方案。", ProviderNameEnum.GROK.getProviderName(), "grok-beta"));
        modelOptions.add(new SelectModelOption("grok-2-latest", "X打造的大模型，提供简洁、有效的代码解决方案。", ProviderNameEnum.GROK.getProviderName(), "grok-2-latest"));
        modelOptions.add(new SelectModelOption("grok-2", "X打造的大模型，提供简洁、有效的代码解决方案。", ProviderNameEnum.GROK.getProviderName(), "grok-2"));

        //DeepSeek
        modelOptions.add(new SelectModelOption("deepseek-chat", "deepseek-chat 模型已全面升级为 DeepSeek-V3。是DeepSeek团队推出的大语言模型，支持多模态对话，具有强大的文本理解能力。", ProviderNameEnum.DEEP_SEEK.getProviderName(), "DeepSeek(DeepSeek-V3)"));
        modelOptions.add(new SelectModelOption("deepseek-reasoner", "deepseek-reasoner是DeepSeek 最新推出的推理模型 DeepSeek-R1。", ProviderNameEnum.DEEP_SEEK.getProviderName(), "DeepSeek-R1"));
        // 火山引擎（豆包）系列 — 仅保留支持 Function Calling 的模型
        modelOptions.add(new SelectModelOption("doubao-seed-2-0-pro-260215", "豆包Seed 2.0 Pro旗舰模型，支持长链路Agent和Function Calling", ProviderNameEnum.VOLC_ENGINE.getProviderName(), "Doubao Seed 2.0 Pro"));
        modelOptions.add(new SelectModelOption("doubao-seed-2-0-lite-260215", "豆包Seed 2.0 Lite均衡型模型，支持Function Calling", ProviderNameEnum.VOLC_ENGINE.getProviderName(), "Doubao Seed 2.0 Lite"));
        modelOptions.add(new SelectModelOption("doubao-seed-2-0-code-preview-260215", "豆包Seed 2.0 Code编程增强版，擅长代码生成与理解", ProviderNameEnum.VOLC_ENGINE.getProviderName(), "Doubao Seed 2.0 Code"));
        modelOptions.add(new SelectModelOption("doubao-1-5-pro-256k-250115", "豆包1.5 Pro 256K长上下文模型，支持Function Calling", ProviderNameEnum.VOLC_ENGINE.getProviderName(), "Doubao 1.5 Pro 256K"));
        modelOptions.add(new SelectModelOption("doubao-1-5-pro-32k-250115", "豆包1.5 Pro 32K模型，综合能力强，支持Function Calling", ProviderNameEnum.VOLC_ENGINE.getProviderName(), "Doubao 1.5 Pro 32K"));

        //允许用户使用自己公司或个人的接口
        modelOptions.add(new SelectModelOption("","用户自己提供的api",ProviderNameEnum.PERSONAL.getProviderName(),"自定义/Personal"));

        provider2ModelList = modelOptions.stream()
                .collect(Collectors.groupingBy(
                                SelectModelOption::getProvider
                        )
                );
        displayName2Model = modelOptions.stream()
                .collect(Collectors.toMap(SelectModelOption::getDisplayName, SelectModelOption::getModelName));
        model2Provider = modelOptions.stream().collect(Collectors.toMap(SelectModelOption::getModelName, SelectModelOption::getProvider));
    }

    public static String getModelNameByDisplay(String displayName) {
        return displayName2Model.get(displayName);
    }

    public static String getUrlByProvider(String providerName) {
        return provider2Url.get(providerName);
    }

    public static String getUrlByModel(String model) {
        String provider = model2Provider.get(model);
        String url = provider2Url.get(provider);
        return url;
    }


    public static SelectModelOption getSelectedModel(Project project) {
       return (SelectModelOption) project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_MODEL_COMBO_BOX).getSelectedItem();
    }

    public static String getSelectedProvider(Project project) {
        return (String) project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_PROVIDER_COMBO_BOX).getSelectedItem();
    }

    /**
     * 获取可用的供应商（优先级：当前选中 > 上次保存 > 系统默认）
     * Get available provider (priority: current selected > last saved > system default)
     */
    public static String getAvailableProvider(Project project) {
        try {
            // 首先尝试获取当前选中的供应商
            String currentProvider = (String) project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_PROVIDER_COMBO_BOX).getSelectedItem();
            if (currentProvider != null && !currentProvider.trim().isEmpty()) {
                return currentProvider;
            }
        } catch (Exception e) {
            // 如果获取失败，继续使用保存的设置
        }
        
        // 使用项目设置中保存的上次选择的供应商
        ProjectSettings settings = ProjectSettings.getInstance(project);
        String lastProvider = settings.getLastProvider();
        if (lastProvider != null && !lastProvider.trim().isEmpty()) {
            return lastProvider;
        }
        
        // 最终fallback到系统默认供应商
        return ProviderNameEnum.ALI.getProviderName(); // 使用阿里云作为默认
    }

    /**
     * 获取可用的模型名称（优先级：当前选中 > 上次保存 > 系统默认）
     * Get available model name (priority: current selected > last saved > system default)
     */
    public static String getAvailableModelName(Project project) {
        try {
            // 首先尝试获取当前选中的模型
            SelectModelOption currentModel = (SelectModelOption) project.getUserData(Gpt4lllComboxKey.GPT_4_LLL_MODEL_COMBO_BOX).getSelectedItem();
            if (currentModel != null && currentModel.getModelName() != null) {
                return currentModel.getModelName();
            }
        } catch (Exception e) {
            // 如果获取失败，继续使用保存的设置
        }
        
        // 使用项目设置中保存的上次选择的模型
        ProjectSettings settings = ProjectSettings.getInstance(project);
        String lastModelDisplayName = settings.getLastModelDisplayName();
        if (lastModelDisplayName != null && !lastModelDisplayName.trim().isEmpty()) {
            String modelName = getModelNameByDisplay(lastModelDisplayName);
            if (modelName != null) {
                return modelName;
            }
        }
        
        // 最终fallback到系统默认模型
        return "qwen-turbo"; // 使用便宜且快速的模型作为默认
    }

}
