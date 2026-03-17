package com.wmsay.gpt4_lll.fc.observability;

import com.wmsay.gpt4_lll.fc.model.FunctionCallConfig.LogLevel;
import com.wmsay.gpt4_lll.fc.model.PerformanceMetrics;
import com.wmsay.gpt4_lll.fc.model.SessionTrace;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.observability.ObservabilityManager.TraceFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试: ObservabilityManager 和 MetricsCollector
 * Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5, 12.6
 */
class ObservabilityManagerTest {

    private ObservabilityManager manager;

    @BeforeEach
    void setUp() {
        manager = new ObservabilityManager();
    }

    // ---- Session lifecycle tests ----

    @Test
    void startSession_shouldTrackActiveSession() {
        manager.startSession("s1");
        assertTrue(manager.isSessionActive("s1"));
        assertEquals(1, manager.getActiveSessionCount());
    }

    @Test
    void endSession_shouldRemoveActiveSession() {
        manager.startSession("s1");
        manager.endSession("s1");
        assertFalse(manager.isSessionActive("s1"));
        assertEquals(0, manager.getActiveSessionCount());
    }

    @Test
    void endSession_unknownSession_shouldNotThrow() {
        assertDoesNotThrow(() -> manager.endSession("nonexistent"));
    }

    @Test
    void multipleSessions_shouldTrackIndependently() {
        manager.startSession("s1");
        manager.startSession("s2");
        assertEquals(2, manager.getActiveSessionCount());

        manager.endSession("s1");
        assertEquals(1, manager.getActiveSessionCount());
        assertTrue(manager.isSessionActive("s2"));
    }

    // ---- Tool call lifecycle tests ----

    @Test
    void startAndEndToolCall_shouldRecordDuration() throws InterruptedException {
        manager.startSession("s1");

        ToolCall toolCall = ToolCall.builder()
                .callId("c1")
                .toolName("read_file")
                .parameters(Map.of("path", "/test"))
                .build();

        manager.startToolCall("s1", "c1", toolCall);
        Thread.sleep(10); // small delay to ensure measurable duration
        manager.endToolCall("s1", "c1");

        PerformanceMetrics metrics = manager.getMetrics();
        assertEquals(1, metrics.getTotalToolCalls());
        assertTrue(metrics.getAverageToolCallDuration() >= 0);
    }

    @Test
    void endToolCall_unknownCall_shouldNotThrow() {
        manager.startSession("s1");
        assertDoesNotThrow(() -> manager.endToolCall("s1", "unknown_call"));
    }

    @Test
    void endToolCall_unknownSession_shouldNotThrow() {
        assertDoesNotThrow(() -> manager.endToolCall("unknown_session", "c1"));
    }

    // ---- Error recording tests ----

    @Test
    void recordError_shouldIncrementErrorMetrics() {
        manager.startSession("s1");
        manager.recordError("s1", new RuntimeException("test error"));

        PerformanceMetrics metrics = manager.getMetrics();
        assertTrue(metrics.getErrorRate() > 0);
        assertTrue(metrics.getErrorsByType().containsKey("RuntimeException"));
        assertEquals(1L, metrics.getErrorsByType().get("RuntimeException"));
    }

    @Test
    void recordError_multipleTypes_shouldTrackSeparately() {
        manager.startSession("s1");
        manager.recordError("s1", new RuntimeException("err1"));
        manager.recordError("s1", new IllegalArgumentException("err2"));
        manager.recordError("s1", new RuntimeException("err3"));

        Map<String, Long> errorsByType = manager.getMetrics().getErrorsByType();
        assertEquals(2L, errorsByType.get("RuntimeException"));
        assertEquals(1L, errorsByType.get("IllegalArgumentException"));
    }

    @Test
    void recordError_unknownSession_shouldStillTrackMetrics() {
        assertDoesNotThrow(() ->
                manager.recordError("unknown", new RuntimeException("err")));

        // Error metrics should still be recorded even if session is unknown
        assertTrue(manager.getMetrics().getErrorsByType().containsKey("RuntimeException"));
    }

    // ---- Metrics tests ----

