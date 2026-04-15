package com.wmsay.gpt4_lll.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 根据写入模式计算"修改后"的完整文件预览内容。
 * 用于 DiffCommitDialog 展示修改前后的差异。
 */
public final class WritePreviewCalculator {

    private WritePreviewCalculator() {
        // 工具类，禁止实例化
    }

    /**
     * 根据写入模式计算"修改后"的完整文件内容。
     *
     * @param mode            写入模式：overwrite / patch / append / insert_after_line
     * @param originalContent 原始文件内容（新建文件为空字符串）
     * @param content         待写入的内容
     * @param params          工具调用参数（patch 模式需要 old_content，insert_after_line 需要 line_number）
     * @param isNewFile       是否为新建文件
     * @return 修改后的完整文件内容
     */
    public static String computePreview(String mode, String originalContent,
                                        String content, Map<String, Object> params,
                                        boolean isNewFile) {
        if (mode == null) {
            return content;
        }
        switch (mode) {
            case "overwrite":
                return content;
            case "patch":
                String oldContent = (String) params.get("old_content");
                return originalContent.replace(oldContent, content);
            case "append":
                if (isNewFile || originalContent.isEmpty()) {
                    return content;
                }
                if (!originalContent.endsWith("\n") && !originalContent.endsWith("\r\n")) {
                    return originalContent + "\n" + content;
                }
                return originalContent + content;
            case "insert_after_line":
                int lineNumber = ((Number) params.get("line_number")).intValue();
                return insertAfterLine(originalContent, content, lineNumber);
            default:
                return content;
        }
    }

    /**
     * 在指定行号后插入内容。
     * <p>
     * 行号从 0 开始：0 表示在文件开头插入，n 表示在第 n 行之后插入。
     * 行号越界时进行钳位处理：负数视为 0，超过总行数视为追加到末尾。
     *
     * @param originalContent 原始文件内容
     * @param content         待插入的内容
     * @param lineNumber      插入位置（在此行之后插入）
     * @return 插入后的完整文件内容
     */
    public static String insertAfterLine(String originalContent, String content, int lineNumber) {
        if (originalContent == null || originalContent.isEmpty()) {
            return content;
        }

        String lineEnding = detectLineEnding(originalContent);
        List<String> lines = splitLines(originalContent);

        // 钳位处理：行号越界时调整到合法范围
        if (lineNumber < 0) {
            lineNumber = 0;
        }
        if (lineNumber > lines.size()) {
            lineNumber = lines.size();
        }

        List<String> result = new ArrayList<>(lines.size() + 1);
        for (int i = 0; i < lines.size(); i++) {
            if (i == lineNumber) {
                result.add(content);
            }
            result.add(lines.get(i));
        }
        if (lineNumber == lines.size()) {
            result.add(content);
        }

        boolean trailingNewline = originalContent.endsWith("\n") || originalContent.endsWith("\r\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                sb.append(lineEnding);
            }
            sb.append(result.get(i));
        }
        if (trailingNewline) {
            sb.append(lineEnding);
        }
        return sb.toString();
    }

    // ─── 内部辅助方法 ───

    /**
     * 检测内容中使用的行尾符（CRLF 或 LF）。
     */
    static String detectLineEnding(String content) {
        if (content == null || content.isEmpty()) {
            return "\n";
        }
        int crlf = 0;
        int lf = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                crlf++;
                i++;
            } else if (c == '\n') {
                lf++;
            }
        }
        return crlf >= lf && crlf > 0 ? "\r\n" : "\n";
    }

    /**
     * 按行拆分内容，保留空行但不保留行尾符本身。
     */
    static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int len = content.length();
        while (start <= len) {
            int nlPos = content.indexOf('\n', start);
            int crPos = content.indexOf('\r', start);

            if (nlPos < 0 && crPos < 0) {
                if (start < len) {
                    lines.add(content.substring(start));
                }
                break;
            }

            int end;
            int nextStart;
            if (crPos >= 0 && crPos < (nlPos < 0 ? Integer.MAX_VALUE : nlPos)) {
                end = crPos;
                nextStart = (crPos + 1 < len && content.charAt(crPos + 1) == '\n')
                        ? crPos + 2 : crPos + 1;
            } else {
                end = nlPos;
                nextStart = nlPos + 1;
            }

            lines.add(content.substring(start, end));
            start = nextStart;
        }
        return lines;
    }
}
