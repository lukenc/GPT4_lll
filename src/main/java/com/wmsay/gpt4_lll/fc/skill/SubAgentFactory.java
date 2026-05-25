package com.wmsay.gpt4_lll.fc.skill;

import com.wmsay.gpt4_lll.fc.core.AgentDefinition;
import com.wmsay.gpt4_lll.fc.core.AgentRuntimeConfig;
import com.wmsay.gpt4_lll.fc.core.FunctionCallResult;
import com.wmsay.gpt4_lll.fc.events.ProgressCallback;
import com.wmsay.gpt4_lll.fc.llm.LlmCaller;
import com.wmsay.gpt4_lll.fc.runtime.AgentRuntime;
import com.wmsay.gpt4_lll.fc.runtime.KnowledgeBase;
import com.wmsay.gpt4_lll.fc.state.AgentSession;
import com.wmsay.gpt4_lll.fc.state.ExecutionContext;
import com.wmsay.gpt4_lll.fc.state.FileSnapshot;
import com.wmsay.gpt4_lll.fc.state.SubAgentProgressProvider;
import com.wmsay.gpt4_lll.fc.state.SubAgentResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 子 Agent 工厂 — 负责根据 SkillDefinition 创建配置完整的子 Agent 实例并执行任务。
 * <p>
 * 流程：
 * <ol>
 *   <li>检查 delegationDepth 是否超限</li>
 *   <li>调用 ContextDistiller 蒸馏上下文</li>
 *   <li>构建 AgentDefinition（systemPrompt + distilledContext + additionalNotes + fileChanges）</li>
 *   <li>注册临时 AgentDefinition 到 AgentRuntime</li>
 *   <li>通过 AgentRuntime.createSession() 创建独立会话</li>
 *   <li>设置 delegationDepth = parentDepth + 1</li>
 *   <li>注入 KnowledgeBase 到子 Agent 的 ContextManager</li>
 *   <li>通知 SubAgentProgressProvider onSubAgentStarting</li>
 *   <li>调用 runtime.send()（带超时控制）</li>
 *   <li>通知 SubAgentProgressProvider onSubAgentCompleted</li>
 *   <li>销毁子 Agent 会话</li>
 *   <li>注销临时 AgentDefinition</li>
 *   <li>返回 SubAgentResult</li>
 * </ol>
 * <p>
 * fc 层纯组件，不依赖任何 IntelliJ Platform API。
 */
public class SubAgentFactory {

    private static final Logger LOG = Logger.getLogger(SubAgentFactory.class.getName());
    private static final String SESSION_ID_PREFIX = "skill-sub-agent-";

    private final AgentRuntime runtime;
    private final ContextDistiller contextDistiller;

    public SubAgentFactory(AgentRuntime runtime, ContextDistiller contextDistiller) {
        this.runtime = runtime;
        this.contextDistiller = contextDistiller;
    }

    /**
     * 创建子 Agent 并执行任务。
     *
     * @param skill            匹配到的 SkillDefinition
     * @param userInput        用户输入文本
     * @param mainSession      主 Agent 的会话
     * @param llmCaller        LLM 调用器
     * @param callback         进度回调（可为 null）
     * @param progressProvider 子 Agent 进度提供者（可为 null）
     * @return SubAgentResult 执行结果
     */
    public SubAgentResult createAndExecute(
            SkillDefinition skill,
            String userInput,
            AgentSession mainSession,
            LlmCaller llmCaller,
            ProgressCallback callback,
            SubAgentProgressProvider progressProvider) {
        return createAndExecute(skill, userInput, mainSession, llmCaller, callback, progressProvider, null);
    }

