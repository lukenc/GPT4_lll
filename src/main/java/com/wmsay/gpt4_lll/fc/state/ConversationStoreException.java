package com.wmsay.gpt4_lll.fc.state;

/**
 * 会话存储操作异常。
 * <p>
 * 当 {@link ConversationStore} 的 save/load/delete 等操作失败时抛出，
 * 包含失败原因和建议信息。继承 {@link RuntimeException}，调用方可选择捕获。
 *
 * @see ConversationStore
 */
public class ConversationStoreException extends RuntimeException {

    private final String suggestion;

    /**
     * 创建会话存储异常。
     *
     * @param message 错误消息
     */
    public ConversationStoreException(String message) {
        super(message);
        this.suggestion = null;
    }

    /**
     * 创建会话存储异常（含原因）。
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public ConversationStoreException(String message, Throwable cause) {
        super(message, cause);
        this.suggestion = null;
    }

    /**
     * 创建会话存储异常（含建议）。
     *
     * @param message    错误消息
     * @param cause      原始异常
     * @param suggestion 建议信息
     */
    public ConversationStoreException(String message, Throwable cause, String suggestion) {
        super(message, cause);
        this.suggestion = suggestion;
    }

    /**
     * 获取建议信息。
     *
     * @return 建议信息，可能为 null
     */
    public String getSuggestion() {
        return suggestion;
    }
}
