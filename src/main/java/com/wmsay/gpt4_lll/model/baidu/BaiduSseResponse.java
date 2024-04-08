package com.wmsay.gpt4_lll.model.baidu;

import java.util.StringJoiner;

public class BaiduSseResponse {
    private String id;
    private String object;
    private long created;
    private int sentence_id;
    private boolean is_end;
    private boolean is_truncated;
    private String result;
    private boolean need_clear_history;

    public String getId() {
        return id;
    }

    public BaiduSseResponse setId(String id) {
        this.id = id;
        return this;
    }

    public String getObject() {
        return object;
    }

    public BaiduSseResponse setObject(String object) {
        this.object = object;
        return this;
    }

    public long getCreated() {
        return created;
    }

    public BaiduSseResponse setCreated(long created) {
        this.created = created;
        return this;
    }

    public int getSentence_id() {
        return sentence_id;
    }

    public BaiduSseResponse setSentence_id(int sentence_id) {
        this.sentence_id = sentence_id;
        return this;
    }

    public boolean isIs_end() {
        return is_end;
    }

    public BaiduSseResponse setIs_end(boolean is_end) {
        this.is_end = is_end;
        return this;
    }

    public boolean isIs_truncated() {
        return is_truncated;
    }

    public BaiduSseResponse setIs_truncated(boolean is_truncated) {
        this.is_truncated = is_truncated;
        return this;
    }

    public String getResult() {
        return result;
    }

    public BaiduSseResponse setResult(String result) {
        this.result = result;
        return this;
    }

    public boolean isNeed_clear_history() {
        return need_clear_history;
    }

    public BaiduSseResponse setNeed_clear_history(boolean need_clear_history) {
        this.need_clear_history = need_clear_history;
        return this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", BaiduSseResponse.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("object='" + object + "'")
                .add("created=" + created)
                .add("sentence_id=" + sentence_id)
                .add("is_end=" + is_end)
                .add("is_truncated=" + is_truncated)
                .add("result='" + result + "'")
                .add("need_clear_history=" + need_clear_history)
                .toString();
    }
}
