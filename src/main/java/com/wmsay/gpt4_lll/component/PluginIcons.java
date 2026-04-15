package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/**
 * 统一图标管理类。
 * 所有 SVG 图标从 /icons/ 目录加载，不使用 AllIcons。
 * 支持 _dark.svg 暗色主题变体（IntelliJ IconLoader 自动识别）。
 */
public final class PluginIcons {
    private static final String ICONS_PATH = "/icons/";

    // 通用图标 (16x16)
    public static final Icon TOOL = load("tool.svg");
    public static final Icon SUCCESS = load("success.svg");
    public static final Icon ERROR = load("error.svg");
    public static final Icon RESULT = load("result.svg");
    public static final Icon PLAN = load("plan.svg");
    public static final Icon SPINNER = load("spinner.svg");
    public static final Icon STOPPED = load("stopped.svg");
    public static final Icon THINKING = load("thinking.svg");

    // 步骤状态图标 (16x16)
    public static final Icon STEP_PENDING = load("step-pending.svg");
    public static final Icon STEP_IN_PROGRESS = load("step-in-progress.svg");
    public static final Icon STEP_COMPLETED = load("step-completed.svg");
    public static final Icon STEP_FAILED = load("step-failed.svg");
    public static final Icon STEP_SKIPPED = load("step-skipped.svg");

    // Agent 头像 (24x24，使用 pluginIcon 设计)
    public static final Icon AGENT_AVATAR = load("agent-avatar.svg");

    private PluginIcons() {}

    private static Icon load(String name) {
        return IconLoader.getIcon(ICONS_PATH + name, PluginIcons.class);
    }
}
