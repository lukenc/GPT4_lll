package com.wmsay.gpt4_lll.component.block;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.component.PluginIcons;
import com.wmsay.gpt4_lll.component.theme.GlassPanel;
import com.wmsay.gpt4_lll.component.theme.GlassVerticalLine;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.component.theme.SpringAnimator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * 工具执行结果内容块（Liquid Glass 风格）。
 * 默认折叠显示摘要，点击可展开查看完整结果。
 * <p>
 * 使用 SafeHtmlPane 替代原始 JEditorPane + HTMLEditorKit，
 * 避免 macOS 上 CoreText 原生字体渲染导致的窗口冻结。
 * <p>
 * 标题行使用 GlassPanel(8px) 渲染，左侧 GlassVerticalLine 渐变竖线，
 * 内容区域使用内嵌玻璃层（更低透明度），预留 SpringAnimator 弹性过渡接口。
 */
public class ToolResultBlock implements ContentBlock {

    private final JPanel wrapper;
    private final GlassPanel headerGlass;
    private final JLabel titleLabel;
    private final JPanel contentPanel;
    private final GlassVerticalLine verticalLine;
    private boolean collapsed = true;

    private final String resultText;
    private final String toolName;

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final SafeHtmlPane htmlPane;

    private String fgHex;
    private String codeBgHex;
    private String mutedFgHex;

    private boolean rendered = false;

    private static final JBColor ACCENT_COLOR = new JBColor(
            new Color(0x4CAF50), new Color(0x66BB6A));

    /** Inner glass layer background for contentPanel (lower transparency than header) */
    private static final JBColor CONTENT_INNER_BG = new JBColor(
            new Color(230, 235, 245, 120),  // light: lower alpha than PRIMARY_BG
            new Color(40, 42, 58, 100));     // dark: lower alpha than PRIMARY_BG

    private static final Set<String> META_KEYS = Set.of(
            "tool", "status", "path", "maxDepth", "showFiles", "showHidden",
            "entryCount", "truncated", "ignoredDirs", "totalMatches",
            "matchCount", "searchPattern", "fileCount", "encoding",
            "totalLines", "startLine", "endLine", "lineCount"
    );

    private static final Set<String> CONTENT_KEYS = Set.of(
            "tree", "content", "matches", "results", "output", "data", "text"
    );

