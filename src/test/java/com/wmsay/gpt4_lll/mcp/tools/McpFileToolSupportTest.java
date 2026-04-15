package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpFileToolSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldResolvePathsInsideWorkspaceAndRejectOutsideOrMissingRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        Path nestedDir = Files.createDirectories(root.resolve("dir/sub"));
        Path absoluteInside = nestedDir.toAbsolutePath().normalize();

        ToolContext context = ToolContext.builder().workspaceRoot(root).build();

        assertEquals(root, McpFileToolSupport.resolvePath(context, Map.of("path", "."), "path"));
        assertEquals(root.resolve("dir/sub"), McpFileToolSupport.resolvePath(context, Map.of("path", "dir/sub"), "path"));
        assertEquals(absoluteInside, McpFileToolSupport.resolvePath(context, Map.of("path", absoluteInside.toString()), "path"));

        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        IllegalArgumentException outOfWorkspace = assertThrows(
                IllegalArgumentException.class,
                () -> McpFileToolSupport.resolvePath(context, Map.of("path", outside.toString()), "path")
        );
        assertTrue(outOfWorkspace.getMessage().contains("Path out of workspace"));

        IllegalArgumentException missingRoot = assertThrows(
                IllegalArgumentException.class,
                () -> McpFileToolSupport.resolvePath(ToolContext.builder().build(), Map.of("path", "."), "path")
        );
        assertTrue(missingRoot.getMessage().contains("Project root is unavailable"));
    }

    @Test
    void shouldParseIntBooleanAndStringWithFallbackDefaults() {
        Map<String, Object> params = new HashMap<>();
        params.put("numberObj", 12L);
        params.put("numberText", "34");
        params.put("numberBad", "abc");
        params.put("boolObj", true);
        params.put("boolText", "TrUe");
        params.put("text", "value");
        params.put("blankText", "   ");

        assertEquals(12, McpFileToolSupport.getInt(params, "numberObj", 1));
        assertEquals(34, McpFileToolSupport.getInt(params, "numberText", 1));
        assertEquals(1, McpFileToolSupport.getInt(params, "numberBad", 1));
        assertEquals(1, McpFileToolSupport.getInt(params, "missingInt", 1));

        assertEquals(true, McpFileToolSupport.getBoolean(params, "boolObj", false));
        assertEquals(true, McpFileToolSupport.getBoolean(params, "boolText", false));
        assertEquals(false, McpFileToolSupport.getBoolean(params, "missingBool", false));

        assertEquals("value", McpFileToolSupport.getString(params, "text", "default"));
        assertEquals("default", McpFileToolSupport.getString(params, "blankText", "default"));
        assertEquals("default", McpFileToolSupport.getString(params, "missingText", "default"));
    }
}
