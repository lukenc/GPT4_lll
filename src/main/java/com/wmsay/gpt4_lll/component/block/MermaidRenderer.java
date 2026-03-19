package com.wmsay.gpt4_lll.component.block;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
/**
 * Mermaid 图表渲染引擎。
 * 使用 mmdc CLI 渲染 mermaid 源代码为 PNG BufferedImage。
 * 线程安全，内置渲染结果缓存。
 * 单例模式，通过 getInstance() 获取。
 */
public class MermaidRenderer {

    private static final MermaidRenderer INSTANCE = new MermaidRenderer();
    static final long RENDER_TIMEOUT_MS = 10_000;

    private final ConcurrentHashMap<String, RenderResult> cache = new ConcurrentHashMap<>();

    private MermaidRenderer() {}

    public static MermaidRenderer getInstance() { return INSTANCE; }

    /**
     * 异步使用 mmdc CLI 渲染 mermaid 源代码为 PNG。
     * 先检查缓存，命中则直接返回；否则在后台线程执行。
     */
    public CompletableFuture<RenderResult> renderWithMmdcAsync(String source, boolean darkTheme) {
        String cacheKey = computeCacheKey(source, darkTheme);
        RenderResult cached = cache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            RenderResult result = renderWithMmdc(source, darkTheme);
            if (result.isSuccess()) {
                cache.put(cacheKey, result);
            }
            return result;
        });
    }

    /** 清空渲染缓存 */
    public void clearCache() { cache.clear(); }

    // ------------------------------------------------------------------
    // mmdc CLI rendering
    // ------------------------------------------------------------------

    private RenderResult renderWithMmdc(String source, boolean darkTheme) {
        java.io.File inputFile = null;
        java.io.File outputFile = null;
        try {
            inputFile = java.io.File.createTempFile("mermaid_", ".mmd");
            outputFile = java.io.File.createTempFile("mermaid_", ".png");

            java.nio.file.Files.writeString(inputFile.toPath(), source, StandardCharsets.UTF_8);

            String theme = darkTheme ? "dark" : "default";
            ProcessBuilder pb = new ProcessBuilder(
                    "mmdc", "-i", inputFile.getAbsolutePath(),
                    "-o", outputFile.getAbsolutePath(),
                    "-t", theme, "-b", "transparent", "-s", "2"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(RENDER_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                return RenderResult.failure("mmdc rendering timed out");
            }
            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return RenderResult.failure("mmdc failed (exit " + process.exitValue() + "): " + output);
            }
            if (!outputFile.exists() || outputFile.length() == 0) {
                return RenderResult.failure("mmdc produced no output");
            }

            BufferedImage image = javax.imageio.ImageIO.read(outputFile);
            if (image == null) {
                return RenderResult.failure("Failed to read mmdc output as image");
            }
            return RenderResult.success(image);
        } catch (java.io.IOException e) {
            return RenderResult.failure("mmdc I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RenderResult.failure("mmdc rendering interrupted");
        } finally {
            if (inputFile != null) inputFile.delete();
            if (outputFile != null) outputFile.delete();
        }
    }

    // ------------------------------------------------------------------
    // Cache key helper
    // ------------------------------------------------------------------

    String computeCacheKey(String source, boolean darkTheme) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((source + ":" + darkTheme).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // --- Package-private test accessors ---
    int cacheSize() { return cache.size(); }
}
