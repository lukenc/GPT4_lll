package com.wmsay.gpt4_lll.component;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.wmsay.gpt4_lll.CommentAction;
import com.wmsay.gpt4_lll.model.Message;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.text.Document;
import java.awt.Point;
import java.io.StringReader;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Gpt4lllTextArea extends JEditorPane {
    private Parser parser;
    private HtmlRenderer renderer;
    private StringBuilder contentBuilder;

    MutableDataSet OPTIONS ;

    private final ConcurrentLinkedQueue<String> pendingContent = new ConcurrentLinkedQueue<>();
    private Timer updateTimer;
    private static final int COALESCE_DELAY_MS = 80;

    private JScrollPane scrollPane;
    /** 上一次渲染后的 HTML，用于跳过无变化的更新 */
    private String lastRenderedHtml = "";

    public void setScrollPane(JScrollPane scrollPane) {
        this.scrollPane = scrollPane;
    }

    public Gpt4lllTextArea() {
        setContentType("text/html");
        setEditable(false);
        // 启用双缓冲，防止重绘时闪烁
        setDoubleBuffered(true);
        contentBuilder = new StringBuilder();
        OPTIONS = new MutableDataSet();
        parser = Parser.builder(OPTIONS).build();
        renderer = HtmlRenderer.builder(OPTIONS).build();
        initTimer();
    }

    private void initTimer() {
        updateTimer = new Timer(COALESCE_DELAY_MS, e -> flushPendingContent());
        updateTimer.setRepeats(false);
    }

    private void flushPendingContent() {
        String item;
        boolean drained = false;
        while ((item = pendingContent.poll()) != null) {
            contentBuilder.append(item);
            drained = true;
        }
        if (!drained) {
            return;
        }

        boolean wasAtBottom = isAtBottom();
        Point oldViewPosition = (scrollPane != null) ? scrollPane.getViewport().getViewPosition() : null;

        updateText();

        if (wasAtBottom) {
            scrollToBottom();
        } else if (oldViewPosition != null) {
            scrollPane.getViewport().setViewPosition(oldViewPosition);
        }
    }

    public void clearShowWindow() {
        updateTimer.stop();
        pendingContent.clear();
        contentBuilder.setLength(0);
        lastRenderedHtml = "";
        setText("""
                <html>
                   <head>
                </head>
                <body style='width: 100%;'>
                </body>
                </html>
                """);
    }

    private void updateText() {
        String html = renderer.render(parser.parse(contentBuilder.toString()));
        // 跳过内容未变化的更新，避免无意义的重绘
        if (html.equals(lastRenderedHtml)) {
            return;
        }
        lastRenderedHtml = html;

        String fullHtml = """
                <html>
                   <head>
                """
                +
                """
                 </head>
                
                 <body style='width: 100%;'>
             """
                +
                html
                +
                "</body></html>";

        // 用 Document 级别替换代替 setText()，避免组件先清空再重绘导致的闪烁
        try {
            Document doc = getDocument();
            doc.putProperty(Document.StreamDescriptionProperty, null);
            // 创建新的空 Document 并读入内容，然后一次性替换
            getEditorKit().read(new StringReader(fullHtml), doc, 0);
        } catch (Exception e) {
            // 降级回 setText，保证功能不受影响
            setText(fullHtml);
        }
    }

    private void scrollToBottom() {
        if (scrollPane != null) {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        }
    }

    private boolean isAtBottom() {
        if (scrollPane != null) {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            return vertical.getValue() + vertical.getVisibleAmount() >= vertical.getMaximum() - 5;
        }
        return false;
    }

    public void appendThingkingTitle() {
        appendContent("====思考过程/Reasoning Process==== \n\n");
    }

    public void appendThingkingEnd() {
        appendContent("\n\n====思考过程结束/Reasoning Process End====\n\n\n");
        appendContent("====完整回复/Reasoned response====\n");
    }

    public void appendContent(String content) {
        pendingContent.add(content);
        SwingUtilities.invokeLater(() -> {
            if (!updateTimer.isRunning()) {
                updateTimer.restart();
            }
        });
    }

    public void appendMessage(Message content) {
        if (content.getContent().startsWith("你是一个")){
            return;
        } else if (content.getContent().startsWith("请帮我完成下面的功能，同时使用")){
            String[] str= content.getContent().split("回复我，功能如下：");
            String xuqiu=str[1];
            appendContent("Generate："+xuqiu);
        }else if (content.getContent().startsWith("请帮我重构下面的代码，不局限于代码性能优化、命名优化、增加注释、简化代码、优化逻辑，请使用")){
            String[] str= content.getContent().split("回复我，代码如下：");
            String xuqiu=str[1];
            appendContent("Optimize："+xuqiu);
        }else if (content.getContent().startsWith("todo后的文字是需要完成的功能，请帮我实现这些描述的功能，同时使用")){
            String[] str= content.getContent().split("需要实现的代码如下：");
            String xuqiu=str[1];
            appendContent("Complete："+xuqiu);
        } else if (content.getContent().startsWith(CommentAction.PROMPT.split("\n")[0])) {
            String[] str= content.getContent().split("代码内容：");
            String xuqiu=str[1];
            xuqiu=xuqiu.split("2. 注释要求：")[0];
            appendContent("Comment："+xuqiu);
        }
        else if (content.getContent().startsWith("评估不限于以下")) {
            String[] str= content.getContent().split("如下代码:");
            String xuqiu=str[1];
            appendContent("Score："+xuqiu);
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
