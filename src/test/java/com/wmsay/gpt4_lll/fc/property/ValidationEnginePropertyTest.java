package com.wmsay.gpt4_lll.fc.property;

import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ValidationError;
import com.wmsay.gpt4_lll.fc.model.ValidationResult;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolRegistry;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;
import com.wmsay.gpt4_lll.fc.tools.ValidationEngine;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 属性测试: ValidationEngine
 * <p>
 * 验证类型检查、必需参数、范围约束和自定义验证器调用。
 * <p>
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
class ValidationEnginePropertyTest {

    private static final String TOOL_PREFIX = "__valtest_";

    private ValidationEngine engine;
    private ToolRegistry toolRegistry;
    private final List<String> registeredToolNames = new ArrayList<>();

    @BeforeProperty
    void setup() {
        toolRegistry = new ToolRegistry();
        engine = new ValidationEngine(toolRegistry);
        registeredToolNames.clear();
    }

    // ---------------------------------------------------------------
    // Property 10: Type Validation
    // Validates: Requirements 3.1, 3.4
    // ---------------------------------------------------------------

    /**
     * Property 10: Type Validation
     * Validates: Requirements 3.1, 3.4
     *
     * For any parameter with a declared type in the schema, if the actual
     * value has a different incompatible type, validation should report a
     * TYPE_MISMATCH error containing both expected and actual types.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 10: Type Validation")
    void typeMismatchShouldBeDetected(
            @ForAll("typeMismatchCases") TypeMismatchCase testCase) {

        // Register a tool whose schema declares the expected type
        String toolName = TOOL_PREFIX + "type_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> fieldSchema = new HashMap<>();
        fieldSchema.put("type", testCase.expectedType);

        Map<String, Object> schema = new HashMap<>();
        schema.put(testCase.fieldName, fieldSchema);

        // Tool registration not needed for direct validateTypes() call
        registeredToolNames.add(toolName);

        // Build params with the mismatched value
        Map<String, Object> params = new HashMap<>();
        params.put(testCase.fieldName, testCase.mismatchedValue);

        // Validate using the engine's public methods directly
        List<ValidationError> errors = engine.validateTypes(schema, params);

        // Should have at least one TYPE_MISMATCH error for this field
        assert !errors.isEmpty() :
                "Expected TYPE_MISMATCH error for field '" + testCase.fieldName +
                        "' (expected=" + testCase.expectedType +
                        ", actual value=" + testCase.mismatchedValue + ")";

        ValidationError error = errors.stream()
                .filter(e -> e.getFieldName().equals(testCase.fieldName))
                .findFirst()
                .orElse(null);

        assert error != null :
                "No error found for field '" + testCase.fieldName + "'";
        assert error.getType() == ValidationError.ErrorType.TYPE_MISMATCH :
                "Expected TYPE_MISMATCH but got " + error.getType();
        assert error.getExpected() != null && error.getExpected().equals(testCase.expectedType) :
                "Expected type should be '" + testCase.expectedType + "' but got '" + error.getExpected() + "'";
        assert error.getActual() != null && !error.getActual().isEmpty() :
                "Actual type should be non-empty";
    }

    /**
     * Property 10 (supplement): Compatible types should NOT produce errors.
     * integer is compatible with number.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 10: Compatible types pass validation")
    void compatibleTypesShouldPass(
            @ForAll("compatibleTypeCases") CompatibleTypeCase testCase) {

        Map<String, Object> fieldSchema = new HashMap<>();
        fieldSchema.put("type", testCase.schemaType);

        Map<String, Object> schema = new HashMap<>();
        schema.put("field", fieldSchema);

        Map<String, Object> params = new HashMap<>();
        params.put("field", testCase.value);

        List<ValidationError> errors = engine.validateTypes(schema, params);

        assert errors.isEmpty() :
                "Expected no errors for compatible type (schema=" + testCase.schemaType +
                        ", value=" + testCase.value + ") but got " + errors.size() + " error(s)";
    }

    // ---------------------------------------------------------------
    // Property 11: Required Parameter Validation
    // Validates: Requirements 3.2, 3.5
    // ---------------------------------------------------------------

    /**
     * Property 11: Required Parameter Validation
     * Validates: Requirements 3.2, 3.5
     *
     * For any tool schema with required parameters, if a tool call omits
     * any required parameter, validation should report a MISSING_REQUIRED
     * error listing all missing parameters.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 11: Required Parameter Validation")
    void missingRequiredFieldsShouldBeDetected(
            @ForAll("requiredFieldCases") RequiredFieldCase testCase) {

        // Build schema with required fields
        Map<String, Object> schema = new HashMap<>();
        for (String reqField : testCase.requiredFields) {
            Map<String, Object> fieldSchema = new HashMap<>();
            fieldSchema.put("type", "string");
            fieldSchema.put("required", true);
            schema.put(reqField, fieldSchema);
        }
        // Add optional fields too
        for (String optField : testCase.optionalFields) {
            Map<String, Object> fieldSchema = new HashMap<>();
            fieldSchema.put("type", "string");
            fieldSchema.put("required", false);
            schema.put(optField, fieldSchema);
        }

        // Provide only a subset of required fields
        Map<String, Object> params = new HashMap<>();
        for (String provided : testCase.providedFields) {
            params.put(provided, "value");
        }

        List<ValidationError> errors = engine.validateRequiredFields(schema, params);

        // Compute expected missing fields
        Set<String> missing = new HashSet<>(testCase.requiredFields);
        missing.removeAll(testCase.providedFields);

        assert errors.size() == missing.size() :
                "Expected " + missing.size() + " MISSING_REQUIRED errors but got " + errors.size();

        Set<String> errorFieldNames = errors.stream()
                .map(ValidationError::getFieldName)
                .collect(Collectors.toSet());

        for (String missingField : missing) {
            assert errorFieldNames.contains(missingField) :
                    "Expected MISSING_REQUIRED error for field '" + missingField + "'";
        }

        for (ValidationError error : errors) {
            assert error.getType() == ValidationError.ErrorType.MISSING_REQUIRED :
                    "Expected MISSING_REQUIRED but got " + error.getType();
        }
    }

    /**
     * Property 11 (supplement): When all required fields are provided,
     * no MISSING_REQUIRED errors should be produced.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 11: All required fields provided passes")
    void allRequiredFieldsProvidedShouldPass(
            @ForAll("requiredFieldNames") List<String> requiredFields) {

        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        for (String field : requiredFields) {
            Map<String, Object> fieldSchema = new HashMap<>();
            fieldSchema.put("type", "string");
            fieldSchema.put("required", true);
            schema.put(field, fieldSchema);
            params.put(field, "value");
        }

        List<ValidationError> errors = engine.validateRequiredFields(schema, params);

        assert errors.isEmpty() :
                "Expected no errors when all required fields are provided, but got " + errors.size();
    }

    // ---------------------------------------------------------------
    // Property 12: Range Validation
    // Validates: Requirements 3.3, 3.6
    // ---------------------------------------------------------------

    /**
     * Property 12: Range Validation
     * Validates: Requirements 3.3, 3.6
     *
     * For any numeric parameter outside min/max range or string outside
     * minLength/maxLength, validation should report an OUT_OF_RANGE error
     * containing the allowed range and actual value.
     */
    @Property(tries = 100)
    @Label("Feature: function-calling-enhancement, Property 12: Range Validation")
    void outOfRangeValuesShouldBeDetected(
            @ForAll("rangeViolationCases") RangeViolationCase testCase) {

        Map<String, Object> fieldSchema = new HashMap<>(testCase.constraints);
        fieldSchema.put("type", testCase.schemaType);

        Map<String, Object> schema = new HashMap<>();
        schema.put(testCase.fieldName, fieldSchema);

        Map<String, Object> params = new HashMap<>();
        params.put(testCase.fieldName, testCase.value);

        List<ValidationError> errors = engine.validateRanges(schema, params);

        assert !errors.isEmpty() :
                "Expected OUT_OF_RANGE error for field '" + testCase.fieldName +
                        "' with value " + testCase.value + " and constraints " + testCase.constraints;

        ValidationError error = errors.stream()
                .filter(e -> e.getFieldName().equals(testCase.fieldName))
                .findFirst()
                .orElse(null);

        assert error != null :
                "No error found for field '" + testCase.fieldName + "'";
        assert error.getType() == ValidationError.ErrorType.OUT_OF_RANGE :
                "Expected OUT_OF_RANGE but got " + error.getType();
        assert error.getExpected() != null && !error.getExpected().isEmpty() :
                "Expected range description should be non-empty";
        assert error.getActual() != null :
                "Actual value should be present in error";
    }

