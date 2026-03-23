package com.wmsay.gpt4_lll.fc.memory;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.wmsay.gpt4_lll.fc.model.FunctionCallConfig;

import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * 记忆管理器工厂。
 * <p>
 * 根据 {@link FunctionCallConfig} 中的配置创建对应的 {@link ConversationMemory} 实例。
 * 支持四种内置策略（sliding_window、summarizing、adaptive、composite），
 * 以及通过 Java SPI 机制加载的自定义实现。
 * <p>
 * 未知策略名称回退到 "sliding_window" 并记录警告日志。
 */
public class MemoryFactory {

    private static final Logger LOG = Logger.getLogger(MemoryFactory.class.getName());

    private MemoryFactory() {
        // utility class
    }

    /**
     * 根据配置创建 ConversationMemory 实例。
     *
     * @param config     FunctionCallConfig 配置
     * @param summarizer LLM 摘要函数，输入待摘要文本，返回摘要结果
     * @return 对应策略的 ConversationMemory 实例
     */
    public static ConversationMemory create(FunctionCallConfig config,
                                            Function<String, String> summarizer) {
        String strategy = config.getMemoryStrategy();
        int maxTokens = config.getMemoryMaxTokens();
        int hardLimitTokens = config.getMemoryHardLimitTokens();
        if (hardLimitTokens <= 0) {
            hardLimitTokens = (int) (maxTokens * 1.2);
        }

        switch (strategy) {
            case "sliding_window":
                return createSlidingWindow(maxTokens, hardLimitTokens);
            case "summarizing":
                return createSummarizing(config, summarizer, maxTokens, hardLimitTokens);
            case "adaptive":
                return createAdaptive(config, summarizer, maxTokens, hardLimitTokens);
            case "composite":
                return createComposite(maxTokens, hardLimitTokens);
            default:
                return createFromSpiOrFallback(strategy, maxTokens, hardLimitTokens);
        }
    }

    private static ConversationMemory createSlidingWindow(int maxTokens, int hardLimitTokens) {
        LOG.info("Creating SlidingWindowMemory: maxTokens=" + maxTokens
                + ", hardLimitTokens=" + hardLimitTokens);
        return new SlidingWindowMemory(maxTokens, hardLimitTokens);
    }

    private static ConversationMemory createSummarizing(FunctionCallConfig config,
                                                        Function<String, String> summarizer,
                                                        int maxTokens,
                                                        int hardLimitTokens) {
        int summarizeThreshold = config.getMemorySummarizeThreshold();
        SlidingWindowMemory delegate = new SlidingWindowMemory(maxTokens, hardLimitTokens);
        LOG.info("Creating SummarizingMemory: summarizeThreshold=" + summarizeThreshold
                + ", maxTokens=" + maxTokens + ", hardLimitTokens=" + hardLimitTokens);
        return new SummarizingMemory(delegate, summarizeThreshold, summarizer, null);
    }

    private static ConversationMemory createAdaptive(FunctionCallConfig config,
                                                     Function<String, String> summarizer,
                                                     int maxTokens,
                                                     int hardLimitTokens) {
        int summarizeThreshold = config.getMemorySummarizeThreshold();
        double similarityThreshold = config.getMemorySimilarityThreshold();

        SlidingWindowMemory slidingWindow = new SlidingWindowMemory(maxTokens, hardLimitTokens);
        SlidingWindowMemory summarizingDelegate = new SlidingWindowMemory(maxTokens, hardLimitTokens);
        SummarizingMemory summarizing = new SummarizingMemory(
                summarizingDelegate, summarizeThreshold, summarizer, null);
        SimilarityAnalyzer analyzer = new SimilarityAnalyzer(summarizer);

        LOG.info("Creating AdaptiveMemory: similarityThreshold=" + similarityThreshold
                + ", summarizeThreshold=" + summarizeThreshold
                + ", maxTokens=" + maxTokens + ", hardLimitTokens=" + hardLimitTokens);
        return new AdaptiveMemory(slidingWindow, summarizing, analyzer, similarityThreshold);
    }

    private static ConversationMemory createComposite(int maxTokens, int hardLimitTokens) {
        SlidingWindowMemory workingMemory = new SlidingWindowMemory(maxTokens, hardLimitTokens);
        SlidingWindowMemory shortTermMemory = new SlidingWindowMemory(maxTokens, hardLimitTokens);
        LOG.info("Creating CompositeMemory: maxTokens=" + maxTokens
                + ", hardLimitTokens=" + hardLimitTokens);
        return new CompositeMemory(workingMemory, shortTermMemory);
    }

    private static ConversationMemory createFromSpiOrFallback(String strategy,
                                                              int maxTokens,
                                                              int hardLimitTokens) {
        // Try SPI loading
        try {
            ServiceLoader<ConversationMemory> loader = ServiceLoader.load(ConversationMemory.class);
            for (ConversationMemory impl : loader) {
                String implName = impl.getClass().getSimpleName();
                if (implName.toLowerCase().contains(strategy.toLowerCase())) {
                    LOG.info("Loaded custom ConversationMemory via SPI: " + impl.getClass().getName()
                            + " for strategy '" + strategy + "'");
                    return impl;
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load ConversationMemory via SPI for strategy '"
                    + strategy + "'", e);
        }

        // Fallback to sliding_window
        LOG.log(Level.WARNING, "Unknown memory strategy '" + strategy
                + "', falling back to 'sliding_window'");
        return new SlidingWindowMemory(maxTokens, hardLimitTokens);
    }
}
