package com.wmsay.gpt4_lll.fc.error;

/**
 * 用户拒绝异常。
 * 当用户在审批对话框中拒绝工具执行时抛出。
 */
public class UserRejectedException extends RuntimeException {

    public UserRejectedException(String message) {
        super(message);
    }
}
