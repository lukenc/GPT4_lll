package com.wmsay.gpt4_lll.service;

import com.wmsay.gpt4_lll.model.CommentTranslation;

/**
 * 翻译进度回调接口
 * Translation progress callback interface
 */
public interface TranslationProgressCallback {
    
    /**
     * 当单个注释翻译完成时调用
     * Called when a single comment translation is completed
     * 
     * @param translation 完成翻译的注释
     * @param currentIndex 当前索引（从0开始）
     * @param totalCount 总注释数量
     */
    void onTranslationCompleted(CommentTranslation translation, int currentIndex, int totalCount);
    
    /**
     * 当翻译出现错误时调用
     * Called when translation error occurs
     * 
     * @param error 错误信息
     * @param currentIndex 当前索引
     * @param totalCount 总注释数量
     */
    default void onTranslationError(String error, int currentIndex, int totalCount) {
        // 默认实现：不处理错误
    }
    
    /**
     * 当所有翻译完成时调用
     * Called when all translations are completed
     */
    default void onAllTranslationsCompleted() {
        // 默认实现：不处理完成事件
    }
}
