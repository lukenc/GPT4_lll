package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.Disposable;
import com.intellij.ui.JBColor;
import com.wmsay.gpt4_lll.component.theme.GlassGlowBorder;
import com.wmsay.gpt4_lll.component.theme.LiquidGlassTheme;
import com.wmsay.gpt4_lll.model.AgentStatusContext;
import com.wmsay.gpt4_lll.model.RuntimeStatus;
import com.wmsay.gpt4_lll.utils.RuntimeStatusManager;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

/**
 * Custom border that visually indicates the current RuntimeStatus on the chat scroll pane.
 * <ul>
 *   <li>IDLE: no border</li>
 *   <li>RUNNING: animated green gradient sweep (2px)</li>
 *   <li>COMPLETED: static green border (2px)</li>
 *   <li>ERROR: static red border (2px)</li>
 * </ul>
 * Implements StatusListener to react to status changes from RuntimeStatusManager.
 */
public class StatusBorderPainter extends AbstractBorder
        implements RuntimeStatusManager.StatusListener, RuntimeStatusManager.AgentPhaseListener, Disposable {

    static final JBColor GREEN = new JBColor(new Color(0x4CAF50), new Color(0x66BB6A));
    static final JBColor RED = new JBColor(new Color(0xF44336), new Color(0xEF5350));
    static final JBColor GRAY = new JBColor(new Color(0x9E9E9E), new Color(0x757575));

    private static final int BORDER_THICKNESS = 2;
    private static final int ANIMATION_INTERVAL_MS = 50;
    private static final float PHASE_INCREMENT = 0.025f;
    private static final int AUTO_RESET_DELAY_MS = 3000;
    /** repaint 时只刷新边框区域的额外像素余量（覆盖 glow 扩散） */
    private static final int REPAINT_MARGIN = 6;

    private Timer animationTimer;
    private Timer autoResetTimer;
    private float animationPhase;
    private RuntimeStatus currentStatus = RuntimeStatus.IDLE;
    private boolean stoppedActive = false;
    private Component parentComponent;

    /** 缓存 GREEN 的半透明版本，避免每帧创建新 Color 对象 */
    private Color cachedTransparentGreen;
    private static final Color GLASS_HIGHLIGHT = new Color(255, 255, 255, 120);

    /** 当组件不可见时，记住动画 timer 是否应该在恢复可见时重启 */
    private boolean animationSuspended = false;
    private boolean autoResetSuspended = false;
    /** 保存 HierarchyListener 引用，dispose 时移除 */
    private HierarchyListener visibilityListener;

    public StatusBorderPainter(Component parentComponent) {
        this.parentComponent = parentComponent;
        installVisibilityListener();
    }

    /**
     * 监听父组件的 SHOWING_CHANGED 事件。
     * 窗口切走时暂停所有 timer，切回时恢复，避免 EDT 队列积压导致假死。
     */
    private void installVisibilityListener() {
        if (parentComponent == null) return;
        visibilityListener = e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (parentComponent.isShowing()) {
                    resumeTimers();
                } else {
                    suspendTimers();
                }
            }
        };
        parentComponent.addHierarchyListener(visibilityListener);
    }

    private void suspendTimers() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
            animationSuspended = true;
        }
        if (autoResetTimer != null && autoResetTimer.isRunning()) {
            autoResetTimer.stop();
            autoResetSuspended = true;
        }
    }

    private void resumeTimers() {
        if (animationSuspended && animationTimer != null) {
            animationTimer.start();
            animationSuspended = false;
        }
        if (autoResetSuspended && autoResetTimer != null) {
            autoResetTimer.start();
            autoResetSuspended = false;
        }
        // 恢复可见后刷新一帧 — 使用 invokeLater 延迟执行，
        // 避免在 HierarchyListener 回调中直接 repaint 触发布局级联
        if (parentComponent != null) {
            SwingUtilities.invokeLater(() -> {
                if (parentComponent != null && parentComponent.isShowing()) {
                    parentComponent.repaint();
                }
            });
        }
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (currentStatus == RuntimeStatus.IDLE && !stoppedActive) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (stoppedActive) {
                paintStaticBorder(g2, x, y, width, height, GRAY);
            } else if (currentStatus == RuntimeStatus.RUNNING) {
                paintAnimatedBorder(g2, x, y, width, height);
            } else if (currentStatus == RuntimeStatus.COMPLETED) {
                paintStaticBorder(g2, x, y, width, height, GREEN);
            } else if (currentStatus == RuntimeStatus.ERROR) {
                paintStaticBorder(g2, x, y, width, height, RED);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintStaticBorder(Graphics2D g2, int x, int y, int width, int height, Color color) {
        if (color == GREEN || color == RED) {
            // COMPLETED / ERROR: use GlassGlowBorder for soft glow effect
            GlassGlowBorder.paint(g2, color, x, y, width - 1, height - 1, LiquidGlassTheme.RADIUS_MEDIUM);
        } else {
            // STOPPED (GRAY) or other: rounded rect border
            g2.setColor(color);
            g2.setStroke(new BasicStroke(BORDER_THICKNESS));
            int offset = BORDER_THICKNESS / 2;
            g2.drawRoundRect(x + offset, y + offset, width - BORDER_THICKNESS, height - BORDER_THICKNESS,
                    LiquidGlassTheme.RADIUS_MEDIUM, LiquidGlassTheme.RADIUS_MEDIUM);
        }
    }

    private void paintAnimatedBorder(Graphics2D g2, int x, int y, int width, int height) {
        float phase = this.animationPhase;

        // 缓存半透明绿色，避免每帧 new Color
        if (cachedTransparentGreen == null) {
            cachedTransparentGreen = new Color(GREEN.getRed(), GREEN.getGreen(), GREEN.getBlue(), 60);
        }

        // 使用角度扫描代替线性位移，保证 0→1 循环无缝衔接。
        // phase 映射到 [0, 2π)，亮点沿矩形周长移动。
        double angle = phase * 2 * Math.PI;
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        // 渐变方向：从中心向 angle 方向
        float radius = Math.max(width, height) / 2f;
        float gx = cx + (float) Math.cos(angle) * radius;
        float gy = cy + (float) Math.sin(angle) * radius;
        // 渐变终点：对侧
        float gx2 = cx - (float) Math.cos(angle) * radius;
        float gy2 = cy - (float) Math.sin(angle) * radius;

        LinearGradientPaint gradient = new LinearGradientPaint(
                gx, gy, gx2, gy2,
                new float[]{0.0f, 0.15f, 0.3f, 0.35f, 0.5f, 0.85f, 1.0f},
                new Color[]{GLASS_HIGHLIGHT, GREEN, cachedTransparentGreen, cachedTransparentGreen,
                        cachedTransparentGreen, GREEN, GLASS_HIGHLIGHT}
        );

        g2.setPaint(gradient);
        g2.setStroke(new BasicStroke(BORDER_THICKNESS));
        int offset = BORDER_THICKNESS / 2;
        g2.drawRoundRect(x + offset, y + offset, width - BORDER_THICKNESS, height - BORDER_THICKNESS,
                LiquidGlassTheme.RADIUS_MEDIUM, LiquidGlassTheme.RADIUS_MEDIUM);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        // 始终返回固定 insets，避免状态变化时 insets 变化触发 layout 级联。
        // IDLE 时 paintBorder 不画任何东西，视觉效果一样，但不会导致 revalidate 循环。
        return new Insets(BORDER_THICKNESS, BORDER_THICKNESS, BORDER_THICKNESS, BORDER_THICKNESS);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }

    @Override
    public void onStatusChanged(RuntimeStatus oldStatus, RuntimeStatus newStatus) {
        // Requirement 7.3: stop animation timer before applying new state
        stopAnimationTimer();

        // Cancel any pending auto-reset
        stopAutoResetTimer();

        currentStatus = newStatus;

        if (newStatus == RuntimeStatus.RUNNING) {
            startAnimationTimer();
        } else if (newStatus == RuntimeStatus.COMPLETED || newStatus == RuntimeStatus.ERROR) {
            startAutoResetTimer();
        }

        // Trigger repaint on EDT
        if (parentComponent != null) {
            parentComponent.repaint();
        }
    }

    @Override
    public void onPhaseChanged(AgentStatusContext oldCtx, AgentStatusContext newCtx) {
        stopAnimationTimer();
        stopAutoResetTimer();
        stoppedActive = false;

        switch (newCtx.getPhase()) {
            case IDLE -> currentStatus = RuntimeStatus.IDLE;
            case RUNNING -> {
                currentStatus = RuntimeStatus.RUNNING;
                startAnimationTimer();
            }
            case COMPLETED -> {
                currentStatus = RuntimeStatus.COMPLETED;
                startAutoResetTimer();
            }
            case ERROR -> {
                currentStatus = RuntimeStatus.ERROR;
                startAutoResetTimer();
            }
            case STOPPED -> {
                currentStatus = RuntimeStatus.IDLE;
                stoppedActive = true;
                startAutoResetTimer();
            }
        }
        if (parentComponent != null) {
            parentComponent.repaint();
        }
    }

    private void startAnimationTimer() {
        animationPhase = 0.0f;
        animationTimer = new Timer(ANIMATION_INTERVAL_MS, e -> {
            animationPhase += PHASE_INCREMENT;
            if (animationPhase >= 1.0f) {
                animationPhase = 0.0f;
            }
            repaintBorderOnly();
        });
        animationTimer.setCoalesce(true);
        animationTimer.start();
    }

    /**
     * 只 repaint 边框区域（四条窄边），避免重绘整个 scrollPane 内容。
     * 这是解决动画卡顿的关键：scrollPane 内部的 Markdown、磨砂玻璃等
     * 重绘开销极大，而边框动画只需要刷新外围几个像素。
     */
    private void repaintBorderOnly() {
        if (parentComponent == null || !parentComponent.isShowing()) return;
        int w = parentComponent.getWidth();
        int h = parentComponent.getHeight();
        int t = BORDER_THICKNESS + REPAINT_MARGIN;
        // top edge
        parentComponent.repaint(0, 0, w, t);
        // bottom edge
        parentComponent.repaint(0, h - t, w, t);
        // left edge
        parentComponent.repaint(0, t, t, h - t * 2);
        // right edge
        parentComponent.repaint(w - t, t, t, h - t * 2);
    }

    private void stopAnimationTimer() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
    }

    private void startAutoResetTimer() {
        autoResetTimer = new Timer(AUTO_RESET_DELAY_MS, e -> {
            currentStatus = RuntimeStatus.IDLE;
            stoppedActive = false;
            if (parentComponent != null) {
                parentComponent.repaint();
            }
        });
        autoResetTimer.setRepeats(false);
        autoResetTimer.start();
    }

    private void stopAutoResetTimer() {
        if (autoResetTimer != null) {
            autoResetTimer.stop();
            autoResetTimer = null;
        }
    }

    /**
     * Stops all timers, removes listeners and cleans up resources.
     * Call when the component is being disposed.
     */
    @Override
    public void dispose() {
        stopAnimationTimer();
        stopAutoResetTimer();
        // 移除 HierarchyListener，避免插件卸载后仍持有引用
        if (parentComponent != null && visibilityListener != null) {
            parentComponent.removeHierarchyListener(visibilityListener);
            visibilityListener = null;
        }
        parentComponent = null;
    }

    // Package-private accessors for testing
    RuntimeStatus getCurrentStatus() {
        return currentStatus;
    }

    Timer getAnimationTimer() {
        return animationTimer;
    }

    Timer getAutoResetTimer() {
        return autoResetTimer;
    }

    float getAnimationPhase() {
        return animationPhase;
    }

    boolean isStoppedActive() {
        return stoppedActive;
    }
}
