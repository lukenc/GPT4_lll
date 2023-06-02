
package com.wmsay.gpt4_lll.model;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;

public class Message {

    @Expose
    private String content;
    @Expose
    private String role;

    private String name;

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
}
