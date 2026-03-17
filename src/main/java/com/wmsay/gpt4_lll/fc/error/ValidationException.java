package com.wmsay.gpt4_lll.fc.error;

import com.wmsay.gpt4_lll.fc.model.ValidationError;

import java.util.Collections;
import java.util.List;

/**
 * 参数验证异常。
 * 包装一组 ValidationError，当参数验证失败时抛出。
 */
public class ValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public ValidationException(List<ValidationError> errors) {
        super("Parameter validation failed: " + errors.size() + " error(s)");
        this.errors = errors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
