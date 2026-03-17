package com.wmsay.gpt4_lll.fc.protocol;

import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ToolCallResult;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.model.Message;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProtocolAdapterRegistry}.
 * Validates: Requirements 8.4, 8.5
 */
class ProtocolAdapterRegistryTest {

    // --- getAdapter: native protocol selection (Req 8.4) ---

    @Test
    void getAdapter_openaiProvider_returnsOpenAIAdapter() {
        ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("openai");
        assertNotNull(adapter);
        assertTrue(adapter.supportsNativeFunctionCalling());
        assertEquals("openai", adapter.getName());
    }

    @Test
    void getAdapter_anthropicProvider_returnsAnthropicAdapter() {
        ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("claude-3");
        assertNotNull(adapter);
        assertTrue(adapter.supportsNativeFunctionCalling());
        assertEquals("anthropic", adapter.getName());
    }

    @Test
    void getAdapter_gpt4oProvider_returnsOpenAIAdapter() {
        ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("gpt-4o");
        assertNotNull(adapter);
        assertTrue(adapter.supportsNativeFunctionCalling());
        assertEquals("openai", adapter.getName());
    }

    // --- getAdapter: fallback to Markdown (Req 8.5) ---

    @Test
    void getAdapter_unknownProvider_fallsBackToMarkdown() {
        ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("some-unknown-provider");
        assertNotNull(adapter);
        assertFalse(adapter.supportsNativeFunctionCalling());
        assertEquals("markdown", adapter.getName());
    }

    @Test
    void getAdapter_nullProvider_fallsBackToMarkdown() {
        ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter(null);
        assertNotNull(adapter);
        assertEquals("markdown", adapter.getName());
    }

    // --- register / getAll ---

    @Test
    void getAll_containsPreRegisteredAdapters() {
        Collection<ProtocolAdapter> all = ProtocolAdapterRegistry.getAll();
        assertNotNull(all);
        assertTrue(all.size() >= 3, "Should have at least openai, anthropic, markdown");
    }

    @Test
    void register_customAdapter_canBeRetrievedByName() {
        // Create a simple custom adapter
        ProtocolAdapter custom = new StubProtocolAdapter("custom-test", false);
        ProtocolAdapterRegistry.register(custom);

        try {
            ProtocolAdapter retrieved = ProtocolAdapterRegistry.getByName("custom-test");
            assertNotNull(retrieved);
            assertEquals("custom-test", retrieved.getName());
        } finally {
            ProtocolAdapterRegistry.unregister("custom-test");
        }
    }

    @Test
    void register_customNativeAdapter_selectedForMatchingProvider() {
        ProtocolAdapter custom = new StubProtocolAdapter("custom-native", true) {
            @Override
            public boolean supports(String providerName) {
                return "my-custom-llm".equals(providerName);
            }
        };
        ProtocolAdapterRegistry.register(custom);

        try {
            ProtocolAdapter adapter = ProtocolAdapterRegistry.getAdapter("my-custom-llm");
            assertNotNull(adapter);
            assertEquals("custom-native", adapter.getName());
            assertTrue(adapter.supportsNativeFunctionCalling());
        } finally {
            ProtocolAdapterRegistry.unregister("custom-native");
        }
    }

    @Test
    void register_nullAdapter_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolAdapterRegistry.register(null));
    }

    @Test
    void getByName_nonExistent_returnsNull() {
        assertNull(ProtocolAdapterRegistry.getByName("does-not-exist"));
    }

    @Test
    void getAll_isUnmodifiable() {
        Collection<ProtocolAdapter> all = ProtocolAdapterRegistry.getAll();
        assertThrows(UnsupportedOperationException.class,
                () -> all.clear());
    }

    // --- Stub adapter for testing ---

    private static class StubProtocolAdapter implements ProtocolAdapter {
        private final String name;
        private final boolean nativeSupport;

        StubProtocolAdapter(String name, boolean nativeSupport) {
            this.name = name;
            this.nativeSupport = nativeSupport;
        }

        @Override
        public String getName() { return name; }

        @Override
        public boolean supports(String providerName) { return false; }

        @Override
        public Object formatToolDescriptions(List<McpTool> tools) { return ""; }

        @Override
        public List<ToolCall> parseToolCalls(String response) { return Collections.emptyList(); }

        @Override
        public Message formatToolResult(ToolCallResult result) { return new Message(); }

        @Override
        public boolean supportsNativeFunctionCalling() { return nativeSupport; }
    }
}
