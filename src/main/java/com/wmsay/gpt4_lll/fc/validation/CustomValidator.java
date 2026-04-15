package com.wmsay.gpt4_lll.fc.validation;

import com.wmsay.gpt4_lll.fc.model.ValidationResult;
import com.wmsay.gpt4_lll.fc.tools.ValidationEngine;

import java.util.Map;

/**
 * 自定义验证器接口。
 * 允许为特定工具注册额外的验证逻辑。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * CustomValidator pathValidator = (toolName, params) -> {
 *     if ("read_file".equals(toolName)) {
 *         String path = (String) params.get("path");
 *         if (path != null && path.contains("..")) {
 *             return ValidationResult.invalid(List.of(
 *                 ValidationError.outOfRange("path", "no path traversal", path)));
 *         }
 *     }
 *     return ValidationResult.valid();
 * };
 * validationEngine.registerCustomValidator(pathValidator);
 * }</pre>
 *
 * @see ValidationEngine#registerCustomValidator(CustomValidator)
 */
public interface CustomValidator {

    /**
     * 验证工具调用参数。
     *
     * @param toolName 工具名称
     * @param params   工具调用参数
     * @return 验证结果
     */
    ValidationResult validate(String toolName, Map<String, Object> params);
}
