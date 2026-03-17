package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileWriteToolTest {

    @TempDir
    Path tempDir;

    private final FileWriteTool tool = new FileWriteTool();

    private McpContext context() throws Exception {
        Path root = tempDir.resolve("workspace");
        if (!Files.exists(root)) {
            Files.createDirectory(root);
        }
        return new McpContext(null, null, root);
    }

    private Path workspaceRoot() {
        return tempDir.resolve("workspace");
    }

    // ═══════════════════════════════════════════════════════
    // overwrite 模式
    // ═══════════════════════════════════════════════════════

    @Test
    void overwrite_createsNewFile() throws Exception {
        McpContext ctx = context();
        Map<String, Object> params = new HashMap<>();
        params.put("path", "hello.txt");
        params.put("content", "Hello, World!");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.STRUCTURED, result.getType());
        Map<String, Object> data = result.getStructuredData();
        assertEquals(true, data.get("success"));
        assertEquals(true, data.get("created"));
        assertEquals("overwrite", data.get("mode"));

        String written = Files.readString(workspaceRoot().resolve("hello.txt"));
        assertEquals("Hello, World!", written);
    }

    @Test
    void overwrite_replacesExistingFile() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("existing.txt");
        Files.writeString(file, "old content");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "existing.txt");
        params.put("content", "new content");

        McpToolResult result = tool.execute(ctx, params);

        Map<String, Object> data = result.getStructuredData();
        assertEquals(true, data.get("success"));
        assertEquals(false, data.get("created"));
        assertEquals("new content", Files.readString(file));
    }

    @Test
    void overwrite_createsParentDirectories() throws Exception {
        McpContext ctx = context();
        Map<String, Object> params = new HashMap<>();
        params.put("path", "deep/nested/dir/file.txt");
        params.put("content", "deep content");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.STRUCTURED, result.getType());
        assertEquals(true, result.getStructuredData().get("success"));

        Path target = workspaceRoot().resolve("deep/nested/dir/file.txt");
        assertTrue(Files.exists(target));
        assertEquals("deep content", Files.readString(target));
    }

    @Test
    void overwrite_failsWhenCreateDirsFalseAndParentMissing() throws Exception {
        McpContext ctx = context();
        Map<String, Object> params = new HashMap<>();
        params.put("path", "nonexistent/dir/file.txt");
        params.put("content", "content");
        params.put("create_dirs", false);

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("Parent directory does not exist"));
        assertTrue(result.getErrorMessage().contains("create_dirs=true"));
    }

    // ═══════════════════════════════════════════════════════
    // patch 模式 — 核心安全测试
    // ═══════════════════════════════════════════════════════

    @Test
    void patch_exactlyOneMatch_succeeds() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("code.java");
        Files.writeString(file, "public class Foo {\n    int x = 1;\n    int y = 2;\n}\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "code.java");
        params.put("mode", "patch");
        params.put("old_content", "    int x = 1;");
        params.put("content", "    int x = 42;");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.STRUCTURED, result.getType());
        assertEquals(true, result.getStructuredData().get("success"));

        String updated = Files.readString(file);
        assertTrue(updated.contains("int x = 42;"));
        assertFalse(updated.contains("int x = 1;"));
        assertTrue(updated.contains("int y = 2;"));
    }

    @Test
    void patch_zeroMatches_failsWithClearMessage() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("code.java");
        Files.writeString(file, "public class Foo {\n    int x = 1;\n}\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "code.java");
        params.put("mode", "patch");
        params.put("old_content", "this text does not exist");
        params.put("content", "replacement");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("old_content not found"));
        assertTrue(result.getErrorMessage().contains("read_file"));
    }

    @Test
    void patch_multipleMatches_failsWithCountAndGuidance() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("code.java");
        Files.writeString(file, "int x = 1;\nint y = 1;\nint z = 1;\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "code.java");
        params.put("mode", "patch");
        params.put("old_content", "= 1;");
        params.put("content", "= 99;");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        String errorMsg = result.getErrorMessage();
        assertTrue(errorMsg.contains("3 times"), "Should report exact count: " + errorMsg);
        assertTrue(errorMsg.contains("expand old_content"), "Should guide agent: " + errorMsg);
        assertTrue(errorMsg.contains("overwrite mode"), "Should suggest alternative: " + errorMsg);
    }

    @Test
    void patch_missingOldContent_failsWithClearMessage() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("code.java");
        Files.writeString(file, "content");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "code.java");
        params.put("mode", "patch");
        params.put("content", "new content");
        // old_content not provided

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("old_content"));
    }

    @Test
    void patch_onNonexistentFile_failsWithClearMessage() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "does_not_exist.txt");
        params.put("mode", "patch");
        params.put("old_content", "old");
        params.put("content", "new");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("does not exist"));
        assertTrue(result.getErrorMessage().contains("overwrite mode"));
    }

    @Test
    void patch_preservesCRLF() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("crlf.txt");
        Files.writeString(file, "line1\r\nline2\r\nline3\r\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "crlf.txt");
        params.put("mode", "patch");
        params.put("old_content", "line2");
        params.put("content", "REPLACED");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(true, result.getStructuredData().get("success"));
        String updated = Files.readString(file);
        assertTrue(updated.contains("\r\n"), "Should preserve CRLF line endings");
        assertTrue(updated.contains("REPLACED"));
        assertFalse(updated.contains("line2"));
    }

    // ═══════════════════════════════════════════════════════
    // append 模式
    // ═══════════════════════════════════════════════════════

    @Test
    void append_toExistingFile() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("log.txt");
        Files.writeString(file, "line1\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "log.txt");
        params.put("mode", "append");
        params.put("content", "line2\n");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(true, result.getStructuredData().get("success"));
        assertEquals("append", result.getStructuredData().get("mode"));
        assertEquals("line1\nline2\n", Files.readString(file));
    }

    @Test
    void append_toNewFile() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "new_log.txt");
        params.put("mode", "append");
        params.put("content", "first line\n");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(true, result.getStructuredData().get("success"));
        assertEquals(true, result.getStructuredData().get("created"));
        assertEquals("first line\n", Files.readString(workspaceRoot().resolve("new_log.txt")));
    }

    // ═══════════════════════════════════════════════════════
    // insert_after_line 模式
    // ═══════════════════════════════════════════════════════

    @Test
    void insertAfterLine_inMiddle() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("lines.txt");
        Files.writeString(file, "alpha\nbeta\ngamma\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "lines.txt");
        params.put("mode", "insert_after_line");
        params.put("line_number", 2);
        params.put("content", "INSERTED");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(true, result.getStructuredData().get("success"));
        String updated = Files.readString(file);
        String[] lines = updated.split("\n", -1);
        assertEquals("alpha", lines[0]);
        assertEquals("beta", lines[1]);
        assertEquals("INSERTED", lines[2]);
        assertEquals("gamma", lines[3]);
    }

    @Test
    void insertAfterLine_atBeginning() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("lines.txt");
        Files.writeString(file, "first\nsecond\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "lines.txt");
        params.put("mode", "insert_after_line");
        params.put("line_number", 0);
        params.put("content", "HEADER");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(true, result.getStructuredData().get("success"));
        String updated = Files.readString(file);
        assertTrue(updated.startsWith("HEADER\n"));
    }

    @Test
    void insertAfterLine_exceedsTotalLines_failsWithClearMessage() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("short.txt");
        Files.writeString(file, "one\ntwo\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "short.txt");
        params.put("mode", "insert_after_line");
        params.put("line_number", 100);
        params.put("content", "NEVER");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("exceeds total lines"));
    }

    @Test
    void insertAfterLine_missingLineNumber_fails() throws Exception {
        McpContext ctx = context();
        Path file = workspaceRoot().resolve("lines.txt");
        Files.writeString(file, "content\n");

        Map<String, Object> params = new HashMap<>();
        params.put("path", "lines.txt");
        params.put("mode", "insert_after_line");
        params.put("content", "stuff");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("line_number"));
    }

    // ═══════════════════════════════════════════════════════
    // 安全性：路径穿越攻击
    // ═══════════════════════════════════════════════════════

    @Test
    void pathTraversal_dotDotSlash_rejected() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "../../../etc/passwd");
        params.put("content", "hacked");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("Path out of workspace"));
    }

    @Test
    void pathTraversal_absolutePathOutside_rejected() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "/tmp/evil.txt");
        params.put("content", "hacked");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("Path out of workspace"));
    }

    @Test
    void pathTraversal_encodedDots_rejected() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "subdir/../../outside.txt");
        params.put("content", "hacked");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("Path out of workspace"));
    }

    // ═══════════════════════════════════════════════════════
    // 参数校验
    // ═══════════════════════════════════════════════════════

    @Test
    void missingContent_fails() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "file.txt");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("content"));
    }

    @Test
    void invalidMode_fails() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "file.txt");
        params.put("content", "data");
        params.put("mode", "delete_everything");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("Invalid mode"));
    }

    @Test
    void invalidEncoding_fails() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "file.txt");
        params.put("content", "data");
        params.put("encoding", "non-existent-charset-xyz");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("Unsupported encoding"));
    }

    @Test
    void writingToDirectory_fails() throws Exception {
        McpContext ctx = context();
        Files.createDirectory(workspaceRoot().resolve("adir"));

        Map<String, Object> params = new HashMap<>();
        params.put("path", "adir");
        params.put("content", "data");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(McpToolResult.ResultType.ERROR, result.getType());
        assertTrue(result.getErrorMessage().contains("not a regular file"));
    }

    // ═══════════════════════════════════════════════════════
    // 编码支持
    // ═══════════════════════════════════════════════════════

    @Test
    void customEncoding_gbk() throws Exception {
        McpContext ctx = context();
        String chinese = "你好世界";

        Map<String, Object> params = new HashMap<>();
        params.put("path", "gbk_file.txt");
        params.put("content", chinese);
        params.put("encoding", "GBK");

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(true, result.getStructuredData().get("success"));

        byte[] bytes = Files.readAllBytes(workspaceRoot().resolve("gbk_file.txt"));
        String read = new String(bytes, java.nio.charset.Charset.forName("GBK"));
        assertEquals(chinese, read);
    }

    // ═══════════════════════════════════════════════════════
    // 行尾符检测
    // ═══════════════════════════════════════════════════════

    @Test
    void detectLineEnding_lf() {
        assertEquals("\n", FileWriteTool.detectLineEnding("a\nb\nc\n"));
    }

    @Test
    void detectLineEnding_crlf() {
        assertEquals("\r\n", FileWriteTool.detectLineEnding("a\r\nb\r\nc\r\n"));
    }

    @Test
    void detectLineEnding_mixed_crlfDominant() {
        assertEquals("\r\n", FileWriteTool.detectLineEnding("a\r\nb\r\nc\n"));
    }

    @Test
    void detectLineEnding_empty() {
        assertEquals("\n", FileWriteTool.detectLineEnding(""));
    }

    // ═══════════════════════════════════════════════════════
    // 返回值结构完整性
    // ═══════════════════════════════════════════════════════

    @Test
    void resultContainsAllExpectedFields() throws Exception {
        McpContext ctx = context();

        Map<String, Object> params = new HashMap<>();
        params.put("path", "result_check.txt");
        params.put("content", "abc");

        McpToolResult result = tool.execute(ctx, params);

        Map<String, Object> data = result.getStructuredData();
        assertTrue(data.containsKey("success"));
        assertTrue(data.containsKey("tool"));
        assertTrue(data.containsKey("path"));
        assertTrue(data.containsKey("mode"));
        assertTrue(data.containsKey("bytes_written"));
        assertTrue(data.containsKey("created"));
        assertTrue(data.containsKey("error"));

        assertEquals("write_file", data.get("tool"));
        assertEquals(true, data.get("success"));
        assertEquals("overwrite", data.get("mode"));
        assertEquals(3, data.get("bytes_written"));
        assertEquals(true, data.get("created"));
        assertNull(data.get("error"));
    }

    // ═══════════════════════════════════════════════════════
    // 原子写入验证：写入后内容一致
    // ═══════════════════════════════════════════════════════

    @Test
    void atomicWrite_preservesContentIntegrity() throws Exception {
        McpContext ctx = context();
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("Line ").append(i).append(": some data here\n");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("path", "large_file.txt");
        params.put("content", largeContent.toString());

        McpToolResult result = tool.execute(ctx, params);

        assertEquals(true, result.getStructuredData().get("success"));
        String read = Files.readString(workspaceRoot().resolve("large_file.txt"));
        assertEquals(largeContent.toString(), read);
    }
}
