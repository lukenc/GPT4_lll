package com.wmsay.gpt4_lll.component.block;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolCallBlock and ToolResultBlock UI components.
 * Tests core logic (collapse state, block type, accessors) without requiring a display.
 */
class ToolBlockUnitTest {

    // ==================== ToolCallBlock Tests ====================

    @Test
    void toolCallBlock_returnsCorrectType() {
        ToolCallBlock block = new ToolCallBlock("call-1", "file_read", Map.of("path", "/tmp"));
        assertEquals(BlockType.TOOL_CALL, block.getType());
    }

    @Test
    void toolCallBlock_isNotAppendable() {
        ToolCallBlock block = new ToolCallBlock("call-1", "file_read", Map.of());
        assertFalse(block.isAppendable());
    }

    @Test
    void toolCallBlock_exposesAccessors() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", "/src/main.java");
        params.put("line", 42);

        ToolCallBlock block = new ToolCallBlock("tc-123", "keyword_search", params);

        assertEquals("tc-123", block.getToolCallId());
        assertEquals("keyword_search", block.getToolName());
        assertEquals(params, block.getParams());
    }

    @Test
    void toolCallBlock_componentIsNotNull() {
        ToolCallBlock block = new ToolCallBlock("call-1", "project_tree", Map.of());
        assertNotNull(block.getComponent());
    }

    @Test
    void toolCallBlock_awaitDecisionReturnsNonNullFuture() {
        ToolCallBlock block = new ToolCallBlock("call-1", "file_read", Map.of());
        assertNotNull(block.awaitDecision());
        assertFalse(block.awaitDecision().isDone());
    }

    // ==================== ToolResultBlock Tests ====================

    @Test
    void toolResultBlock_returnsCorrectType() {
        ToolResultBlock block = new ToolResultBlock("file_read", "file content here");
        assertEquals(BlockType.TOOL_RESULT, block.getType());
    }

    @Test
    void toolResultBlock_isNotAppendable() {
        ToolResultBlock block = new ToolResultBlock("file_read", "content");
        assertFalse(block.isAppendable());
    }

    @Test
    void toolResultBlock_startsCollapsed() {
        ToolResultBlock block = new ToolResultBlock("file_read", "some result");
        assertTrue(block.isCollapsed());
    }

    @Test
    void toolResultBlock_toggleChangesState() {
        ToolResultBlock block = new ToolResultBlock("file_read", "some result");
        assertTrue(block.isCollapsed());

        block.toggleCollapse();
        assertFalse(block.isCollapsed());

        block.toggleCollapse();
        assertTrue(block.isCollapsed());
    }

    @Test
    void toolResultBlock_exposesAccessors() {
        ToolResultBlock block = new ToolResultBlock("keyword_search", "found 3 matches");
        assertEquals("keyword_search", block.getToolName());
        assertEquals("found 3 matches", block.getResultText());
    }

    @Test
    void toolResultBlock_componentIsNotNull() {
        ToolResultBlock block = new ToolResultBlock("project_tree", "tree output");
        assertNotNull(block.getComponent());
    }

    @Test
    void toolResultBlock_handlesMarkdownContent() {
        String markdownResult = "# Results\n```java\npublic class Foo {}\n```\n- item 1\n- item 2";
        ToolResultBlock block = new ToolResultBlock("search", markdownResult);
        // Should not throw; markdown is rendered via flexmark
        assertNotNull(block.getComponent());
        assertEquals(markdownResult, block.getResultText());
    }

    @Test
    void toolResultBlock_handlesEmptyResult() {
        ToolResultBlock block = new ToolResultBlock("empty_tool", "");
        assertNotNull(block.getComponent());
        assertEquals("", block.getResultText());
    }

    @Test
    void toolResultBlock_handlesNullResult() {
        ToolResultBlock block = new ToolResultBlock("null_tool", null);
        assertNotNull(block.getComponent());
        assertNull(block.getResultText());
    }
}
