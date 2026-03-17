package com.wmsay.gpt4_lll.component.block;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.StringReader;
import java.util.*;
import java.util.List;

/**
 * 工具执行结果内容块。
 * 默认折叠显示摘要，点击可展开查看完整结果。
 * <p>
 * 智能解析：自动识别 JSON 结构化结果，提取关键字段以人类可读方式展示；
 * 对于纯文本和 Markdown 内容，使用 flexmark 渲染。
 * <p>
 * 宽度约束：htmlPane 的 preferredSize 严格跟随父容器宽度，
 * 配合 CSS word-wrap 确保内容不会溢出右边栏。
 */
public class ToolResultBlock implements ContentBlock {

    private final JPanel wrapper;
    private final JLabel titleLabel;
    private final JPanel contentPanel;
    private boolean collapsed = true;

    /** 结果原始文本 */
    private final String resultText;
    /** 工具名称 */
    private final String toolName;

    /** flexmark 解析器 */
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final JEditorPane htmlPane;

    /** 渲染时动态计算的主题色（在 renderResult 中初始化） */
    private String fgHex;
    private String codeBgHex;
    private String mutedFgHex;

    /** 左侧强调色：绿色系表示结果 */
    private static final JBColor ACCENT_COLOR = new JBColor(
            new Color(0x4CAF50), new Color(0x66BB6A));

    /** JSON 中被视为"元数据"的键，展示在摘要区而非正文 */
    private static final Set<String> META_KEYS = Set.of(
            "tool", "status", "path", "maxDepth", "showFiles", "showHidden",
            "entryCount", "truncated", "ignoredDirs", "totalMatches",
            "matchCount", "searchPattern", "fileCount", "encoding",
            "totalLines", "startLine", "endLine", "lineCount"
    );

    /** JSON 中被视为"大段正文内容"的键，单独渲染 */
    private static final Set<String> CONTENT_KEYS = Set.of(
            "tree", "content", "matches", "results", "output", "data", "text"
    );

