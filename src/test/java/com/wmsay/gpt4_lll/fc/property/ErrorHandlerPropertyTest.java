package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;

import com.wmsay.gpt4_lll.fc.core.ErrorMessage;
import com.wmsay.gpt4_lll.fc.error.*;
import com.wmsay.gpt4_lll.fc.model.ValidationError;
import com.wmsay.gpt4_lll.fc.tools.ErrorHandler;
import com.wmsay.gpt4_lll.mcp.*;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 属性测试: ErrorHandler
 * <p>
 * 验证工具不存在错误内容、验证错误完整性、验证错误建议和异常分类。
 * <p>
 * Validates: Requirements 4.2, 4.3, 5.1, 5.2, 5.3, 6.2
 */
class ErrorHandlerPropertyTest {

    private static final String TOOL_PREFIX = "__errtest_";

    private ErrorHandler handler;
    private final List<String> registeredToolNames = new ArrayList<>();

    @BeforeProperty
    void setup() {
        // Use a supplier that reads from McpToolRegistry so registered tools are visible
        handler = new ErrorHandler(
                () -> McpToolRegistry.getAllTools().stream()
                        .map(Tool::name)
                        .collect(java.util.stream.Collectors.toList()));
        // Clean up any previously registered test tools
        registeredToolNames.clear();
    }

    // ---------------------------------------------------------------
    // Property 14: Tool Not Found Error Content
    // Validates: Requirements 4.2, 4.3
    // ---------------------------------------------------------------

    /**
     * Property 14: Tool Not Found Error Content
     * **Validates: Requirements 4.2, 4.3**
     *
     * For any non-existent tool name, the error message should contain
     * the requested tool name, all available tool names, and a suggestion
     * for the most similar tool name.
     */
    @Property(tries = 80)
    @Label("Feature: function-calling-enhancement, Property 14: Tool Not Found Error Content")
    void toolNotFoundErrorShouldContainRequiredContent(
            @ForAll("toolNotFoundCases") ToolNotFoundCase testCase) {

        // Register available tools
        for (String toolName : testCase.availableTools) {
            String fullName = TOOL_PREFIX + toolName;
            Tool tool = new StubMcpTool(fullName, "test tool", Collections.emptyMap());
            McpToolRegistry.registerTool(tool);
            registeredToolNames.add(fullName);
        }

        String requestedName = TOOL_PREFIX + testCase.requestedTool;
        ToolNotFoundException ex = new ToolNotFoundException(requestedName);

        ErrorMessage result = handler.handleToolNotFound(ex);

        // Type should be "tool_not_found"
        assert "tool_not_found".equals(result.getType()) :
                "Expected type 'tool_not_found' but got '" + result.getType() + "'";

        // Message should contain the requested tool name
        assert result.getMessage() != null && result.getMessage().contains(requestedName) :
                "Error message should contain requested tool name '" + requestedName +
                        "' but got: " + result.getMessage();

        // Details should contain requested_tool
        Map<String, Object> details = result.getDetails();
        assert details != null : "Details should not be null";
        assert requestedName.equals(details.get("requested_tool")) :
                "Details should contain requested_tool '" + requestedName + "'";

        // Details should contain available_tools list
        Object availableToolsObj = details.get("available_tools");
        assert availableToolsObj instanceof List :
                "Details should contain available_tools as a List";
        @SuppressWarnings("unchecked")
        List<String> availableToolsList = (List<String>) availableToolsObj;
        // All registered tools (including our test tools) should be present
        for (String toolName : testCase.availableTools) {
            String fullToolName = TOOL_PREFIX + toolName;
            assert availableToolsList.contains(fullToolName) :
                    "Available tools list should contain '" + fullToolName + "'";
        }

        // If there are available tools, there should be a suggestion
        if (!testCase.availableTools.isEmpty()) {
            Object suggestion = details.get("suggestion");
            assert suggestion != null :
                    "Details should contain a suggestion when tools are available";

            // Suggestion should also appear in the suggestion text
            assert result.getSuggestion() != null && !result.getSuggestion().isEmpty() :
                    "Suggestion text should not be empty when tools are available";
        }
    }

    // ---------------------------------------------------------------
    // Property 18: Validation Error Completeness
    // Validates: Requirements 5.1, 5.2
    // ---------------------------------------------------------------

