package com.wmsay.gpt4_lll.fc.strategy;

import com.wmsay.gpt4_lll.fc.model.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;

import java.util.List;

/**
 * 执行钩子 — 在 Agent 执行的关键节点插入自定义逻辑。
 * <p>
 * 比独立的 Reflector 接口更轻量、更灵活。
 * 策略在执行循环的关键节点调用钩子方法，钩子通过返回 {@link HookAction}
 * 控制执行流程（继续、中止、重试）。
 * <p>
 * 使用场景：
 * <ul>
 *   <li>超时保护：检测执行时间是否超出阈值</li>
 *   <li>连续失败检测：工具连续失败时提前终止</li>
 *   <li>质量检查：执行完成后评估结果质量</li>
 *   <li>自定义反思：在每轮结束后注入额外的反思逻辑</li>
 * </ul>
 *
 * @see CompoundExecutionHook
 */
public interface ExecutionHook {

    /**
     * 钩子动作 — 控制执行流程。
     */
    enum HookAction {
        /** 继续正常执行 */
        CONTINUE,
        /** 中止执行，使用当前已有结果 */
        ABORT,
        /** 重试当前操作（需要策略支持） */
        RETRY
    }

    /**
     * 带附加信息的钩子动作结果。
     */
    class HookResult {
        private final HookAction action;
        private final String reason;

        private HookResult(HookAction action, String reason) {
            this.action = action;
            this.reason = reason;
        }

        public HookAction getAction() { return action; }
        public String getReason() { return reason; }

        public static HookResult continueExecution() {
            return new HookResult(HookAction.CONTINUE, null);
        }

        public static HookResult abort(String reason) {
            return new HookResult(HookAction.ABORT, reason);
        }

        public static HookResult retry(String reason) {
            return new HookResult(HookAction.RETRY, reason);
        }
    }

    /**
     * 新一次执行开始前调用。
     * 有状态的钩子应在此方法中重置内部状态（计时器、计数器等），
     * 确保每次执行的判断基于本次执行的数据。
     */
    default void beforeExecution() {}

    /**
     * 每轮工具执行完成后调用。
     * 可检查本轮结果，决定是否继续、中止或重试。
     *
     * @param round   当前轮次（从 0 开始）
     * @param results 本轮所有工具调用结果
     * @return 钩子动作
     */
    default HookResult afterRound(int round, List<ToolCallResult> results) {
        return HookResult.continueExecution();
    }

    /**
     * Plan-and-Execute 策略中，单个步骤执行前调用。
     * 可决定是否跳过某个步骤。
     *
     * @param stepIndex   步骤索引
     * @param description 步骤描述
     * @return 钩子动作（CONTINUE = 执行步骤，ABORT = 跳过步骤）
     */
    default HookResult beforeStep(int stepIndex, String description) {
        return HookResult.continueExecution();
    }

    /**
     * Plan-and-Execute 策略中，单个步骤执行后调用。
     *
     * @param stepIndex 步骤索引
     * @param result    步骤执行结果
     * @return 钩子动作
     */
    default HookResult afterStep(int stepIndex, FunctionCallResult result) {
        return HookResult.continueExecution();
    }

    /**
     * Agent 执行完成后调用，可决定是否接受结果或要求重试。
     *
     * @param result 最终执行结果
     * @return 钩子动作（CONTINUE/ACCEPT = 接受，RETRY = 重新执行）
     */
    default HookResult afterCompletion(FunctionCallResult result) {
        return HookResult.continueExecution();
    }

    /**
     * 执行过程中发生异常时调用。
     * 钩子可决定是否吞掉异常继续执行。
     *
     * @param phase     发生异常的阶段描述
     * @param exception 异常
     * @return 钩子动作（CONTINUE = 忽略异常继续，ABORT = 立即终止）
     */
    default HookResult onError(String phase, Exception exception) {
        return HookResult.abort(exception.getMessage());
    }
}