    @Test
    void getMetrics_noData_shouldReturnDefaults() {
        PerformanceMetrics metrics = manager.getMetrics();
        assertEquals(0, metrics.getTotalToolCalls());
        assertEquals(0.0, metrics.getAverageSessionDuration());
        assertEquals(0.0, metrics.getAverageToolCallDuration());
        assertEquals(1.0, metrics.getSuccessRate()); // no calls = 100% success
        assertEquals(0.0, metrics.getErrorRate());
        assertTrue(metrics.getErrorsByType().isEmpty());
    }

    @Test
    void getMetrics_afterSessionEnd_shouldRecordSessionDuration() throws InterruptedException {
        manager.startSession("s1");
        Thread.sleep(10);
        manager.endSession("s1");

        PerformanceMetrics metrics = manager.getMetrics();
        assertTrue(metrics.getAverageSessionDuration() >= 10);
    }

    // ---- Log level filtering tests ----

    @Test
    void defaultLogLevel_shouldBeInfo() {
        assertEquals(LogLevel.INFO, manager.getLogLevel());
    }

    @Test
    void setLogLevel_shouldUpdateLevel() {
        manager.setLogLevel(LogLevel.DEBUG);
        assertEquals(LogLevel.DEBUG, manager.getLogLevel());
    }

    @Test
    void logLevelFiltering_debugEnabled_shouldAllowAllLevels() {
        manager.setLogLevel(LogLevel.DEBUG);
        assertTrue(manager.isLevelEnabled(LogLevel.DEBUG));
        assertTrue(manager.isLevelEnabled(LogLevel.INFO));
        assertTrue(manager.isLevelEnabled(LogLevel.WARN));
        assertTrue(manager.isLevelEnabled(LogLevel.ERROR));
    }

    @Test
    void logLevelFiltering_errorOnly_shouldFilterLowerLevels() {
        manager.setLogLevel(LogLevel.ERROR);
        assertFalse(manager.isLevelEnabled(LogLevel.DEBUG));
        assertFalse(manager.isLevelEnabled(LogLevel.INFO));
        assertFalse(manager.isLevelEnabled(LogLevel.WARN));
        assertTrue(manager.isLevelEnabled(LogLevel.ERROR));
    }

    @Test
    void logLevelFiltering_warnLevel_shouldFilterDebugAndInfo() {
        manager.setLogLevel(LogLevel.WARN);
        assertFalse(manager.isLevelEnabled(LogLevel.DEBUG));
        assertFalse(manager.isLevelEnabled(LogLevel.INFO));
        assertTrue(manager.isLevelEnabled(LogLevel.WARN));
        assertTrue(manager.isLevelEnabled(LogLevel.ERROR));
    }

    // ---- Structured JSON log format tests ----