    /**
     * Property 18: Validation Error Completeness
     * **Validates: Requirements 5.1, 5.2**
     *
     * For any tool call with multiple parameter validation failures,
     * the error report should contain details for ALL failed parameters,
     * including parameter name, error type, expected value, and actual value.
     */
    @Property(tries = 80)
    @Label("Feature: function-calling-enhancement, Property 18: Validation Error Completeness")
    void validationErrorShouldContainAllErrorDetails(
            @ForAll("validationErrorCases") ValidationErrorCase testCase) {

        ValidationException ex = new ValidationException(testCase.errors);
        ErrorMessage result = handler.handleValidationError(ex);

        // Type should be "validation_error"
        assert "validation_error".equals(result.getType()) :
                "Expected type 'validation_error' but got '" + result.getType() + "'";

        // Details should contain errors list
        Map<String, Object> details = result.getDetails();
        assert details != null : "Details should not be null";

        Object errorsObj = details.get("errors");
        assert errorsObj instanceof List :
                "Details should contain 'errors' as a List";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errorDetails = (List<Map<String, Object>>) errorsObj;

        // Error count should match
        assert errorDetails.size() == testCase.errors.size() :
                "Expected " + testCase.errors.size() + " error details but got " + errorDetails.size();

        Object errorCount = details.get("error_count");
        assert errorCount != null && ((Number) errorCount).intValue() == testCase.errors.size() :
                "error_count should be " + testCase.errors.size();

        // Each error detail should contain field, error_type, expected, actual
        for (int i = 0; i < testCase.errors.size(); i++) {
            ValidationError originalError = testCase.errors.get(i);
            Map<String, Object> detail = errorDetails.get(i);

            assert detail.containsKey("field") :
                    "Error detail " + i + " should contain 'field'";
            assert originalError.getFieldName().equals(detail.get("field")) :
                    "Error detail " + i + " field should be '" + originalError.getFieldName() +
                            "' but got '" + detail.get("field") + "'";

            assert detail.containsKey("error_type") :
                    "Error detail " + i + " should contain 'error_type'";

            assert detail.containsKey("expected") :
                    "Error detail " + i + " should contain 'expected'";

            assert detail.containsKey("actual") :
                    "Error detail " + i + " should contain 'actual'";
        }
    }

    // ---------------------------------------------------------------
    // Property 19: Validation Error Suggestions
    // Validates: Requirements 5.3
    // ---------------------------------------------------------------

    /**
     * Property 19: Validation Error Suggestions
     * **Validates: Requirements 5.3**
     *
     * For any parameter validation error, the error message should include
     * actionable suggestions for fixing each error.
     */
    @Property(tries = 80)
    @Label("Feature: function-calling-enhancement, Property 19: Validation Error Suggestions")
    void validationErrorShouldIncludeSuggestions(
            @ForAll("validationErrorWithSuggestionCases") ValidationErrorCase testCase) {

        ValidationException ex = new ValidationException(testCase.errors);
        ErrorMessage result = handler.handleValidationError(ex);

        // The overall suggestion should not be null or empty
        assert result.getSuggestion() != null && !result.getSuggestion().isEmpty() :
                "Validation error should include a suggestion";

        // The suggestion should reference each error's field name or suggestion
        for (ValidationError error : testCase.errors) {
            String fieldName = error.getFieldName();
            String errorSuggestion = error.getSuggestion();

            // The overall suggestion should mention the field or include the per-error suggestion
            boolean mentionsField = fieldName != null && result.getSuggestion().contains(fieldName);
            boolean mentionsSuggestion = errorSuggestion != null && result.getSuggestion().contains(errorSuggestion);

            assert mentionsField || mentionsSuggestion :
                    "Suggestion should reference field '" + fieldName +
                            "' or include per-error suggestion '" + errorSuggestion +
                            "' but got: " + result.getSuggestion();
        }
    }

    // ---------------------------------------------------------------
    // Property 9: Error classification and structured messages
    // Validates: Requirements 3.10, 14.1
    // ---------------------------------------------------------------