    /**
     * Property 12 (supplement): Values within range should not produce errors.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 12: In-range values pass validation")
    void inRangeValuesShouldPass(
            @ForAll("inRangeCases") InRangeCase testCase) {

        Map<String, Object> fieldSchema = new HashMap<>(testCase.constraints);
        fieldSchema.put("type", testCase.schemaType);

        Map<String, Object> schema = new HashMap<>();
        schema.put("field", fieldSchema);

        Map<String, Object> params = new HashMap<>();
        params.put("field", testCase.value);

        List<ValidationError> errors = engine.validateRanges(schema, params);

        assert errors.isEmpty() :
                "Expected no range errors for value " + testCase.value +
                        " with constraints " + testCase.constraints + " but got " + errors.size();
    }

    // ---------------------------------------------------------------
    // Property 13: Custom Validator Invocation
    // Validates: Requirements 3.7
    // ---------------------------------------------------------------

    /**
     * Property 13: Custom Validator Invocation
     * Validates: Requirements 3.7
     *
     * When custom validators are registered, they should be invoked during
     * validation and their errors included in the result.
     */
    @Property(tries = 50)
    @Label("Feature: function-calling-enhancement, Property 13: Custom Validator Invocation")
    void customValidatorsShouldBeInvoked(
            @ForAll("customValidatorCases") CustomValidatorCase testCase) {

        // Use a fresh engine per try so validators don't accumulate
        ToolRegistry localRegistry = new ToolRegistry();
        ValidationEngine localEngine = new ValidationEngine(localRegistry);

        String toolName = TOOL_PREFIX + "cv_" + UUID.randomUUID().toString().substring(0, 8);

        // Register a tool with an empty schema (no built-in validation errors)
        Tool tool = new StubTool(toolName, "custom validator test", Collections.emptyMap());
        localRegistry.registerTool(tool);
        registeredToolNames.add(toolName);

        // Track invocation
        List<String> invocations = new ArrayList<>();

        // Register custom validators that produce errors
        for (int i = 0; i < testCase.validatorCount; i++) {
            final int idx = i;
            final boolean shouldFail = testCase.failingIndices.contains(i);
            localEngine.registerCustomValidator((tn, params) -> {
                invocations.add("validator_" + idx + "_for_" + tn);
                if (shouldFail) {
                    return ValidationResult.invalid(Collections.singletonList(
                            ValidationError.builder()
                                    .fieldName("custom_field_" + idx)
                                    .type(ValidationError.ErrorType.CUSTOM_VALIDATION)
                                    .expected("valid")
                                    .actual("invalid")
                                    .suggestion("Fix custom_field_" + idx)
                                    .build()
                    ));
                }
                return ValidationResult.valid();
            });
        }

        // Build a ToolCall and validate
        ToolCall toolCall = ToolCall.builder()
                .callId("test_call_" + UUID.randomUUID().toString().substring(0, 8))
                .toolName(toolName)
                .parameters(Collections.emptyMap())
                .build();

        ValidationResult result = localEngine.validate(toolCall);

        // All validators should have been invoked
        assert invocations.size() == testCase.validatorCount :
                "Expected " + testCase.validatorCount + " validator invocations but got " + invocations.size();

        // All invocations should be for the correct tool name
        for (String inv : invocations) {
            assert inv.contains(toolName) :
                    "Validator invocation should reference tool '" + toolName + "': " + inv;
        }

        // Result should contain errors from failing validators
        if (testCase.failingIndices.isEmpty()) {
            assert result.isValid() :
                    "Expected valid result when no custom validators fail";
        } else {
            assert !result.isValid() :
                    "Expected invalid result when custom validators fail";

            long customErrors = result.getErrors().stream()
                    .filter(e -> e.getType() == ValidationError.ErrorType.CUSTOM_VALIDATION)
                    .count();
            assert customErrors == testCase.failingIndices.size() :
                    "Expected " + testCase.failingIndices.size() +
                            " CUSTOM_VALIDATION errors but got " + customErrors;
        }
    }

