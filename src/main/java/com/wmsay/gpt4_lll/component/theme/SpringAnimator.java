package com.wmsay.gpt4_lll.component.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.util.function.Consumer;

/**
 * 基于阻尼弹簧方程的动画驱动器。
 * 通过 javax.swing.Timer 在 EDT 上逐帧更新，支持暂停/恢复。
 * <p>
 * 弹簧方程：
 * F = -stiffness * (current - target) - damping * velocity
 * damping = 2 * dampingRatio * sqrt(stiffness)
 */
public class SpringAnimator {

    private static final int FRAME_INTERVAL_MS = 16; // ~60fps
    private static final double EPSILON = 0.5;

    private final LiquidGlassTheme.SpringConfig config;
    private final Timer timer;

    private double currentValue;
    private double targetValue;
    private double velocity;
    private Consumer<Double> onUpdate;
    private Runnable onComplete;

    /** 组件不可见时记住 timer 是否应在恢复可见时重启 */
    private boolean suspended = false;

    public SpringAnimator(LiquidGlassTheme.SpringConfig config) {
        this.config = config;
        this.timer = new Timer(FRAME_INTERVAL_MS, e -> tick());
        this.timer.setRepeats(true);
    }

    /**
     * 启动弹簧动画，从当前值过渡到目标值。
     *
     * @param from       起始值
     * @param target     目标值
     * @param onUpdate   每帧回调，参数为当前值
     * @param onComplete 动画完成回调（可为 null）
     */
    public void animateTo(double from, double target, Consumer<Double> onUpdate, Runnable onComplete) {
        this.currentValue = from;
        this.targetValue = target;
        this.velocity = config.initialVelocity();
        this.onUpdate = onUpdate;
        this.onComplete = onComplete;
        this.suspended = false;
        timer.start();
    }

    /**
     * 简化版：从当前值动画到目标值。
     */
    public void animateTo(double target, Consumer<Double> onUpdate, Runnable onComplete) {
        this.targetValue = target;
        this.onUpdate = onUpdate;
        this.onComplete = onComplete;
        this.suspended = false;
        timer.start();
    }

    private void tick() {
        try {
            double damping = 2.0 * config.dampingRatio() * Math.sqrt(config.stiffness());
            double springForce = -config.stiffness() * (currentValue - targetValue);
            double dampingForce = -damping * velocity;
            double acceleration = springForce + dampingForce;

            double dt = FRAME_INTERVAL_MS / 1000.0;
            velocity += acceleration * dt;
            currentValue += velocity * dt;

            // 溢出保护
            if (Double.isNaN(currentValue) || Double.isInfinite(currentValue)) {
                currentValue = targetValue;
                velocity = 0;
                timer.stop();
                if (onUpdate != null) onUpdate.accept(currentValue);
                if (onComplete != null) onComplete.run();
                return;
            }

            if (onUpdate != null) onUpdate.accept(currentValue);

            // 收敛判定
            if (Math.abs(currentValue - targetValue) < EPSILON
                    && Math.abs(velocity) < EPSILON) {
                timer.stop();
                currentValue = targetValue;
                if (onUpdate != null) onUpdate.accept(currentValue);
                if (onComplete != null) onComplete.run();
            }
        } catch (Exception e) {
            timer.stop();
        }
    }

    /**
     * 将此动画器绑定到组件的可见性，不可见时暂停，恢复可见时继续。
     */
    public void bindToComponent(Component component) {
        component.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (component.isShowing()) {
                    if (suspended) {
                        timer.start();
                        suspended = false;
                    }
                } else {
                    if (timer.isRunning()) {
                        timer.stop();
                        suspended = true;
                    }
                }
            }
        });
    }

    public void stop() {
        timer.stop();
        suspended = false;
    }

    public boolean isRunning() { return timer.isRunning(); }

    public double getCurrentValue() { return currentValue; }

    public double getTargetValue() { return targetValue; }

    // Package-private for testing
    Timer getTimer() { return timer; }
    boolean isSuspended() { return suspended; }
}
