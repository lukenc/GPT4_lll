package com.wmsay.gpt4_lll.fc.core;

import java.util.List;
import java.util.Map;
import com.alibaba.fastjson.annotation.JSONField;

public class Message {

    private String content;
    private String role;

    private String name;

    @JSONField(name = "tool_call_id")
    private String toolCallId;

    /**
     * OpenAI function calling: assistant 消息中的 tool_calls 数组。
     * 当 AI 请求调用工具时，此字段包含工具调用的结构化数据。
     * 序列化为 JSON 时作为 "tool_calls" 字段发送给 API。
     */
    @JSONField(name = "tool_calls")
    private List<Object> toolCalls;

    @JSONField(name = "thinking_content")
    private String thinkingContent;

    /**
     * OpenAI/DeepSeek 等响应里的推理字段。
     * 与 thinkingContent 分开保存，避免在 JSON 反序列化时丢失 reasoning_content。
     */
    @JSONField(name = "reasoning_content")
    private String reasoningContent;

    @JSONField(name = "tool_call_summaries")
    private List<ToolCallSummary> toolCallSummaries;

    /** 按时间顺序保存的内容块列表，用于历史加载时保持正确的交错顺序。 */
    @JSONField(name = "content_blocks")
    private List<ContentBlockRecord> contentBlocks;

    /**
     * 有序内容块记录。type 取值：
     * "thinking" — 思考过程，content 为思考文本
     * "text" — AI 对话文本，content 为正文
     * "tool_use" — 工具调用，toolName/params/success/durationMs
     * "tool_result" — 工具结果，toolName/resultText
     * "plan" — PlanAndExecute 执行计划，content 为格式化的计划文本
     * "plan_step" — 计划步骤执行完成，stepIndex/stepSuccess/stepResult
     */
    public static class ContentBlockRecord {
        private String type;
        private String content;
        @JSONField(name = "tool_name")
        private String toolName;
        private Map<String, Object> params;
        private boolean success;
        @JSONField(name = "duration_ms")
        private long durationMs;
        @JSONField(name = "result_text")
        private String resultText;
        /** plan_step 类型：步骤索引（从 0 开始） */
        @JSONField(name = "step_index")
        private Integer stepIndex;
        /** plan_step 类型：步骤是否成功 */
        @JSONField(name = "step_success")
        private Boolean stepSuccess;
        /** plan_step 类型：步骤执行结果摘要 */
        @JSONField(name = "step_result")
        private String stepResult;

        /** file_changes 类型：文件快照记录列表 */
        @JSONField(name = "file_snapshots")
        private List<FileSnapshotRecord> fileSnapshots;

        /**
         * 文件快照持久化记录，用于 file_changes 类型的 ContentBlockRecord。
         * 仅保存文件路径和变更类型元数据，不保存文件内容。
         */
        public static class FileSnapshotRecord {
            @JSONField(name = "file_path")
            private String filePath;

            @JSONField(name = "change_type")
            private String changeType; // "added", "deleted", "modified"

            public FileSnapshotRecord() {}

            public FileSnapshotRecord(String filePath, String changeType) {
                this.filePath = filePath;
                this.changeType = changeType;
            }

            public String getFilePath() { return filePath; }
            public void setFilePath(String filePath) { this.filePath = filePath; }
            public String getChangeType() { return changeType; }
            public void setChangeType(String changeType) { this.changeType = changeType; }
        }

        public ContentBlockRecord() {}

        public static ContentBlockRecord thinking(String content) {
            ContentBlockRecord r = new ContentBlockRecord();
            r.type = "thinking";
            r.content = content;
            return r;
        }

        public static ContentBlockRecord text(String content) {
            ContentBlockRecord r = new ContentBlockRecord();
            r.type = "text";
            r.content = content;
            return r;
        }

        public static ContentBlockRecord toolUse(String toolName, Map<String, Object> params,
                                                  boolean success, long durationMs) {
            ContentBlockRecord r = new ContentBlockRecord();
            r.type = "tool_use";
            r.toolName = toolName;
            r.params = params;
            r.success = success;
            r.durationMs = durationMs;
            return r;
        }

