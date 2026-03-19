package com.wmsay.gpt4_lll.component.block;

import com.wmsay.gpt4_lll.component.TurnPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TurnPanel Mermaid integration.
 * Validates: Requirements 1.1, 1.2
 */
class TurnPanelMermaidUnitTest {

    @Test
    void mermaidCodeFence_createsMermaidBlock() {
        TurnPanel panel = new TurnPanel("assistant");
        // Feed a complete mermaid code fence
        panel.appendContent("```mermaid\n");
        panel.appendContent("graph TD\n");
        panel.appendContent("  A-->B\n");
        panel.appendContent("```\n");
        panel.flushContent();

        // Find the MermaidBlock in the blocks list
        boolean hasMermaidBlock = panel.getBlocks().stream()
                .anyMatch(b -> b.getType() == BlockType.MERMAID);
        assertTrue(hasMermaidBlock, "Should have created a MermaidBlock for mermaid code fence");

        panel.clear();
    }

    @Test
    void nonMermaidCodeFence_createsCodeBlock() throws Exception {
        TurnPanel panel = new TurnPanel("assistant");
        panel.appendContent("```java\n");
        panel.appendContent("System.out.println(\"hello\");\n");
        panel.appendContent("```\n");
        panel.flushContent();

        boolean hasCodeBlock = panel.getBlocks().stream()
                .anyMatch(b -> b.getType() == BlockType.CODE);
        assertTrue(hasCodeBlock, "Should have created a CodeBlock for java code fence");

        boolean hasMermaidBlock = panel.getBlocks().stream()
                .anyMatch(b -> b.getType() == BlockType.MERMAID);
        assertFalse(hasMermaidBlock, "Should NOT have created a MermaidBlock for java code fence");

        // CodeBlock has an internal coalescing Timer (80ms, non-repeating).
        // Wait for it to fire so the IntelliJ test framework doesn't complain
        // about undisposed Swing timers.
        Thread.sleep(150);
        panel.clear();
    }
}
