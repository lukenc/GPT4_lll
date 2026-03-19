package com.wmsay.gpt4_lll.fc.strategy;

import com.wmsay.gpt4_lll.fc.model.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 组合执行钩子 — 将多个 {@link ExecutionHook} 组合为一个。
 * <p>
 * 按注册顺序依次调用每个钩子。任何一个钩子返回非 CONTINUE 动作时，
 * 立即停止调用后续钩子并返回该动作。
 * <p>
 * 线程安全：内部使用 {@link CopyOnWriteArrayList}，支持运行时添加/移除钩子。
 */
public class CompoundExecutionHook implements ExecutionHook {

    private final CopyOnWriteArrayList<ExecutionHook> hooks = new CopyOnWriteArrayList<>();

    public CompoundExecutionHook() {}

    public CompoundExecutionHook(List<ExecutionHook> hooks) {
        if (hooks != null) {
            this.hooks.addAll(hooks);
        }
    }

    public void addHook(ExecutionHook hook) {
        if (hook != null && hook != this) {
            hooks.add(hook);
        }
    }

    public void removeHook(ExecutionHook hook) {
        hooks.remove(hook);
    }

    public List<ExecutionHook> getHooks() {
        return Collections.unmodifiableList(new ArrayList<>(hooks));
    }

    public boolean isEmpty() {
        return hooks.isEmpty();
    }

    @Override
    public void beforeExecution() {
        for (ExecutionHook hook : hooks) {
            hook.beforeExecution();
        }
    }

    @Override
    public HookResult afterRound(int round, List<ToolCallResult> results) {
        for (ExecutionHook hook : hooks) {
            HookResult result = hook.afterRound(round, results);
            if (result.getAction() != HookAction.CONTINUE) {
                return result;
            }
        }
        return HookResult.continueExecution();
    }

    @Override
    public HookResult beforeStep(int stepIndex, String description) {
        for (ExecutionHook hook : hooks) {
            HookResult result = hook.beforeStep(stepIndex, description);
            if (result.getAction() != HookAction.CONTINUE) {
                return result;
            }
        }
        return HookResult.continueExecution();
    }

    @Override
    public HookResult afterStep(int stepIndex, FunctionCallResult result) {
        for (ExecutionHook hook : hooks) {
            HookResult hr = hook.afterStep(stepIndex, result);
            if (hr.getAction() != HookAction.CONTINUE) {
                return hr;
            }
        }
        return HookResult.continueExecution();
    }

    @Override
    public HookResult afterCompletion(FunctionCallResult result) {
        for (ExecutionHook hook : hooks) {
            HookResult hr = hook.afterCompletion(result);
            if (hr.getAction() != HookAction.CONTINUE) {
                return hr;
            }
        }
        return HookResult.continueExecution();
    }

    @Override
    public HookResult onError(String phase, Exception exception) {
        for (ExecutionHook hook : hooks) {
            HookResult hr = hook.onError(phase, exception);
            if (hr.getAction() != HookAction.CONTINUE) {
                return hr;
            }
        }
        return HookResult.abort(exception.getMessage());
    }
}