    // ---------------------------------------------------------------
    // Test case record types
    // ---------------------------------------------------------------

    static class TypeMismatchCase {
        final String fieldName;
        final String expectedType;
        final Object mismatchedValue;

        TypeMismatchCase(String fieldName, String expectedType, Object mismatchedValue) {
            this.fieldName = fieldName;
            this.expectedType = expectedType;
            this.mismatchedValue = mismatchedValue;
        }

        @Override
        public String toString() {
            return "TypeMismatch{field=" + fieldName + ", expected=" + expectedType +
                    ", value=" + mismatchedValue + "(" + mismatchedValue.getClass().getSimpleName() + ")}";
        }
    }

    static class CompatibleTypeCase {
        final String schemaType;
        final Object value;

        CompatibleTypeCase(String schemaType, Object value) {
            this.schemaType = schemaType;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Compatible{schema=" + schemaType + ", value=" + value + "}";
        }
    }

    static class RequiredFieldCase {
        final List<String> requiredFields;
        final List<String> optionalFields;
        final List<String> providedFields;

        RequiredFieldCase(List<String> requiredFields, List<String> optionalFields, List<String> providedFields) {
            this.requiredFields = requiredFields;
            this.optionalFields = optionalFields;
            this.providedFields = providedFields;
        }

        @Override
        public String toString() {
            return "RequiredField{required=" + requiredFields +
                    ", optional=" + optionalFields +
                    ", provided=" + providedFields + "}";
        }
    }