    /**
     * 创建子 Agent 并执行任务（带 stepsContext，用于 PlanAndExecute 步骤级执行）。
     * <p>
     * 当 stepsContext 非 null 时，ContextDistiller 会将已完成步骤的上下文纳入蒸馏输入，
     * 使子 Agent 能了解前序步骤的执行结果（需求 8.8）。
     *
     * @param skill            匹配到的 SkillDefinition
     * @param userInput        用户输入文本（步骤描述）
     * @param mainSession      主 Agent 的会话
     * @param llmCaller        LLM 调用器
     * @param callback         进度回调（可为 null）
     * @param progressProvider 子 Agent 进度提供者（可为 null）
     * @param stepsContext     已完成步骤的上下文（PlanAndExecute 场景），可为 null
     * @return SubAgentResult 执行结果
     */
    public SubAgentResult createAndExecute(
            SkillDefinition skill,
            String userInput,
            AgentSession mainSession,
            LlmCaller llmCaller,
            ProgressCallback callback,
            SubAgentProgressProvider progressProvider,
            String stepsContext) {

        long startTime = System.currentTimeMillis();
        String skillName = skill.getName();
        AgentRuntimeConfig config = runtime.getConfig();

        // 1. 检查 delegationDepth 是否超限
        int parentDepth = mainSession.getDelegationDepth();
        int newDepth = parentDepth + 1;
        if (newDepth > config.getMaxDelegationDepth()) {
            LOG.warning("[SubAgentFactory] Delegation depth exceeded: " + newDepth
                    + " > max " + config.getMaxDelegationDepth()
                    + ", skill=" + skillName);
            return SubAgentResult.failure(
                    "Delegation depth exceeded (depth=" + newDepth
                            + ", max=" + config.getMaxDelegationDepth() + ")",
                    skillName,
                    System.currentTimeMillis() - startTime);
        }

        // 2. ContextDistiller 蒸馏上下文
        String distilledContext = "";
        try {
            distilledContext = contextDistiller.distill(
                    mainSession.getMemory(),
                    skill.getPurpose(),
                    skill.getTrigger(),
                    stepsContext, // PlanAndExecute 场景下传入已完成步骤的上下文（需求 8.8）
                    llmCaller,
                    null, // modelName — 使用默认
                    config.getSubAgentContextFallbackMessageCount());
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "[SubAgentFactory] ContextDistiller failed, proceeding with empty context: " + e.getMessage(), e);
        }

        // 3. 构建 systemPrompt
        String systemPrompt = buildSystemPrompt(skill, distilledContext, mainSession);

        // 4. 构建工具列表
        List<String> toolNames = resolveToolNames(skill, mainSession);

        // 5. 构建 AgentDefinition
        String agentId = SESSION_ID_PREFIX + UUID.randomUUID();
        AgentDefinition agentDef = AgentDefinition.builder()
                .id(agentId)
                .name("sub-agent-" + skillName)
                .systemPrompt(systemPrompt)
                .availableToolNames(toolNames)
                .build();

