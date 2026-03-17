package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.error.*;
import com.wmsay.gpt4_lll.fc.model.ErrorMessage;
import com.wmsay.gpt4_lll.fc.model.ValidationError;
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
        handler = new ErrorHandler();
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
            McpTool tool = new StubMcpTool(fullName, "test tool", Collections.emptyMap());
            McpToolRegistry.register(tool);
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

    // ---------------------------------------------------------------
    // Providers (Arbitraries)
    // ---------------------------------------------------------------

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

    private static class StubMcpTool implements McpTool {
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
        public McpToolResult execute(McpContext context, Map<String, Object> params) {
            return McpToolResult.text("stub result");
        }
    }
}