    @Test
    void formatJsonLog_shouldProduceValidJson() {
        String json = ObservabilityManager.formatJsonLog("test_event", "INFO",
                Map.of("key1", "value1", "key2", 42));

        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"event\":\"test_event\""));
        assertTrue(json.contains("\"level\":\"INFO\""));
        assertTrue(json.contains("\"timestamp\":"));
        assertTrue(json.contains("\"key2\":42"));
    }

    @Test
    void formatJsonLog_shouldEscapeSpecialCharacters() {
        String json = ObservabilityManager.formatJsonLog("test", "INFO",
                Map.of("msg", "line1\nline2\ttab\"quote"));

        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\t"));
        assertTrue(json.contains("\\\"quote"));
    }

    @Test
    void formatJsonLog_emptyFields_shouldProduceMinimalJson() {
        String json = ObservabilityManager.formatJsonLog("evt", "DEBUG", Map.of());

        assertTrue(json.contains("\"event\":\"evt\""));
        assertTrue(json.contains("\"level\":\"DEBUG\""));
    }

    // ---- Full lifecycle integration test ----

    @Test
    void fullLifecycle_shouldTrackAllMetrics() throws InterruptedException {
        manager.startSession("s1");

        ToolCall call1 = ToolCall.builder()
                .callId("c1").toolName("read_file")
                .parameters(Map.of("path", "/a")).build();
        ToolCall call2 = ToolCall.builder()
                .callId("c2").toolName("write_file")
                .parameters(Map.of("path", "/b")).build();

        manager.startToolCall("s1", "c1", call1);
        Thread.sleep(5);
        manager.endToolCall("s1", "c1");

        manager.startToolCall("s1", "c2", call2);
        manager.recordError("s1", new RuntimeException("write failed"));
        manager.endToolCall("s1", "c2");

        manager.endSession("s1");

        PerformanceMetrics metrics = manager.getMetrics();
        assertEquals(2, metrics.getTotalToolCalls());
        assertTrue(metrics.getAverageSessionDuration() > 0);
        assertTrue(metrics.getAverageToolCallDuration() >= 0);
        assertFalse(metrics.getErrorsByType().isEmpty());
    }

    // ---- ID generation tests (Req 15.1, 15.2) ----

    @Test
    void generateSessionId_shouldReturnUniqueIds() {
        String id1 = ObservabilityManager.generateSessionId();
        String id2 = ObservabilityManager.generateSessionId();
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("session-"));
    }

    @Test
    void generateCallId_shouldReturnUniqueIds() {
        String id1 = ObservabilityManager.generateCallId();
        String id2 = ObservabilityManager.generateCallId();
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("call-"));
    }

    // ---- Parent-child call tracking tests (Req 15.3) ----

    @Test
    void startToolCall_withParentCallId_shouldTrackRelationship() {
        manager.startSession("s1");

        ToolCall parent = ToolCall.builder()
                .callId("c1").toolName("orchestrate")
                .parameters(Map.of()).build();
        ToolCall child = ToolCall.builder()
                .callId("c2").toolName("read_file")
                .parameters(Map.of("path", "/x")).build();

        manager.startToolCall("s1", "c1", parent);
        manager.startToolCall("s1", "c2", child, "c1");
        manager.endToolCall("s1", "c2");
        manager.endToolCall("s1", "c1");

        // Verify via trace export
        String json = manager.exportTrace("s1", TraceFormat.JSON);
        assertNotNull(json);
        assertTrue(json.contains("\"parentCallId\":\"c1\""));
    }

    @Test
    void startToolCall_withNullParentCallId_shouldWorkLikeOriginal() {
        manager.startSession("s1");

        ToolCall tc = ToolCall.builder()
                .callId("c1").toolName("read_file")
                .parameters(Map.of()).build();

        manager.startToolCall("s1", "c1", tc, null);
        manager.endToolCall("s1", "c1");

        String json = manager.exportTrace("s1", TraceFormat.JSON);
        assertNotNull(json);
        assertFalse(json.contains("\"parentCallId\""));
    }

    // ---- Trace export tests (Req 15.4, 15.5, 15.6) ----

    @Test
    void exportTrace_unknownSession_shouldReturnNull() {
        assertNull(manager.exportTrace("nonexistent", TraceFormat.JSON));
    }

    @Test
    void exportTrace_json_shouldContainSessionAndCalls() {
        manager.startSession("s1");

        ToolCall tc = ToolCall.builder()
                .callId("c1").toolName("read_file")
                .parameters(Map.of("path", "/test")).build();
        manager.startToolCall("s1", "c1", tc);
        manager.endToolCall("s1", "c1");

        String json = manager.exportTrace("s1", TraceFormat.JSON);
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"sessionId\":\"s1\""));
        assertTrue(json.contains("\"callId\":\"c1\""));
        assertTrue(json.contains("\"toolName\":\"read_file\""));
        assertTrue(json.contains("\"toolCalls\":["));
        assertTrue(json.contains("\"errors\":["));
    }

    @Test
    void exportTrace_json_withErrors_shouldIncludeErrors() {
        manager.startSession("s1");
        manager.recordError("s1", new RuntimeException("boom"));

        String json = manager.exportTrace("s1", TraceFormat.JSON);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"RuntimeException\""));
        assertTrue(json.contains("\"message\":\"boom\""));
    }

    @Test
    void exportTrace_mermaid_shouldProduceSequenceDiagram() {
        manager.startSession("s1");

        ToolCall tc = ToolCall.builder()
                .callId("c1").toolName("read_file")
                .parameters(Map.of()).build();
        manager.startToolCall("s1", "c1", tc);
        manager.endToolCall("s1", "c1");

        String mermaid = manager.exportTrace("s1", TraceFormat.MERMAID);
        assertNotNull(mermaid);
        assertTrue(mermaid.startsWith("sequenceDiagram"));
        assertTrue(mermaid.contains("participant Client"));
        assertTrue(mermaid.contains("participant read_file"));
        assertTrue(mermaid.contains("Client->>read_file: c1"));
        assertTrue(mermaid.contains("read_file-->>Client: done ("));
    }

    @Test
    void exportTrace_mermaid_withParentChild_shouldShowNestedCalls() {
        manager.startSession("s1");

        ToolCall parent = ToolCall.builder()
                .callId("c1").toolName("orchestrate")
                .parameters(Map.of()).build();
        ToolCall child = ToolCall.builder()
                .callId("c2").toolName("read_file")
                .parameters(Map.of()).build();

        manager.startToolCall("s1", "c1", parent);
        manager.startToolCall("s1", "c2", child, "c1");
        manager.endToolCall("s1", "c2");
        manager.endToolCall("s1", "c1");

        String mermaid = manager.exportTrace("s1", TraceFormat.MERMAID);
        assertNotNull(mermaid);
        // Child call should show orchestrate as source
        assertTrue(mermaid.contains("orchestrate->>read_file: c2"));
    }

    @Test
    void exportTrace_text_shouldBeHumanReadable() {
        manager.startSession("s1");

        ToolCall tc = ToolCall.builder()
                .callId("c1").toolName("read_file")
                .parameters(Map.of("path", "/test")).build();
        manager.startToolCall("s1", "c1", tc);
        manager.endToolCall("s1", "c1");

        String text = manager.exportTrace("s1", TraceFormat.TEXT);
        assertNotNull(text);
        assertTrue(text.contains("Session: s1"));
        assertTrue(text.contains("[c1] read_file"));
        assertTrue(text.contains("Tool Calls (1):"));
        assertTrue(text.contains("Params:"));
    }

    @Test
    void exportTrace_text_withParentChild_shouldShowRelationship() {
        manager.startSession("s1");

        ToolCall parent = ToolCall.builder()
                .callId("c1").toolName("orchestrate")
                .parameters(Map.of()).build();
        ToolCall child = ToolCall.builder()
                .callId("c2").toolName("read_file")
                .parameters(Map.of()).build();

        manager.startToolCall("s1", "c1", parent);
        manager.startToolCall("s1", "c2", child, "c1");
        manager.endToolCall("s1", "c2");
        manager.endToolCall("s1", "c1");

        String text = manager.exportTrace("s1", TraceFormat.TEXT);
        assertNotNull(text);
        assertTrue(text.contains("(parent: c1)"));
    }

    @Test
    void exportTrace_text_withErrors_shouldShowErrors() {
        manager.startSession("s1");
        manager.recordError("s1", new IllegalArgumentException("bad param"));

        String text = manager.exportTrace("s1", TraceFormat.TEXT);
        assertNotNull(text);
        assertTrue(text.contains("Errors (1):"));
        assertTrue(text.contains("IllegalArgumentException: bad param"));
    }

    // ---- buildSessionTrace tests ----

    @Test
    void buildSessionTrace_shouldConvertActiveSessionToModel() {
        manager.startSession("s1");

        ToolCall tc = ToolCall.builder()
                .callId("c1").toolName("read_file")
                .parameters(Map.of("path", "/a")).build();
        manager.startToolCall("s1", "c1", tc);
        manager.endToolCall("s1", "c1");
        manager.recordError("s1", new RuntimeException("err"));

        // Access internal session for direct testing
        String json = manager.exportTrace("s1", TraceFormat.JSON);
        assertNotNull(json);
        // Verify the trace contains all expected data
        assertTrue(json.contains("\"sessionId\":\"s1\""));
        assertTrue(json.contains("\"callId\":\"c1\""));
        assertTrue(json.contains("\"toolName\":\"read_file\""));
        assertTrue(json.contains("RuntimeException"));
    }
}
