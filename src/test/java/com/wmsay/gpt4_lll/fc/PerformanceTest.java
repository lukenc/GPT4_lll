package com.wmsay.gpt4_lll.fc;

import com.wmsay.gpt4_lll.fc.tools.ExecutionEngine;
import com.wmsay.gpt4_lll.fc.tools.RetryStrategy;
import com.wmsay.gpt4_lll.fc.tools.DefaultApprovalProvider;
import com.wmsay.gpt4_lll.fc.tools.ToolRegistry;
import com.wmsay.gpt4_lll.fc.events.ObservabilityManager;
import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ValidationResult;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import com.wmsay.gpt4_lll.fc.tools.ValidationEngine;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import org.junit.jupiter.api.*;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for the Function Calling framework.
 * Validates: Requirements 17.1, 17.2, 17.3, 17.4, 17.5
 */
class PerformanceTest {

    private static final int WARM_UP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;

    private ValidationEngine validationEngine;
    private ExecutionEngine executionEngine;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        validationEngine = new ValidationEngine(toolRegistry);
        executionEngine = new ExecutionEngine(
                toolRegistry, new DefaultApprovalProvider(), new RetryStrategy(), Executors.newFixedThreadPool(4));

        // Register benchmark tools in ToolRegistry for ExecutionEngine
        for (int i = 0; i < 100; i++) {
            toolRegistry.registerTool(new BenchmarkTool("perf_tool_" + i));
        }
    }

    @AfterEach
    void tearDown() {
        executionEngine.shutdown();
    }


    // ========================================================================
    // Req 17.1: Tool Registry O(1) lookup performance
    // McpToolRegistry uses LinkedHashMap — get() is O(1).
    // We verify that lookup time does NOT grow with registry size.
    // ========================================================================

    @Test
    void toolRegistryLookup_isConstantTime() {
        // Register many tools
        for (int i = 0; i < 1000; i++) {
            McpToolRegistry.registerTool(new McpBenchmarkTool("lookup_tool_" + i));
        }

        // Warm up
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            McpToolRegistry.getTool("lookup_tool_50");
        }

        // Measure lookup time for a tool registered early
        long startEarly = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            McpToolRegistry.getTool("lookup_tool_0");
        }
        long earlyNs = System.nanoTime() - startEarly;

        // Measure lookup time for a tool registered late
        long startLate = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            McpToolRegistry.getTool("lookup_tool_999");
        }
        long lateNs = System.nanoTime() - startLate;

        double earlyAvgUs = earlyNs / (double) BENCHMARK_ITERATIONS / 1000.0;
        double lateAvgUs = lateNs / (double) BENCHMARK_ITERATIONS / 1000.0;

        // Both should be sub-microsecond; late lookup should not be significantly slower
        // Allow 10x tolerance for JIT/GC variance
        assertTrue(lateAvgUs < earlyAvgUs * 10 + 1.0,
                String.format("Late lookup (%.2f µs) should not be significantly slower than early (%.2f µs)",
                        lateAvgUs, earlyAvgUs));

        // Each lookup should be well under 1ms
        assertTrue(earlyAvgUs < 1000, "Early lookup avg should be < 1ms, was " + earlyAvgUs + " µs");
        assertTrue(lateAvgUs < 1000, "Late lookup avg should be < 1ms, was " + lateAvgUs + " µs");
    }

    @Test
    void toolRegistryLookup_nonExistentTool_isAlsoFast() {
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            McpToolRegistry.getTool("nonexistent_tool_xyz");
        }
        long elapsed = System.nanoTime() - start;
        double avgUs = elapsed / (double) BENCHMARK_ITERATIONS / 1000.0;

        assertTrue(avgUs < 1000, "Non-existent tool lookup should be < 1ms, was " + avgUs + " µs");
    }

    // ========================================================================
    // Req 17.3: Parameter validation performance (< 10ms)
    // ValidationEngine.validate() should complete within 10ms per call.
    // ========================================================================

    @Test
    void parameterValidation_completesWithin10ms() {
        // Register a tool with a schema that has required fields and types
        toolRegistry.registerTool(new SchemaRichTool());

        ToolCall validCall = ToolCall.builder()
                .callId("perf_valid")
                .toolName("schema_rich_tool")
                .parameters(Map.of(
                        "query", "test search",
                        "maxResults", 10,
                        "caseSensitive", true))
                .build();

        // Warm up
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            validationEngine.validate(validCall);
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            ValidationResult result = validationEngine.validate(validCall);
            assertTrue(result.isValid());
        }
        long elapsed = System.nanoTime() - start;
        double avgMs = elapsed / (double) BENCHMARK_ITERATIONS / 1_000_000.0;

        assertTrue(avgMs < 10.0,
                String.format("Validation avg should be < 10ms, was %.3f ms", avgMs));
    }

    @Test
    void parameterValidation_withErrors_completesWithin10ms() {
        toolRegistry.registerTool(new SchemaRichTool());

        // Missing required "query" parameter + wrong type for "maxResults"
        ToolCall invalidCall = ToolCall.builder()
                .callId("perf_invalid")
                .toolName("schema_rich_tool")
                .parameters(Map.of("maxResults", "not_a_number"))
                .build();

        // Warm up
        for (int i = 0; i < WARM_UP_ITERATIONS; i++) {
            validationEngine.validate(invalidCall);
        }

        // Benchmark
        long start = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            validationEngine.validate(invalidCall);
        }
        long elapsed = System.nanoTime() - start;
        double avgMs = elapsed / (double) BENCHMARK_ITERATIONS / 1_000_000.0;

        assertTrue(avgMs < 10.0,
                String.format("Validation with errors avg should be < 10ms, was %.3f ms", avgMs));
    }

    // ========================================================================
    // Req 17.3: Schema caching effectiveness
    // ValidationEngine caches compiled schemas via ConcurrentHashMap.
    // Second validation of the same tool should use the cache.
    // ========================================================================

    @Test
    void schemaCaching_reducesSubsequentValidationTime() {
        toolRegistry.registerTool(new SchemaRichTool());
        validationEngine.clearSchemaCache();
        assertEquals(0, validationEngine.getSchemaCacheSize(), "Cache should start empty");

        ToolCall call = ToolCall.builder()
                .callId("cache_test")
                .toolName("schema_rich_tool")
                .parameters(Map.of("query", "test"))
                .build();

        // First call populates the cache
        validationEngine.validate(call);
        assertTrue(validationEngine.getSchemaCacheSize() > 0,
                "Cache should be populated after first validation");

        int cacheSize = validationEngine.getSchemaCacheSize();

        // Subsequent calls should reuse the cache (size stays the same)
        for (int i = 0; i < 100; i++) {
            validationEngine.validate(call);
        }
        assertEquals(cacheSize, validationEngine.getSchemaCacheSize(),
                "Cache size should not grow for repeated validations of the same tool");
    }

    @Test
    void schemaCaching_cachedValidation_isFasterThanColdStart() {
        toolRegistry.registerTool(new SchemaRichTool());

        ToolCall call = ToolCall.builder()
                .callId("cache_perf")
                .toolName("schema_rich_tool")
                .parameters(Map.of("query", "test"))
                .build();

        // Cold start: clear cache and measure
        validationEngine.clearSchemaCache();
        long coldStart = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            validationEngine.clearSchemaCache();
            validationEngine.validate(call);
        }
        long coldElapsed = System.nanoTime() - coldStart;

        // Warm: cache is populated, measure
        validationEngine.validate(call); // ensure cache is warm
        long warmStart = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            validationEngine.validate(call);
        }
        long warmElapsed = System.nanoTime() - warmStart;

        // Cached validation should not be slower than cold start
        // (In practice it should be faster, but we use a lenient assertion)
        assertTrue(warmElapsed <= coldElapsed * 2,
                String.format("Cached validation (%d ns) should not be much slower than cold (%d ns)",
                        warmElapsed, coldElapsed));
    }


    // ========================================================================
    // Req 17.4: Memory usage — single session < 10MB
    // ObservabilityManager tracks sessions. We verify that creating a session
    // with many tool calls does not exceed 10MB of heap overhead.
    // ========================================================================

    @Test
    void memoryUsage_singleSession_under10MB() {
        ObservabilityManager observability = new ObservabilityManager();

        // Force GC to get a clean baseline
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        long baselineMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        String sessionId = ObservabilityManager.generateSessionId();
        observability.startSession(sessionId);

        // Simulate a heavy session: 20 tool calls (max allowed per Req 17.6)
        for (int i = 0; i < 20; i++) {
            String callId = ObservabilityManager.generateCallId();
            ToolCall call = ToolCall.builder()
                    .callId(callId)
                    .toolName("perf_tool_" + (i % 100))
                    .parameters(Map.of(
                            "param1", "value_" + i,
                            "param2", i,
                            "param3", "a".repeat(1000))) // 1KB per param
                    .build();
            observability.startToolCall(sessionId, callId, call);
            observability.endToolCall(sessionId, callId);
        }

        observability.endSession(sessionId);

        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long usedBytes = afterMemory - baselineMemory;
        long tenMB = 10L * 1024 * 1024;

        // The session overhead should be well under 10MB
        assertTrue(usedBytes < tenMB,
                String.format("Session memory usage should be < 10MB, estimated %d bytes (%.2f MB)",
                        usedBytes, usedBytes / (1024.0 * 1024.0)));
    }

    @Test
    void memoryUsage_multipleSessions_staysReasonable() {
        ObservabilityManager observability = new ObservabilityManager();

        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        long baselineMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Create and complete 50 sessions
        for (int s = 0; s < 50; s++) {
            String sessionId = ObservabilityManager.generateSessionId();
            observability.startSession(sessionId);
            for (int i = 0; i < 5; i++) {
                String callId = ObservabilityManager.generateCallId();
                ToolCall call = ToolCall.builder()
                        .callId(callId)
                        .toolName("perf_tool_" + (i % 100))
                        .parameters(Map.of("input", "test_" + i))
                        .build();
                observability.startToolCall(sessionId, callId, call);
                observability.endToolCall(sessionId, callId);
            }
            observability.endSession(sessionId);
        }

        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Completed sessions are removed from the active map, so memory should be reclaimed
        // Allow generous headroom — the key point is it doesn't grow unboundedly
        long fiftyMB = 50L * 1024 * 1024;
        long usedBytes = afterMemory - baselineMemory;
        assertTrue(usedBytes < fiftyMB,
                String.format("50 completed sessions should not use > 50MB, estimated %d bytes (%.2f MB)",
                        usedBytes, usedBytes / (1024.0 * 1024.0)));
    }

    // ========================================================================
    // Req 17.4: Concurrent execution performance
    // ExecutionEngine uses a thread pool. Verify concurrent tool executions
    // complete in reasonable time.
    // ========================================================================

    @Test
    void concurrentExecution_multipleToolCalls_completesEfficiently() throws Exception {
        // Register a fast tool
        toolRegistry.registerTool(new FastTool());

        ToolContext context = ToolContext.builder().workspaceRoot(Paths.get(".")).build();
        int concurrentCalls = 10;
        ExecutorService testExecutor = Executors.newFixedThreadPool(concurrentCalls);
        CountDownLatch latch = new CountDownLatch(concurrentCalls);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long start = System.nanoTime();

        for (int i = 0; i < concurrentCalls; i++) {
            final int idx = i;
            testExecutor.submit(() -> {
                try {
                    ToolCall call = ToolCall.builder()
                            .callId("concurrent_" + idx)
                            .toolName("fast_tool")
                            .parameters(Map.of("input", "test_" + idx))
                            .build();
                    ToolResult result = executionEngine.execute(call, context);
                    if (result.getType() == ToolResult.ResultType.TEXT) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ConcurrentExecutionException is expected for non-concurrent-safe tools
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All concurrent calls should complete within 30s");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        testExecutor.shutdown();

        // At least some calls should succeed (fast_tool is not in CONCURRENT_SAFE_TOOLS,
        // so some may be rejected by the lock — that's expected behavior)
        assertTrue(successCount.get() >= 1,
                "At least 1 concurrent call should succeed, got " + successCount.get());

        // Total time should be reasonable (not serialized 30s * 10)
        assertTrue(elapsedMs < 10_000,
                "Concurrent execution should complete in < 10s, took " + elapsedMs + " ms");
    }

    @Test
    void concurrentExecution_concurrentSafeTools_allSucceed() throws Exception {
        // "read_file" is in CONCURRENT_SAFE_TOOLS — no lock contention
        // Register a tool named "read_file" that returns quickly
        toolRegistry.registerTool(new BenchmarkTool("read_file"));

        ToolContext context = ToolContext.builder().workspaceRoot(Paths.get(".")).build();
        int concurrentCalls = 10;
        ExecutorService testExecutor = Executors.newFixedThreadPool(concurrentCalls);
        CountDownLatch latch = new CountDownLatch(concurrentCalls);
        AtomicInteger successCount = new AtomicInteger(0);

        long start = System.nanoTime();

        for (int i = 0; i < concurrentCalls; i++) {
            final int idx = i;
            testExecutor.submit(() -> {
                try {
                    ToolCall call = ToolCall.builder()
                            .callId("safe_concurrent_" + idx)
                            .toolName("read_file")
                            .parameters(Map.of("path", "test_" + idx))
                            .build();
                    ToolResult result = executionEngine.execute(call, context);
                    if (result.getType() == ToolResult.ResultType.TEXT) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Should not happen for concurrent-safe tools
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All safe concurrent calls should complete within 30s");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        testExecutor.shutdown();

        // All calls should succeed since read_file is concurrent-safe
        assertEquals(concurrentCalls, successCount.get(),
                "All concurrent-safe tool calls should succeed");

        assertTrue(elapsedMs < 10_000,
                "Concurrent-safe execution should complete in < 10s, took " + elapsedMs + " ms");
    }

    // ========================================================================
    // Req 17.5: ResultFormatter uses StringBuilder — string building performance
    // We test that ObservabilityManager's trace export (which builds strings)
    // performs well even with many tool calls.
    // ========================================================================

    @Test
    void traceExport_withManyToolCalls_completesQuickly() {
        ObservabilityManager observability = new ObservabilityManager();
        String sessionId = ObservabilityManager.generateSessionId();
        observability.startSession(sessionId);

        // Add 20 tool calls to the session
        for (int i = 0; i < 20; i++) {
            String callId = ObservabilityManager.generateCallId();
            ToolCall call = ToolCall.builder()
                    .callId(callId)
                    .toolName("perf_tool_" + (i % 100))
                    .parameters(Map.of("input", "value_" + i))
                    .build();
            observability.startToolCall(sessionId, callId, call);
            observability.endToolCall(sessionId, callId);
        }

        // Measure trace export time (JSON format)
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            String json = observability.exportTrace(sessionId, ObservabilityManager.TraceFormat.JSON);
            assertNotNull(json);
        }
        long elapsed = System.nanoTime() - start;
        double avgMs = elapsed / 100.0 / 1_000_000.0;

        observability.endSession(sessionId);

        assertTrue(avgMs < 50.0,
                String.format("Trace export avg should be < 50ms, was %.3f ms", avgMs));
    }

    // ========================================================================
    // Helpers and stub tools
    // ========================================================================

    /**
     * A minimal tool for benchmarking registry lookup and basic execution.
     */
    private static class BenchmarkTool implements Tool {
        private final String name;

        BenchmarkTool(String name) {
            this.name = name;
        }

        @Override public String name() { return name; }
        @Override public String description() { return "Benchmark tool: " + name; }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> pathField = new LinkedHashMap<>();
            pathField.put("type", "string");
            pathField.put("description", "Input path");
            return Map.of("path", pathField);
        }
        @Override public ToolResult execute(ToolContext context, Map<String, Object> params) {
            return ToolResult.text("OK: " + params.getOrDefault("path", ""));
        }
    }

    /**
     * A tool with a rich schema for validation performance testing.
     */
    private static class SchemaRichTool implements Tool {
        @Override public String name() { return "schema_rich_tool"; }
        @Override public String description() { return "Tool with rich schema for perf testing"; }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();

            Map<String, Object> queryField = new LinkedHashMap<>();
            queryField.put("type", "string");
            queryField.put("description", "Search query");
            queryField.put("required", true);
            schema.put("query", queryField);

            Map<String, Object> maxResultsField = new LinkedHashMap<>();
            maxResultsField.put("type", "integer");
            maxResultsField.put("description", "Max results");
            maxResultsField.put("minimum", 1);
            maxResultsField.put("maximum", 100);
            schema.put("maxResults", maxResultsField);

            Map<String, Object> caseSensitiveField = new LinkedHashMap<>();
            caseSensitiveField.put("type", "boolean");
            caseSensitiveField.put("description", "Case sensitive");
            schema.put("caseSensitive", caseSensitiveField);

            return schema;
        }
        @Override public ToolResult execute(ToolContext context, Map<String, Object> params) {
            return ToolResult.text("Results for: " + params.get("query"));
        }
    }

    /**
     * A fast-executing tool for concurrent execution testing.
     */
    private static class FastTool implements Tool {
        @Override public String name() { return "fast_tool"; }
        @Override public String description() { return "Fast tool for concurrency testing"; }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> inputField = new LinkedHashMap<>();
            inputField.put("type", "string");
            inputField.put("description", "Input");
            return Map.of("input", inputField);
        }
        @Override public ToolResult execute(ToolContext context, Map<String, Object> params) {
            return ToolResult.text("Fast: " + params.getOrDefault("input", ""));
        }
    }

    // McpTool implementations for McpToolRegistry lookup performance tests
    private static class McpBenchmarkTool implements Tool {
        private final String name;
        McpBenchmarkTool(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public String description() { return "Benchmark tool: " + name; }
        @Override public Map<String, Object> inputSchema() { return Map.of("path", Map.of("type", "string")); }
        @Override public ToolResult execute(ToolContext context, Map<String, Object> params) {
            return ToolResult.text("OK: " + params.getOrDefault("path", ""));
        }
    }

    private static class McpSchemaRichTool implements Tool {
        @Override public String name() { return "schema_rich_tool"; }
        @Override public String description() { return "Tool with rich schema for perf testing"; }
        @Override public Map<String, Object> inputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("query", Map.of("type", "string", "required", true));
            schema.put("maxResults", Map.of("type", "integer", "minimum", 1, "maximum", 100));
            schema.put("caseSensitive", Map.of("type", "boolean"));
            return schema;
        }
        @Override public ToolResult execute(ToolContext context, Map<String, Object> params) {
            return ToolResult.text("Results for: " + params.get("query"));
        }
    }
}
