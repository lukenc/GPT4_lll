package com.wmsay.gpt4_lll.component.block;

import net.jqwik.api.*;

import java.util.List;

class MermaidBlockPropertyTest {

    /**
     * Property 2: 流式内容累积完整性
     * Feature: mermaid-rendering, Property 2: 流式内容累积完整性
     * Validates: Requirements 2.3
     *
     * For any string sequence [s1, s2, ..., sN], after calling
     * MermaidBlock.appendContent(si) for each, getSourceCode() should
     * return s1 + s2 + ... + sN concatenated.
     */
    @Property(tries = 100)
    @Tag("Feature: mermaid-rendering")
    @Tag("Property 2: 流式内容累积完整性")
    void streamingContentAccumulationCompleteness(@ForAll("stringSequences") List<String> chunks) {
        MermaidBlock block = new MermaidBlock();
        for (String chunk : chunks) {
            block.appendContent(chunk);
        }
        String expected = String.join("", chunks);
        // appendContent updates contentBuilder synchronously;
        // the timer is only for UI (codeArea) updates, so getSourceCode() reflects all content immediately.
        assert block.getSourceCode().equals(expected) :
            "Expected '" + expected + "' but got '" + block.getSourceCode() + "'";
    }

    @Provide
    Arbitrary<List<String>> stringSequences() {
        return Arbitraries.strings()
                .ofMinLength(0).ofMaxLength(50)
                .list().ofMinSize(0).ofMaxSize(20);
    }
}
