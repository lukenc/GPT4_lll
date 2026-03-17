package com.wmsay.gpt4_lll.component.block;

import javax.swing.*;

/**
 * 内容块接口，代表一个轮次（TurnPanel）中的单个可视元素。
 * 不同类型的内容块（Markdown、思考过程、工具调用等）均实现此接口。
 */
public interface ContentBlock {

    BlockType getType();

    JComponent getComponent();

    /**
     * 是否支持流式内容追加。
     * MarkdownBlock 和 ThinkingBlock 返回 true，ToolCallBlock 等返回 false。
     */
    default boolean isAppendable() {
        return false;
    }

    /**
     * 追加流式内容片段。仅 isAppendable() 为 true 时有效。
     */
    default void appendContent(String delta) {
        throw new UnsupportedOperationException(getType() + " does not support appendContent");
    }
}
