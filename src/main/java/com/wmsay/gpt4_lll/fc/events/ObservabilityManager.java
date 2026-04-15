package com.wmsay.gpt4_lll.fc.events;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.wmsay.gpt4_lll.fc.core.FunctionCallConfig.LogLevel;
import com.wmsay.gpt4_lll.fc.model.PerformanceMetrics;
import com.wmsay.gpt4_lll.fc.model.SessionTrace;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ToolCallTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 可观测性管理器。
 * 提供日志、追踪、监控功能，支持调试和性能优化。
 * 管理活跃会话的生命周期，收集性能指标，输出结构化 JSON 日志。
 *
 * <p>核心功能：
 * <ul>
 *   <li>会话和工具调用的生命周期追踪（start/end）</li>
 *   <li>父子调用关系追踪（嵌套工具调用）</li>
 *   <li>结构化 JSON 日志输出，支持日志级别过滤</li>
 *   <li>性能指标收集（通过 {@link MetricsCollector}）</li>
 *   <li>调用链导出（JSON、Mermaid、Text 格式）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * ObservabilityManager obs = new ObservabilityManager();
 * obs.setLogLevel(LogLevel.DEBUG);
 *
 * String sessionId = ObservabilityManager.generateSessionId();
 * obs.startSession(sessionId);
 *
 * String callId = ObservabilityManager.generateCallId();
 * obs.startToolCall(sessionId, callId, toolCall);
 * // ... 执行工具 ...
 * obs.endToolCall(sessionId, callId);
 *
 * // 导出调用链
 * String trace = obs.exportTrace(sessionId, TraceFormat.JSON);
 *
 * obs.endSession(sessionId);
 * PerformanceMetrics metrics = obs.getMetrics();
 * }</pre>
 *
 * @see MetricsCollector
 * @see SessionTrace
 * @see PerformanceMetrics
 */
public class ObservabilityManager {

    /**
     * 追踪数据导出格式。
     */
    public enum TraceFormat {
        JSON,
        MERMAID,
        TEXT
    }

    private static final Logger LOG = Logger.getLogger(ObservabilityManager.class.getName());

    /**
     * 生成唯一的会话 ID (UUID)。
     *
     * @return 格式为 "session-{uuid}" 的唯一会话 ID
     */
    public static String generateSessionId() {
        return "session-" + UUID.randomUUID();
    }

    /**
     * 生成唯一的调用 ID (UUID)。
     *
     * @return 格式为 "call-{uuid}" 的唯一调用 ID
     */
    public static String generateCallId() {
        return "call-" + UUID.randomUUID();
    }

    private final ConcurrentHashMap<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();
    private final MetricsCollector metrics = new MetricsCollector();
    private volatile LogLevel logLevel = LogLevel.INFO;

    /**
     * 设置日志级别过滤。
     *
     * @param logLevel 日志级别
     */
    public void setLogLevel(LogLevel logLevel) {
        if (logLevel == null) {
            throw new IllegalArgumentException("logLevel must not be null");
        }
        this.logLevel = logLevel;
    }

    /**
     * 获取当前日志级别。
     *
     * @return 当前日志级别
     */
    public LogLevel getLogLevel() {
        return logLevel;
    }

    /**
     * 开始会话追踪。
     *
     * @param sessionId 会话 ID
     */
    public void startSession(String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        ActiveSession session = new ActiveSession(sessionId, System.currentTimeMillis());
        activeSessions.put(sessionId, session);

        logInfo("session_start", Map.of(
                "sessionId", sessionId,
                "timestamp", session.startTime
        ));
    }

    /**
     * 开始工具调用追踪。
     *
     * @param sessionId 会话 ID
     * @param callId    调用 ID
     * @param toolCall  工具调用
     */
    public void startToolCall(String sessionId, String callId, ToolCall toolCall) {
        startToolCall(sessionId, callId, toolCall, null);
    }

