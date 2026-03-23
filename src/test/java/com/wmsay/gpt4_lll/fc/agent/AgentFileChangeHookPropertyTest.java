package com.wmsay.gpt4_lll.fc.agent;

import com.wmsay.gpt4_lll.fc.model.ErrorMessage;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterTry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Property 6: 文件变更选择性追踪
 * <p>
 * 验证 AgentFileChangeHook 仅在工具名称为 "write_file" 且执行成功时
 * 向 FileChangeTracker 记录变更；对于其他工具名称，FileChangeTracker.size() 不变。
 * <p>
 * **Validates: Requirements 5.4, 5.5**
 */
class AgentFileChangeHookPropertyTest {

    private Path tempDir;

    @AfterTry
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
            tempDir = null;
        }
    }

    // ---------------------------------------------------------------
    // Property 6: 文件变更选择性追踪
    // Validates: Requirements 5.4, 5.5
    // ---------------------------------------------------------------

    /**
     * Property 6 — Non-write_file tools: For any tool call result whose toolName
     * is NOT "write_file", calling afterRound() does NOT change FileChangeTracker.size().
     *
     * **Validates: Requirements 5.4, 5.5**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 6: 文件变更选择性追踪 — non-write_file ignored")
    void nonWriteFileToolsDoNotTrackChanges(
            @ForAll("nonWriteFileToolNames") String toolName) throws Exception {

        tempDir = Files.createTempDirectory("hook-prop-test-");
        FileChangeTracker tracker = new FileChangeTracker();
        AgentFileChangeHook hook = new AgentFileChangeHook(tracker, tempDir.toAbsolutePath().toString());

        int sizeBefore = tracker.size();

        // Create a successful ToolCallResult with a non-write_file tool name
        ToolCallResult result = ToolCallResult.success(
                UUID.randomUUID().toString(),
                toolName,
                McpToolResult.text("some output"),
                100L);

        hook.afterRound(0, Collections.singletonList(result));

        assert tracker.size() == sizeBefore :
                "FileChangeTracker.size() should not change for tool '" + toolName
                        + "', expected " + sizeBefore + " but got " + tracker.size();
    }

    /**
     * Property 6 — write_file success: For a write_file tool call result that is
     * successful and has a valid file path in structured data, calling afterRound()
     * increases FileChangeTracker.size().
     *
     * **Validates: Requirements 5.4, 5.5**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 6: 文件变更选择性追踪 — write_file success tracked")
    void writeFileSuccessTracksChanges(
            @ForAll("fileContents") String content) throws Exception {

        tempDir = Files.createTempDirectory("hook-prop-test-");
        FileChangeTracker tracker = new FileChangeTracker();
        AgentFileChangeHook hook = new AgentFileChangeHook(tracker, tempDir.toAbsolutePath().toString());

        // Create an actual temp file to simulate a written file
        String fileName = "test-" + UUID.randomUUID() + ".txt";
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));

        int sizeBefore = tracker.size();

        // Create a successful write_file ToolCallResult with structured data containing "path"
        ToolCallResult result = ToolCallResult.success(
                UUID.randomUUID().toString(),
                "write_file",
                McpToolResult.structured(Map.of("path", fileName)),
                100L);

        hook.afterRound(0, Collections.singletonList(result));

        assert tracker.size() == sizeBefore + 1 :
                "FileChangeTracker.size() should increase by 1 for successful write_file, "
                        + "expected " + (sizeBefore + 1) + " but got " + tracker.size();
    }

    /**
     * Property 6 — write_file failure: For a write_file tool call result that failed
     * (execution error), calling afterRound() does NOT change FileChangeTracker.size().
     *
     * **Validates: Requirements 5.4, 5.5**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 6: 文件变更选择性追踪 — write_file failure ignored")
    void writeFileFailureDoesNotTrackChanges(
            @ForAll("errorMessages") String errorMsg) throws Exception {

        tempDir = Files.createTempDirectory("hook-prop-test-");
        FileChangeTracker tracker = new FileChangeTracker();
        AgentFileChangeHook hook = new AgentFileChangeHook(tracker, tempDir.toAbsolutePath().toString());

        int sizeBefore = tracker.size();

        // Create a failed write_file ToolCallResult
        ToolCallResult result = ToolCallResult.executionError(
                UUID.randomUUID().toString(),
                "write_file",
                ErrorMessage.builder()
                        .type("EXECUTION_ERROR")
                        .message(errorMsg)
                        .build(),
                100L);

        hook.afterRound(0, Collections.singletonList(result));

        assert tracker.size() == sizeBefore :
                "FileChangeTracker.size() should not change for failed write_file, "
                        + "expected " + sizeBefore + " but got " + tracker.size();
    }

    /**
     * Property 6 — mixed tool calls: In a round with multiple tool call results,
     * only successful write_file calls increase the tracker size.
     *
     * **Validates: Requirements 5.4, 5.5**
     */
    @Property(tries = 50)
    @Label("Feature: agent-runtime-ui-integration, Property 6: 文件变更选择性追踪 — mixed tools")
    void mixedToolCallsOnlyTrackSuccessfulWriteFile(
            @ForAll("nonWriteFileToolNames") String otherTool,
            @ForAll("fileContents") String content) throws Exception {

        tempDir = Files.createTempDirectory("hook-prop-test-");
        FileChangeTracker tracker = new FileChangeTracker();
        AgentFileChangeHook hook = new AgentFileChangeHook(tracker, tempDir.toAbsolutePath().toString());

        // Create an actual temp file for the write_file result
        String fileName = "mixed-" + UUID.randomUUID() + ".txt";
        Path filePath = tempDir.resolve(fileName);
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));

        int sizeBefore = tracker.size();

        // Build a list with: one non-write_file success, one write_file failure, one write_file success
        List<ToolCallResult> results = List.of(
                // non-write_file — should be ignored
                ToolCallResult.success(
                        UUID.randomUUID().toString(),
                        otherTool,
                        McpToolResult.text("output"),
                        50L),
                // write_file failure — should be ignored
                ToolCallResult.executionError(
                        UUID.randomUUID().toString(),
                        "write_file",
                        ErrorMessage.builder()
                                .type("EXECUTION_ERROR")
                                .message("disk full")
                                .build(),
                        80L),
                // write_file success — should be tracked
                ToolCallResult.success(
                        UUID.randomUUID().toString(),
                        "write_file",
                        McpToolResult.structured(Map.of("path", fileName)),
                        120L)
        );

        hook.afterRound(0, results);

        assert tracker.size() == sizeBefore + 1 :
                "Only the successful write_file should be tracked in mixed results, "
                        + "expected " + (sizeBefore + 1) + " but got " + tracker.size();
    }

    // ---------------------------------------------------------------
    // Providers
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<String> nonWriteFileToolNames() {
        return Arbitraries.of(
                "read_file",
                "shell_exec",
                "keyword_search",
                "project_tree",
                "list_directory",
                "search_replace",
                "run_tests",
                "git_status",
                "create_directory",
                "delete_file"
        );
    }

    @Provide
    Arbitrary<String> fileContents() {
        return Arbitraries.of(
                "hello world",
                "public class Test {}",
                "line1\nline2\nline3",
                "",
                "中文内容测试",
                "special chars: !@#$%^&*()",
                "a".repeat(1000),
                "{\n  \"key\": \"value\"\n}",
                "# Markdown heading\n\nSome text.",
                "import java.util.List;"
        );
    }

    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
                "Permission denied",
                "Disk full",
                "File not found",
                "IO error",
                "写入失败",
                "Path too long",
                "Read-only filesystem",
                "Encoding error",
                "Timeout writing file",
                "Unexpected error"
        );
    }
}
