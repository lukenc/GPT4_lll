package com.wmsay.gpt4_lll.component.block;

/**
 * 流式内容拆分器。
 * 基于行解析，检测 ``` 代码围栏，将流式内容拆分为 markdown 段和 code 段。
 * 内联代码（单反引号 `code`）不会被拆分，仍由 MarkdownBlock 处理。
 */
public class StreamContentSplitter {

    public enum State { NORMAL, IN_CODE }

    public interface Sink {
        void onMarkdownContent(String text);
        void onCodeFenceStart(String language);
        void onCodeContent(String text);
        void onCodeFenceEnd();
    }

    private State state = State.NORMAL;
    private final StringBuilder lineBuffer = new StringBuilder();
    private final Sink sink;

    public StreamContentSplitter(Sink sink) {
        this.sink = sink;
    }

    public State getState() {
        return state;
    }

    /**
     * 接收流式增量内容，按行解析并触发回调。
     */
    public void append(String delta) {
        lineBuffer.append(delta);

        int start = 0;
        for (int i = 0; i < lineBuffer.length(); i++) {
            if (lineBuffer.charAt(i) == '\n') {
                String line = lineBuffer.substring(start, i + 1);
                start = i + 1;
                processLine(line);
            }
        }

        if (start > 0) {
            lineBuffer.delete(0, start);
        }
    }

    /**
     * 将缓冲中的不完整行强制输出到当前 block（生成结束时调用）。
     */
    public void flush() {
        if (lineBuffer.isEmpty()) {
            return;
        }
        String remaining = lineBuffer.toString();
        lineBuffer.setLength(0);

        if (state == State.NORMAL) {
            sink.onMarkdownContent(remaining);
        } else {
            sink.onCodeContent(remaining);
        }
    }

    public void reset() {
        state = State.NORMAL;
        lineBuffer.setLength(0);
    }

    private void processLine(String line) {
        String trimmed = line.trim();

        if (state == State.NORMAL) {
            if (trimmed.startsWith("```")) {
                String lang = trimmed.substring(3).trim();
                state = State.IN_CODE;
                sink.onCodeFenceStart(lang);
            } else {
                sink.onMarkdownContent(line);
            }
        } else {
            if (trimmed.equals("```")) {
                state = State.NORMAL;
                sink.onCodeFenceEnd();
            } else {
                sink.onCodeContent(line);
            }
        }
    }
}