        public static ContentBlockRecord toolResult(String toolName, String resultText) {
            ContentBlockRecord r = new ContentBlockRecord();
            r.type = "tool_result";
            r.toolName = toolName;
            r.resultText = resultText;
            return r;
        }

        /** PlanAndExecute：执行计划（步骤列表） */
        public static ContentBlockRecord plan(String planText) {
            ContentBlockRecord r = new ContentBlockRecord();
            r.type = "plan";
            r.content = planText;
            return r;
        }

        /** PlanAndExecute：单个步骤执行完成 */
        public static ContentBlockRecord planStep(int stepIndex, boolean success, String resultSummary) {
            ContentBlockRecord r = new ContentBlockRecord();
            r.type = "plan_step";
            r.stepIndex = stepIndex;
            r.stepSuccess = success;
            r.stepResult = resultSummary != null ? resultSummary : "";
            return r;
        }

        /** 文件变更摘要块 */
        public static ContentBlockRecord fileChanges(List<FileSnapshotRecord> snapshots) {
            ContentBlockRecord r = new ContentBlockRecord();
            r.type = "file_changes";
            r.fileSnapshots = snapshots;
            return r;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getResultText() { return resultText; }
        public void setResultText(String resultText) { this.resultText = resultText; }
        public Integer getStepIndex() { return stepIndex; }
        public void setStepIndex(Integer stepIndex) { this.stepIndex = stepIndex; }
        public Boolean getStepSuccess() { return stepSuccess; }
        public void setStepSuccess(Boolean stepSuccess) { this.stepSuccess = stepSuccess; }
        public String getStepResult() { return stepResult; }
        public void setStepResult(String stepResult) { this.stepResult = stepResult; }
        public List<FileSnapshotRecord> getFileSnapshots() { return fileSnapshots; }
        public void setFileSnapshots(List<FileSnapshotRecord> fileSnapshots) { this.fileSnapshots = fileSnapshots; }
    }

    public static class ToolCallSummary {
        @JSONField(name = "tool_name")
        private String toolName;
        private Map<String, Object> params;
        private boolean success;
        @JSONField(name = "duration_ms")
        private long durationMs;
        @JSONField(name = "result_text")
        private String resultText;

        public ToolCallSummary() {}

        public ToolCallSummary(String toolName, Map<String, Object> params,
                               boolean success, long durationMs, String resultText) {
            this.toolName = toolName;
            this.params = params;
            this.success = success;
            this.durationMs = durationMs;
            this.resultText = resultText;
        }

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getResultText() { return resultText; }
        public void setResultText(String resultText) { this.resultText = resultText; }
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JSONField(name = "tool_call_id")
    public String getToolCallId() {
        return toolCallId;
    }

    @JSONField(name = "tool_call_id")
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    @JSONField(name = "tool_calls")
    public List<Object> getToolCalls() {
        return toolCalls;
    }

    @JSONField(name = "tool_calls")
    public void setToolCalls(List<Object> toolCalls) {
        this.toolCalls = toolCalls;
    }

    @JSONField(name = "thinking_content")
    public String getThinkingContent() {
        return thinkingContent;
    }

    @JSONField(name = "thinking_content")
    public void setThinkingContent(String thinkingContent) {
        this.thinkingContent = thinkingContent;
    }

    @JSONField(name = "reasoning_content")
    public String getReasoningContent() {
        return reasoningContent;
    }

    @JSONField(name = "reasoning_content")
    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    @JSONField(name = "tool_call_summaries")
    public List<ToolCallSummary> getToolCallSummaries() {
        return toolCallSummaries;
    }

    @JSONField(name = "tool_call_summaries")
    public void setToolCallSummaries(List<ToolCallSummary> toolCallSummaries) {
        this.toolCallSummaries = toolCallSummaries;
    }

    @JSONField(name = "content_blocks")
    public List<ContentBlockRecord> getContentBlocks() {
        return contentBlocks;
    }

    @JSONField(name = "content_blocks")
    public void setContentBlocks(List<ContentBlockRecord> contentBlocks) {
        this.contentBlocks = contentBlocks;
    }
}
