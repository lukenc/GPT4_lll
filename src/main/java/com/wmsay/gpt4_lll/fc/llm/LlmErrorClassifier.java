package com.wmsay.gpt4_lll.fc.llm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * LLM API 错误分类器。无状态工具类。
 * <p>
 * 根据 HTTP 状态码和响应体将 LLM API 错误分类为结构化的 {@link LlmErrorInfo}，
 * 生成中英双语的用户可读消息和修复建议。
 * <p>
 * 核心对话模块组件，不依赖任何 UI/展示层类。
 */
public final class LlmErrorClassifier {

    private LlmErrorClassifier() {
        // 工具类，禁止实例化
    }

    // ── 错误类型常量 ──────────────────────────────────────────

    public static final String TYPE_AUTHENTICATION = "authentication_error";
    public static final String TYPE_INSUFFICIENT_BALANCE = "insufficient_balance";
    public static final String TYPE_MODEL_NOT_FOUND = "model_not_found";
    public static final String TYPE_RATE_LIMIT = "rate_limit_error";
    public static final String TYPE_SERVER_ERROR = "server_error";
    public static final String TYPE_NO_VALID_RESPONSE = "no_valid_response";
    public static final String TYPE_UNKNOWN = "unknown_error";

    private static final int MAX_RAW_DETAIL_LENGTH = 500;

    // ── 内部数据类 ───────────────────────────────────────────

    /**
     * 从 JSON 响应体中提取的错误详情。
     */
    public static class ErrorDetail {
        private final String message;
        private final String type;
        private final String rawDetail;

        public ErrorDetail(String message, String type, String rawDetail) {
            this.message = message != null ? message : "";
            this.type = type != null ? type : "";
            this.rawDetail = rawDetail != null ? rawDetail : "";
        }

        public String getMessage() { return message; }
        public String getType() { return type; }
        public String getRawDetail() { return rawDetail; }
    }

    // ── 核心分类方法 ─────────────────────────────────────────

    /**
     * 根据 HTTP 状态码和响应体分类错误。
     *
     * @param statusCode   HTTP 状态码
     * @param responseBody 响应体（可为 null）
     * @return 结构化错误信息，永不为 null
     */
    public static LlmErrorInfo classify(int statusCode, String responseBody) {
        String errorType = mapStatusCodeToType(statusCode);

        // 尝试从响应体提取更精确的错误信息
        ErrorDetail detail = extractErrorDetail(responseBody);

        // 如果响应体中有 error.type/code，且状态码映射为 unknown，则用响应体中的类型
        if (TYPE_UNKNOWN.equals(errorType) && !detail.getType().isEmpty()) {
            errorType = refineTypeFromBody(detail.getType());
        }

        return LlmErrorInfo.builder()
                .errorType(errorType)
                .httpStatusCode(statusCode)
                .message(getUserMessage(errorType, detail.getMessage()))
                .suggestion(getSuggestion(errorType))
                .rawDetail(detail.getRawDetail())
                .build();
    }

    /**
     * 从响应体中的 error 字段分类错误（无 HTTP 状态码时使用）。
     *
     * @param body 响应体 JSON
     * @return 结构化错误信息
     */
    public static LlmErrorInfo classifyFromBody(String body) {
        ErrorDetail detail = extractErrorDetail(body);
        String errorType = detail.getType().isEmpty()
                ? TYPE_UNKNOWN
                : refineTypeFromBody(detail.getType());

        return LlmErrorInfo.builder()
                .errorType(errorType)
                .httpStatusCode(0)
                .message(getUserMessage(errorType, detail.getMessage()))
                .suggestion(getSuggestion(errorType))
                .rawDetail(detail.getRawDetail())
                .build();
    }

    /**
     * 生成 no_valid_response 类型的 LlmErrorInfo。
     *
     * @return no_valid_response 错误信息
     */
    public static LlmErrorInfo noValidResponse() {
        return LlmErrorInfo.builder()
                .errorType(TYPE_NO_VALID_RESPONSE)
                .httpStatusCode(0)
                .message(getUserMessage(TYPE_NO_VALID_RESPONSE, ""))
                .suggestion(getSuggestion(TYPE_NO_VALID_RESPONSE))
                .rawDetail("")
                .build();
    }

