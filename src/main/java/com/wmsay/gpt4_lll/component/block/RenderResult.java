package com.wmsay.gpt4_lll.component.block;

import java.awt.image.BufferedImage;

/**
 * Mermaid 渲染结果。不可变数据类。
 */
public final class RenderResult {

    private final boolean success;
    private final BufferedImage image;
    private final String errorMessage;

    private RenderResult(boolean success, BufferedImage image, String errorMessage) {
        this.success = success;
        this.image = image;
        this.errorMessage = errorMessage;
    }

    public static RenderResult success(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image must not be null for success result");
        }
        return new RenderResult(true, image, null);
    }

    public static RenderResult failure(String errorMessage) {
        if (errorMessage == null) {
            throw new IllegalArgumentException("errorMessage must not be null for failure result");
        }
        return new RenderResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public BufferedImage getImage() {
        return image;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
