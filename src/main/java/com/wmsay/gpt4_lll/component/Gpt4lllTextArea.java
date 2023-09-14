package com.wmsay.gpt4_lll.component;

import com.wmsay.gpt4_lll.model.Message;

import javax.swing.*;

public class Gpt4lllTextArea extends JEditorPane {
    public void clearShowWindow() {
        setText("");
    }

    public void appendContent(String content) {
        setText(getText()+content);
        setCaretPosition(getDocument().getLength());
    }


    public  void appendMessage(Message content) {
        if (content.getContent().startsWith("你是一个有用的助手，")){
            return;
        }else
        if (content.getContent().startsWith("请帮我完成下面的功能，同时使用")){
            String[] str= content.getContent().split("回复我，功能如下：");
            String xuqiu=str[1];
            setText(getText()+"Generate："+xuqiu);
        }else
        if (content.getContent().startsWith("请帮我重构下面的代码，不局限于代码性能优化、命名优化、增加注释、简化代码、优化逻辑，请使用")){
            String[] str= content.getContent().split("回复我，代码如下：");
            String xuqiu=str[1];
            setText(getText()+"Optimize："+xuqiu);
        }else if (content.getContent().startsWith("todo后的文字是需要完成的功能，请帮我实现这些描述的功能，同时使用")){
            String[] str= content.getContent().split("需要实现的代码如下：");
            String xuqiu=str[1];
            setText(getText()+"Complete："+xuqiu);
        } else if (content.getContent().startsWith("请帮忙使用")) {
            String[] str= content.getContent().split("代码如下:");
            String xuqiu=str[1];
            setText(getText()+"Comment："+xuqiu);
        } else
        {
            appendContent("\n\n- - - - - - - - - - - \n");
            if (content.getRole().equals("user")){
                appendContent("YOU:"+content.getContent());
            }else {
                appendContent(content.getContent());
            }

        }
    }

}
