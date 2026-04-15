package com.wmsay.gpt4_lll.mcp.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.wmsay.gpt4_lll.fc.tools.Tool;
import com.wmsay.gpt4_lll.fc.tools.ToolContext;
import com.wmsay.gpt4_lll.fc.tools.ToolResult;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件写入工具，支持 overwrite / patch / append / insert_after_line 四种模式。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>原子写入：先写临时文件再 rename，避免写入中途崩溃损坏目标文件</li>
 *   <li>行尾符保留：检测原有 CRLF/LF 风格并在写入时保持一致</li>
 *   <li>patch 唯一性保证：old_content 必须在文件中恰好出现 1 次，否则明确报错</li>
 *   <li>路径安全：复用 McpFileToolSupport.resolvePath 做工作区边界检测</li>
 * </ul>
 */
public class FileWriteTool implements Tool {

    private static final String MODE_OVERWRITE = "overwrite";
    private static final String MODE_PATCH = "patch";
    private static final String MODE_APPEND = "append";
    private static final String MODE_INSERT = "insert_after_line";

    private static final long MAX_CONTENT_BYTES = 5L * 1024 * 1024; // 5 MB

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Write file content. Default mode is patch (str_replace). "
                + "Supports: patch (replace old_content with content), overwrite (replace entire file), "
                + "append (add to end with auto newline), insert_after_line (insert after line N, 1-based). "
                + "Uses atomic write. Max content size: 5 MB. "
                + "Use read_file first to inspect current content before patching.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("path", Map.of(
                "type", "string",
                "required", true,
                "description", "target file path relative to workspace root"));
        schema.put("content", Map.of(
                "type", "string",
                "required", true,
                "description", "The new text to write. In patch mode, this is the replacement text "
                        + "that will replace old_content. Pass empty string in patch mode to delete old_content."));
        schema.put("mode", Map.of(
                "type", "string",
                "required", false,
                "default", MODE_PATCH,
                "enum", List.of(MODE_OVERWRITE, MODE_PATCH, MODE_APPEND, MODE_INSERT),
                "description", "write mode (default: patch). "
                        + "overwrite: replace entire file content; "
                        + "patch: replace old_content with content (str_replace); "
                        + "append: add content after existing file content (auto newline separator); "
                        + "insert_after_line: insert content after the specified line_number."));
        schema.put("old_content", Map.of(
                "type", "string",
                "required", false,
                "description", "The exact original text to find and replace (required for patch mode). "
                        + "Must appear exactly once in the file. "
                        + "Include surrounding context lines if the snippet is not unique."));
        schema.put("line_number", Map.of(
                "type", "integer",
                "required", false,
                "description", "1-based line number. Use 0 to insert at the very beginning of the file "
                        + "(before line 1). Use N to insert after line N. (required for insert_after_line mode)"));
        schema.put("create_dirs", Map.of(
                "type", "boolean",
                "required", false,
                "default", true,
                "description", "Auto-create parent directories if missing (default: true). "
                        + "Set to false if you want an error when the parent directory does not exist."));
        schema.put("encoding", Map.of(
                "type", "string",
                "required", false,
                "default", "utf-8",
                "description", "file encoding"));
        return schema;
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> params) {
        // --- 参数提取 ---
        String mode = McpFileToolSupport.getString(params, "mode", MODE_PATCH);
        if (!MODE_OVERWRITE.equals(mode) && !MODE_PATCH.equals(mode)
                && !MODE_APPEND.equals(mode) && !MODE_INSERT.equals(mode)) {
            return ToolResult.error("Invalid mode '" + mode
                    + "'. Allowed values: overwrite, patch, append, insert_after_line");
        }

        // patch 模式允许空字符串 content（表示删除 old_content）
        String content;
        if (MODE_PATCH.equals(mode)) {
            content = McpFileToolSupport.getStringAllowEmpty(params, "content", null);
        } else {
            content = McpFileToolSupport.getString(params, "content", null);
        }
        if (content == null) {
            return ToolResult.error("Missing required parameter: content");
        }

        boolean createDirs = McpFileToolSupport.getBoolean(params, "create_dirs", true);
        String encodingName = McpFileToolSupport.getString(params, "encoding", "utf-8");

        Charset charset;
        try {
            charset = Charset.forName(encodingName);
        } catch (UnsupportedCharsetException ex) {
            return ToolResult.error("Unsupported encoding '" + encodingName
                    + "'. Use standard charset names such as utf-8, gbk, iso-8859-1.");
        }

        // --- 路径解析与安全检查 ---
        Path filePath;
        try {
            filePath = McpFileToolSupport.resolvePath(context, params, "path");
        } catch (IllegalArgumentException ex) {
            return ToolResult.error(ex.getMessage());
        }

        if (Files.exists(filePath) && !Files.isRegularFile(filePath)) {
            return ToolResult.error("Path exists but is not a regular file: " + filePath);
        }

        // --- 父目录处理 ---
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            if (!createDirs) {
                return ToolResult.error("Parent directory does not exist: " + parentDir
                        + ". Set create_dirs=true to auto-create.");
            }
            try {
                Files.createDirectories(parentDir);
            } catch (IOException ex) {
                return ToolResult.error("Failed to create parent directories for " + parentDir
                        + ": " + friendlyIOMessage(ex));
            }
        }

        // --- 大文件写入保护 ---
        byte[] contentBytes = content.getBytes(charset);
        if (contentBytes.length > MAX_CONTENT_BYTES) {
            return ToolResult.error("Content size " + contentBytes.length
                    + " bytes exceeds maximum allowed size of " + MAX_CONTENT_BYTES
                    + " bytes (5 MB).");
        }

        boolean isNewFile = !Files.exists(filePath);

        // 在写入前捕获原始内容，供 AgentFileChangeHook 追踪变更
        String originalContent = "";
        if (!isNewFile) {
            try {
                originalContent = Files.readString(filePath, charset);
            } catch (IOException ex) {
                // 读取失败不阻塞写入，记录为空
                originalContent = "";
            }
        }

        // --- 分模式执行 ---
        try {
            String finalContent;
            switch (mode) {
                case MODE_OVERWRITE:
                    finalContent = content;
                    break;
                case MODE_PATCH:
                    finalContent = executePatch(filePath, content, params, charset);
                    break;
                case MODE_APPEND:
                    finalContent = executeAppend(filePath, content, charset);
                    break;
                case MODE_INSERT:
                    finalContent = executeInsert(filePath, content, params, charset);
                    break;
                default:
                    return ToolResult.error("Unrecognized mode: " + mode);
            }

            byte[] bytes = finalContent.getBytes(charset);
            atomicWrite(filePath, bytes);

            // 通知 IntelliJ VFS 刷新，使编辑器立即看到磁盘变更
            refreshVirtualFile(filePath);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("tool", name());
            result.put("path", context.getWorkspaceRoot().relativize(filePath).toString());
            result.put("mode", mode);
            result.put("bytes_written", bytes.length);
            result.put("created", isNewFile);
            result.put("original_content", originalContent);
            result.put("error", null);
            return ToolResult.structured(result);

        } catch (WriteToolException ex) {
            return ToolResult.error(ex.getMessage());
        } catch (IOException ex) {
            return ToolResult.error("Write failed: " + friendlyIOMessage(ex));
        }
    }

    // ─── patch 模式：精确字符串替换，必须恰好匹配 1 次 ───

    private String executePatch(Path filePath, String newContent,
                                Map<String, Object> params, Charset charset) throws IOException {
        String oldContent = McpFileToolSupport.getString(params, "old_content", null);
        if (oldContent == null || oldContent.isEmpty()) {
            throw new WriteToolException(
                    "patch mode requires parameter 'old_content' (the text to be replaced).");
        }

        if (!Files.exists(filePath)) {
            throw new WriteToolException(
                    "patch mode requires an existing file, but '" + filePath + "' does not exist. "
                            + "Use overwrite mode to create a new file.");
        }

        String existing = Files.readString(filePath, charset);
        String lineEnding = detectLineEnding(existing);

        // 统一为 LF 做匹配，避免 CRLF/LF 不一致导致的误判
        String normalizedExisting = existing.replace("\r\n", "\n");
        String normalizedOld = oldContent.replace("\r\n", "\n");

        int firstIndex = normalizedExisting.indexOf(normalizedOld);
        if (firstIndex < 0) {
            throw new WriteToolException(
                    "old_content not found in file '" + filePath.getFileName()
                            + "'. Please verify the exact text including whitespace and indentation. "
                            + "Use read_file to check the current file content.");
        }

        int secondIndex = normalizedExisting.indexOf(normalizedOld, firstIndex + 1);
        if (secondIndex >= 0) {
            int count = countOccurrences(normalizedExisting, normalizedOld);
            throw new WriteToolException(
                    "old_content appears " + count + " times in file '"
                            + filePath.getFileName() + "', cannot determine replacement position. "
                            + "Please expand old_content with more surrounding context (include adjacent lines) "
                            + "until the snippet is unique in the file. "
                            + "If you intend to replace all occurrences, use overwrite mode instead.");
        }

        String normalizedNew = newContent.replace("\r\n", "\n");
        String result = normalizedExisting.substring(0, firstIndex)
                + normalizedNew
                + normalizedExisting.substring(firstIndex + normalizedOld.length());

        // 还原原有行尾符风格
        if ("\r\n".equals(lineEnding)) {
            result = result.replace("\n", "\r\n");
        }
        return result;
    }

    // ─── append 模式 ───

    private String executeAppend(Path filePath, String content, Charset charset) throws IOException {
        if (!Files.exists(filePath)) {
            return content;
        }
        String existing = Files.readString(filePath, charset);
        // 自动补换行：如果现有内容不以换行符结尾，插入一个换行符
        if (!existing.isEmpty() && !existing.endsWith("\n") && !existing.endsWith("\r\n")) {
            String lineEnding = detectLineEnding(existing);
            return existing + lineEnding + content;
        }
        return existing + content;
    }

    // ─── insert_after_line 模式 ───

    private String executeInsert(Path filePath, String content,
                                 Map<String, Object> params, Charset charset) throws IOException {
        int lineNumber = McpFileToolSupport.getInt(params, "line_number", -1);
        if (lineNumber < 0) {
            throw new WriteToolException(
                    "insert_after_line mode requires parameter 'line_number' (1-based). "
                            + "Use 0 to insert at the beginning of the file.");
        }

        if (!Files.exists(filePath)) {
            if (lineNumber != 0) {
                throw new WriteToolException(
                        "insert_after_line mode requires an existing file when line_number > 0, "
                                + "but '" + filePath + "' does not exist.");
            }
            return content;
        }

        String existing = Files.readString(filePath, charset);
        String lineEnding = detectLineEnding(existing);
        String sep = lineEnding;

        List<String> lines = splitLines(existing);
        if (lineNumber > lines.size()) {
            throw new WriteToolException(
                    "line_number " + lineNumber + " exceeds total lines (" + lines.size()
                            + ") in file '" + filePath.getFileName() + "'.");
        }

        List<String> result = new ArrayList<>(lines.size() + 1);
        for (int i = 0; i < lines.size(); i++) {
            if (i == lineNumber) {
                // insert_after_line=0 → 在第 0 行之后（即文件开头）插入
                // insert_after_line=n → 在第 n 行之后插入
                result.add(content);
            }
            result.add(lines.get(i));
        }
        if (lineNumber == lines.size()) {
            result.add(content);
        }

        // 用原有行尾符拼接
        boolean trailingNewline = existing.endsWith("\n") || existing.endsWith("\r\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(result.get(i));
        }
        if (trailingNewline) {
            sb.append(sep);
        }
        return sb.toString();
    }

    // ─── VFS 刷新：通知 IDE 磁盘文件已变更 ───

    /**
     * 通知 IntelliJ VFS 刷新指定文件，使编辑器能立即看到磁盘上的变更。
     * 通过 invokeLater 在 EDT 上执行，避免阻塞工具执行线程。
     */
    private static void refreshVirtualFile(Path filePath) {
        if (ApplicationManager.getApplication() == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            LocalFileSystem fs = LocalFileSystem.getInstance();
            File ioFile = filePath.toFile();
            VirtualFile vf = fs.refreshAndFindFileByIoFile(ioFile);
            if (vf != null) {
                vf.refresh(false, false);
            }
            // 同时刷新父目录（处理新建文件场景）
            File parent = ioFile.getParentFile();
            if (parent != null) {
                VirtualFile parentVf = fs.refreshAndFindFileByIoFile(parent);
                if (parentVf != null) {
                    parentVf.refresh(false, false);
                }
            }
        });
    }

    // ─── 原子写入：先写临时文件再 rename ───

    /**
     * 原子写入：先把内容写到同目录下的临时文件，再通过 rename 替换目标文件。
     * rename 在同文件系统上是原子操作，可以防止写到一半崩溃导致目标文件损坏。
     */
    private static void atomicWrite(Path target, byte[] data) throws IOException {
        Path parent = target.getParent();
        Path tmpFile = Files.createTempFile(
                parent != null ? parent : target.toAbsolutePath().getParent(),
                ".write_file_", ".tmp");
        try {
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                os.write(data);
                os.flush();
            }
            try {
                Files.move(tmpFile, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    // ─── 辅助方法 ───

    /**
     * 检测文件内容的行尾符风格。如果 CRLF 出现次数 >= LF-only 次数则视为 CRLF 文件。
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
                i++; // skip the \n
            } else if (c == '\n') {
                lf++;
            }
        }
        return crlf >= lf && crlf > 0 ? "\r\n" : "\n";
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    /**
     * 按行拆分，保留空行但不保留行尾符本身。
     */
    private static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int len = content.length();
        while (start <= len) {
            int nlPos = content.indexOf('\n', start);
            int crPos = content.indexOf('\r', start);

            int end;
            int nextStart;
            if (nlPos < 0 && crPos < 0) {
                // 没有更多换行符了
                if (start < len) {
                    lines.add(content.substring(start));
                }
                break;
            } else if (crPos >= 0 && crPos < (nlPos < 0 ? Integer.MAX_VALUE : nlPos)) {
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
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static String friendlyIOMessage(IOException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.toLowerCase().contains("permission denied")) {
            return "Permission denied. Check file/directory permissions.";
        }
        if (msg != null && msg.toLowerCase().contains("read-only file system")) {
            return "File system is read-only.";
        }
        return msg != null ? msg : ex.getClass().getSimpleName();
    }

    /**
     * 工具内部异常，携带人类友好的错误描述。
     * 不暴露底层 errno 或堆栈信息，直接作为 error message 返回给 agent。
     */
    private static class WriteToolException extends RuntimeException {
        WriteToolException(String message) {
            super(message);
        }
    }
}