    public ToolResultBlock(String toolName, String resultText) {
        this.toolName = toolName;
        this.resultText = resultText;

        MutableDataSet options = new MutableDataSet();
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();

        // wrapper: 限制最大宽度 = 父容器宽度
        wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    d.width = Math.min(d.width, p.getWidth());
                }
                return d;
            }

            @Override
            public Dimension getMaximumSize() {
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    Dimension pref = getPreferredSize();
                    return new Dimension(p.getWidth(), pref.height);
                }
                return super.getMaximumSize();
            }
        };
        wrapper.setOpaque(false);
        wrapper.setBorder(JBUI.Borders.empty(2, 0));

        // --- 标题行 ---
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        headerPanel.setOpaque(false);
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        titleLabel = new JLabel(buildTitleText(true));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        titleLabel.setForeground(ACCENT_COLOR);
        titleLabel.setBorder(JBUI.Borders.empty(2, 4));
        headerPanel.add(titleLabel);

        headerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleCollapse();
            }
        });
        wrapper.add(headerPanel, BorderLayout.NORTH);

        // --- 内容面板 ---
        htmlPane = new JEditorPane() {
            @Override
            public Dimension getPreferredSize() {
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    setSize(p.getWidth(), Short.MAX_VALUE);
                    Dimension d = super.getPreferredSize();
                    return new Dimension(p.getWidth(), d.height);
                }
                return super.getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        htmlPane.setContentType("text/html");
        htmlPane.setEditable(false);
        htmlPane.setOpaque(false);
        htmlPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        contentPanel = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    Dimension pref = getPreferredSize();
                    return new Dimension(p.getWidth(), pref.height);
                }
                return super.getMaximumSize();
            }
        };
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_COLOR),
                JBUI.Borders.empty(4, 8)
        ));
        contentPanel.add(htmlPane, BorderLayout.CENTER);
        contentPanel.setVisible(false);
        wrapper.add(contentPanel, BorderLayout.CENTER);

        // 延迟渲染
        wrapper.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && wrapper.isShowing() && htmlPane.getDocument().getLength() == 0) {
                SwingUtilities.invokeLater(this::renderResult);
            }
        });
    }

    @Override
    public BlockType getType() {
        return BlockType.TOOL_RESULT;
    }

    @Override
    public JComponent getComponent() {
        return wrapper;
    }

    public void toggleCollapse() {
        collapsed = !collapsed;
        contentPanel.setVisible(!collapsed);
        titleLabel.setText(buildTitleText(collapsed));
        // 向上传播 layout invalidation，与 MarkdownBlock 一致
        wrapper.invalidate();
        Container parent = wrapper.getParent();
        while (parent != null) {
            parent.invalidate();
            if (parent instanceof JComponent jc) {
                jc.revalidate();
                break;
            }
            parent = parent.getParent();
        }
        wrapper.repaint();
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public String getToolName() {
        return toolName;
    }

    public String getResultText() {
        return resultText;
    }

    // ==================== 内部方法 ====================

    private String buildTitleText(boolean isCollapsed) {
        String arrow = isCollapsed ? "▶" : "▼";
        String summary = buildSummary();
        String hint = isCollapsed ? " (点击展开)" : " (点击折叠)";
        return arrow + " \uD83D\uDCCB " + toolName + summary + hint;
    }

    /**
     * 从结果中提取简短摘要，显示在折叠标题上。
     * 例如 tree 工具显示 "42 entries"，read_file 显示 "128 lines" 等。
     */
    private String buildSummary() {
        if (resultText == null || resultText.isEmpty()) {
            return " — (空结果)";
        }
        try {
            Object parsed = com.alibaba.fastjson.JSON.parse(resultText);
            if (parsed instanceof com.alibaba.fastjson.JSONObject json) {
                List<String> parts = new ArrayList<>();
                if (json.containsKey("entryCount")) {
                    parts.add(json.get("entryCount") + " 项");
                }
                if (json.containsKey("totalMatches")) {
                    parts.add(json.get("totalMatches") + " 匹配");
                }
                if (json.containsKey("matchCount")) {
                    parts.add(json.get("matchCount") + " 匹配");
                }
                if (json.containsKey("lineCount")) {
                    parts.add(json.get("lineCount") + " 行");
                }
                if (json.containsKey("totalLines")) {
                    parts.add(json.get("totalLines") + " 行");
                }
                if (json.containsKey("path")) {
                    parts.add(String.valueOf(json.get("path")));
                }
                if (json.getBoolean("truncated") != null && json.getBoolean("truncated")) {
                    parts.add("已截断");
                }
                if (!parts.isEmpty()) {
                    return " — " + String.join(", ", parts);
                }
            }
        } catch (Exception ignored) {
            // 非 JSON，不提取摘要
        }
        // 纯文本：显示行数
        long lineCount = resultText.chars().filter(c -> c == '\n').count() + 1;
        if (lineCount > 1) {
            return " — " + lineCount + " 行";
        }
        int len = resultText.length();
        if (len > 80) {
            return " — " + len + " 字符";
        }
        return "";
    }

    /**
     * 智能渲染结果：
     * 1. JSON 结构化数据 → 元数据表格 + 正文内容分离展示
     * 2. Markdown 文本 → flexmark 渲染
     * 3. 纯文本 → pre 等宽显示（带自动换行）
     */
    private void renderResult() {
        if (resultText == null || resultText.isEmpty()) {
            return;
        }

        computeThemeColors();

        String htmlBody = tryRenderStructuredJson(resultText);
        if (htmlBody == null) {
            if (containsMarkdown(resultText)) {
                htmlBody = renderer.render(parser.parse(resultText));
            } else {
                htmlBody = "<div style='font-family:monospace;font-size:11px;margin:0;'>"
                        + preformattedToHtml(resultText) + "</div>";
            }
        }

        String fullHtml = wrapHtml(htmlBody);
        try {
            Document doc = htmlPane.getDocument();
            doc.putProperty(Document.StreamDescriptionProperty, null);
            htmlPane.getEditorKit().read(new StringReader(fullHtml), doc, 0);
        } catch (Exception e) {
            htmlPane.setContentType("text/plain");
            htmlPane.setText(resultText);
        }

        // 渲染后传播 layout invalidation
        htmlPane.invalidate();
        Container parent = htmlPane.getParent();
        while (parent != null) {
            parent.invalidate();
            if (parent instanceof JComponent jc) {
                jc.revalidate();
                break;
            }
            parent = parent.getParent();
        }
        htmlPane.repaint();
    }

    /**
     * 尝试将文本解析为 JSON 并生成人类可读的 HTML。
     * 返回 null 表示非 JSON 或解析失败。
     */
    private String tryRenderStructuredJson(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null;

        try {
            Object parsed = com.alibaba.fastjson.JSON.parse(trimmed);
            if (parsed instanceof com.alibaba.fastjson.JSONObject json) {
                return renderJsonObject(json);
            } else if (parsed instanceof com.alibaba.fastjson.JSONArray arr) {
                return renderJsonArray(arr);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 将 JSON 对象渲染为：元数据表格 + 正文内容区。
     */
    private String renderJsonObject(com.alibaba.fastjson.JSONObject json) {
        StringBuilder sb = new StringBuilder();

        // 1) 提取元数据键值对
        List<String[]> metaRows = new ArrayList<>();
        // 2) 提取正文内容
        Map<String, Object> contentEntries = new LinkedHashMap<>();
        // 3) 其他键
        Map<String, Object> otherEntries = new LinkedHashMap<>();

        for (String key : json.keySet()) {
            Object val = json.get(key);
            if (CONTENT_KEYS.contains(key)) {
                contentEntries.put(key, val);
            } else if (META_KEYS.contains(key)) {
                metaRows.add(new String[]{humanizeKey(key), formatMetaValue(val)});
            } else {
                // 小值当元数据，大值当内容
                String valStr = String.valueOf(val);
                if (valStr.length() < 120 && !(val instanceof com.alibaba.fastjson.JSONArray)
                        && !(val instanceof com.alibaba.fastjson.JSONObject)) {
                    metaRows.add(new String[]{humanizeKey(key), escapeHtml(valStr)});
                } else {
                    otherEntries.put(key, val);
                }
            }
        }

        // 渲染元数据表格
        if (!metaRows.isEmpty()) {
            sb.append("<table style='width:100%;margin-bottom:8px;'>");
            for (String[] row : metaRows) {
                sb.append("<tr>")
                  .append("<td style='padding:2px 8px 2px 0;font-weight:bold;white-space:nowrap;"
                          + "vertical-align:top;color:").append(mutedFgHex).append(";'>").append(row[0]).append("</td>")
                  .append("<td style='padding:2px 0;'>").append(row[1]).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }

        // 渲染正文内容
        for (Map.Entry<String, Object> entry : contentEntries.entrySet()) {
            sb.append(renderContentValue(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Object> entry : otherEntries.entrySet()) {
            sb.append(renderContentValue(entry.getKey(), entry.getValue()));
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 渲染 JSON 数组为人类可读 HTML。
     */
    private String renderJsonArray(com.alibaba.fastjson.JSONArray arr) {
        if (arr.isEmpty()) return "<em>(空数组)</em>";

        // 如果数组元素是简单值，渲染为列表
        boolean allSimple = arr.stream().allMatch(
                v -> v == null || v instanceof String || v instanceof Number || v instanceof Boolean);
        if (allSimple) {
            StringBuilder sb = new StringBuilder("<ul style='margin:4px 0;padding-left:20px;'>");
            for (Object item : arr) {
                sb.append("<li>").append(escapeHtml(String.valueOf(item))).append("</li>");
            }
            sb.append("</ul>");
            return sb.toString();
        }

        // 复杂数组：逐项渲染
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (item instanceof com.alibaba.fastjson.JSONObject obj) {
                sb.append("<div style='margin:4px 0;padding:4px 0;"
                        + "border-bottom:1px solid ").append(mutedFgHex).append(";'>");
                sb.append("<span style='color:").append(mutedFgHex).append(";font-size:10px;'>#").append(i + 1).append("</span> ");
                sb.append(renderJsonObject(obj));
                sb.append("</div>");
            } else {
                sb.append("<div>").append(escapeHtml(String.valueOf(item))).append("</div>");
            }
        }
        return sb.toString();
    }

    /**
     * 渲染单个正文内容值。
     * 对 tree 类文本用 pre 等宽显示，对 matches 数组做结构化展示。
     */
    private String renderContentValue(String key, Object value) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top:6px;'>");
        sb.append("<div style='font-weight:bold;color:").append(mutedFgHex).append(";margin-bottom:2px;'>")
          .append(humanizeKey(key)).append("</div>");

        if (value instanceof String strVal) {
            if (looksLikeTree(strVal) || looksLikeCode(strVal)) {
                sb.append("<div style='font-family:monospace;font-size:11px;"
                        + "margin:0;padding:6px 8px;"
                        + "background-color:").append(codeBgHex).append(";color:").append(fgHex).append(";'>");
                sb.append(preformattedToHtml(strVal));
                sb.append("</div>");
            } else if (containsMarkdown(strVal)) {
                sb.append(renderer.render(parser.parse(strVal)));
            } else {
                sb.append("<div style='font-family:monospace;font-size:11px;"
                        + "margin:0;'>");
                sb.append(preformattedToHtml(strVal));
                sb.append("</div>");
            }
        } else if (value instanceof com.alibaba.fastjson.JSONArray arr) {
            sb.append(renderJsonArray(arr));
        } else if (value instanceof com.alibaba.fastjson.JSONObject obj) {
            sb.append(renderJsonObject(obj));
        } else {
            sb.append(escapeHtml(String.valueOf(value)));
        }

        sb.append("</div>");
        return sb.toString();
    }

    /**
     * 将 camelCase / snake_case 键名转为人类可读标签。
     */
    private static String humanizeKey(String key) {
        // snake_case → 空格分隔，首字母大写
        String spaced = key.replace('_', ' ');
        // camelCase → 插入空格
        spaced = spaced.replaceAll("([a-z])([A-Z])", "$1 $2");
        if (!spaced.isEmpty()) {
            spaced = Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
        }
        return escapeHtml(spaced);
    }

    private static String formatMetaValue(Object val) {
        if (val instanceof Boolean b) {
            return b ? "✓" : "✗";
        }
        if (val instanceof com.alibaba.fastjson.JSONArray arr) {
            if (arr.isEmpty()) return "<em>—</em>";
            StringJoiner sj = new StringJoiner(", ");
            for (Object item : arr) {
                sj.add(escapeHtml(String.valueOf(item)));
            }
            return sj.toString();
        }
        return escapeHtml(String.valueOf(val));
    }

    private static boolean looksLikeTree(String text) {
        return text.contains("|-- ") || text.contains("`-- ") || text.contains("├── ");
    }

    private static boolean looksLikeCode(String text) {
        // 多行且含典型代码特征
        long lines = text.chars().filter(c -> c == '\n').count();
        return lines > 3 && (text.contains("    ") || text.contains("\t"));
    }

    private static boolean containsMarkdown(String text) {
        if (text == null) return false;
        return text.contains("```")
                || text.contains("# ")
                || text.contains("- ")
                || text.contains("* ")
                || text.contains("| ");
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 将预格式化文本转为 HTML：保留换行（→ br）和行首缩进（→ &amp;nbsp;），
     * 同时允许行内内容自然换行，避免溢出容器。
     */
    private static String preformattedToHtml(String text) {
        if (text == null) return "";
        String escaped = escapeHtml(text);
        StringBuilder sb = new StringBuilder();
        for (String line : escaped.split("\n", -1)) {
            if (sb.length() > 0) sb.append("<br>");
            int i = 0;
            while (i < line.length() && line.charAt(i) == ' ') {
                sb.append("&nbsp;");
                i++;
            }
            sb.append(line.substring(i));
        }
        return sb.toString();
    }

    private void computeThemeColors() {
        Color fg = UIManager.getColor("Label.foreground");
        Color codeBg = UIManager.getColor("EditorPane.background");
        if (fg == null) fg = Color.DARK_GRAY;
        if (codeBg == null) codeBg = new Color(245, 245, 245);

        fgHex = toHex(fg);
        codeBgHex = toHex(codeBg);
        Color muted = new Color(
                (fg.getRed() + codeBg.getRed()) / 2,
                (fg.getGreen() + codeBg.getGreen()) / 2,
                (fg.getBlue() + codeBg.getBlue()) / 2);
        mutedFgHex = toHex(muted);
    }

    private String wrapHtml(String bodyContent) {
        return "<html><head><style>"
                + "body { width:100%; "
                + "margin:2px 0; font-family:sans-serif; font-size:11px; color:" + fgHex + "; }"
                + "pre { background-color:" + codeBgHex + "; padding:6px 8px; "
                + "font-family:monospace; font-size:11px; }"
                + "code { background-color:" + codeBgHex + "; padding:1px 4px; "
                + "font-family:monospace; }"
                + "table { width:100%; }"
                + "td { vertical-align:top; }"
                + "ul { margin:4px 0; padding-left:20px; }"
                + "</style></head>"
                + "<body>" + bodyContent + "</body></html>";
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