    // ── 响应体解析方法 ───────────────────────────────────────

    /**
     * 从 JSON 响应体提取 error.message 和 error.type/error.code。
     * 非 JSON 时截取前 500 字符作为 rawDetail。
     *
     * @param responseBody 响应体（可为 null）
     * @return 提取的错误详情
     */
    public static ErrorDetail extractErrorDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new ErrorDetail("", "", "");
        }

        String trimmed = responseBody.trim();
        if (!trimmed.startsWith("{")) {
            // 非 JSON，截取前 500 字符
            String raw = trimmed.length() > MAX_RAW_DETAIL_LENGTH
                    ? trimmed.substring(0, MAX_RAW_DETAIL_LENGTH)
                    : trimmed;
            return new ErrorDetail("", "", raw);
        }

        try {
            JsonElement element = JsonParser.parseString(trimmed);
            if (!element.isJsonObject()) {
                String raw = truncate(trimmed);
                return new ErrorDetail("", "", raw);
            }

            JsonObject root = element.getAsJsonObject();
            if (!root.has("error")) {
                String raw = truncate(trimmed);
                return new ErrorDetail("", "", raw);
            }

            JsonElement errorElement = root.get("error");
            if (errorElement.isJsonObject()) {
                JsonObject errorObj = errorElement.getAsJsonObject();
                String message = getStringField(errorObj, "message");
                String type = getStringField(errorObj, "type");
                if (type.isEmpty()) {
                    type = getStringField(errorObj, "code");
                }
                String raw = message.isEmpty() ? truncate(trimmed) : message;
                return new ErrorDetail(message, type, raw);
            } else if (errorElement.isJsonPrimitive()) {
                // error 字段是字符串
                String errorStr = errorElement.getAsString();
                return new ErrorDetail(errorStr, "", errorStr);
            }

            String raw = truncate(trimmed);
            return new ErrorDetail("", "", raw);
        } catch (JsonSyntaxException e) {
            // JSON 解析失败，视为非 JSON
            String raw = truncate(trimmed);
            return new ErrorDetail("", "", raw);
        }
    }

    /**
     * 检测 JSON 中是否包含 error 字段且不包含 choices/content 字段。
     *
     * @param body 响应体
     * @return true 表示是错误响应
     */
    public static boolean containsErrorField(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }

        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return false;
        }

        try {
            JsonElement element = JsonParser.parseString(trimmed);
            if (!element.isJsonObject()) {
                return false;
            }
            JsonObject obj = element.getAsJsonObject();
            return obj.has("error") && !obj.has("choices") && !obj.has("content");
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * 检测 SSE data 行是否为 error JSON。
     *
     * @param lineData SSE data 行内容（不含 "data:" 前缀）
     * @return true 表示是错误数据行
     */
    public static boolean isErrorDataLine(String lineData) {
        if (lineData == null || lineData.isBlank()) {
            return false;
        }

        String trimmed = lineData.trim();
        if ("[DONE]".equals(trimmed)) {
            return false;
        }
        if (!trimmed.startsWith("{")) {
            return false;
        }

        try {
            JsonElement element = JsonParser.parseString(trimmed);
            if (!element.isJsonObject()) {
                return false;
            }
            JsonObject obj = element.getAsJsonObject();
            return obj.has("error");
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    // ── 消息与建议生成 ───────────────────────────────────────

    /**
     * 为每种错误类型生成中英双语修复建议。
     *
     * @param errorType 错误类型标识
     * @return 中英双语修复建议
     */
    public static String getSuggestion(String errorType) {
        if (errorType == null) {
            return "请检查配置后重试 / Please check configuration and try again";
        }
        switch (errorType) {
            case TYPE_AUTHENTICATION:
                return "请在 Settings > GPT4 lll Settings 中检查 API Key 配置 / Please check your API Key in Settings > GPT4 lll Settings";
            case TYPE_RATE_LIMIT:
                return "请稍后重试 / Please try again later";
            case TYPE_INSUFFICIENT_BALANCE:
                return "请充值后重试 / Please top up and try again";
            case TYPE_MODEL_NOT_FOUND:
                return "请检查模型选择 / Please check model selection";
            case TYPE_SERVER_ERROR:
                return "请稍后重试 / Please try again later";
            case TYPE_NO_VALID_RESPONSE:
                return "请检查 API 地址和网络连接 / Please check API URL and network connection";
            default:
                return "请检查配置后重试 / Please check configuration and try again";
        }
    }

    /**
     * 为每种错误类型生成中英双语用户可读消息。
     *
     * @param errorType 错误类型标识
     * @param detail    具体错误详情（可为空）
     * @return 中英双语消息
     */
    public static String getUserMessage(String errorType, String detail) {
        String baseMessage;
        if (errorType == null) {
            baseMessage = "未知错误 / Unknown Error";
        } else {
            switch (errorType) {
                case TYPE_AUTHENTICATION:
                    baseMessage = "认证错误 / Authentication Error";
                    break;
                case TYPE_RATE_LIMIT:
                    baseMessage = "请求频率超限 / Rate Limit Exceeded";
                    break;
                case TYPE_INSUFFICIENT_BALANCE:
                    baseMessage = "账户余额不足 / Insufficient Balance";
                    break;
                case TYPE_MODEL_NOT_FOUND:
                    baseMessage = "模型不存在 / Model Not Found";
                    break;
                case TYPE_SERVER_ERROR:
                    baseMessage = "服务端错误 / Server Error";
                    break;
                case TYPE_NO_VALID_RESPONSE:
                    baseMessage = "未收到有效响应 / No Valid Response";
                    break;
                default:
                    baseMessage = "未知错误 / Unknown Error";
                    break;
            }
        }

        if (detail != null && !detail.isBlank()) {
            return baseMessage + "\n" + detail;
        }
        return baseMessage;
    }

    // ── 内部辅助方法 ─────────────────────────────────────────

    /**
     * 将 HTTP 状态码映射为错误类型标识。
     */
    private static String mapStatusCodeToType(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return TYPE_AUTHENTICATION;
        } else if (statusCode == 402) {
            return TYPE_INSUFFICIENT_BALANCE;
        } else if (statusCode == 404) {
            return TYPE_MODEL_NOT_FOUND;
        } else if (statusCode == 429) {
            return TYPE_RATE_LIMIT;
        } else if (statusCode >= 500) {
            return TYPE_SERVER_ERROR;
        }
        return TYPE_UNKNOWN;
    }

    /**
     * 根据响应体中的 error.type/code 字段值，尝试映射为已知错误类型。
     */
    private static String refineTypeFromBody(String bodyType) {
        if (bodyType == null || bodyType.isEmpty()) {
            return TYPE_UNKNOWN;
        }
        String lower = bodyType.toLowerCase();
        if (lower.contains("auth") || lower.contains("api_key") || lower.contains("invalid_key")) {
            return TYPE_AUTHENTICATION;
        } else if (lower.contains("rate_limit") || lower.contains("rate limit")) {
            return TYPE_RATE_LIMIT;
        } else if (lower.contains("insufficient") || lower.contains("balance") || lower.contains("quota")) {
            return TYPE_INSUFFICIENT_BALANCE;
        } else if (lower.contains("model_not_found") || lower.contains("not_found")) {
            return TYPE_MODEL_NOT_FOUND;
        } else if (lower.contains("server_error") || lower.contains("internal")) {
            return TYPE_SERVER_ERROR;
        }
        return TYPE_UNKNOWN;
    }

    private static String getStringField(JsonObject obj, String field) {
        if (obj.has(field) && obj.get(field).isJsonPrimitive()) {
            return obj.get(field).getAsString();
        }
        return "";
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_RAW_DETAIL_LENGTH
                ? text.substring(0, MAX_RAW_DETAIL_LENGTH)
                : text;
    }
}
