package com.wmsay.gpt4_lll.component;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.profile.pegdown.Extensions;
import com.vladsch.flexmark.profile.pegdown.PegdownOptionsAdapter;
import com.vladsch.flexmark.util.data.DataHolder;
import com.wmsay.gpt4_lll.model.Message;

import javax.swing.*;

public class Gpt4lllTextArea extends JEditorPane {
    private Parser parser;
    private HtmlRenderer renderer;
    private StringBuilder contentBuilder;

    final private static DataHolder OPTIONS = PegdownOptionsAdapter.flexmarkOptions(
            Extensions.ALL
    );
    public Gpt4lllTextArea() {
        setContentType("text/html");
        setEditable(false);
        contentBuilder = new StringBuilder("<html><body style='width: 100%; word-wrap: break-word;'>");

        parser = Parser.builder(OPTIONS).build();
        renderer = HtmlRenderer.builder(OPTIONS).build();
    }

    public void clearShowWindow() {
        contentBuilder.setLength(0);
        updateText();
    }
    private void updateText() {
        String html = renderer.render(parser.parse(contentBuilder.toString()));
        setText("<html><body style='width: 100%; word-wrap: break-word;'>" + html + "</body></html>");

        setCaretPosition(getDocument().getLength());
    }


    public void appendContent(String content) {
        contentBuilder.append(content);
        updateText();
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
            String[] str= content.getContent().split("如下代码:");
            if (str.length<2){
                str= content.getContent().split("代码如下:");
            }
            String xuqiu=str[1];
            setText(getText()+"Comment："+xuqiu);
        }
        else if (content.getContent().startsWith("评估不限于以下")) {
            String[] str= content.getContent().split("如下代码:");
            if (str.length<2){
                str= content.getContent().split("代码如下:");
            }
            String xuqiu=str[1];
            setText(getText()+"Score："+xuqiu);
        }

        else
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
