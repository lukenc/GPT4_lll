package com.wmsay.gpt4_lll.fc.validation;

import com.wmsay.gpt4_lll.fc.model.ToolCall;
import com.wmsay.gpt4_lll.fc.model.ValidationError;
import com.wmsay.gpt4_lll.fc.model.ValidationResult;
import com.wmsay.gpt4_lll.mcp.McpTool;
import com.wmsay.gpt4_lll.mcp.McpToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证引擎。
 * 根据工具的 JSON Schema 验证工具调用参数的合法性和完整性。
 * 支持必需参数验证、类型验证、范围验证和自定义验证规则。
 *
 * <p>Schema 缓存使用 {@link ConcurrentHashMap} 实现，避免重复解析（满足 Req 17.3）。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * ValidationEngine engine = new ValidationEngine();
 *
 * // 注册自定义验证器
 * engine.registerCustomValidator((toolName, params) -> {
 *     if ("write_file".equals(toolName) && !params.containsKey("content")) {
 *         return ValidationResult.invalid(List.of(
 *             ValidationError.missingRequired("content")));
 *     }
 *     return ValidationResult.valid();
 * });
 *
 * // 验证工具调用
 * ToolCall call = ToolCall.builder()
 *     .toolName("read_file")
 *     .parameters(Map.of("path", "/src/Main.java"))
 *     .build();
 * ValidationResult result = engine.validate(call);
 * if (!result.isValid()) {
 *     result.getErrors().forEach(e -> System.out.println(e.getFieldName()));
 * }
 * }</pre>
 *
 * @see CustomValidator
 * @see ValidationResult
 * @see ValidationError
 */
public class ValidationEngine {

    /**
     * Schema 缓存最大条目数。超过此限制时，最早的条目将被淘汰。
     * 防止在大量工具注册/注销场景下缓存无限增长。
     */
    static final int MAX_CACHE_SIZE = 256;

    /**
     * 已编译的 schema 缓存，key 为工具名称。
     * 使用 LRU 淘汰策略，最大容量为 {@value #MAX_CACHE_SIZE}。
     */
    private final Map<String, Map<String, Object>> schemaCache = new ConcurrentHashMap<>();

    /**
     * 已注册的自定义验证器列表。
     */
    private final List<CustomValidator> customValidators = new ArrayList<>();

