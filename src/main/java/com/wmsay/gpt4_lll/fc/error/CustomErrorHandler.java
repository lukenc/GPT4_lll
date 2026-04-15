package com.wmsay.gpt4_lll.fc.error;

import com.wmsay.gpt4_lll.fc.core.ErrorMessage;
import com.wmsay.gpt4_lll.fc.tools.ErrorHandler;

/**
 * 自定义错误处理器接口。
 * 允许通过 SPI 机制加载自定义的错误处理逻辑。
 *
 * <p>自定义处理器在内置处理逻辑之前被调用。如果返回非 null 的 {@link ErrorMessage}，
 * 则直接使用该结果；返回 null 表示此处理器不处理该异常，交由内置逻辑处理。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public class MyErrorHandler implements CustomErrorHandler {
 *     public ErrorMessage handle(String toolName, Throwable error) {
 *         if (error instanceof MyCustomException) {
 *             return ErrorMessage.builder()
 *                 .type("custom_error")
 *                 .message("Custom error: " + error.getMessage())
 *                 .suggestion("Try a different approach.")
 *                 .build();
 *         }
 *         return null; // 交给内置处理器
 *     }
 * }
 * }</pre>
 *
 * @see ErrorHandler
 */
public interface CustomErrorHandler {

    /**
     * 处理指定工具的异常。
     *
     * @param toolName 工具名称（可能为 null）
     * @param error    异常
     * @return 格式化的错误消息，如果此处理器不处理该异常则返回 null
     */
    ErrorMessage handle(String toolName, Throwable error);
}
