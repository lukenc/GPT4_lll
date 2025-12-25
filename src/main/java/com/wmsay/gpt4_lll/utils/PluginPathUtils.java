package com.wmsay.gpt4_lll.utils;

import com.intellij.openapi.application.PathManager;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility for plugin-scoped temp paths.
 */
public final class PluginPathUtils {

    private static final String PLUGIN_TEMP_DIR = "gpt4_lll";

    private PluginPathUtils() {
    }

    /**
     * Returns the plugin-scoped temp directory name.
     */
    public static String getPluginTempDirName() {
        return PLUGIN_TEMP_DIR;
    }

    /**
     * Returns a temp file path under the plugin-scoped directory.
     */
    public static Path pluginTempFile(String fileName) {
        return Paths.get(PathManager.getTempPath(), PLUGIN_TEMP_DIR, fileName);
    }
}

