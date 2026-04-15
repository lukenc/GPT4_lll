package com.wmsay.gpt4_lll.fc.planning;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行策略工厂 — 管理所有已注册的执行策略，根据名称创建/获取策略实例。
 * <p>
 * 内置 ReAct 和 PlanAndExecute 两种策略，支持通过 {@link #register(ExecutionStrategy)}
 * 注册自定义策略，也支持通过 SPI（{@link ServiceLoader}）自动发现自定义策略。
 */
public class ExecutionStrategyFactory {

    private static final Logger LOG = Logger.getLogger(ExecutionStrategyFactory.class.getName());

    /** 默认策略名称 */
    public static final String DEFAULT_STRATEGY = "react";

    private static final Map<String, ExecutionStrategy> strategies = new ConcurrentHashMap<>();

    static {
        // 内置策略
        register(new ReActStrategy());
        register(new PlanAndExecuteStrategy());
        // SPI 自动发现自定义策略
        loadFromServiceLoader();
    }

    private ExecutionStrategyFactory() {
        // utility class
    }

    /**
     * 清空所有已注册的策略。用于插件动态卸载时释放类加载器引用。
     */
    public static void reset() {
        strategies.clear();
    }

    /**
     * 重新初始化内置策略（动态重新加载后调用）。
     */
    public static void reinitialize() {
        reset();
        register(new ReActStrategy());
        register(new PlanAndExecuteStrategy());
        loadFromServiceLoader();
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
            LOG.log(Level.WARNING, "Unknown execution strategy '" + name
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
        return Collections.unmodifiableList(names);
    }

    /**
     * 检查指定名称的策略是否已注册。
     */
    public static boolean isRegistered(String name) {
        return strategies.containsKey(name);
    }

    /**
     * 通过 SPI（ServiceLoader）自动发现并注册自定义 ExecutionStrategy 实现。
     * <p>
     * 加载异常时记录警告日志并继续，不中断框架初始化。
     */
    private static void loadFromServiceLoader() {
        try {
            ServiceLoader<ExecutionStrategy> loader = ServiceLoader.load(ExecutionStrategy.class);
            for (ExecutionStrategy strategy : loader) {
                try {
                    register(strategy);
                    LOG.info("SPI discovered execution strategy: " + strategy.getName());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to register SPI strategy: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load ExecutionStrategy SPI providers: " + e.getMessage(), e);
        }
    }
}
