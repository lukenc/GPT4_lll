package com.wmsay.gpt4_lll.fc.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogSanitizer 单元测试。
 * 验证 API Key 脱敏、文件路径脱敏和用户输入截断。
 */
class LogSanitizerTest {

    // ---- API Key Sanitization ----

    @Test
    void sanitizeApiKey_masksAllButLast4() {
        assertEquals("***6789", LogSanitizer.sanitizeApiKey("sk-abc123456789"));
    }

    @Test
    void sanitizeApiKey_shortKey_returnsOnlyMask() {
        assertEquals("***", LogSanitizer.sanitizeApiKey("abc"));
        assertEquals("***", LogSanitizer.sanitizeApiKey("abcd"));
    }

    @Test
    void sanitizeApiKey_null_returnsNull() {
        assertNull(LogSanitizer.sanitizeApiKey(null));
    }

    @Test
    void sanitizeApiKey_empty_returnsEmpty() {
        assertEquals("", LogSanitizer.sanitizeApiKey(""));
    }

    @Test
    void sanitizeApiKey_exactlyFourChars_returnsOnlyMask() {
        assertEquals("***", LogSanitizer.sanitizeApiKey("1234"));
    }

    @Test
    void sanitizeApiKey_fiveChars_showsLastFour() {
        assertEquals("***2345", LogSanitizer.sanitizeApiKey("12345"));
    }

    // ---- File Path Sanitization ----

    @Test
    void sanitizeFilePath_relativePath_unchanged() {
        assertEquals("src/Main.java",
                LogSanitizer.sanitizeFilePath("src/Main.java", "/project"));
    }

    @Test
    void sanitizeFilePath_absoluteWithProjectRoot_stripsRoot() {
        assertEquals("src/Main.java",
                LogSanitizer.sanitizeFilePath("/Users/dev/project/src/Main.java", "/Users/dev/project"));
    }

    @Test
    void sanitizeFilePath_absoluteWithTrailingSlashRoot_stripsRoot() {
        assertEquals("src/Main.java",
                LogSanitizer.sanitizeFilePath("/Users/dev/project/src/Main.java", "/Users/dev/project/"));
    }

    @Test
    void sanitizeFilePath_absoluteOutsideProject_showsFilenameOnly() {
        String result = LogSanitizer.sanitizeFilePath("/etc/passwd", "/Users/dev/project");
        assertEquals(".../passwd", result);
    }

    @Test
    void sanitizeFilePath_nullProjectRoot_showsFilenameOnly() {
        String result = LogSanitizer.sanitizeFilePath("/home/user/secret.txt", null);
        assertEquals(".../secret.txt", result);
    }

    @Test
    void sanitizeFilePath_null_returnsNull() {
        assertNull(LogSanitizer.sanitizeFilePath(null, "/project"));
    }

    @Test
    void sanitizeFilePath_empty_returnsEmpty() {
        assertEquals("", LogSanitizer.sanitizeFilePath("", "/project"));
    }

    // ---- User Input Sanitization ----

    @Test
    void sanitizeUserInput_shortInput_unchanged() {
        assertEquals("hello world", LogSanitizer.sanitizeUserInput("hello world"));
    }

    @Test
    void sanitizeUserInput_exactlyMaxLength_unchanged() {
        String input = "a".repeat(LogSanitizer.MAX_USER_INPUT_LENGTH);
        assertEquals(input, LogSanitizer.sanitizeUserInput(input));
    }

    @Test
    void sanitizeUserInput_exceedsMaxLength_truncated() {
        String input = "a".repeat(LogSanitizer.MAX_USER_INPUT_LENGTH + 50);
        String result = LogSanitizer.sanitizeUserInput(input);
        assertTrue(result.endsWith("...[truncated]"));
        assertEquals(LogSanitizer.MAX_USER_INPUT_LENGTH + "...[truncated]".length(), result.length());
    }

    @Test
    void sanitizeUserInput_null_returnsNull() {
        assertNull(LogSanitizer.sanitizeUserInput(null));
    }

    // ---- Log Message Sanitization ----

    @Test
    void sanitizeLogMessage_replacesApiKeyPatterns() {
        String msg = "Using key sk-abcdefghijklmnop for request";
        String result = LogSanitizer.sanitizeLogMessage(msg);
        assertFalse(result.contains("abcdefghijklmnop"));
        assertTrue(result.contains("***"));
    }

    @Test
    void sanitizeLogMessage_replacesBearerToken() {
        String msg = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9token";
        String result = LogSanitizer.sanitizeLogMessage(msg);
        assertFalse(result.contains("eyJhbGciOiJIUzI1NiJ9token"));
        assertTrue(result.contains("***"));
    }

    @Test
    void sanitizeLogMessage_noSensitiveData_unchanged() {
        String msg = "Tool read_file executed successfully in 42ms";
        assertEquals(msg, LogSanitizer.sanitizeLogMessage(msg));
    }

    @Test
    void sanitizeLogMessage_null_returnsNull() {
        assertNull(LogSanitizer.sanitizeLogMessage(null));
    }

    @Test
    void sanitizeLogMessage_empty_returnsEmpty() {
        assertEquals("", LogSanitizer.sanitizeLogMessage(""));
    }
}