    public ToolResultBlock(String toolName, String resultText) {
        this.toolName = toolName;
        this.resultText = resultText;

        MutableDataSet options = new MutableDataSet();
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();

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

        // ── Left vertical line: GlassVerticalLine with accent color ──
        verticalLine = new GlassVerticalLine(ACCENT_COLOR);

        // ── Inner layout: verticalLine + main content ──
        JPanel innerLayout = new JPanel(new BorderLayout());
        innerLayout.setOpaque(false);
        innerLayout.add(verticalLine, BorderLayout.WEST);

        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.setOpaque(false);

        // --- 标题行: GlassPanel with 8px corner radius + highlight ---
        headerGlass = new GlassPanel(LiquidGlassTheme.RADIUS_SMALL);
        headerGlass.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        headerGlass.setBgColor(LiquidGlassTheme.PRIMARY_BG);
        headerGlass.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        titleLabel = new JLabel(buildTitleText(true));
        titleLabel.setIcon(PluginIcons.RESULT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        titleLabel.setForeground(ACCENT_COLOR);
        titleLabel.setBorder(JBUI.Borders.empty(2, 4));
        headerGlass.add(titleLabel);

        headerGlass.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleCollapse();
            }
        });
        mainContent.add(headerGlass, BorderLayout.NORTH);

        // --- 内容面板：内嵌玻璃层（更低透明度），使用 SafeHtmlPane ---
        htmlPane = new SafeHtmlPane();

        contentPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(CONTENT_INNER_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            LiquidGlassTheme.RADIUS_SMALL, LiquidGlassTheme.RADIUS_SMALL);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
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
        contentPanel.setOpaque(false);
        contentPanel.setBorder(JBUI.Borders.empty(4, 8));
        contentPanel.add(htmlPane, BorderLayout.CENTER);
        contentPanel.setVisible(false);
        mainContent.add(contentPanel, BorderLayout.CENTER);

        innerLayout.add(mainContent, BorderLayout.CENTER);
        wrapper.add(innerLayout, BorderLayout.CENTER);

        // 延迟渲染：仅首次可见时触发
        wrapper.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && wrapper.isShowing() && !rendered) {
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

    /**
     * 切换折叠/展开状态（无动画）。
     */
    public void toggleCollapse() {
        collapsed = !collapsed;
        contentPanel.setVisible(!collapsed);
        titleLabel.setText(buildTitleText(collapsed));
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

    /**
     * 切换折叠/展开状态，使用 SpringAnimator 弹性过渡。
     * 预留接口：当 springConfig 非 null 时，通过 SpringAnimator 驱动
     * contentPanel 高度从当前值到目标值的弹性过渡。
     *
     * @param springConfig 弹簧动画配置，为 null 时等同于 toggleCollapse()
     */
    public void toggleCollapse(LiquidGlassTheme.SpringConfig springConfig) {
        if (springConfig == null) {
            toggleCollapse();
            return;
        }
        // SpringAnimator 弹性过渡预留接口
        collapsed = !collapsed;
        titleLabel.setText(buildTitleText(collapsed));
        if (!collapsed) {
            contentPanel.setVisible(true);
        }
        // 使用 SpringAnimator 驱动高度过渡
        int targetHeight = collapsed ? 0 : contentPanel.getPreferredSize().height;
        int currentHeight = collapsed ? contentPanel.getHeight() : 0;
        SpringAnimator animator = new SpringAnimator(springConfig);
        animator.bindToComponent(wrapper);
        animator.animateTo(currentHeight, targetHeight, value -> {
            int h = Math.max(0, (int) Math.round(value));
            contentPanel.setPreferredSize(new Dimension(contentPanel.getWidth(), h));
            contentPanel.revalidate();
            wrapper.repaint();
        }, () -> {
            if (collapsed) {
                contentPanel.setVisible(false);
            }
            contentPanel.setPreferredSize(null); // reset to natural size
            wrapper.revalidate();
            wrapper.repaint();
        });
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

    private String buildTitleText(boolean isCollapsed) {
        String arrow = isCollapsed ? "▶" : "▼";
        String summary = buildSummary();
        String hint = isCollapsed ? " (点击展开)" : " (点击折叠)";
        return arrow + " " + toolName + summary + hint;
    }

    private String buildSummary() {
        if (resultText == null || resultText.isEmpty()) {
            return " — (空结果)";
        }
        try {
            Object parsed = com.alibaba.fastjson.JSON.parse(resultText);
            if (parsed instanceof com.alibaba.fastjson.JSONObject json) {
                List<String> parts = new ArrayList<>();
                if (json.containsKey("entryCount")) parts.add(json.get("entryCount") + " 项");
                if (json.containsKey("totalMatches")) parts.add(json.get("totalMatches") + " 匹配");
                if (json.containsKey("matchCount")) parts.add(json.get("matchCount") + " 匹配");
                if (json.containsKey("lineCount")) parts.add(json.get("lineCount") + " 行");
                if (json.containsKey("totalLines")) parts.add(json.get("totalLines") + " 行");
                if (json.containsKey("path")) parts.add(String.valueOf(json.get("path")));
                if (json.getBoolean("truncated") != null && json.getBoolean("truncated")) parts.add("已截断");
                if (!parts.isEmpty()) return " — " + String.join(", ", parts);
            }
        } catch (Exception ignored) {}
        long lineCount = resultText.chars().filter(c -> c == '\n').count() + 1;
        if (lineCount > 1) return " — " + lineCount + " 行";
        int len = resultText.length();
        if (len > 80) return " — " + len + " 字符";
        return "";
    }

    private void renderResult() {
        if (resultText == null || resultText.isEmpty()) return;
        if (rendered) return;
        rendered = true;

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

        htmlPane.setHtmlContent(wrapHtml(htmlBody));
    }

    private String tryRenderStructuredJson(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null;
        try {
            Object parsed = com.alibaba.fastjson.JSON.parse(trimmed);
            if (parsed instanceof com.alibaba.fastjson.JSONObject json) return renderJsonObject(json);
            else if (parsed instanceof com.alibaba.fastjson.JSONArray arr) return renderJsonArray(arr);
        } catch (Exception ignored) {}
        return null;
    }

    private String renderJsonObject(com.alibaba.fastjson.JSONObject json) {
        StringBuilder sb = new StringBuilder();
        List<String[]> metaRows = new ArrayList<>();
        Map<String, Object> contentEntries = new LinkedHashMap<>();
        Map<String, Object> otherEntries = new LinkedHashMap<>();

        for (String key : json.keySet()) {
            Object val = json.get(key);
            if (CONTENT_KEYS.contains(key)) {
                contentEntries.put(key, val);
            } else if (META_KEYS.contains(key)) {
                metaRows.add(new String[]{humanizeKey(key), formatMetaValue(val)});
            } else {
                String valStr = String.valueOf(val);
                if (valStr.length() < 120 && !(val instanceof com.alibaba.fastjson.JSONArray)
                        && !(val instanceof com.alibaba.fastjson.JSONObject)) {
                    metaRows.add(new String[]{humanizeKey(key), escapeHtml(valStr)});
                } else {
                    otherEntries.put(key, val);
                }
            }
        }

        if (!metaRows.isEmpty()) {
            sb.append("<table style='width:100%;margin-bottom:8px;'>");
            for (String[] row : metaRows) {
                sb.append("<tr>")
                  .append("<td style='padding:2px 8px 2px 0;font-weight:bold;white-space:nowrap;vertical-align:top;color:")
                  .append(mutedFgHex).append(";'>").append(row[0]).append("</td>")
                  .append("<td style='padding:2px 0;'>").append(row[1]).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }

        for (Map.Entry<String, Object> entry : contentEntries.entrySet())
            sb.append(renderContentValue(entry.getKey(), entry.getValue()));
        for (Map.Entry<String, Object> entry : otherEntries.entrySet())
            sb.append(renderContentValue(entry.getKey(), entry.getValue()));

        return sb.length() > 0 ? sb.toString() : null;
    }

    private String renderJsonArray(com.alibaba.fastjson.JSONArray arr) {
        if (arr.isEmpty()) return "<em>(空数组)</em>";
        boolean allSimple = arr.stream().allMatch(
                v -> v == null || v instanceof String || v instanceof Number || v instanceof Boolean);
        if (allSimple) {
            StringBuilder sb = new StringBuilder("<ul style='margin:4px 0;padding-left:20px;'>");
            for (Object item : arr) sb.append("<li>").append(escapeHtml(String.valueOf(item))).append("</li>");
            sb.append("</ul>");
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (item instanceof com.alibaba.fastjson.JSONObject obj) {
                sb.append("<div style='margin:4px 0;padding:4px 0;border-bottom:1px solid ")
                  .append(mutedFgHex).append(";'>");
                sb.append("<span style='color:").append(mutedFgHex).append(";font-size:10px;'>#")
                  .append(i + 1).append("</span> ");
                sb.append(renderJsonObject(obj));
                sb.append("</div>");
            } else {
                sb.append("<div>").append(escapeHtml(String.valueOf(item))).append("</div>");
            }
        }
        return sb.toString();
    }

    private String renderContentValue(String key, Object value) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top:6px;'>");
        sb.append("<div style='font-weight:bold;color:").append(mutedFgHex).append(";margin-bottom:2px;'>")
          .append(humanizeKey(key)).append("</div>");

        if (value instanceof String strVal) {
            if (looksLikeTree(strVal) || looksLikeCode(strVal)) {
                sb.append("<div style='font-family:monospace;font-size:11px;margin:0;padding:6px 8px;background-color:")
                  .append(codeBgHex).append(";color:").append(fgHex).append(";'>");
                sb.append(preformattedToHtml(strVal));
                sb.append("</div>");
            } else if (containsMarkdown(strVal)) {
                sb.append(renderer.render(parser.parse(strVal)));
            } else {
                sb.append("<div style='font-family:monospace;font-size:11px;margin:0;'>");
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

    private static String humanizeKey(String key) {
        String spaced = key.replace('_', ' ');
        spaced = spaced.replaceAll("([a-z])([A-Z])", "$1 $2");
        if (!spaced.isEmpty()) spaced = Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
        return escapeHtml(spaced);
    }

    private static String formatMetaValue(Object val) {
        if (val instanceof Boolean b) return b ? "✓" : "✗";
        if (val instanceof com.alibaba.fastjson.JSONArray arr) {
            if (arr.isEmpty()) return "<em>—</em>";
            StringJoiner sj = new StringJoiner(", ");
            for (Object item : arr) sj.add(escapeHtml(String.valueOf(item)));
            return sj.toString();
        }
        return escapeHtml(String.valueOf(val));
    }

    private static boolean looksLikeTree(String text) {
        return text.contains("|-- ") || text.contains("`-- ") || text.contains("├── ");
    }

    private static boolean looksLikeCode(String text) {
        long lines = text.chars().filter(c -> c == '\n').count();
        return lines > 3 && (text.contains("    ") || text.contains("\t"));
    }

    private static boolean containsMarkdown(String text) {
        if (text == null) return false;
        return text.contains("```") || text.contains("# ") || text.contains("- ")
                || text.contains("* ") || text.contains("| ");
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String preformattedToHtml(String text) {
        if (text == null) return "";
        String escaped = escapeHtml(text);
        StringBuilder sb = new StringBuilder();
        for (String line : escaped.split("\n", -1)) {
            if (sb.length() > 0) sb.append("<br>");
            int i = 0;
            while (i < line.length() && line.charAt(i) == ' ') { sb.append("&nbsp;"); i++; }
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
                + "body { width:100%; margin:2px 0; font-family:sans-serif; font-size:11px; color:" + fgHex + "; }"
                + "pre { background-color:" + codeBgHex + "; padding:6px 8px; font-family:monospace; font-size:11px; }"
                + "code { background-color:" + codeBgHex + "; padding:1px 4px; font-family:monospace; }"
                + "table { width:100%; }"
                + "td { vertical-align:top; }"
                + "ul { margin:4px 0; padding-left:20px; }"
                + "</style></head>"
                + "<body>" + bodyContent + "</body></html>";
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ── Package-private accessors for testing ──
    GlassPanel getHeaderGlass() { return headerGlass; }
    GlassVerticalLine getVerticalLine() { return verticalLine; }
    JPanel getContentPanel() { return contentPanel; }
}