    /**
     * 开始工具调用追踪（带父调用 ID，用于嵌套调用链追踪）。
     *
     * @param sessionId    会话 ID
     * @param callId       调用 ID
     * @param toolCall     工具调用
     * @param parentCallId 父调用 ID（可为 null）
     */
    public void startToolCall(String sessionId, String callId, ToolCall toolCall, String parentCallId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (callId == null) {
            throw new IllegalArgumentException("callId must not be null");
        }
        if (toolCall == null) {
            throw new IllegalArgumentException("toolCall must not be null");
        }
        ActiveSession session = activeSessions.get(sessionId);
        if (session != null) {
            ActiveToolCall activeCall = new ActiveToolCall(
                    callId, toolCall.getToolName(), toolCall.getParameters(), System.currentTimeMillis(), parentCallId);
            session.toolCalls.put(callId, activeCall);
        }

        logInfo("tool_call_start", Map.of(
                "sessionId", sessionId,
                "callId", callId,
                "toolName", toolCall.getToolName(),
                "parentCallId", parentCallId != null ? parentCallId : ""
        ));
    }

    /**
     * 结束工具调用追踪。
     *
     * @param sessionId 会话 ID
     * @param callId    调用 ID
     */
    public void endToolCall(String sessionId, String callId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (callId == null) {
            throw new IllegalArgumentException("callId must not be null");
        }
        ActiveSession session = activeSessions.get(sessionId);
        if (session != null) {
            ActiveToolCall activeCall = session.toolCalls.get(callId);
            if (activeCall != null) {
                activeCall.endTime = System.currentTimeMillis();
                long duration = activeCall.endTime - activeCall.startTime;

                metrics.recordToolCallDuration(activeCall.toolName, duration);

                logInfo("tool_call_end", Map.of(
                        "sessionId", sessionId,
                        "callId", callId,
                        "toolName", activeCall.toolName,
                        "durationMs", duration
                ));
                return;
            }
        }

        logInfo("tool_call_end", Map.of(
                "sessionId", sessionId,
                "callId", callId
        ));
    }