    /**
     * Property 9: Error classification and structured messages
     * **Validates: Requirements 3.10, 14.1**
     *
     * For any ToolNotFoundException, ValidationException, TimeoutException,
     * or ConcurrentExecutionException, ErrorHandler should produce an ErrorMessage
     * with the correct type field, and non-null message and suggestion fields.
     */
    @Property(tries = 80)
    @Tag("Feature: agent-framework-extraction, Property 9: Error classification and structured messages")
    void errorHandlerShouldClassifyFourExceptionTypesCorrectly(
            @ForAll("fourExceptionTypes") ClassifiedExceptionCase testCase) {

        ErrorMessage result = testCase.handler.apply(handler);

        // Type should match expected classification
        assert testCase.expectedType.equals(result.getType()) :
                "Exception " + testCase.label + " should produce type '" + testCase.expectedType +
                        "' but got '" + result.getType() + "'";

        // Message should be non-null and non-empty
        assert result.getMessage() != null && !result.getMessage().isEmpty() :
                "ErrorMessage.message should be non-null and non-empty for " + testCase.label +
                        " but got: " + result.getMessage();

        // Suggestion should be non-null and non-empty
        assert result.getSuggestion() != null && !result.getSuggestion().isEmpty() :
                "ErrorMessage.suggestion should be non-null and non-empty for " + testCase.label +
                        " but got: " + result.getSuggestion();

        // Details should be non-null
        assert result.getDetails() != null :
                "ErrorMessage.details should be non-null for " + testCase.label;
    }

    /**
     * Property 9 (supplement): Each of the four exception types should produce
     * a distinct error type, confirming correct classification differentiation.
     */
    @Property(tries = 50)
    @Tag("Feature: agent-framework-extraction, Property 9: Error classification and structured messages")
    void fourExceptionTypesShouldProduceDistinctErrorTypes() {
        // Create one of each exception type and verify they produce different types
        ErrorHandler localHandler = new ErrorHandler(() -> List.of("read_file", "write_file"));

        ErrorMessage toolNotFound = localHandler.handleToolNotFound(
                new ToolNotFoundException("nonexistent_tool"));
        ErrorMessage validation = localHandler.handleValidationError(
                new ValidationException(List.of(ValidationError.missingRequired("path"))));
        ErrorMessage timeout = localHandler.handleTimeout(
                new TimeoutException("timed out"));
        ErrorMessage concurrent = localHandler.handleConcurrentExecution(
                new ConcurrentExecutionException("already running"));

        Set<String> types = new HashSet<>(Arrays.asList(
                toolNotFound.getType(), validation.getType(),
                timeout.getType(), concurrent.getType()));

        assert types.size() == 4 :
                "Four exception types should produce 4 distinct error types but got " +
                        types.size() + ": " + types;
    }

    // ---------------------------------------------------------------
    // Property 10: Levenshtein distance tool suggestion
    // Validates: Requirements 14.2
    // ---------------------------------------------------------------

    /**
     * Property 10: Levenshtein distance tool suggestion
     * **Validates: Requirements 14.2**
     *
     * For any tool name and available tools list, findMostSimilar() should
     * return the tool name with the minimum Levenshtein distance.
     */
    @Property(tries = 100)
    @Tag("Feature: agent-framework-extraction, Property 10: Levenshtein distance tool suggestion")
    void findMostSimilarShouldReturnMinimumDistanceTool(
            @ForAll("levenshteinCases") LevenshteinCase testCase) {

        String result = handler.findMostSimilar(testCase.target, testCase.candidates);

        if (testCase.candidates == null || testCase.candidates.isEmpty()) {
            assert result == null :
                    "findMostSimilar should return null for empty candidates but got: " + result;
        } else {
            // Result must be from the candidates list
            assert testCase.candidates.contains(result) :
                    "Suggestion '" + result + "' should be from candidates " + testCase.candidates;

            // Result should have the minimum Levenshtein distance
            int resultDistance = handler.levenshteinDistance(
                    testCase.target.toLowerCase(), result.toLowerCase());
            for (String candidate : testCase.candidates) {
                int candidateDistance = handler.levenshteinDistance(
                        testCase.target.toLowerCase(), candidate.toLowerCase());
                assert resultDistance <= candidateDistance :
                        "Suggestion '" + result + "' (distance=" + resultDistance +
                                ") should have distance <= '" + candidate +
                                "' (distance=" + candidateDistance + ") for target '" + testCase.target + "'";
            }
        }
    }