    static class RangeViolationCase {
        final String fieldName;
        final String schemaType;
        final Map<String, Object> constraints;
        final Object value;

        RangeViolationCase(String fieldName, String schemaType, Map<String, Object> constraints, Object value) {
            this.fieldName = fieldName;
            this.schemaType = schemaType;
            this.constraints = constraints;
            this.value = value;
        }

        @Override
        public String toString() {
            return "RangeViolation{field=" + fieldName + ", type=" + schemaType +
                    ", constraints=" + constraints + ", value=" + value + "}";
        }
    }

    static class InRangeCase {
        final String schemaType;
        final Map<String, Object> constraints;
        final Object value;

        InRangeCase(String schemaType, Map<String, Object> constraints, Object value) {
            this.schemaType = schemaType;
            this.constraints = constraints;
            this.value = value;
        }

        @Override
        public String toString() {
            return "InRange{type=" + schemaType + ", constraints=" + constraints + ", value=" + value + "}";
        }
    }

    static class CustomValidatorCase {
        final int validatorCount;
        final Set<Integer> failingIndices;

        CustomValidatorCase(int validatorCount, Set<Integer> failingIndices) {
            this.validatorCount = validatorCount;
            this.failingIndices = failingIndices;
        }

        @Override
        public String toString() {
            return "CustomValidator{count=" + validatorCount + ", failing=" + failingIndices + "}";
        }
    }

    // ---------------------------------------------------------------
    // Providers (Arbitraries)
    // ---------------------------------------------------------------

    @Provide
    Arbitrary<TypeMismatchCase> typeMismatchCases() {
        // Generate cases where schema type != actual value type
        return Arbitraries.oneOf(
                // Schema says "string", provide non-string
                Arbitraries.oneOf(
                        Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
                        Arbitraries.of(true, false).map(b -> (Object) b),
                        Arbitraries.doubles().between(-100.0, 100.0).map(d -> (Object) d)
                ).map(v -> new TypeMismatchCase("param", "string", v)),

                // Schema says "integer", provide non-integer
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                        Arbitraries.of(true, false).map(b -> (Object) b),
                        Arbitraries.doubles().between(0.1, 99.9).filter(d -> d != Math.floor(d)).map(d -> (Object) d)
                ).map(v -> new TypeMismatchCase("count", "integer", v)),

                // Schema says "boolean", provide non-boolean
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                        Arbitraries.integers().between(-100, 100).map(i -> (Object) i)
                ).map(v -> new TypeMismatchCase("flag", "boolean", v)),

