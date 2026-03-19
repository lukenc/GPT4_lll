package com.wmsay.gpt4_lll.component.block;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * 可缩放、可拖拽平移的图片查看面板。
 * 跨平台支持 macOS / Windows / Linux。
 * 支持：
 * - 鼠标滚轮缩放（以鼠标位置为中心，兼容 macOS 触控板自然滚动）
 * - 鼠标拖拽平移
 * - 双击恢复适应窗口大小
 * - 键盘快捷键：Ctrl/Cmd + Plus 放大, Ctrl/Cmd + Minus 缩小, Ctrl/Cmd + 0 适应窗口, Escape 关闭
 */
class ZoomableImagePanel extends JPanel {

    private final BufferedImage image;
    private double scale = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;
    private Point dragStart;

    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 10.0;
    private static final double ZOOM_FACTOR = 1.15;

    private Runnable onScaleChanged;

    ZoomableImagePanel(BufferedImage image) {
        this.image = image;
        setBackground(Color.DARK_GRAY);
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        setFocusable(true);

        addMouseWheelListener(this::handleMouseWheel);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                requestFocusInWindow(); // 确保键盘事件能到达面板
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    fitToPanel();
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    offsetX += e.getX() - dragStart.x;
                    offsetY += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                    repaint();
                }
            }
        });

        // 键盘快捷键：Ctrl+Plus 放大, Ctrl+Minus 缩小, Ctrl+0 适应窗口, Escape 关闭
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                boolean ctrl = e.isControlDown() || e.isMetaDown(); // Ctrl(Win/Linux) / Cmd(macOS)
                if (ctrl && (e.getKeyCode() == KeyEvent.VK_EQUALS || e.getKeyCode() == KeyEvent.VK_ADD)) {
                    zoomIn();
                } else if (ctrl && (e.getKeyCode() == KeyEvent.VK_MINUS || e.getKeyCode() == KeyEvent.VK_SUBTRACT)) {
                    zoomOut();
                } else if (ctrl && e.getKeyCode() == KeyEvent.VK_0) {
                    fitToPanel();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // 关闭所在的 dialog
                    Window w = SwingUtilities.getWindowAncestor(ZoomableImagePanel.this);
                    if (w instanceof JDialog) w.dispose();
                }
            }
        });
    }

    void setOnScaleChanged(Runnable callback) {
        this.onScaleChanged = callback;
    }

    double getScale() {
        return scale;
    }

    /** 适应面板大小 */
    void fitToPanel() {
        int pw = getWidth();
        int ph = getHeight();
        if (pw <= 0 || ph <= 0) return;
        double sx = (double) pw / image.getWidth();
        double sy = (double) ph / image.getHeight();
        scale = Math.min(sx, sy);
        // 居中
        offsetX = (pw - image.getWidth() * scale) / 2.0;
        offsetY = (ph - image.getHeight() * scale) / 2.0;
        repaint();
        fireScaleChanged();
    }

    /** 显示实际大小 (100%) */
    void actualSize() {
        scale = 1.0;
        int pw = getWidth();
        int ph = getHeight();
        offsetX = (pw - image.getWidth()) / 2.0;
        offsetY = (ph - image.getHeight()) / 2.0;
        repaint();
        fireScaleChanged();
    }

    /** 放大 */
    void zoomIn() {
        zoomAt(getWidth() / 2.0, getHeight() / 2.0, ZOOM_FACTOR);
    }

    /** 缩小 */
    void zoomOut() {
        zoomAt(getWidth() / 2.0, getHeight() / 2.0, 1.0 / ZOOM_FACTOR);
    }

    private void handleMouseWheel(MouseWheelEvent e) {
        // getPreciseWheelRotation 语义：
        //   鼠标滚轮（Windows/macOS/Linux）：向上滚=负值，向下滚=正值
        //   macOS 触控板（自然滚动）：双指向上=负值（系统已翻转）
        // 统一行为：负值=放大，正值=缩小
        // 不需要额外取反——Java 已经在所有平台上统一了方向语义
        double rotation = e.getPreciseWheelRotation();
        double factor = Math.pow(ZOOM_FACTOR, -rotation);
        zoomAt(e.getX(), e.getY(), factor);
    }

    private void zoomAt(double mouseX, double mouseY, double factor) {
        double newScale = scale * factor;
        newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        // 以鼠标位置为中心缩放
        offsetX = mouseX - (mouseX - offsetX) * (newScale / scale);
        offsetY = mouseY - (mouseY - offsetY) * (newScale / scale);
        scale = newScale;
        repaint();
        fireScaleChanged();
    }

    private void fireScaleChanged() {
        if (onScaleChanged != null) onScaleChanged.run();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        AffineTransform at = new AffineTransform();
        at.translate(offsetX, offsetY);
        at.scale(scale, scale);
        g2.drawImage(image, at, null);
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(image.getWidth(), image.getHeight());
    }
}
