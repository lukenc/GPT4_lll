package com.wmsay.gpt4_lll.fc.error;

/**
 * 并发执行异常。
 * 当同一 Project 中已有工具正在执行，且新工具不支持并发时抛出。
 */
public class ConcurrentExecutionException extends RuntimeException {

    public ConcurrentExecutionException(String message) {
        super(message);
    }
}