    /**
     * 验证工具调用参数。
     *
     * @param toolCall 工具调用
     * @return 验证结果
     */
    public ValidationResult validate(ToolCall toolCall) {
        McpTool tool = McpToolRegistry.getTool(toolCall.getToolName());

        if (tool == null) {
            return ValidationResult.toolNotFound(toolCall.getToolName());
        }

        Map<String, Object> schema = getSchema(tool);
        Map<String, Object> params = toolCall.getParameters();

        List<ValidationError> errors = new ArrayList<>();

        errors.addAll(validateRequiredFields(schema, params));
        errors.addAll(validateTypes(schema, params));
        errors.addAll(validateRanges(schema, params));
        errors.addAll(executeCustomValidators(toolCall.getToolName(), params));

        return errors.isEmpty()
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors);
    }

    /**
     * 验证必需参数是否全部提供。
     *
     * @param schema 工具的 inputSchema
     * @param params 实际提供的参数
     * @return 验证错误列表
     */
    @SuppressWarnings("unchecked")
    public List<ValidationError> validateRequiredFields(
            Map<String, Object> schema, Map<String, Object> params) {
        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String fieldName = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> fieldSchema = (Map<String, Object>) entry.getValue();

            Object requiredObj = fieldSchema.get("required");
            boolean required = Boolean.TRUE.equals(requiredObj);

            if (required && !params.containsKey(fieldName)) {
                errors.add(ValidationError.missingRequired(fieldName));
            }
        }

        return errors;
    }

    /**
     * 验证参数类型是否匹配 schema 定义。
     *
     * @param schema 工具的 inputSchema
     * @param params 实际提供的参数
     * @return 验证错误列表
     */
    @SuppressWarnings("unchecked")
    public List<ValidationError> validateTypes(
            Map<String, Object> schema, Map<String, Object> params) {
        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (!(schema.get(fieldName) instanceof Map)) {
                continue;
            }
            Map<String, Object> fieldSchema = (Map<String, Object>) schema.get(fieldName);

            String expectedType = (String) fieldSchema.get("type");
            if (expectedType == null) {
                continue;
            }

            String actualType = getType(value);

            if (!isTypeCompatible(expectedType, actualType)) {
                errors.add(ValidationError.typeMismatch(
                    fieldName, expectedType, actualType, value));
            }
        }

        return errors;
    }

    /**
     * 验证参数值是否在允许的范围内。
     * 支持数值的 min/max、字符串的 minLength/maxLength、枚举值 enum。
     *
     * @param schema 工具的 inputSchema
     * @param params 实际提供的参数
     * @return 验证错误列表
     */
    @SuppressWarnings("unchecked")
    public List<ValidationError> validateRanges(
            Map<String, Object> schema, Map<String, Object> params) {
        List<ValidationError> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (!(schema.get(fieldName) instanceof Map)) {
                continue;
            }
            Map<String, Object> fieldSchema = (Map<String, Object>) schema.get(fieldName);

            // Numeric range: min / max
            if (value instanceof Number) {
                double numValue = ((Number) value).doubleValue();
                errors.addAll(validateNumericRange(fieldName, fieldSchema, numValue));
            }

            // String length: minLength / maxLength
            if (value instanceof String) {
                int len = ((String) value).length();
                errors.addAll(validateStringLength(fieldName, fieldSchema, (String) value, len));
            }

            // Enum validation
            errors.addAll(validateEnum(fieldName, fieldSchema, value));
        }

        return errors;
    }

    /**
     * 注册自定义验证器。
     *
     * @param validator 自定义验证器
     */
    public void registerCustomValidator(CustomValidator validator) {
        customValidators.add(validator);
    }

    /**
     * 清除 schema 缓存。
     */
    public void clearSchemaCache() {
        schemaCache.clear();
    }

    /**
     * 获取当前 schema 缓存大小。
     *
     * @return 缓存条目数
     */
    public int getSchemaCacheSize() {
        return schemaCache.size();
    }

    // ---- private helpers ----

    /**
     * 获取工具的 schema，优先从缓存读取。
     * 当缓存超过 {@value #MAX_CACHE_SIZE} 条目时，清除最早的一半条目（简单 LRU 近似）。
     */
    private Map<String, Object> getSchema(McpTool tool) {
        return schemaCache.computeIfAbsent(tool.name(), k -> {
            if (schemaCache.size() >= MAX_CACHE_SIZE) {
                evictOldestEntries();
            }
            return tool.inputSchema();
        });
    }

    /**
     * 淘汰缓存中约一半的条目，防止无限增长。
     * 使用简单的迭代器删除策略（ConcurrentHashMap 无序，近似 LRU）。
     */
    private void evictOldestEntries() {
        int toRemove = schemaCache.size() / 2;
        var iterator = schemaCache.keySet().iterator();
        while (toRemove > 0 && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
            toRemove--;
        }
    }

    /**
     * 执行所有已注册的自定义验证器。
     */
    private List<ValidationError> executeCustomValidators(
            String toolName, Map<String, Object> params) {
        List<ValidationError> errors = new ArrayList<>();

        for (CustomValidator validator : customValidators) {
            ValidationResult result = validator.validate(toolName, params);
            if (result != null && !result.isValid()) {
                errors.addAll(result.getErrors());
            }
        }

        return errors;
    }

    /**
     * 验证数值范围 (min / max)。
     */
    private List<ValidationError> validateNumericRange(
            String fieldName, Map<String, Object> fieldSchema, double numValue) {
        List<ValidationError> errors = new ArrayList<>();

        Number min = getNumber(fieldSchema, "min");
        Number max = getNumber(fieldSchema, "max");

        if (min != null && numValue < min.doubleValue()) {
            errors.add(ValidationError.outOfRange(
                fieldName,
                String.format("min=%s", min),
                numValue));
        }

        if (max != null && numValue > max.doubleValue()) {
            errors.add(ValidationError.outOfRange(
                fieldName,
                String.format("max=%s", max),
                numValue));
        }

        return errors;
    }

    /**
     * 验证字符串长度 (minLength / maxLength)。
     */
    private List<ValidationError> validateStringLength(
            String fieldName, Map<String, Object> fieldSchema,
            String value, int len) {
        List<ValidationError> errors = new ArrayList<>();

        Number minLength = getNumber(fieldSchema, "minLength");
        Number maxLength = getNumber(fieldSchema, "maxLength");

        if (minLength != null && len < minLength.intValue()) {
            errors.add(ValidationError.outOfRange(
                fieldName,
                String.format("minLength=%s", minLength),
                value));
        }

        if (maxLength != null && len > maxLength.intValue()) {
            errors.add(ValidationError.outOfRange(
                fieldName,
                String.format("maxLength=%s", maxLength),
                value));
        }

        return errors;
    }

    /**
     * 验证枚举值。
     */
    private List<ValidationError> validateEnum(
            String fieldName, Map<String, Object> fieldSchema, Object value) {
        Object enumValues = fieldSchema.get("enum");
        if (!(enumValues instanceof List)) {
            return Collections.emptyList();
        }

        List<?> allowedValues = (List<?>) enumValues;
        if (!allowedValues.contains(value)) {
            return List.of(ValidationError.outOfRange(
                fieldName,
                "enum=" + allowedValues,
                value));
        }

        return Collections.emptyList();
    }

    /**
     * 从 schema 字段中安全获取 Number 值。
     */
    private Number getNumber(Map<String, Object> fieldSchema, String key) {
        Object val = fieldSchema.get(key);
        if (val instanceof Number) {
            return (Number) val;
        }
        return null;
    }

    /**
     * 获取值的类型名称。
     */
    static String getType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "integer";
        }
        if (value instanceof Float || value instanceof Double) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof List) {
            return "array";
        }
        if (value instanceof Map) {
            return "object";
        }
        return value.getClass().getSimpleName().toLowerCase();
    }

    /**
     * 判断实际类型是否与期望类型兼容。
     * "number" 兼容 "integer"（integer 是 number 的子类型）。
     */
    static boolean isTypeCompatible(String expectedType, String actualType) {
        if (expectedType.equals(actualType)) {
            return true;
        }
        // integer 是 number 的子类型
        if ("number".equals(expectedType) && "integer".equals(actualType)) {
            return true;
        }
        return false;
    }
}
