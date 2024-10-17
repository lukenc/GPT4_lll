package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.project.Project;
import com.wmsay.gpt4_lll.component.Gpt4lllComboxKey;
import com.wmsay.gpt4_lll.model.ModelProvider;
import com.wmsay.gpt4_lll.model.SelectModelOption;
import com.wmsay.gpt4_lll.model.enums.ProviderNameEnum;

import java.util.ArrayList;
import java.util.HashMap;
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
        modelOptions = new ArrayList<>(50);

        //模型厂商
        modelProviders.add(new ModelProvider(ProviderNameEnum.BAIDU.getProviderName(), "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/", "一个特立独行的哨兵供应商，瞎立什么规范"));
        modelProviders.add(new ModelProvider(ProviderNameEnum.OPEN_AI.getProviderName(), "https://api.openai.com/v1/chat/completions", ""));
        modelProviders.add(new ModelProvider(ProviderNameEnum.ALI.getProviderName(), "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "国内厂商中头部水平了"));
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
        modelOptions.add(new SelectModelOption("qwen-coder-turbo", "推荐：稳定版的专门搞代码的模型，便宜。", ProviderNameEnum.ALI.getProviderName(), "Qwen Coder Turbo"));
        modelOptions.add(new SelectModelOption("qwen-coder-turbo-latest", "推荐：最新版本的专门搞代码的模型，便宜。", ProviderNameEnum.ALI.getProviderName(), "Qwen Coder Turbo Latest"));
        modelOptions.add(new SelectModelOption("qwen-max", "Max系列是阿里系最高规格的模型，这个是稳定版", ProviderNameEnum.ALI.getProviderName(), "Qwen Max"));
        modelOptions.add(new SelectModelOption("qwen-max-latest", "Max系列是阿里系最高规格的模型，这个是最新版", ProviderNameEnum.ALI.getProviderName(), "Qwen Max Latest"));
        modelOptions.add(new SelectModelOption("qwen-max-longcontext", "Max系列是阿里系最高规格的模型，这个是长上下文版本", ProviderNameEnum.ALI.getProviderName(), "Qwen Max Long Context"));
        modelOptions.add(new SelectModelOption("qwen-plus", "稳定的Plus适合中等复杂任务", ProviderNameEnum.ALI.getProviderName(), "Qwen Plus"));
        modelOptions.add(new SelectModelOption("qwen-plus-latest", "最新的Plus适合中等复杂任务", ProviderNameEnum.ALI.getProviderName(), "Qwen Plus Latest"));
        modelOptions.add(new SelectModelOption("qwen-turbo", "turbo稳定版。速度最快、成本很低的模型，适合简单任务", ProviderNameEnum.ALI.getProviderName(), "Qwen Turbo"));
        modelOptions.add(new SelectModelOption("qwen-turbo-latest", "turbo最新版。速度最快、成本很低的模型，适合简单任务", ProviderNameEnum.ALI.getProviderName(), "Qwen Turbo Latest"));
        modelOptions.add(new SelectModelOption("qwen-long", "Long-context handling version of Qwen", ProviderNameEnum.ALI.getProviderName(), "Qwen Long"));
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

}
