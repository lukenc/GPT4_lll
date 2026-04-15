package com.wmsay.gpt4_lll.fc.agent;

import org.junit.jupiter.api.Test;

import com.wmsay.gpt4_lll.fc.core.AgentRuntimeConfig;

import static org.junit.jupiter.api.Assertions.*;

class AgentRuntimeConfigTest {

    @Test
    void defaultConfig_returnsExpectedDefaults() {
        AgentRuntimeConfig config = AgentRuntimeConfig.defaultConfig();
        assertEquals(5, config.getMaxConcurrentSessions());
        assertEquals(3, config.getMaxDelegationDepth());
        assertEquals(120, config.getDelegationTimeoutSeconds());
        assertEquals(600, config.getSessionIdleTimeoutSeconds());
        assertFalse(config.isRecruitMode());
        assertEquals(5, config.getSubAgentContextFallbackMessageCount());
        assertEquals(180, config.getSubAgentTimeoutSeconds());
    }

    @Test
    void builder_customValues() {
        AgentRuntimeConfig config = AgentRuntimeConfig.builder()
                .maxConcurrentSessions(10)
                .maxDelegationDepth(5)
                .delegationTimeoutSeconds(60)
                .sessionIdleTimeoutSeconds(300)
                .recruitMode(true)
                .subAgentContextFallbackMessageCount(10)
                .subAgentTimeoutSeconds(300)
                .build();
        assertEquals(10, config.getMaxConcurrentSessions());
        assertEquals(5, config.getMaxDelegationDepth());
        assertEquals(60, config.getDelegationTimeoutSeconds());
        assertEquals(300, config.getSessionIdleTimeoutSeconds());
        assertTrue(config.isRecruitMode());
        assertEquals(10, config.getSubAgentContextFallbackMessageCount());
        assertEquals(300, config.getSubAgentTimeoutSeconds());
    }

    @Test
    void builder_maxConcurrentSessionsLessThan1_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AgentRuntimeConfig.builder().maxConcurrentSessions(0).build());
        assertTrue(ex.getMessage().contains("maxConcurrentSessions"));
    }

    @Test
    void builder_maxDelegationDepthNegative_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AgentRuntimeConfig.builder().maxDelegationDepth(-1).build());
        assertTrue(ex.getMessage().contains("maxDelegationDepth"));
    }

    @Test
    void builder_maxDelegationDepthZero_allowed() {
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().maxDelegationDepth(0).build();
        assertEquals(0, config.getMaxDelegationDepth());
    }

    @Test
    void builder_maxConcurrentSessionsOne_allowed() {
        AgentRuntimeConfig config = AgentRuntimeConfig.builder().maxConcurrentSessions(1).build();
        assertEquals(1, config.getMaxConcurrentSessions());
    }
}
