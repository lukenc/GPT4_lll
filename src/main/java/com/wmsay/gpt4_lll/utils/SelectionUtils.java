package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.wmsay.gpt4_lll.model.SelectionContent;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑器选区相关的通用工具。
 */
public final class SelectionUtils {

    private SelectionUtils() {
    }

    /**
     * 获取当前选中区域的内容与对应行号信息。
     * <p>当未选中文本时返回 {@code null}。</p>
     */
    public static SelectionContent getSelectionWithLineNumbers(Editor editor) {
        if (editor == null) {
            return null;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        if (!selectionModel.hasSelection()) {
            return null;
        }

        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            return null;
        }

        Document document = editor.getDocument();
        int startLine = document.getLineNumber(selectionModel.getSelectionStart()) + 1;

        String[] lines = selectedText.split("\n", -1);
        List<SelectionContent.LineWithNumber> lineInfos = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 兼容 Windows 换行，去掉行尾的 \r
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            lineInfos.add(new SelectionContent.LineWithNumber(startLine + i, line));
        }

        return new SelectionContent(selectedText, startLine, lineInfos);
    }
}

