package com.wmsay.gpt4_lll.mcp.tools.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * 有界输出缓冲区。
 * 从 InputStream 读取内容并累积到内存中，超出字节上限后仅保留头部内容并继续统计总字节数。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>按行读取，避免在多字节字符中间截断</li>
 *   <li>记录原始总字节数，用于结果中的 stdoutBytes / stderrBytes</li>
 *   <li>截断后标记 truncated，结果中体现</li>
 * </ul>
 */
public class OutputAccumulator {

    private final StringBuilder buffer = new StringBuilder();
    private final int maxBytes;
    private long totalBytes;
    private boolean truncated;

    public OutputAccumulator(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * 从 InputStream 读取全部内容（直到 EOF）。
     * 应在独立线程中调用，避免阻塞主线程。
     */
    public void consume(InputStream inputStream, Charset charset) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, charset))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                int lineBytes = line.getBytes(charset).length + 1; // +1 for newline
                totalBytes += lineBytes;

                if (!truncated) {
                    if (buffer.length() + line.length() + 1 > maxBytes) {
                        truncated = true;
                        int remaining = maxBytes - buffer.length();
                        if (remaining > 0) {
                            if (!first) {
                                buffer.append('\n');
                                remaining--;
                            }
                            if (remaining > 0) {
                                buffer.append(line, 0, Math.min(remaining, line.length()));
                            }
                        }
                        buffer.append("\n... [output truncated, total bytes: see stdoutBytes/stderrBytes]");
                    } else {
                        if (!first) {
                            buffer.append('\n');
                        }
                        buffer.append(line);
                    }
                }
                first = false;
            }
        }
    }

    public String getContent() {
        return buffer.toString();
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
