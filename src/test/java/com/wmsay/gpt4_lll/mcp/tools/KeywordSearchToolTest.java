package com.wmsay.gpt4_lll.mcp.tools;

import com.wmsay.gpt4_lll.mcp.McpContext;
import com.wmsay.gpt4_lll.mcp.McpToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordSearchToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSearchWithCaseRegexFilePatternAndMaxResultBoundaries() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        Files.write(root.resolve("A.java"), List.of("HELLO world", "a.b literal", "target", "target"));
        Files.write(root.resolve("notes.txt"), List.of("hello WORLD", "target"));

        KeywordSearchTool tool = new KeywordSearchTool();
        McpContext context = new McpContext(null, null, root);

        McpToolResult ignoreCase = tool.execute(context, Map.of(
                "keyword", "hello",
                "ignoreCase", true
        ));
        assertEquals(McpToolResult.ResultType.STRUCTURED, ignoreCase.getType());
        assertEquals(2, ignoreCase.getStructuredData().get("matchCount"));

        McpToolResult literalPattern = tool.execute(context, Map.of(
                "keyword", "a.b",
                "regex", false
        ));
        assertEquals(McpToolResult.ResultType.STRUCTURED, literalPattern.getType());
        assertEquals(1, literalPattern.getStructuredData().get("matchCount"));

        McpToolResult suffixPattern = tool.execute(context, Map.of(
                "keyword", "target",
                "filePattern", ".java"
        ));
        assertEquals(McpToolResult.ResultType.STRUCTURED, suffixPattern.getType());
        assertEquals(2, suffixPattern.getStructuredData().get("matchCount"));

        McpToolResult containsPattern = tool.execute(context, Map.of(
                "keyword", "target",
                "filePattern", "notes"
        ));
        assertEquals(McpToolResult.ResultType.STRUCTURED, containsPattern.getType());
        assertEquals(1, containsPattern.getStructuredData().get("matchCount"));

        McpToolResult maxResultClamp = tool.execute(context, Map.of(
                "keyword", "target",
                "maxResults", 0
        ));
        assertEquals(McpToolResult.ResultType.STRUCTURED, maxResultClamp.getType());
        assertEquals(1, maxResultClamp.getStructuredData().get("matchCount"));
    }

    @Test
    void shouldReturnErrorsForBlankKeywordInvalidRegexPathProblems() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        KeywordSearchTool tool = new KeywordSearchTool();
        McpContext context = new McpContext(null, null, root);

        McpToolResult blankKeyword = tool.execute(context, Map.of("keyword", "   "));
        assertEquals(McpToolResult.ResultType.ERROR, blankKeyword.getType());
        assertTrue(blankKeyword.getErrorMessage().contains("Missing keyword"));

        McpToolResult invalidRegex = tool.execute(context, Map.of(
                "keyword", "[unclosed",
                "regex", true
        ));
        assertEquals(McpToolResult.ResultType.ERROR, invalidRegex.getType());
        assertTrue(invalidRegex.getErrorMessage().contains("Invalid regex"));

        McpToolResult missingPath = tool.execute(context, Map.of(
                "keyword", "x",
                "path", "not-exists"
        ));
        assertEquals(McpToolResult.ResultType.ERROR, missingPath.getType());
        assertTrue(missingPath.getErrorMessage().contains("Path not found"));

        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        McpToolResult outOfWorkspace = tool.execute(context, Map.of(
                "keyword", "x",
                "path", outside.toString()
        ));
        assertEquals(McpToolResult.ResultType.ERROR, outOfWorkspace.getType());
        assertTrue(outOfWorkspace.getErrorMessage().contains("Path out of workspace"));
    }
}
