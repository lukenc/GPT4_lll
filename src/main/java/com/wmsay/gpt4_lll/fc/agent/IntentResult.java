package com.wmsay.gpt4_lll.fc.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 意图识别结果 — 不可变数据类。
 * 包含请求清晰度、复杂度评估、推荐执行策略和过滤后的工具名称列表。
 */
public class IntentResult {

    public enum Clarity { CLEAR, AMBIGUOUS }
    public enum Complexity { SIMPLE, COMPLEX }

    private final Clarity clarity;
    private final Complexity complexity;
    private final String recommendedStrategy;
    private final String reasoning;
    private final List<String> filteredToolNames;

    private IntentResult(Clarity clarity, Complexity complexity,
                         String recommendedStrategy, String reasoning,
                         List<String> filteredToolNames) {
        this.clarity = clarity;
        this.complexity = complexity;
        this.recommendedStrategy = recommendedStrategy;
        this.reasoning = reasoning;
        this.filteredToolNames = filteredToolNames == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(filteredToolNames));
    }

    public Clarity getClarity() { return clarity; }
    public Complexity getComplexity() { return complexity; }
    public String getRecommendedStrategy() { return recommendedStrategy; }
    public String getReasoning() { return reasoning; }
    public List<String> getFilteredToolNames() { return filteredToolNames; }

    /**
     * 默认结果 — LLM 调用失败时的回退值。
     * clarity=CLEAR, complexity=SIMPLE, strategy="react", 空 filteredToolNames。
     */
    public static IntentResult defaultResult() {
        return new IntentResult(Clarity.CLEAR, Complexity.SIMPLE, "react",
            "default fallback", Collections.emptyList());
    }

    /**
     * 静态工厂方法。
     */
    public static IntentResult of(Clarity clarity, Complexity complexity,
                                  String recommendedStrategy, String reasoning,
                                  List<String> filteredToolNames) {
        return new IntentResult(clarity, complexity, recommendedStrategy, reasoning, filteredToolNames);
    }
}
