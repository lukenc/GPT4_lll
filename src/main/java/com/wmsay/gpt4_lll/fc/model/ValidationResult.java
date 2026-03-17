package com.wmsay.gpt4_lll.fc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 参数验证结果。
 * 包含验证是否通过以及所有验证错误的详细信息。
 */
public class ValidationResult {

    private final boolean valid;
    private final List<ValidationError> errors;

    private ValidationResult(Builder builder) {
        this.valid = builder.valid;
        this.errors = builder.errors == null ? 
            Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(builder.errors));
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ValidationResult valid() {
        return builder().valid(true).build();
    }

    public static ValidationResult invalid(List<ValidationError> errors) {
        return builder()
            .valid(false)
            .errors(errors)
            .build();
    }

    public static ValidationResult toolNotFound(String toolName) {
        return builder()
            .valid(false)
            .errors(Collections.singletonList(ValidationError.toolNotFound(toolName)))
            .build();
    }

    public static class Builder {
        private boolean valid;
        private List<ValidationError> errors;

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder errors(List<ValidationError> errors) {
            this.errors = errors;
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(this);
        }
    }
}
