package com.wmsay.gpt4_lll.fc.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DegradationManager}.
 * Validates: Requirements 16.1, 16.2, 16.3, 16.5, 16.6
 */
class DegradationManagerTest {

    private DegradationManager manager;

    @BeforeEach
    void setUp() {
        manager = new DegradationManager();
    }

    @Test
    void initialState_notDisabled() {
        assertFalse(manager.isDisabled());
        assertEquals(0.0, manager.getFailureRate());
        assertEquals(0, manager.getTotalAttempts());
        assertEquals(0, manager.getFailedAttempts());
    }

    @Test
    void recordSuccess_updatesCounters() {
        manager.recordParseAttempt(true);
        assertEquals(1, manager.getTotalAttempts());
        assertEquals(0, manager.getFailedAttempts());
        assertEquals(0.0, manager.getFailureRate());
        assertFalse(manager.isDisabled());
    }

    @Test
    void recordFailure_updatesCounters() {
        manager.recordParseAttempt(false);
        assertEquals(1, manager.getTotalAttempts());
        assertEquals(1, manager.getFailedAttempts());
        assertEquals(1.0, manager.getFailureRate());
        // Not disabled yet — below MIN_ATTEMPTS_FOR_DISABLE
        assertFalse(manager.isDisabled());
    }

    @Test
    void failureRateAboveThreshold_disablesAfterMinAttempts() {
        // Record enough failures to exceed threshold and min attempts
        // 4 attempts, all failures → 100% failure rate > 50%
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE; i++) {
            manager.recordParseAttempt(false);
        }
        assertTrue(manager.isDisabled());
        assertEquals(1.0, manager.getFailureRate());
    }

    @Test
    void failureRateBelowThreshold_doesNotDisable() {
        // 4 attempts: 1 failure, 3 successes → 25% failure rate < 50%
        manager.recordParseAttempt(false);
        manager.recordParseAttempt(true);
        manager.recordParseAttempt(true);
        manager.recordParseAttempt(true);
        assertFalse(manager.isDisabled());
        assertEquals(0.25, manager.getFailureRate(), 0.01);
    }

    @Test
    void failureRateExactlyAtThreshold_doesNotDisable() {
        // 4 attempts: 2 failures, 2 successes → exactly 50% — threshold is > 50%, not >=
        manager.recordParseAttempt(false);
        manager.recordParseAttempt(false);
        manager.recordParseAttempt(true);
        manager.recordParseAttempt(true);
        assertFalse(manager.isDisabled());
        assertEquals(0.5, manager.getFailureRate(), 0.01);
    }

    @Test
    void failureRateJustAboveThreshold_disables() {
        // 4 attempts: 3 failures, 1 success → 75% > 50%
        manager.recordParseAttempt(false);
        manager.recordParseAttempt(false);
        manager.recordParseAttempt(false);
        manager.recordParseAttempt(true);
        assertTrue(manager.isDisabled());
        assertEquals(0.75, manager.getFailureRate(), 0.01);
    }

    @Test
    void belowMinAttempts_neverDisables() {
        // Even with 100% failure rate, if below min attempts, should not disable
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE - 1; i++) {
            manager.recordParseAttempt(false);
        }
        assertFalse(manager.isDisabled());
    }

    @Test
    void reset_clearsAllState() {
        // First disable it
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE; i++) {
            manager.recordParseAttempt(false);
        }
        assertTrue(manager.isDisabled());

        // Reset
        manager.reset();
        assertFalse(manager.isDisabled());
        assertEquals(0, manager.getTotalAttempts());
        assertEquals(0, manager.getFailedAttempts());
        assertEquals(0.0, manager.getFailureRate());
    }

    @Test
    void getCurrentModeDescription_disabled() {
        // Disable it
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE; i++) {
            manager.recordParseAttempt(false);
        }
        String desc = manager.getCurrentModeDescription(false);
        assertTrue(desc.contains("disabled"));
        assertTrue(desc.contains("traditional"));
    }

    @Test
    void getCurrentModeDescription_nativeFunctionCalling() {
        String desc = manager.getCurrentModeDescription(true);
        assertTrue(desc.contains("native"));
    }

    @Test
    void getCurrentModeDescription_promptEngineering() {
        String desc = manager.getCurrentModeDescription(false);
        assertTrue(desc.contains("Prompt Engineering"));
    }

    @Test
    void recordDegradationToPromptEngineering_doesNotThrow() {
        // Just verify it doesn't throw — logging is side-effect
        assertDoesNotThrow(() -> manager.recordDegradationToPromptEngineering("unknown-provider"));
    }

    @Test
    void recordDisabledEvent_doesNotThrow() {
        assertDoesNotThrow(() -> manager.recordDisabledEvent("test reason"));
    }

    @Test
    void onceDisabled_staysDisabledEvenWithSuccesses() {
        // Disable it
        for (int i = 0; i < DegradationManager.MIN_ATTEMPTS_FOR_DISABLE; i++) {
            manager.recordParseAttempt(false);
        }
        assertTrue(manager.isDisabled());

        // Record successes — should stay disabled
        manager.recordParseAttempt(true);
        manager.recordParseAttempt(true);
        assertTrue(manager.isDisabled());
    }
}