    /**
     * Property 10 (supplement): When the exact tool name exists in the list,
     * it should be returned as the suggestion (distance = 0).
     */
    @Property(tries = 50)
    @Tag("Feature: agent-framework-extraction, Property 10: Levenshtein distance tool suggestion")
    void exactMatchShouldBeReturnedAsSuggestion(
            @ForAll("exactMatchCases") LevenshteinCase testCase) {

        String result = handler.findMostSimilar(testCase.target, testCase.candidates);

        assert testCase.target.equals(result) :
                "When exact match exists, findMostSimilar should return '" + testCase.target +
                        "' but got '" + result + "'";
    }

    /**
     * Property 10 (supplement): For typos with 1-2 character differences,
     * the correct tool should be suggested.
     */
    @Property(tries = 50)
    @Tag("Feature: agent-framework-extraction, Property 10: Levenshtein distance tool suggestion")
    void typosShouldSuggestCorrectTool(
            @ForAll("typoCases") TypoCase testCase) {

        String result = handler.findMostSimilar(testCase.typo, testCase.candidates);

        assert testCase.expectedSuggestion.equals(result) :
                "For typo '" + testCase.typo + "', should suggest '" + testCase.expectedSuggestion +
                        "' but got '" + result + "'";
    }

    // ---------------------------------------------------------------
    // Property 22: Exception Classification
    // Validates: Requirements 6.2
    // ---------------------------------------------------------------

    /**
     * Property 22: Exception Classification
     * **Validates: Requirements 6.2**
     *
     * The ErrorHandler should correctly classify exceptions into categories:
     * timeout, permission, io, business_logic. We verify this through
     * handleGenericError which uses classifyException internally and
     * exposes the classification as the ErrorMessage type.
     */
    @Property(tries = 80)
    @Label("Feature: function-calling-enhancement, Property 22: Exception Classification")
    void exceptionsShouldBeCorrectlyClassified(
            @ForAll("classifiableExceptions") ClassifiableException testCase) {

        ErrorMessage result = handler.handleGenericError(testCase.exception);

        // The type field reflects the classification
        assert testCase.expectedCategory.equals(result.getType()) :
                "Exception " + testCase.exception.getClass().getSimpleName() +
                        " should be classified as '" + testCase.expectedCategory +
                        "' but got '" + result.getType() + "'";

        // Details should contain error_type matching the classification
        Map<String, Object> details = result.getDetails();
        assert details != null : "Details should not be null";
        assert testCase.expectedCategory.equals(details.get("error_type")) :
                "Details error_type should be '" + testCase.expectedCategory +
                        "' but got '" + details.get("error_type") + "'";

        // Details should contain exception_class
        assert details.containsKey("exception_class") :
                "Details should contain 'exception_class'";

        // Suggestion should not be empty
        assert result.getSuggestion() != null && !result.getSuggestion().isEmpty() :
                "Suggestion should not be empty for generic errors";
    }

