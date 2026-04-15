package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTreeToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRespectDepthFileHiddenAndTruncationBoundaries() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        Path src = Files.createDirectory(root.resolve("src"));
        Path nested = Files.createDirectory(src.resolve("nested"));
        Files.createFile(src.resolve("Main.java"));
        Files.createFile(nested.resolve("Deep.java"));
        Files.createDirectory(root.resolve("docs"));
        Files.createFile(root.resolve(".env"));
        Files.createDirectory(root.resolve(".git"));

        ProjectTreeTool tool = new ProjectTreeTool();
        ToolContext context = ToolContext.builder().workspaceRoot(root).build();

        ToolResult onlyDirs = tool.execute(context, Map.of(
                "path", ".",
                "showFiles", false,
                "showHidden", false,
                "maxDepth", 5
        ));
        assertEquals(ToolResult.ResultType.STRUCTURED, onlyDirs.getType());
        String onlyDirsTree = (String) onlyDirs.getStructuredData().get("tree");
        assertTrue(onlyDirsTree.contains("src/"));
        assertTrue(onlyDirsTree.contains("docs/"));
        assertFalse(onlyDirsTree.contains("Main.java"));
        assertFalse(onlyDirsTree.contains(".env"));
        assertFalse(onlyDirsTree.contains(".git/"));

        ToolResult depthZero = tool.execute(context, Map.of("maxDepth", 0));
        assertEquals(ToolResult.ResultType.STRUCTURED, depthZero.getType());
        assertEquals(0, depthZero.getStructuredData().get("entryCount"));
        assertEquals(false, depthZero.getStructuredData().get("truncated"));

        ToolResult truncated = tool.execute(context, Map.of(
                "maxEntries", 1,
                "showHidden", true
        ));
        assertEquals(ToolResult.ResultType.STRUCTURED, truncated.getType());
        String truncatedTree = (String) truncated.getStructuredData().get("tree");
        assertTrue((Boolean) truncated.getStructuredData().get("truncated"));
        assertTrue(truncatedTree.contains("... (truncated)"));
    }

    @Test
    void shouldReturnErrorsForMissingPathAndOutOfWorkspacePath() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        ProjectTreeTool tool = new ProjectTreeTool();
        ToolContext context = ToolContext.builder().workspaceRoot(root).build();

        ToolResult missingPath = tool.execute(context, Map.of("path", "not-exists"));
        assertEquals(ToolResult.ResultType.ERROR, missingPath.getType());
        assertTrue(missingPath.getErrorMessage().contains("Path not found"));

        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        ToolResult outOfWorkspace = tool.execute(context, Map.of("path", outside.toString()));
        assertEquals(ToolResult.ResultType.ERROR, outOfWorkspace.getType());
        assertTrue(outOfWorkspace.getErrorMessage().contains("Path out of workspace"));
    }
}
