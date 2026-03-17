package com.wmsay.gpt4_lll.fc.model;

/**
 * 参数验证错误。
 * 描述单个参数的验证失败详情。
 */
public class ValidationError {

    public enum ErrorType {
        MISSING_REQUIRED,
        TYPE_MISMATCH,
        OUT_OF_RANGE,
        CUSTOM_VALIDATION,
        TOOL_NOT_FOUND
    }

    private final String fieldName;
    private final ErrorType type;
    private final String expected;
    private final String actual;
    private final String suggestion;

    private ValidationError(Builder builder) {
        this.fieldName = builder.fieldName;
        this.type = builder.type;
        this.expected = builder.expected;
        this.actual = builder.actual;
        this.suggestion = builder.suggestion;
    }

    public String getFieldName() {
        return fieldName;
    }

    public ErrorType getType() {
        return type;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ValidationError missingRequired(String fieldName) {
        return builder()
            .fieldName(fieldName)
            .type(ErrorType.MISSING_REQUIRED)
            .expected("required")
            .actual("missing")
            .suggestion("Please provide the required parameter: " + fieldName)
            .build();
    }

    public static ValidationError typeMismatch(String fieldName, String expected, String actual, Object value) {
        return builder()
            .fieldName(fieldName)
            .type(ErrorType.TYPE_MISMATCH)
            .expected(expected)
            .actual(actual)
            .suggestion(String.format("Expected type '%s' but got '%s' for value: %s", expected, actual, value))
            .build();
    }

    public static ValidationError outOfRange(String fieldName, String range, Object value) {
        return builder()
            .fieldName(fieldName)
            .type(ErrorType.OUT_OF_RANGE)
            .expected(range)
            .actual(String.valueOf(value))
            .suggestion(String.format("Value %s is out of allowed range: %s", value, range))
            .build();
    }

    public static ValidationError toolNotFound(String toolName) {
        return builder()
            .fieldName("tool")
            .type(ErrorType.TOOL_NOT_FOUND)
            .expected("registered tool")
            .actual(toolName)
            .suggestion("Tool '" + toolName + "' is not registered")
            .build();
    }

    public static class Builder {
        private String fieldName;
        private ErrorType type;
        private String expected;
        private String actual;
        private String suggestion;

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder type(ErrorType type) {
            this.type = type;
            return this;
        }

        public Builder expected(String expected) {
            this.expected = expected;
            return this;
        }

        public Builder actual(String actual) {
            this.actual = actual;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public ValidationError build() {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            return new ValidationError(this);
        }
    }
}