                // Schema says "number", provide non-number (string or boolean)
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                        Arbitraries.of(true, false).map(b -> (Object) b)
                ).map(v -> new TypeMismatchCase("score", "number", v)),

                // Schema says "array", provide non-array
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                        Arbitraries.integers().between(0, 100).map(i -> (Object) i)
                ).map(v -> new TypeMismatchCase("items", "array", v)),

                // Schema says "object", provide non-object
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                        Arbitraries.integers().between(0, 100).map(i -> (Object) i)
                ).map(v -> new TypeMismatchCase("config", "object", v))
        );
    }

    @Provide
    Arbitrary<CompatibleTypeCase> compatibleTypeCases() {
        return Arbitraries.oneOf(
                // string -> string
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                        .map(s -> new CompatibleTypeCase("string", s)),
                // integer -> integer
                Arbitraries.integers().between(-1000, 1000)
                        .map(i -> new CompatibleTypeCase("integer", i)),
                // number -> number (double)
                Arbitraries.doubles().between(-1000.0, 1000.0)
                        .map(d -> new CompatibleTypeCase("number", d)),
                // number -> integer (integer is subtype of number)
                Arbitraries.integers().between(-1000, 1000)
                        .map(i -> new CompatibleTypeCase("number", i)),
                // boolean -> boolean
                Arbitraries.of(true, false)
                        .map(b -> new CompatibleTypeCase("boolean", b))
        );
    }

    @Provide
    Arbitrary<RequiredFieldCase> requiredFieldCases() {
        Arbitrary<List<String>> reqFields = Arbitraries.of("path", "query", "content", "mode", "depth")
                .list().ofMinSize(1).ofMaxSize(4).uniqueElements();
        Arbitrary<List<String>> optFields = Arbitraries.of("format", "verbose", "limit")
                .list().ofMinSize(0).ofMaxSize(2).uniqueElements();

        return Combinators.combine(reqFields, optFields).as((req, opt) -> {
            // Provide a random subset of required fields (but not all)
            List<String> provided = new ArrayList<>();
            if (req.size() > 1) {
                // Provide some but not all required fields
                int provideCount = Math.max(0, req.size() - 1 - (int) (Math.random() * req.size()));
                for (int i = 0; i < Math.min(provideCount, req.size()); i++) {
                    provided.add(req.get(i));
                }
            }
            // Also provide optional fields (these shouldn't cause errors)
            provided.addAll(opt);
            return new RequiredFieldCase(req, opt, provided);
        });
    }

    @Provide
    Arbitrary<List<String>> requiredFieldNames() {
        return Arbitraries.of("path", "query", "content", "mode", "depth", "format")
                .list().ofMinSize(1).ofMaxSize(4).uniqueElements();
    }

    @Provide
    Arbitrary<RangeViolationCase> rangeViolationCases() {
        return Arbitraries.oneOf(
                // Numeric: value below min
                Arbitraries.integers().between(10, 100).flatMap(min ->
                        Arbitraries.integers().between(min - 50, min - 1).map(val -> {
                            Map<String, Object> constraints = new HashMap<>();
                            constraints.put("min", min);
                            return new RangeViolationCase("count", "integer", constraints, val);
                        })
                ),
                // Numeric: value above max
                Arbitraries.integers().between(10, 100).flatMap(max ->
                        Arbitraries.integers().between(max + 1, max + 50).map(val -> {
                            Map<String, Object> constraints = new HashMap<>();
                            constraints.put("max", max);
                            return new RangeViolationCase("count", "integer", constraints, val);
                        })
                ),
                // String: length below minLength
                Arbitraries.integers().between(5, 20).map(minLen -> {
                    Map<String, Object> constraints = new HashMap<>();
                    constraints.put("minLength", minLen);
                    // Create a string shorter than minLength
                    String shortStr = "a".repeat(Math.max(0, minLen - 1));
                    return new RangeViolationCase("name", "string", constraints, shortStr);
                }),
                // String: length above maxLength
                Arbitraries.integers().between(3, 15).map(maxLen -> {
                    Map<String, Object> constraints = new HashMap<>();
                    constraints.put("maxLength", maxLen);
                    // Create a string longer than maxLength
                    String longStr = "x".repeat(maxLen + 1);
                    return new RangeViolationCase("name", "string", constraints, longStr);
                })
        );
    }

    @Provide
    Arbitrary<InRangeCase> inRangeCases() {
        return Arbitraries.oneOf(
                // Numeric: value within [min, max]
                Arbitraries.integers().between(0, 50).flatMap(min ->
                        Arbitraries.integers().between(min + 10, min + 100).flatMap(max ->
                                Arbitraries.integers().between(min, max).map(val -> {
                                    Map<String, Object> constraints = new HashMap<>();
                                    constraints.put("min", min);
                                    constraints.put("max", max);
                                    return new InRangeCase("integer", constraints, val);
                                })
                        )
                ),
                // String: length within [minLength, maxLength]
                Arbitraries.integers().between(1, 5).flatMap(minLen ->
                        Arbitraries.integers().between(minLen + 1, minLen + 10).flatMap(maxLen ->
                                Arbitraries.integers().between(minLen, maxLen).map(len -> {
                                    Map<String, Object> constraints = new HashMap<>();
                                    constraints.put("minLength", minLen);
                                    constraints.put("maxLength", maxLen);
                                    String str = "a".repeat(len);
                                    return new InRangeCase("string", constraints, str);
                                })
                        )
                )
        );
    }

    @Provide
    Arbitrary<CustomValidatorCase> customValidatorCases() {
        return Arbitraries.integers().between(1, 4).flatMap(count -> {
            // Generate a set of failing indices (subset of 0..count-1)
            return Arbitraries.of(0, 1, 2, 3)
                    .filter(i -> i < count)
                    .set().ofMinSize(0).ofMaxSize(count)
                    .map(failingSet -> new CustomValidatorCase(count, failingSet));
        });
    }

    // ---------------------------------------------------------------
    // Stub Tool for testing
    // ---------------------------------------------------------------

    private static class StubTool implements Tool {
        private final String name;
        private final String description;
        private final Map<String, Object> schema;

        StubTool(String name, String description, Map<String, Object> schema) {
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
