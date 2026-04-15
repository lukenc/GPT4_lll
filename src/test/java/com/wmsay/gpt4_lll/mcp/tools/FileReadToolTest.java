package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileReadToolTest {

    private static final Logger log = LoggerFactory.getLogger(FileReadToolTest.class);
    @TempDir
    Path tempDir;

    @Test
    void shouldReadRangeAndMarkTruncatedWhenMaxLinesReached() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        Path file = root.resolve("sample.txt");
        Files.write(file, List.of("alpha", "beta", "gamma", "delta"));

        FileReadTool tool = new FileReadTool();
        Map<String, Object> params = new HashMap<>();
        params.put("path", "sample.txt");
        params.put("start_line", 2);
        params.put("end_line", 10);
        params.put("max_lines", 2);

        ToolResult result = tool.execute(ToolContext.builder().workspaceRoot(root).build(), params);

        assertEquals(ToolResult.ResultType.STRUCTURED, result.getType());
        Map<String, Object> data = result.getStructuredData();
        assertEquals(4, data.get("totalLines"));
        assertEquals(2, data.get("startLine"));
        assertEquals(3, data.get("endLine"));
        assertEquals("2|beta\n3|gamma", data.get("content"));
        assertEquals(true, data.get("truncated"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) data.get("lines");
        assertEquals(2, lines.size());
        assertEquals(2, lines.get(0).get("line"));
        assertEquals("beta", lines.get(0).get("text"));
        log.info("{}",data);
    }

    @Test
    void shouldClampInputsAndHandleEmptyFile() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        Path file = root.resolve("empty.txt");
        Files.createFile(file);

        FileReadTool tool = new FileReadTool();
        Map<String, Object> params = Map.of(
                "path", "empty.txt",
                "start_line", -100,
                "max_lines", 0
        );

        ToolResult result = tool.execute(ToolContext.builder().workspaceRoot(root).build(), params);
        assertEquals(ToolResult.ResultType.TEXT, result.getType());
        assertEquals("", result.getTextContent());
    }

    @Test
    void shouldReturnErrorForDirectoryMissingFileAndOutOfWorkspacePath() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        Path dir = Files.createDirectory(root.resolve("folder"));
        Files.createFile(root.resolve("inside.txt"));
        Path outsideFile = Files.createFile(tempDir.resolve("outside.txt"));

        FileReadTool tool = new FileReadTool();
        ToolContext context = ToolContext.builder().workspaceRoot(root).build();

        ToolResult missing = tool.execute(context, Map.of("path", "missing.txt"));
        assertEquals(ToolResult.ResultType.ERROR, missing.getType());
        assertTrue(missing.getErrorMessage().contains("File not found"));

        ToolResult directory = tool.execute(context, Map.of("path", root.relativize(dir).toString()));
        assertEquals(ToolResult.ResultType.ERROR, directory.getType());
        assertTrue(directory.getErrorMessage().contains("Path is not a file"));

        ToolResult outOfWorkspace = tool.execute(context, Map.of("path", outsideFile.toString()));
        assertEquals(ToolResult.ResultType.ERROR, outOfWorkspace.getType());
        assertTrue(outOfWorkspace.getErrorMessage().contains("Path out of workspace"));
        assertFalse(outOfWorkspace.getErrorMessage().isBlank());
    }
}
