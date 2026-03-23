package com.wmsay.gpt4_lll.component;

import com.wmsay.gpt4_lll.model.AgentStatusContext;
import com.wmsay.gpt4_lll.utils.RuntimeStatusManager;

import javax.swing.*;
import java.awt.*;

/**
 * 悬浮状态指示条 — 显示在对话滚动区域底部。
 * 实现 AgentPhaseListener，自动响应阶段变化。
 * 半透明背景，不占用对话内容布局空间。
 */
public class StatusIndicatorPanel extends JPanel
        implements RuntimeStatusManager.AgentPhaseListener {

    private final JLabel iconLabel;
    private final JLabel textLabel;
    private final JLabel detailLabel;
    private Timer fadeOutTimer;
    private Timer spinnerTimer;
    private int spinnerFrame = 0;
    private static final String[] SPINNER_FRAMES =
            {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    static final int COMPLETED_HIDE_DELAY_MS = 3000;
    static final int ERROR_HIDE_DELAY_MS = 5000;
    static final int STOPPED_HIDE_DELAY_MS = 3000;

    public StatusIndicatorPanel() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 6, 4));
        setOpaque(false);
        setVisible(false);

        iconLabel = new JLabel();
        textLabel = new JLabel();
        detailLabel = new JLabel();
        detailLabel.setForeground(Color.GRAY);

        add(iconLabel);
        add(textLabel);
        add(detailLabel);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public void onPhaseChanged(AgentStatusContext oldCtx, AgentStatusContext newCtx) {
        SwingUtilities.invokeLater(() -> updateDisplay(newCtx));
    }

    private void updateDisplay(AgentStatusContext ctx) {
        stopTimers();

        switch (ctx.getPhase()) {
            case IDLE -> setVisible(false);
            case RUNNING -> showRunning(ctx);
            case COMPLETED -> showTerminal("✓", "已完成", COMPLETED_HIDE_DELAY_MS);
            case ERROR -> showTerminal("✗",
                    ctx.getDetail() != null ? ctx.getDetail() : "出错",
                    ERROR_HIDE_DELAY_MS);
            case STOPPED -> showTerminal("■", "已停止", STOPPED_HIDE_DELAY_MS);
        }
    }

    private void showRunning(AgentStatusContext ctx) {
        setVisible(true);
        textLabel.setText("运行中");
        detailLabel.setText(ctx.getDetail() != null ? ctx.getDetail() : "");
        startSpinner();
    }

    private void showTerminal(String icon, String text, int delayMs) {
        setVisible(true);
        stopSpinner();
        iconLabel.setText(icon);
        textLabel.setText(text);
        detailLabel.setText("");
        fadeOutTimer = new Timer(delayMs, e -> setVisible(false));
        fadeOutTimer.setRepeats(false);
        fadeOutTimer.start();
    }

    private void startSpinner() {
        spinnerFrame = 0;
        iconLabel.setText(SPINNER_FRAMES[0]);
        spinnerTimer = new Timer(100, e -> {
            spinnerFrame = (spinnerFrame + 1) % SPINNER_FRAMES.length;
            iconLabel.setText(SPINNER_FRAMES[spinnerFrame]);
        });
        spinnerTimer.start();
    }

    private void stopSpinner() {
        if (spinnerTimer != null) {
            spinnerTimer.stop();
            spinnerTimer = null;
        }
    }

    private void stopTimers() {
        stopSpinner();
        if (fadeOutTimer != null) {
            fadeOutTimer.stop();
            fadeOutTimer = null;
        }
    }

    public void dispose() {
        stopTimers();
    }

    // Package-private accessors for testing
    Timer getFadeOutTimer() { return fadeOutTimer; }
    Timer getSpinnerTimer() { return spinnerTimer; }
    JLabel getIconLabel() { return iconLabel; }
    JLabel getTextLabel() { return textLabel; }
    JLabel getDetailLabel() { return detailLabel; }
}