    /**
     * 记录错误。
     *
     * @param sessionId 会话 ID
     * @param error     异常
     */
    public void recordError(String sessionId, Throwable error) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (error == null) {
            throw new IllegalArgumentException("error must not be null");
        }
        ActiveSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.errors.add(error);
        }

        metrics.incrementErrorCount(error.getClass().getSimpleName());

        logError("error_recorded", Map.of(
                "sessionId", sessionId,
                "errorType", error.getClass().getSimpleName(),
                "errorMessage", error.getMessage() != null ? error.getMessage() : ""
        ), error);
    }

    /**
     * 结束会话追踪。
     *
     * @param sessionId 会话 ID
     */
    public void endSession(String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        ActiveSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.endTime = System.currentTimeMillis();
            long duration = session.endTime - session.startTime;

            metrics.recordSessionDuration(duration);
            metrics.recordToolCallCount(session.toolCalls.size());

            logInfo("session_end", Map.of(
                    "sessionId", sessionId,
                    "durationMs", duration,
                    "toolCallCount", session.toolCalls.size(),
                    "errorCount", session.errors.size()
            ));
        } else {
            logWarn("session_end_unknown", Map.of(
                    "sessionId", sessionId,
                    "message", "Session not found"
            ));
        }
    }

    /**
     * 获取性能指标。
     *
     * @return 当前时刻的性能指标快照
     */
    public PerformanceMetrics getMetrics() {
        return metrics.getMetrics();
    }

    /**
     * 获取 MetricsCollector 实例（用于直接访问指标）。
     *
     * @return 内部使用的 MetricsCollector 实例
     */
    public MetricsCollector getMetricsCollector() {
        return metrics;
    }

    /**
     * 检查会话是否活跃。
     *
     * @param sessionId 会话 ID
     * @return 是否活跃
     */
    public boolean isSessionActive(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    /**
     * 获取活跃会话数量。
     *
     * @return 当前活跃会话数
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    // ---- 调用链追踪导出 ----

    /**
     * 导出指定会话的调用链追踪数据。
     *
     * @param sessionId 会话 ID
     * @param format    导出格式
     * @return 格式化的追踪数据，会话不存在时返回 null
     */
    public String exportTrace(String sessionId, TraceFormat format) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        ActiveSession session = activeSessions.get(sessionId);
        if (session == null) {
            return null;
        }
        SessionTrace trace = buildSessionTrace(session);
        return switch (format) {
            case JSON -> exportAsJson(trace);
            case MERMAID -> exportAsMermaid(trace);
            case TEXT -> exportAsText(trace);
        };
    }

    /**
     * 将内部 ActiveSession 转换为不可变的 SessionTrace 模型。
     */
    SessionTrace buildSessionTrace(ActiveSession session) {
        List<ToolCallTrace> traces = session.toolCalls.values().stream()
                .map(ac -> ToolCallTrace.builder()
                        .callId(ac.callId)
                        .toolName(ac.toolName)
                        .parameters(ac.parameters)
                        .startTime(ac.startTime)
                        .endTime(ac.endTime > 0 ? ac.endTime : 0)
                        .parentCallId(ac.parentCallId)
                        .build())
                .collect(Collectors.toList());

        return SessionTrace.builder()
                .sessionId(session.sessionId)
                .startTime(session.startTime)
                .endTime(session.endTime > 0 ? session.endTime : 0)
                .toolCalls(traces)
                .errors(new ArrayList<>(session.errors))
                .build();
    }

    private String exportAsJson(SessionTrace trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"sessionId\":\"").append(escapeJson(trace.getSessionId())).append("\"");
        sb.append(",\"startTime\":").append(trace.getStartTime());
        sb.append(",\"endTime\":").append(trace.getEndTime());
        sb.append(",\"toolCalls\":[");

        List<ToolCallTrace> calls = trace.getToolCalls();
        for (int i = 0; i < calls.size(); i++) {
            if (i > 0) sb.append(",");
            ToolCallTrace tc = calls.get(i);
            sb.append("{\"callId\":\"").append(escapeJson(tc.getCallId())).append("\"");
            sb.append(",\"toolName\":\"").append(escapeJson(tc.getToolName())).append("\"");
            sb.append(",\"startTime\":").append(tc.getStartTime());
            sb.append(",\"endTime\":").append(tc.getEndTime());
            if (tc.getParentCallId() != null) {
                sb.append(",\"parentCallId\":\"").append(escapeJson(tc.getParentCallId())).append("\"");
            }
            sb.append(",\"parameters\":{");
            int j = 0;
            for (Map.Entry<String, Object> entry : tc.getParameters().entrySet()) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                Object val = entry.getValue();
                if (val instanceof Number) {
                    sb.append(val);
                } else {
                    sb.append("\"").append(escapeJson(String.valueOf(val))).append("\"");
                }
                j++;
            }
            sb.append("}}");
        }

        sb.append("],\"errors\":[");
        List<Throwable> errors = trace.getErrors();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) sb.append(",");
            Throwable err = errors.get(i);
            sb.append("{\"type\":\"").append(escapeJson(err.getClass().getSimpleName())).append("\"");
            sb.append(",\"message\":\"").append(escapeJson(err.getMessage() != null ? err.getMessage() : "")).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String exportAsMermaid(SessionTrace trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("sequenceDiagram\n");
        sb.append("    participant Client\n");

        // Collect unique tool names as participants
        List<String> toolNames = trace.getToolCalls().stream()
                .map(ToolCallTrace::getToolName)
                .distinct()
                .collect(Collectors.toList());
        for (String name : toolNames) {
            sb.append("    participant ").append(name).append("\n");
        }

        for (ToolCallTrace tc : trace.getToolCalls()) {
            String source = tc.getParentCallId() != null ? findToolName(trace, tc.getParentCallId()) : "Client";
            sb.append("    ").append(source).append("->>").append(tc.getToolName())
                    .append(": ").append(tc.getCallId()).append("\n");
            if (tc.getEndTime() > 0) {
                long duration = tc.getEndTime() - tc.getStartTime();
                sb.append("    ").append(tc.getToolName()).append("-->>").append(source)
                        .append(": done (").append(duration).append("ms)\n");
            }
        }

        return sb.toString();
    }

    private String findToolName(SessionTrace trace, String callId) {
        for (ToolCallTrace tc : trace.getToolCalls()) {
            if (tc.getCallId().equals(callId)) {
                return tc.getToolName();
            }
        }
        return "Client";
    }

    private String exportAsText(SessionTrace trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("Session: ").append(trace.getSessionId()).append("\n");
        sb.append("Start: ").append(trace.getStartTime()).append("\n");
        if (trace.getEndTime() > 0) {
            sb.append("End: ").append(trace.getEndTime()).append("\n");
            sb.append("Duration: ").append(trace.getEndTime() - trace.getStartTime()).append("ms\n");
        }
        sb.append("Tool Calls (").append(trace.getToolCallCount()).append("):\n");

        for (ToolCallTrace tc : trace.getToolCalls()) {
            sb.append("  [").append(tc.getCallId()).append("] ").append(tc.getToolName());
            if (tc.getParentCallId() != null) {
                sb.append(" (parent: ").append(tc.getParentCallId()).append(")");
            }
            sb.append("\n");
            sb.append("    Start: ").append(tc.getStartTime());
            if (tc.getEndTime() > 0) {
                sb.append(" | End: ").append(tc.getEndTime());
                sb.append(" | Duration: ").append(tc.getEndTime() - tc.getStartTime()).append("ms");
            }
            sb.append("\n");
            if (!tc.getParameters().isEmpty()) {
                sb.append("    Params: ").append(tc.getParameters()).append("\n");
            }
        }

        if (!trace.getErrors().isEmpty()) {
            sb.append("Errors (").append(trace.getErrors().size()).append("):\n");
            for (Throwable err : trace.getErrors()) {
                sb.append("  - ").append(err.getClass().getSimpleName())
                        .append(": ").append(err.getMessage() != null ? err.getMessage() : "").append("\n");
            }
        }

        return sb.toString();
    }

    // ---- 结构化 JSON 日志输出 ----

    private void logDebug(String event, Map<String, Object> fields) {
        if (isLevelEnabled(LogLevel.DEBUG)) {
            LOG.fine(formatJsonLog(event, "DEBUG", fields));
        }
    }

    private void logInfo(String event, Map<String, Object> fields) {
        if (isLevelEnabled(LogLevel.INFO)) {
            LOG.info(formatJsonLog(event, "INFO", fields));
        }
    }

    private void logWarn(String event, Map<String, Object> fields) {
        if (isLevelEnabled(LogLevel.WARN)) {
            LOG.log(Level.WARNING, formatJsonLog(event, "WARN", fields));
        }
    }

    private void logError(String event, Map<String, Object> fields, Throwable error) {
        if (isLevelEnabled(LogLevel.ERROR)) {
            LOG.log(Level.SEVERE, formatJsonLog(event, "ERROR", fields) + " | " + error.getMessage());
        }
    }

    /**
     * 判断指定日志级别是否启用。
     */
    boolean isLevelEnabled(LogLevel level) {
        return level.ordinal() >= logLevel.ordinal();
    }

    /**
     * 格式化结构化 JSON 日志。
     * 输出格式: {"event":"...","level":"...","timestamp":...,"field1":"value1",...}
     */
    static String formatJsonLog(String event, String level, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"event\":\"").append(escapeJson(event)).append("\"");
        sb.append(",\"level\":\"").append(escapeJson(level)).append("\"");
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            sb.append(",\"").append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ---- 内部可变状态类 ----

    /**
     * 活跃会话的可变状态（内部使用）。
     */
    static class ActiveSession {
        final String sessionId;
        final long startTime;
        volatile long endTime;
        final ConcurrentHashMap<String, ActiveToolCall> toolCalls = new ConcurrentHashMap<>();
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ActiveSession(String sessionId, long startTime) {
            this.sessionId = sessionId;
            this.startTime = startTime;
        }
    }

    /**
     * 活跃工具调用的可变状态（内部使用）。
     */
    static class ActiveToolCall {
        final String callId;
        final String toolName;
        final Map<String, Object> parameters;
        final long startTime;
        volatile long endTime;
        final String parentCallId;

        ActiveToolCall(String callId, String toolName, Map<String, Object> parameters, long startTime) {
            this(callId, toolName, parameters, startTime, null);
        }

        ActiveToolCall(String callId, String toolName, Map<String, Object> parameters, long startTime, String parentCallId) {
            this.callId = callId;
            this.toolName = toolName;
            this.parameters = parameters;
            this.startTime = startTime;
            this.parentCallId = parentCallId;
        }
    }
}
