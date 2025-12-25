package com.wmsay.gpt4_lll.model;

import java.util.Collections;
import java.util.List;

/**
 * 封装编辑器选中区域的信息，包含原始内容、起始行号以及逐行的行号映射。
 */
public class SelectionContent {
    private final String rawText;
    private final int startLine;
    private final List<LineWithNumber> lines;

    public SelectionContent(String rawText, int startLine, List<LineWithNumber> lines) {
        this.rawText = rawText;
        this.startLine = startLine;
        this.lines = lines;
    }

    public String getRawText() {
        return rawText;
    }

    public int getStartLine() {
        return startLine;
    }

    public List<LineWithNumber> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * 将行号与内容拼接成带行号的文本，格式如 "  12 | code...".
     */
    public String toNumberedText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            LineWithNumber line = lines.get(i);
            sb.append(String.format("%d: %s", line.getLineNumber(), line.getContent()));
            if (i < lines.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 单行信息，包含行号与内容。
     */
    public static class LineWithNumber {
        private final int lineNumber;
        private final String content;

        public LineWithNumber(int lineNumber, String content) {
            this.lineNumber = lineNumber;
            this.content = content;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getContent() {
            return content;
        }
    }
}

