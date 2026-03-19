package com.wmsay.gpt4_lll.fc.strategy;

import com.intellij.openapi.diagnostic.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行策略工厂 — 管理所有已注册的执行策略，根据名称创建/获取策略实例。
 * <p>
 * 内置 ReAct 和 PlanAndExecute 两种策略，支持通过 {@link #register(ExecutionStrategy)}
 * 注册自定义策略。
 */
public class ExecutionStrategyFactory {

    private static final Logger LOG = Logger.getInstance(ExecutionStrategyFactory.class);

    /** 默认策略名称 */
    public static final String DEFAULT_STRATEGY = "react";

    private static final Map<String, ExecutionStrategy> strategies = new ConcurrentHashMap<>();

    static {
        register(new ReActStrategy());
        register(new PlanAndExecuteStrategy());
    }

    private ExecutionStrategyFactory() {
        // utility class
    }

    /**
     * 注册一个执行策略。
     *
     * @param strategy 策略实例
     */
    public static void register(ExecutionStrategy strategy) {
        strategies.put(strategy.getName(), strategy);
        LOG.info("Registered execution strategy: " + strategy.getName()
                + " (" + strategy.getDisplayName() + ")");
    }

    /**
     * 根据名称获取策略实例。不存在时返回默认 ReAct 策略。
     *
     * @param name 策略名称
     * @return 策略实例
     */
    public static ExecutionStrategy get(String name) {
        if (name == null || name.isEmpty()) {
            return strategies.get(DEFAULT_STRATEGY);
        }
        ExecutionStrategy strategy = strategies.get(name);
        if (strategy == null) {
            LOG.warn("Unknown execution strategy '" + name
                    + "', falling back to '" + DEFAULT_STRATEGY + "'");
            return strategies.get(DEFAULT_STRATEGY);
        }
        return strategy;
    }

    /**
     * 获取所有已注册的策略（按名称排序）。
     *
     * @return 策略列表
     */
    public static List<ExecutionStrategy> getAll() {
        List<ExecutionStrategy> list = new ArrayList<>(strategies.values());
        list.sort(Comparator.comparing(ExecutionStrategy::getName));
        return Collections.unmodifiableList(list);
    }

    /**
     * 获取所有策略的名称列表。
     */
    public static List<String> getAllNames() {
        List<String> names = new ArrayList<>(strategies.keySet());
        Collections.sort(names);
        return names;
    }

    /**
     * 检查指定名称的策略是否已注册。
     */
    public static boolean isRegistered(String name) {
        return strategies.containsKey(name);
    }
}