        // 6. 注册临时 AgentDefinition
        String subSessionId = null;
        try {
            runtime.register(agentDef);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "[SubAgentFactory] Failed to register temp agent: " + e.getMessage(), e);
            return SubAgentResult.failure(
                    "Failed to register sub-agent: " + e.getMessage(),
                    skillName,
                    System.currentTimeMillis() - startTime);
        }

        try {
            // 7. 创建独立会话
            ExecutionContext execContext = ExecutionContext.fromToolContext(mainSession.getToolContext());
            AgentSession subSession = runtime.createSession(agentId, execContext);
            subSessionId = subSession.getSessionId();

            // 8. 设置 delegationDepth
            subSession.setDelegationDepth(newDepth);

            // 9. 注入 KnowledgeBase
            KnowledgeBase kb = runtime.getKnowledgeBase();
            if (kb != null) {
                subSession.getContextManager().setKnowledgeBase(kb);
            }

            LOG.info("[SubAgentFactory] Created sub-agent: skill=" + skillName
                    + ", sessionId=" + subSessionId
                    + ", delegationDepth=" + newDepth);

            // 10. 通知 progressProvider
            if (progressProvider != null) {
                progressProvider.notifyStarting(skillName, skill.isGenerated());
            }

            // 11. 替换 promptTemplate 中的 {{user_input}} 并执行
            String effectiveMessage = skill.getPromptTemplate().replace("{{user_input}}", userInput);

            // 12. 带超时控制的 runtime.send()
            FunctionCallResult fcResult = executeWithTimeout(
                    subSessionId, effectiveMessage, llmCaller, callback,
                    config.getSubAgentTimeoutSeconds());

            long durationMs = System.currentTimeMillis() - startTime;

            // 13. 构建 SubAgentResult
            SubAgentResult result;
            if (fcResult != null && fcResult.isSuccess()) {
                result = SubAgentResult.success(
                        fcResult.getContent() != null ? fcResult.getContent() : "",
                        skillName, durationMs);
            } else {
                String errorContent = fcResult != null && fcResult.getContent() != null
                        ? fcResult.getContent() : "Sub-agent execution failed";
                result = SubAgentResult.failure(errorContent, skillName, durationMs);
            }

            // 14. 通知完成
            if (progressProvider != null) {
                progressProvider.notifyCompleted(result);
            }

            LOG.info("[SubAgentFactory] Sub-agent completed: skill=" + skillName
                    + ", success=" + result.isSuccess()
                    + ", durationMs=" + durationMs);

            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            LOG.log(Level.WARNING,
                    "[SubAgentFactory] Sub-agent execution failed: skill=" + skillName
                            + ", error=" + e.getMessage(), e);

            SubAgentResult failResult = SubAgentResult.failure(
                    "Sub-agent execution error: " + e.getMessage(),
                    skillName, durationMs);

            if (progressProvider != null) {
                progressProvider.notifyFailed(e.getMessage());
            }

            return failResult;
        } finally {
            // 15. 销毁会话 & 注销临时 agent
            if (subSessionId != null) {
                try {
                    runtime.destroySession(subSessionId);
                } catch (Exception e) {
                    LOG.log(Level.WARNING,
                            "[SubAgentFactory] Failed to destroy sub-agent session: " + e.getMessage(), e);
                }
            }
            try {
                runtime.unregister(agentId);
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "[SubAgentFactory] Failed to unregister temp agent: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 构建子 Agent 的 systemPrompt。
     * 包含: SkillDefinition.systemPrompt + distilledContext + additionalNotes + 已变更文件路径列表。
     */
    String buildSystemPrompt(SkillDefinition skill, String distilledContext, AgentSession mainSession) {
        StringBuilder sb = new StringBuilder();

        // 基础 systemPrompt
        sb.append(skill.getSystemPrompt());

        // 蒸馏后的上下文
        if (distilledContext != null && !distilledContext.isBlank()) {
            sb.append("\n\n## Context\n").append(distilledContext);
        }

        // additionalNotes
        if (skill.getAdditionalNotes() != null && !skill.getAdditionalNotes().isBlank()) {
            sb.append("\n\n## Additional Notes\n").append(skill.getAdditionalNotes());
        }

        // 已变更文件路径列表
        List<FileSnapshot> changes = mainSession.getFileChangeTracker().getChanges();
        if (!changes.isEmpty()) {
            List<String> changedPaths = changes.stream()
                    .map(FileSnapshot::getFilePath)
                    .distinct()
                    .collect(Collectors.toList());
            sb.append("\n\n## Changed Files\n");
            for (String path : changedPaths) {
                sb.append("- ").append(path).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 解析子 Agent 的工具集。
     * tools 非空时仅配置声明的工具，为空时继承主 Agent 全量工具集。
     */
    List<String> resolveToolNames(SkillDefinition skill, AgentSession mainSession) {
        List<String> skillTools = skill.getTools();
        if (skillTools != null && !skillTools.isEmpty()) {
            return skillTools;
        }
        // 继承主 Agent 的全量工具集
        return mainSession.getDefinition().getAvailableToolNames();
    }

    /**
     * 带超时控制的 runtime.send() 执行。
     * 使用 ExecutorService + Future.get(timeout) 实现超时控制。
     */
    private FunctionCallResult executeWithTimeout(
            String sessionId, String message,
            LlmCaller llmCaller, ProgressCallback callback,
            int timeoutSeconds) {

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sub-agent-exec-" + sessionId);
            t.setDaemon(true);
            return t;
        });

        try {
            Future<FunctionCallResult> future = executor.submit(
                    () -> runtime.send(sessionId, message, llmCaller, callback));

            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.warning("[SubAgentFactory] Sub-agent execution timed out after "
                    + timeoutSeconds + "s, sessionId=" + sessionId);
            return FunctionCallResult.error(
                    "Sub-agent execution timed out after " + timeoutSeconds + " seconds",
                    sessionId);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "[SubAgentFactory] Sub-agent execution error: " + e.getMessage(), e);
            return FunctionCallResult.error(
                    "Sub-agent execution error: " + e.getMessage(),
                    sessionId);
        } finally {
            executor.shutdownNow();
        }
    }
}
