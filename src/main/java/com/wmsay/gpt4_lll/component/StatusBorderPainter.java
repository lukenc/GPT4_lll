package com.wmsay.gpt4_lll.component;

import com.intellij.ui.JBColor;
import com.wmsay.gpt4_lll.model.RuntimeStatus;
import com.wmsay.gpt4_lll.utils.RuntimeStatusManager;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;

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
        implements RuntimeStatusManager.StatusListener {

    static final JBColor GREEN = new JBColor(new Color(0x4CAF50), new Color(0x66BB6A));
    static final JBColor RED = new JBColor(new Color(0xF44336), new Color(0xEF5350));

    private static final int BORDER_THICKNESS = 2;
    private static final int ANIMATION_INTERVAL_MS = 40;
    private static final float PHASE_INCREMENT = 0.02f;
    private static final int AUTO_RESET_DELAY_MS = 3000;

    private Timer animationTimer;
    private Timer autoResetTimer;
    private float animationPhase;
    private RuntimeStatus currentStatus = RuntimeStatus.IDLE;
    private Component parentComponent;

    public StatusBorderPainter(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (currentStatus == RuntimeStatus.IDLE) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (currentStatus == RuntimeStatus.RUNNING) {
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
        g2.setColor(color);
        g2.setStroke(new BasicStroke(BORDER_THICKNESS));
        // Draw rect inset by half the stroke width so the full stroke is visible
        int offset = BORDER_THICKNESS / 2;
        g2.drawRect(x + offset, y + offset, width - BORDER_THICKNESS, height - BORDER_THICKNESS);
    }

    private void paintAnimatedBorder(Graphics2D g2, int x, int y, int width, int height) {
        // Create a gradient sweep that moves along the border based on animationPhase
        float phase = this.animationPhase;

        // Gradient start/end positions sweep diagonally across the component
        float startX = x + (width * phase);
        float startY = y;
        float endX = x + (width * phase) - width * 0.3f;
        float endY = y + height;

        Color transparent = new Color(GREEN.getRed(), GREEN.getGreen(), GREEN.getBlue(), 60);
        Color bright = GREEN;

        LinearGradientPaint gradient = new LinearGradientPaint(
                startX, startY, endX, endY,
                new float[]{0.0f, 0.4f, 0.6f, 1.0f},
                new Color[]{transparent, bright, bright, transparent}
        );

        g2.setPaint(gradient);
        g2.setStroke(new BasicStroke(BORDER_THICKNESS));
        int offset = BORDER_THICKNESS / 2;
        g2.drawRect(x + offset, y + offset, width - BORDER_THICKNESS, height - BORDER_THICKNESS);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        if (currentStatus == RuntimeStatus.IDLE) {
            return new Insets(0, 0, 0, 0);
        }
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

    private void startAnimationTimer() {
        animationPhase = 0.0f;
        animationTimer = new Timer(ANIMATION_INTERVAL_MS, e -> {
            animationPhase += PHASE_INCREMENT;
            if (animationPhase >= 1.0f) {
                animationPhase = 0.0f;
            }
            if (parentComponent != null) {
                parentComponent.repaint();
            }
        });
        animationTimer.start();
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
     * Stops all timers and cleans up resources. Call when the component is being disposed.
     */
    public void dispose() {
        stopAnimationTimer();
        stopAutoResetTimer();
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
}
