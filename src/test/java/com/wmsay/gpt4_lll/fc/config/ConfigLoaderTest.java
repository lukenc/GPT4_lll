package com.wmsay.gpt4_lll.fc.config;

import com.wmsay.gpt4_lll.fc.model.FunctionCallConfig;
import com.wmsay.gpt4_lll.fc.model.FunctionCallConfig.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    private ConfigLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ConfigLoader(name -> null); // no env vars by default
    }

    // --- loadFromFile tests ---

    @Test
    void loadFromFile_validJson_returnsConfig(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            {
              "defaultTimeout": 60,
              "maxRetries": 5,
              "maxRounds": 10,
              "enableApproval": false,
              "enableFunctionCalling": false,
              "logLevel": "DEBUG"
            }
            """);

        FunctionCallConfig config = loader.loadFromFile(configFile);

        assertEquals(60, config.getDefaultTimeout());
        assertEquals(5, config.getMaxRetries());
        assertEquals(10, config.getMaxRounds());
        assertFalse(config.isEnableApproval());
        assertFalse(config.isEnableFunctionCalling());
        assertEquals(LogLevel.DEBUG, config.getLogLevel());
    }

    @Test
    void loadFromFile_partialJson_usesDefaultsForMissing(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "defaultTimeout": 45 }
            """);

        FunctionCallConfig config = loader.loadFromFile(configFile);

        assertEquals(45, config.getDefaultTimeout());
        assertEquals(3, config.getMaxRetries());
        assertEquals(20, config.getMaxRounds());
        assertTrue(config.isEnableApproval());
        assertTrue(config.isEnableFunctionCalling());
        assertEquals(LogLevel.INFO, config.getLogLevel());
    }

    @Test
    void loadFromFile_nonExistentPath_returnsDefault() {
        FunctionCallConfig config = loader.loadFromFile(Path.of("/nonexistent/config.json"));
        assertDefaultConfig(config);
    }

    @Test
    void loadFromFile_nullPath_returnsDefault() {
        FunctionCallConfig config = loader.loadFromFile(null);
        assertDefaultConfig(config);
    }

    @Test
    void loadFromFile_invalidJson_returnsDefault(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "not valid json {{{");

        FunctionCallConfig config = loader.loadFromFile(configFile);
        assertDefaultConfig(config);
    }

    @Test
    void loadFromFile_emptyFile_returnsDefault(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "");

        FunctionCallConfig config = loader.loadFromFile(configFile);
        assertDefaultConfig(config);
    }

    // --- Validation fallback tests (Req 18.5, 18.6) ---

    @Test
    void loadFromFile_invalidTimeout_returnsDefault(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "defaultTimeout": -1 }
            """);

        FunctionCallConfig config = loader.loadFromFile(configFile);
        assertDefaultConfig(config);
    }

    @Test
    void loadFromFile_negativeRetries_returnsDefault(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "maxRetries": -5 }
            """);

        FunctionCallConfig config = loader.loadFromFile(configFile);
        assertDefaultConfig(config);
    }

    @Test
    void loadFromFile_zeroMaxRounds_returnsDefault(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "maxRounds": 0 }
            """);

        FunctionCallConfig config = loader.loadFromFile(configFile);
        assertDefaultConfig(config);
    }

    @Test
    void loadFromFile_invalidLogLevel_usesDefaultLevel(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "logLevel": "TRACE" }
            """);

        FunctionCallConfig config = loader.loadFromFile(configFile);
        assertEquals(LogLevel.INFO, config.getLogLevel());
    }

    @Test
    void loadFromFile_caseInsensitiveLogLevel(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "logLevel": "debug" }
            """);

        FunctionCallConfig config = loader.loadFromFile(configFile);
        assertEquals(LogLevel.DEBUG, config.getLogLevel());
    }

    // --- loadFromEnv tests (Req 16.4, 18.2) ---

    @Test
    void loadFromEnv_noEnvVars_returnsDefault() {
        ConfigLoader noEnvLoader = new ConfigLoader(name -> null);
        FunctionCallConfig config = noEnvLoader.loadFromEnv();
        assertDefaultConfig(config);
    }

    @Test
    void loadFromEnv_enabledTrue_setsFunctionCalling() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_ENABLED".equals(name) ? "true" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertTrue(config.isEnableFunctionCalling());
    }

    @Test
    void loadFromEnv_enabledFalse_disablesFunctionCalling() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_ENABLED".equals(name) ? "false" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertFalse(config.isEnableFunctionCalling());
    }

    @Test
    void loadFromEnv_enabledCaseInsensitive() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_ENABLED".equals(name) ? "TRUE" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertTrue(config.isEnableFunctionCalling());
    }

    @Test
    void loadFromEnv_logLevel_setsLevel() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_LOG_LEVEL".equals(name) ? "DEBUG" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertEquals(LogLevel.DEBUG, config.getLogLevel());
    }

    @Test
    void loadFromEnv_logLevel_caseInsensitive() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_LOG_LEVEL".equals(name) ? "error" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertEquals(LogLevel.ERROR, config.getLogLevel());
    }

    @Test
    void loadFromEnv_logLevel_invalid_usesDefault() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_LOG_LEVEL".equals(name) ? "TRACE" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertEquals(LogLevel.INFO, config.getLogLevel());
    }

    @Test
    void loadFromEnv_maxRounds_setsValue() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_MAX_ROUNDS".equals(name) ? "50" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertEquals(50, config.getMaxRounds());
    }

    @Test
    void loadFromEnv_maxRounds_invalidNumber_usesDefault() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_MAX_ROUNDS".equals(name) ? "abc" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertEquals(20, config.getMaxRounds());
    }

    @Test
    void loadFromEnv_maxRounds_zero_usesDefault() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_MAX_ROUNDS".equals(name) ? "0" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertEquals(20, config.getMaxRounds());
    }

    @Test
    void loadFromEnv_maxRounds_negative_usesDefault() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_MAX_ROUNDS".equals(name) ? "-5" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertEquals(20, config.getMaxRounds());
    }

    @Test
    void loadFromEnv_traceExport_true() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_TRACE_EXPORT".equals(name) ? "true" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertTrue(config.isTraceExportEnabled());
    }

    @Test
    void loadFromEnv_traceExport_false() {
        ConfigLoader envLoader = new ConfigLoader(name ->
                "LLL_FC_TRACE_EXPORT".equals(name) ? "false" : null);
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertFalse(config.isTraceExportEnabled());
    }

    @Test
    void loadFromEnv_allVarsSet() {
        Map<String, String> env = Map.of(
                "LLL_FC_ENABLED", "false",
                "LLL_FC_LOG_LEVEL", "WARN",
                "LLL_FC_MAX_ROUNDS", "5",
                "LLL_FC_TRACE_EXPORT", "true"
        );
        ConfigLoader envLoader = new ConfigLoader(env::get);
        FunctionCallConfig config = envLoader.loadFromEnv();

        assertFalse(config.isEnableFunctionCalling());
        assertEquals(LogLevel.WARN, config.getLogLevel());
        assertEquals(5, config.getMaxRounds());
        assertTrue(config.isTraceExportEnabled());
    }

    @Test
    void loadFromEnv_blankValues_usesDefaults() {
        ConfigLoader envLoader = new ConfigLoader(name -> "  ");
        FunctionCallConfig config = envLoader.loadFromEnv();
        assertDefaultConfig(config);
    }

    // --- load (combined / merge) tests (Req 16.4, 18.2) ---

    @Test
    void load_validFile_returnsFileConfig(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "defaultTimeout": 90, "logLevel": "ERROR" }
            """);

        FunctionCallConfig config = loader.load(configFile);

        assertEquals(90, config.getDefaultTimeout());
        assertEquals(LogLevel.ERROR, config.getLogLevel());
    }

    @Test
    void load_nullPath_returnsDefault() {
        FunctionCallConfig config = loader.load(null);
        assertDefaultConfig(config);
    }

    @Test
    void load_envOverridesFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "maxRounds": 10, "logLevel": "DEBUG", "enableFunctionCalling": true }
            """);

        Map<String, String> env = new HashMap<>();
        env.put("LLL_FC_LOG_LEVEL", "ERROR");
        env.put("LLL_FC_MAX_ROUNDS", "50");
        env.put("LLL_FC_ENABLED", "false");

        ConfigLoader mergeLoader = new ConfigLoader(env::get);
        FunctionCallConfig config = mergeLoader.load(configFile);

        // env overrides file
        assertEquals(LogLevel.ERROR, config.getLogLevel());
        assertEquals(50, config.getMaxRounds());
        assertFalse(config.isEnableFunctionCalling());
        // file value preserved for non-overridden fields
        assertEquals(30, config.getDefaultTimeout());
    }

    @Test
    void load_envPartialOverride_preservesFileValues(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "defaultTimeout": 60, "maxRounds": 15, "logLevel": "DEBUG" }
            """);

        // Only override logLevel via env
        ConfigLoader partialEnvLoader = new ConfigLoader(name ->
                "LLL_FC_LOG_LEVEL".equals(name) ? "WARN" : null);
        FunctionCallConfig config = partialEnvLoader.load(configFile);

        assertEquals(60, config.getDefaultTimeout());
        assertEquals(15, config.getMaxRounds());
        assertEquals(LogLevel.WARN, config.getLogLevel()); // overridden
    }

    @Test
    void load_invalidEnvValue_keepsFileValue(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            { "logLevel": "DEBUG", "maxRounds": 15 }
            """);

        Map<String, String> env = new HashMap<>();
        env.put("LLL_FC_LOG_LEVEL", "INVALID_LEVEL");
        env.put("LLL_FC_MAX_ROUNDS", "not_a_number");

        ConfigLoader badEnvLoader = new ConfigLoader(env::get);
        FunctionCallConfig config = badEnvLoader.load(configFile);

        // Invalid env values should keep file values
        assertEquals(LogLevel.DEBUG, config.getLogLevel());
        assertEquals(15, config.getMaxRounds());
    }

    @Test
    void load_traceExportFromEnv(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{}");

        ConfigLoader traceLoader = new ConfigLoader(name ->
                "LLL_FC_TRACE_EXPORT".equals(name) ? "true" : null);
        FunctionCallConfig config = traceLoader.load(configFile);

        assertTrue(config.isTraceExportEnabled());
    }

    // --- parseJson edge cases ---

    @Test
    void parseJson_emptyObject_returnsAllDefaults() {
        FunctionCallConfig config = loader.parseJson("{}");
        assertDefaultConfig(config);
    }

    @Test
    void parseJson_zeroRetries_isValid() {
        FunctionCallConfig config = loader.parseJson("""
            { "maxRetries": 0 }
            """);
        assertEquals(0, config.getMaxRetries());
    }

    // --- Helper ---

    private void assertDefaultConfig(FunctionCallConfig config) {
        assertNotNull(config);
        assertEquals(30, config.getDefaultTimeout());
        assertEquals(3, config.getMaxRetries());
        assertEquals(20, config.getMaxRounds());
        assertTrue(config.isEnableApproval());
        assertTrue(config.isEnableFunctionCalling());
        assertEquals(LogLevel.INFO, config.getLogLevel());
        assertFalse(config.isTraceExportEnabled());
    }
}
