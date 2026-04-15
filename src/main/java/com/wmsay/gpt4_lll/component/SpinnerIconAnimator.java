package com.wmsay.gpt4_lll.component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;

/**
 * SVG 旋转动画器：通过 javax.swing.Timer 驱动单个 Icon 旋转绘制。
 * <p>
 * 使用 Graphics2D.rotate() 对 baseIcon 进行 12 帧旋转（83ms/帧，~12fps），
 * 每帧角度 = currentFrame * 30°。通过 HierarchyListener 自动暂停/恢复，
 * 避免组件不可见时 EDT 队列积压。
 */
public class SpinnerIconAnimator {

    private static final int FRAME_COUNT = 12;
    private static final int FRAME_DELAY_MS = 83; // ~12fps

    private final Icon baseIcon;
    private final JLabel target;
    private final Timer timer;
    private int currentFrame = 0;

    /** 组件不可见时记住 timer 是否应在恢复可见时重启 */
    private boolean suspended = false;

    public SpinnerIconAnimator(Icon baseIcon, JLabel target) {
        this.baseIcon = baseIcon;
        this.target = target;

        this.timer = new Timer(FRAME_DELAY_MS, e -> {
            currentFrame = (currentFrame + 1) % FRAME_COUNT;
            target.setIcon(createRotatedIcon(currentFrame));
        });
        timer.setRepeats(true);

        // HierarchyListener: 组件不可见时暂停，可见时恢复
        target.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (target.isShowing()) {
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

    /**
     * 启动旋转动画。设置初始帧图标并启动 Timer。
     */
    public void start() {
        if (baseIcon == null) return;
        currentFrame = 0;
        target.setIcon(createRotatedIcon(0));
        suspended = false;
        timer.start();
    }

    /**
     * 停止旋转动画。停止 Timer 并重置图标为 baseIcon（静止状态）。
     */
    public void stop() {
        timer.stop();
        suspended = false;
        if (baseIcon != null) {
            target.setIcon(baseIcon);
        }
    }

    /**
     * 创建旋转后的 Icon。
     * 角度 = frame * (360.0 / FRAME_COUNT) 度。
     */
    private Icon createRotatedIcon(int frame) {
        if (baseIcon == null) return null;

        int w = baseIcon.getIconWidth();
        int h = baseIcon.getIconHeight();
        double angle = Math.toRadians(frame * 360.0 / FRAME_COUNT);

        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                // 以图标中心为旋转原点
                double cx = x + w / 2.0;
                double cy = y + h / 2.0;
                g2.rotate(angle, cx, cy);
                baseIcon.paintIcon(c, g2, x, y);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return w;
            }

            @Override
            public int getIconHeight() {
                return h;
            }
        };
    }

    // Package-private accessors for testing
    Timer getTimer() { return timer; }
    int getCurrentFrame() { return currentFrame; }
    boolean isSuspended() { return suspended; }
}