    /**
     * Property 22 (supplement): The handle() dispatcher should route
     * generic exceptions through handleGenericError with correct classification.
     * Note: TimeoutException is routed to handleTimeout by handle(), so we
     * only test exceptions that go through handleGenericError here.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 22: handle() dispatches generic exceptions correctly")
    void handleShouldDispatchGenericExceptionsCorrectly(
            @ForAll("genericOnlyExceptions") ClassifiableException testCase) {

        ErrorMessage result = handler.handle(testCase.exception);

        assert testCase.expectedCategory.equals(result.getType()) :
                "handle() should classify " + testCase.exception.getClass().getSimpleName() +
                        " as '" + testCase.expectedCategory + "' but got '" + result.getType() + "'";
    }

    // ---------------------------------------------------------------
    // Test case record types
    // ---------------------------------------------------------------

    static class ToolNotFoundCase {
        final String requestedTool;
        final List<String> availableTools;

        ToolNotFoundCase(String requestedTool, List<String> availableTools) {
            this.requestedTool = requestedTool;
            this.availableTools = availableTools;
        }

        @Override
        public String toString() {
            return "ToolNotFound{requested=" + requestedTool + ", available=" + availableTools + "}";
        }
    }

    static class ValidationErrorCase {
        final List<ValidationError> errors;

        ValidationErrorCase(List<ValidationError> errors) {
            this.errors = errors;
        }

        @Override
        public String toString() {
            return "ValidationErrors{count=" + errors.size() + ", fields=" +
                    errors.stream().map(ValidationError::getFieldName).collect(Collectors.toList()) + "}";
        }
    }

    static class ClassifiableException {
        final Throwable exception;
        final String expectedCategory;

        ClassifiableException(Throwable exception, String expectedCategory) {
            this.exception = exception;
            this.expectedCategory = expectedCategory;
        }

        @Override
        public String toString() {
            return "Classifiable{class=" + exception.getClass().getSimpleName() +
                    ", expected=" + expectedCategory + "}";
        }
    }

    static class ClassifiedExceptionCase {
        final String label;
        final String expectedType;
        final java.util.function.Function<ErrorHandler, ErrorMessage> handler;

        ClassifiedExceptionCase(String label, String expectedType,
                                java.util.function.Function<ErrorHandler, ErrorMessage> handler) {
            this.label = label;
            this.expectedType = expectedType;
            this.handler = handler;
        }

        @Override
        public String toString() {
            return "ClassifiedExc{label=" + label + ", expectedType=" + expectedType + "}";
        }
    }

    static class LevenshteinCase {
        final String target;
        final List<String> candidates;

        LevenshteinCase(String target, List<String> candidates) {
            this.target = target;
            this.candidates = candidates;
        }

        @Override
        public String toString() {
            return "Levenshtein{target=" + target + ", candidates=" + candidates + "}";
        }
    }

    static class TypoCase {
        final String typo;
        final String expectedSuggestion;
        final List<String> candidates;

        TypoCase(String typo, String expectedSuggestion, List<String> candidates) {
            this.typo = typo;
            this.expectedSuggestion = expectedSuggestion;
            this.candidates = candidates;
        }

        @Override
        public String toString() {
            return "Typo{typo=" + typo + ", expected=" + expectedSuggestion + "}";
        }
    }

    // ---------------------------------------------------------------
    // Providers (Arbitraries)
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<ClassifiedExceptionCase> fourExceptionTypes() {
        return Arbitraries.oneOf(
                // ToolNotFoundException → "tool_not_found"
                Arbitraries.of("unknown_tool", "bad_tool", "missing_func", "no_such_tool")
                        .map(name -> new ClassifiedExceptionCase(
                                "ToolNotFoundException(" + name + ")",
                                "tool_not_found",
                                h -> h.handleToolNotFound(new ToolNotFoundException(name)))),

                // ValidationException → "validation_error"
                Arbitraries.of("path", "query", "content", "mode")
                        .map(field -> new ClassifiedExceptionCase(
                                "ValidationException(field=" + field + ")",
                                "validation_error",
                                h -> h.handleValidationError(
                                        new ValidationException(List.of(ValidationError.missingRequired(field)))))),

                // TimeoutException → "timeout"
                Arbitraries.of("operation timed out", "read timeout", "connect timeout")
                        .map(msg -> new ClassifiedExceptionCase(
                                "TimeoutException(" + msg + ")",
                                "timeout",
                                h -> h.handleTimeout(new TimeoutException(msg)))),

                // ConcurrentExecutionException → "concurrent_execution"
                Arbitraries.of("tool already running", "lock held", "concurrent conflict")
                        .map(msg -> new ClassifiedExceptionCase(
                                "ConcurrentExecutionException(" + msg + ")",
                                "concurrent_execution",
                                h -> h.handleConcurrentExecution(new ConcurrentExecutionException(msg))))
        );
    }

    @Provide
    Arbitrary<LevenshteinCase> levenshteinCases() {
        Arbitrary<String> targets = Arbitraries.of(
                "read_flie", "writ_file", "serch", "projct_tree",
                "keyword_serch", "shel_exec", "grep_tol", "file_reed"
        );

        Arbitrary<List<String>> candidateLists = Arbitraries.of(
                "read_file", "write_file", "search", "project_tree",
                "keyword_search", "shell_exec", "grep_tool"
        ).list().ofMinSize(1).ofMaxSize(5).uniqueElements();

        // Also include empty list case
        return Arbitraries.oneOf(
                Combinators.combine(targets, candidateLists).as(LevenshteinCase::new),
                targets.map(t -> new LevenshteinCase(t, Collections.emptyList()))
        );
    }

    @Provide
    Arbitrary<LevenshteinCase> exactMatchCases() {
        // Target is always present in the candidates list
        Arbitrary<String> tools = Arbitraries.of(
                "read_file", "write_file", "search", "project_tree",
                "keyword_search", "shell_exec", "grep_tool"
        );

        return tools.flatMap(tool -> {
            // Build a candidates list that always contains the exact tool
            Arbitrary<List<String>> otherTools = Arbitraries.of(
                    "read_file", "write_file", "search", "project_tree",
                    "keyword_search", "shell_exec", "grep_tool"
            ).list().ofMinSize(0).ofMaxSize(4).uniqueElements();

            return otherTools.map(others -> {
                List<String> candidates = new ArrayList<>(others);
                if (!candidates.contains(tool)) {
                    candidates.add(tool);
                }
                Collections.shuffle(candidates);
                return new LevenshteinCase(tool, candidates);
            });
        });
    }

    @Provide
    Arbitrary<TypoCase> typoCases() {
        // Predefined typo → correct mappings with 1-2 char differences
        List<String> allTools = List.of("read_file", "write_file", "search",
                "project_tree", "keyword_search", "shell_exec");

        return Arbitraries.of(
                new TypoCase("read_flie", "read_file", allTools),    // swap: i↔l
                new TypoCase("writ_file", "write_file", allTools),   // missing: e
                new TypoCase("serch", "search", allTools),           // missing: a
                new TypoCase("shel_exec", "shell_exec", allTools),   // missing: l
                new TypoCase("keyword_serch", "keyword_search", allTools), // missing: a
                new TypoCase("project_tre", "project_tree", allTools),     // missing: e
                new TypoCase("reed_file", "read_file", allTools),    // extra: e→ee
                new TypoCase("write_fiel", "write_file", allTools)   // swap: l↔e
        );
    }

    @Provide
    Arbitrary<ToolNotFoundCase> toolNotFoundCases() {
        Arbitrary<String> requestedTools = Arbitraries.of(
                "file_reed", "serch_file", "projct_tree", "grep_tool",
                "read_flie", "keyword_serch", "tree_project"
        );

        Arbitrary<List<String>> availableToolLists = Arbitraries.of(
                "file_read", "keyword_search", "project_tree", "grep"
        ).list().ofMinSize(1).ofMaxSize(4).uniqueElements();

        return Combinators.combine(requestedTools, availableToolLists)
                .as(ToolNotFoundCase::new);
    }

    @Provide
    Arbitrary<ValidationErrorCase> validationErrorCases() {
        Arbitrary<ValidationError> errorArbitrary = Arbitraries.oneOf(
                // MISSING_REQUIRED errors
                Arbitraries.of("path", "query", "content", "mode", "depth").map(
                        field -> ValidationError.missingRequired(field)
                ),
                // TYPE_MISMATCH errors
                Combinators.combine(
                        Arbitraries.of("path", "count", "flag", "score"),
                        Arbitraries.of("string", "integer", "boolean", "number"),
                        Arbitraries.of("integer", "string", "boolean", "number")
                ).as((field, expected, actual) ->
                        ValidationError.typeMismatch(field, expected, actual, "badValue")
                ),
                // OUT_OF_RANGE errors
                Combinators.combine(
                        Arbitraries.of("count", "depth", "limit"),
                        Arbitraries.of("0-100", "1-50", "0-1000"),
                        Arbitraries.integers().between(-100, 500)
                ).as((field, range, value) ->
                        ValidationError.outOfRange(field, range, value)
                )
        );

        return errorArbitrary.list().ofMinSize(1).ofMaxSize(5)
                .map(ValidationErrorCase::new);
    }

    @Provide
    Arbitrary<ValidationErrorCase> validationErrorWithSuggestionCases() {
        // Generate errors that always have non-null suggestions
        Arbitrary<ValidationError> errorArbitrary = Arbitraries.oneOf(
                Arbitraries.of("path", "query", "content").map(
                        ValidationError::missingRequired
                ),
                Combinators.combine(
                        Arbitraries.of("count", "flag"),
                        Arbitraries.of("string", "integer"),
                        Arbitraries.of("boolean", "number")
                ).as((field, expected, actual) ->
                        ValidationError.typeMismatch(field, expected, actual, "badValue")
                ),
                Combinators.combine(
                        Arbitraries.of("count", "depth"),
                        Arbitraries.of("0-100", "1-50")
                ).as((field, range) ->
                        ValidationError.outOfRange(field, range, -1)
                )
        );

        return errorArbitrary.list().ofMinSize(1).ofMaxSize(4)
                .map(ValidationErrorCase::new);
    }

    @Provide
    Arbitrary<ClassifiableException> genericOnlyExceptions() {
        // Exceptions that go through handleGenericError (not routed to specific handlers)
        // Excludes: TimeoutException (-> handleTimeout), ToolNotFoundException, ValidationException, ConcurrentExecutionException
        return Arbitraries.oneOf(
                // SocketTimeoutException extends IOException — handle() sees it as generic
                Arbitraries.of("socket read timeout", "connection timeout")
                        .map(msg -> new ClassifiableException(new SocketTimeoutException(msg), "timeout")),

                Arbitraries.of("access denied", "permission denied")
                        .map(msg -> new ClassifiableException(new SecurityException(msg), "permission")),

                Arbitraries.of("file not found", "disk full")
                        .map(msg -> new ClassifiableException(new IOException(msg), "io")),

                Arbitraries.of("invalid state", "null pointer")
                        .map(msg -> new ClassifiableException(new RuntimeException(msg), "business_logic")),

                Arbitraries.of("wrapped timeout")
                        .map(msg -> new ClassifiableException(
                                new RuntimeException(msg, new TimeoutException("inner")), "timeout")),

                Arbitraries.of("wrapped io")
                        .map(msg -> new ClassifiableException(
                                new RuntimeException(msg, new IOException("inner")), "io"))
        );
    }

    @Provide
    Arbitrary<ClassifiableException> classifiableExceptions() {
        return Arbitraries.oneOf(
                // Timeout exceptions -> "timeout"
                Arbitraries.of("operation timed out", "read timeout", "connect timeout")
                        .map(msg -> new ClassifiableException(new TimeoutException(msg), "timeout")),

                Arbitraries.of("socket read timeout", "connection timeout")
                        .map(msg -> new ClassifiableException(new SocketTimeoutException(msg), "timeout")),

                // Permission exceptions -> "permission"
                Arbitraries.of("access denied", "permission denied", "not authorized")
                        .map(msg -> new ClassifiableException(new SecurityException(msg), "permission")),

                // IO exceptions -> "io"
                Arbitraries.of("file not found", "disk full", "broken pipe")
                        .map(msg -> new ClassifiableException(new IOException(msg), "io")),

                // Business logic exceptions -> "business_logic"
                Arbitraries.of("invalid state", "null pointer", "bad argument")
                        .map(msg -> new ClassifiableException(new RuntimeException(msg), "business_logic")),

                Arbitraries.of("illegal argument")
                        .map(msg -> new ClassifiableException(new IllegalArgumentException(msg), "business_logic")),

                // Wrapped cause: RuntimeException wrapping TimeoutException -> "timeout"
                Arbitraries.of("wrapped timeout")
                        .map(msg -> new ClassifiableException(
                                new RuntimeException(msg, new TimeoutException("inner timeout")), "timeout")),

                // Wrapped cause: RuntimeException wrapping IOException -> "io"
                Arbitraries.of("wrapped io")
                        .map(msg -> new ClassifiableException(
                                new RuntimeException(msg, new IOException("inner io")), "io"))
        );
    }

    // ---------------------------------------------------------------
    // Stub McpTool for testing
    // ---------------------------------------------------------------

    private static class StubMcpTool implements Tool {
        private final String name;
        private final String description;
        private final Map<String, Object> schema;

        StubMcpTool(String name, String description, Map<String, Object> schema) {
            this.name = name;
            this.description = description;
            this.schema = schema;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return schema;
        }

        @Override
        public ToolResult execute(ToolContext context, Map<String, Object> params) {
            return ToolResult.text("stub result");
        }
    }
}
