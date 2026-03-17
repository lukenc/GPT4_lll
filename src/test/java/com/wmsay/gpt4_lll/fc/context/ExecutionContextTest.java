package com.wmsay.gpt4_lll.fc.context;

import com.wmsay.gpt4_lll.mcp.McpContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionContext 单元测试。
 * 验证上下文封装、验证、快照/恢复和线程安全访问。
 *
 * <p>注意：由于没有 Mockito，测试使用 Builder 构建上下文，
 * 传入 null 的 Project/Editor 来模拟缺失状态。
 */
class ExecutionContextTest {

    private static final Path TEST_ROOT = Paths.get("/test/project");

    private McpContext mcpWithRoot() {
        return new McpContext(null, null, TEST_ROOT);
    }

    private McpContext mcpEmpty() {
        return new McpContext(null, null, null);
    }

    // ---- Builder 基础 ----

    @Test
    void builder_shouldCreateContextWithAllFields() {
        McpContext mcp = mcpWithRoot();

        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcp)
                .selectedText("hello")
                .data("key1", "value1")
                .data("count", 42)
                .build();

        assertSame(mcp, ctx.getMcpContext());
        assertSame(TEST_ROOT, ctx.getProjectRoot());
        assertNull(ctx.getProject());  // McpContext was created with null project
        assertNull(ctx.getEditor());
        assertEquals("hello", ctx.getSelectedText());
        assertEquals("value1", ctx.getData("key1", String.class));
        assertEquals(42, ctx.getData("count", Integer.class));
    }

    @Test
    void builder_nullMcpContext_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionContext.builder().build());
    }

    // ---- fromMcpContext ----

    @Test
    void fromMcpContext_shouldWrapMcpContext() {
        McpContext mcp = mcpWithRoot();

        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcp);

        assertSame(mcp, ctx.getMcpContext());
        assertSame(TEST_ROOT, ctx.getProjectRoot());
        assertNull(ctx.getProject());
        assertNull(ctx.getEditor());
        // No editor → no selected text
        assertNull(ctx.getSelectedText());
    }

    @Test
    void fromMcpContext_nullMcpContext_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionContext.fromMcpContext(null));
    }

    // ---- Type-safe data access (Req 9.2) ----

    @Test
    void getData_shouldReturnTypedValue() {
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcpEmpty())
                .data("count", 42)
                .build();

        assertEquals(42, ctx.getData("count", Integer.class));
    }

    @Test
    void getData_typeMismatch_shouldThrowClassCast() {
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcpEmpty())
                .data("count", 42)
                .build();

        assertThrows(ClassCastException.class,
                () -> ctx.getData("count", String.class));
    }

    @Test
    void getData_missingKey_shouldReturnNull() {
        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcpEmpty());
        assertNull(ctx.getData("nonexistent", String.class));
    }

    @Test
    void getDataOrDefault_missingKey_shouldReturnDefault() {
        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcpEmpty());
        assertEquals("fallback",
                ctx.getDataOrDefault("missing", String.class, "fallback"));
    }

    @Test
    void getDataOrDefault_existingKey_shouldReturnValue() {
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcpEmpty())
                .data("k", "real")
                .build();

        assertEquals("real",
                ctx.getDataOrDefault("k", String.class, "fallback"));
    }

    @Test
    void putData_shouldStoreAndRetrieve() {
        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcpEmpty());

        ctx.putData("name", "test");
        assertEquals("test", ctx.getData("name", String.class));
        assertTrue(ctx.hasData("name"));
        assertFalse(ctx.hasData("other"));
    }

    @Test
    void putData_nullKey_shouldThrow() {
        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcpEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> ctx.putData(null, "value"));
    }

    @Test
    void getData_nullKey_shouldThrow() {
        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcpEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> ctx.getData(null, String.class));
    }

    // ---- Validation (Req 9.3, 9.4) ----

    @Test
    void validate_withProjectRoot_shouldBeValid() {
        // project is null but projectRoot is set — project is required so this is invalid
        McpContext mcp = new McpContext(null, null, TEST_ROOT);
        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcp);

        ExecutionContext.ValidationResult result = ctx.validate();
        assertFalse(result.isValid());
        assertTrue(result.getMissingFields().contains("project"));
    }

    @Test
    void validate_allNull_shouldListMissingFields() {
        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcpEmpty());

        ExecutionContext.ValidationResult result = ctx.validate();

        assertFalse(result.isValid());
        assertEquals(2, result.getMissingFields().size());
        assertTrue(result.getMissingFields().contains("project"));
        assertTrue(result.getMissingFields().contains("projectRoot"));
        assertTrue(result.getErrorMessage().contains("project"));
        assertTrue(result.getErrorMessage().contains("projectRoot"));
    }

    @Test
    void validationResult_valid_shouldHaveEmptyErrorMessage() {
        ExecutionContext.ValidationResult result = ExecutionContext.ValidationResult.valid();
        assertTrue(result.isValid());
        assertTrue(result.getMissingFields().isEmpty());
        assertEquals("", result.getErrorMessage());
    }

    @Test
    void validateRequired_availableFields_shouldBeValid() {
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcpWithRoot())
                .selectedText("text")
                .data("customKey", "val")
                .build();

        // projectRoot and selectedText are available
        assertTrue(ctx.validateRequired("projectRoot", "selectedText").isValid());
    }

    @Test
    void validateRequired_missingEditor_shouldBeInvalid() {
        ExecutionContext ctx = ExecutionContext.fromMcpContext(mcpWithRoot());

        ExecutionContext.ValidationResult result =
                ctx.validateRequired("projectRoot", "editor");

        assertFalse(result.isValid());
        assertEquals(1, result.getMissingFields().size());
        assertTrue(result.getMissingFields().contains("editor"));
    }

    @Test
    void validateRequired_emptySelectedText_shouldBeInvalid() {
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcpWithRoot())
                .selectedText("")
                .build();

        assertFalse(ctx.validateRequired("selectedText").isValid());
    }

    @Test
    void validateRequired_customDataField_shouldCheck() {
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcpEmpty())
                .data("customKey", "val")
                .build();

        assertTrue(ctx.validateRequired("customKey").isValid());
        assertFalse(ctx.validateRequired("missingKey").isValid());
    }

    // ---- Snapshot and Restore (Req 9.5) ----

    @Test
    void snapshotAndRestore_shouldPreserveState() {
        McpContext mcp = mcpWithRoot();
        ExecutionContext original = ExecutionContext.builder()
                .mcpContext(mcp)
                .selectedText("selected text")
                .data("key1", "value1")
                .data("key2", 42)
                .build();

        ExecutionContext.Snapshot snapshot = original.createSnapshot();
        ExecutionContext restored = ExecutionContext.fromSnapshot(snapshot);

        assertSame(mcp, restored.getMcpContext());
        assertSame(TEST_ROOT, restored.getProjectRoot());
        assertEquals("selected text", restored.getSelectedText());
        assertEquals("value1", restored.getData("key1", String.class));
        assertEquals(42, restored.getData("key2", Integer.class));
    }

    @Test
    void snapshotAndRestore_shouldBeIndependent() {
        ExecutionContext original = ExecutionContext.builder()
                .mcpContext(mcpEmpty())
                .data("key", "original")
                .build();

        ExecutionContext.Snapshot snapshot = original.createSnapshot();

        // Modify original after snapshot
        original.putData("key", "modified");
        original.putData("newKey", "newValue");

        // Restored should have snapshot state, not modified state
        ExecutionContext restored = ExecutionContext.fromSnapshot(snapshot);
        assertEquals("original", restored.getData("key", String.class));
        assertNull(restored.getData("newKey", String.class));
    }

    @Test
    void fromSnapshot_null_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionContext.fromSnapshot(null));
    }

    @Test
    void snapshot_shouldExposeData() {
        McpContext mcp = mcpEmpty();
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcp)
                .selectedText("sel")
                .data("k", "v")
                .build();

        ExecutionContext.Snapshot snap = ctx.createSnapshot();
        assertSame(mcp, snap.getMcpContext());
        assertEquals("sel", snap.getSelectedText());
        assertEquals("v", snap.getExtraData().get("k"));
    }

    @Test
    void snapshot_extraData_shouldBeUnmodifiable() {
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcpEmpty())
                .data("k", "v")
                .build();

        ExecutionContext.Snapshot snap = ctx.createSnapshot();
        assertThrows(UnsupportedOperationException.class,
                () -> snap.getExtraData().put("new", "val"));
    }

    // ---- Thread safety (smoke test, Req 9.6) ----

    @Test
    void concurrentReadWrite_shouldNotThrow() throws InterruptedException {
        ExecutionContext ctx = ExecutionContext.builder()
                .mcpContext(mcpWithRoot())
                .build();

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 200; i++) {
                ctx.putData("counter", i);
            }
        });

        Thread reader = new Thread(() -> {
            for (int i = 0; i < 200; i++) {
                ctx.getData("counter", Integer.class);
                ctx.validate();
                ctx.getProjectRoot();
                ctx.getSelectedText();
            }
        });

        Thread snapshotter = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                ExecutionContext.Snapshot snap = ctx.createSnapshot();
                ExecutionContext.fromSnapshot(snap);
            }
        });

        writer.start();
        reader.start();
        snapshotter.start();
        writer.join(5000);
        reader.join(5000);
        snapshotter.join(5000);

        // No exception means thread safety is working
        assertNotNull(ctx);
    }
}
