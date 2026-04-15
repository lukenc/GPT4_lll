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
        ToolContext context = ToolContext.builder().workspaceRoot(root).build();

        ToolResult ignoreCase = tool.execute(context, Map.of(
                "keyword", "hello",
                "ignore_case", true
        ));
        assertEquals(ToolResult.ResultType.STRUCTURED, ignoreCase.getType());
        assertEquals(2, ignoreCase.getStructuredData().get("matchCount"));

        ToolResult literalPattern = tool.execute(context, Map.of(
                "keyword", "a.b",
                "regex", false
        ));
        assertEquals(ToolResult.ResultType.STRUCTURED, literalPattern.getType());
        assertEquals(1, literalPattern.getStructuredData().get("matchCount"));

        ToolResult suffixPattern = tool.execute(context, Map.of(
                "keyword", "target",
                "file_pattern", ".java"
        ));
        assertEquals(ToolResult.ResultType.STRUCTURED, suffixPattern.getType());
        assertEquals(2, suffixPattern.getStructuredData().get("matchCount"));

        ToolResult containsPattern = tool.execute(context, Map.of(
                "keyword", "target",
                "file_pattern", "notes"
        ));
        assertEquals(ToolResult.ResultType.STRUCTURED, containsPattern.getType());
        assertEquals(1, containsPattern.getStructuredData().get("matchCount"));

        ToolResult maxResultClamp = tool.execute(context, Map.of(
                "keyword", "target",
                "max_results", 0
        ));
        assertEquals(ToolResult.ResultType.STRUCTURED, maxResultClamp.getType());
        assertEquals(1, maxResultClamp.getStructuredData().get("matchCount"));
    }

    @Test
    void shouldReturnErrorsForBlankKeywordInvalidRegexPathProblems() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("workspace"));
        KeywordSearchTool tool = new KeywordSearchTool();
        ToolContext context = ToolContext.builder().workspaceRoot(root).build();

        ToolResult blankKeyword = tool.execute(context, Map.of("keyword", "   "));
        assertEquals(ToolResult.ResultType.ERROR, blankKeyword.getType());
        assertTrue(blankKeyword.getErrorMessage().contains("Missing keyword"));

        ToolResult invalidRegex = tool.execute(context, Map.of(
                "keyword", "[unclosed",
                "regex", true
        ));
        assertEquals(ToolResult.ResultType.ERROR, invalidRegex.getType());
        assertTrue(invalidRegex.getErrorMessage().contains("Invalid regex"));

        ToolResult missingPath = tool.execute(context, Map.of(
                "keyword", "x",
                "path", "not-exists"
        ));
        assertEquals(ToolResult.ResultType.ERROR, missingPath.getType());
        assertTrue(missingPath.getErrorMessage().contains("Path not found"));

        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        ToolResult outOfWorkspace = tool.execute(context, Map.of(
                "keyword", "x",
                "path", outside.toString()
        ));
        assertEquals(ToolResult.ResultType.ERROR, outOfWorkspace.getType());
        assertTrue(outOfWorkspace.getErrorMessage().contains("Path out of workspace"));
    }
}
