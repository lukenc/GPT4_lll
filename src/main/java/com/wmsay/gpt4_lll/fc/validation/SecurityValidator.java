package com.wmsay.gpt4_lll.fc.validation;

import com.wmsay.gpt4_lll.fc.model.ValidationError;
import com.wmsay.gpt4_lll.fc.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 安全验证器。
 * 检查工具调用参数中的路径遍历攻击和命令注入攻击模式。
 *
 * <p>检查内容：
 * <ul>
 *   <li>路径遍历：{@code ../}、{@code ..\}、绝对路径（以 {@code /} 或盘符开头）</li>
 *   <li>命令注入：{@code ;}、{@code |}、{@code &&}、{@code ||}、反引号、{@code $()}</li>
 * </ul>
 *
 * @see CustomValidator
 * @see ValidationEngine
 */
public class SecurityValidator implements CustomValidator {

    /** 包含文件路径参数的参数名集合 */
    private static final Set<String> PATH_PARAM_NAMES = Set.of(
            "path", "file", "filePath", "file_path", "directory", "dir", "target", "destination"
    );

    /** 包含可能被注入命令的参数名集合 */
    private static final Set<String> COMMAND_PARAM_NAMES = Set.of(
            "command", "cmd", "exec", "shell", "script", "query"
    );

    /** 路径遍历模式：../ 或 ..\ */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "\\.\\.[\\\\/]"
    );

    /** Windows 盘符绝对路径模式：C:\ 或 D:/ */
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile(
            "^[A-Za-z]:[\\\\/]"
    );

    /** 命令注入危险字符模式 */
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
            "[;|&`]|\\$\\(|\\|\\||&&"
    );

    @Override
    public ValidationResult validate(String toolName, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return ValidationResult.valid();
        }

        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String paramName = entry.getKey();
            Object value = entry.getValue();

            if (!(value instanceof String)) {
                continue;
            }
            String strValue = (String) value;

            // Check path parameters for traversal attacks
            if (isPathParam(paramName)) {
                errors.addAll(checkPathTraversal(paramName, strValue));
            }

            // Check command parameters for injection attacks
            if (isCommandParam(paramName)) {
                errors.addAll(checkCommandInjection(paramName, strValue));
            }
        }

        return errors.isEmpty()
                ? ValidationResult.valid()
                : ValidationResult.invalid(errors);
    }

    /**
     * 检查路径遍历攻击模式。
     *
     * @param paramName 参数名
     * @param value     参数值
     * @return 验证错误列表
     */
    List<ValidationError> checkPathTraversal(String paramName, String value) {
        List<ValidationError> errors = new ArrayList<>();

        // Check for ../ or ..\
        if (PATH_TRAVERSAL_PATTERN.matcher(value).find()) {
            errors.add(ValidationError.builder()
                    .fieldName(paramName)
                    .type(ValidationError.ErrorType.CUSTOM_VALIDATION)
                    .expected("relative path without traversal")
                    .actual(value)
                    .suggestion("Path contains traversal pattern '../'. Use a path relative to the project root.")
                    .build());
        }

        // Check for Unix absolute paths (starting with /)
        if (value.startsWith("/")) {
            errors.add(ValidationError.builder()
                    .fieldName(paramName)
                    .type(ValidationError.ErrorType.CUSTOM_VALIDATION)
                    .expected("relative path")
                    .actual(value)
                    .suggestion("Absolute paths are not allowed. Use a path relative to the project root.")
                    .build());
        }

        // Check for Windows absolute paths (e.g., C:\)
        if (WINDOWS_ABSOLUTE_PATH.matcher(value).find()) {
            errors.add(ValidationError.builder()
                    .fieldName(paramName)
                    .type(ValidationError.ErrorType.CUSTOM_VALIDATION)
                    .expected("relative path")
                    .actual(value)
                    .suggestion("Absolute paths are not allowed. Use a path relative to the project root.")
                    .build());
        }

        return errors;
    }

    /**
     * 检查命令注入攻击模式。
     *
     * @param paramName 参数名
     * @param value     参数值
     * @return 验证错误列表
     */
    List<ValidationError> checkCommandInjection(String paramName, String value) {
        List<ValidationError> errors = new ArrayList<>();

        if (COMMAND_INJECTION_PATTERN.matcher(value).find()) {
            errors.add(ValidationError.builder()
                    .fieldName(paramName)
                    .type(ValidationError.ErrorType.CUSTOM_VALIDATION)
                    .expected("safe command without injection characters")
                    .actual(value)
                    .suggestion("Command contains potentially dangerous characters (;, |, &, `, $()). "
                            + "Please use a single, safe command.")
                    .build());
        }

        return errors;
    }

    /**
     * 判断参数名是否为路径类参数。
     */
    static boolean isPathParam(String paramName) {
        if (paramName == null) return false;
        String lower = paramName.toLowerCase();
        return PATH_PARAM_NAMES.contains(paramName)
                || lower.contains("path")
                || lower.contains("file")
                || lower.contains("dir");
    }

    /**
     * 判断参数名是否为命令类参数。
     */
    static boolean isCommandParam(String paramName) {
        if (paramName == null) return false;
        return COMMAND_PARAM_NAMES.contains(paramName)
                || paramName.toLowerCase().contains("command")
                || paramName.toLowerCase().contains("cmd");
    }
}
