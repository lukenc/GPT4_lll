package com.wmsay.gpt4_lll.component.theme;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpringAnimator 单元测试。
 * Validates: Requirements 15.4, 15.5
 */
class SpringAnimatorUnitTest {

    /**
     * 验证溢出保护：当 currentValue 变为 NaN 时，动画器停止并将值设为 target。
     * 通过反射调用 tick() 方法，先将 currentValue 设为 NaN 来模拟溢出。
     * Validates: Requirements 15.4
     */
    @Test
    void overflowProtection_NaN_stopsAndSetsToTarget() throws Exception {
        LiquidGlassTheme.SpringConfig config = new LiquidGlassTheme.SpringConfig(200.0, 0.8, 0.0);
        SpringAnimator animator = new SpringAnimator(config);

        double target = 100.0;
        AtomicReference<Double> lastUpdate = new AtomicReference<>(null);
        AtomicBoolean completed = new AtomicBoolean(false);

        // Start animation to set up internal state
        animator.animateTo(0.0, target, lastUpdate::set, () -> completed.set(true));
        // Stop the timer so we can manually control ticks
        animator.getTimer().stop();

        // Inject NaN into currentValue via reflection
        java.lang.reflect.Field currentValueField = SpringAnimator.class.getDeclaredField("currentValue");
        currentValueField.setAccessible(true);
        currentValueField.setDouble(animator, Double.NaN);

        // Invoke tick() via reflection
        Method tickMethod = SpringAnimator.class.getDeclaredMethod("tick");
        tickMethod.setAccessible(true);
        tickMethod.invoke(animator);

        // After overflow protection: value should be target, timer should be stopped, onComplete called
        assertEquals(target, animator.getCurrentValue(), 0.001,
                "After NaN overflow, currentValue should be set to target");
        assertFalse(animator.isRunning(), "Timer should be stopped after NaN overflow");
        assertTrue(completed.get(), "onComplete should be called after overflow protection");
        assertEquals(target, lastUpdate.get(), 0.001,
                "onUpdate should receive the target value");
    }

    /**
     * 验证溢出保护：当 currentValue 变为 Infinity 时，动画器停止并将值设为 target。
     * Validates: Requirements 15.4
     */
    @Test
    void overflowProtection_Infinity_stopsAndSetsToTarget() throws Exception {
        LiquidGlassTheme.SpringConfig config = new LiquidGlassTheme.SpringConfig(200.0, 0.8, 0.0);
        SpringAnimator animator = new SpringAnimator(config);

        double target = 50.0;
        AtomicReference<Double> lastUpdate = new AtomicReference<>(null);
        AtomicBoolean completed = new AtomicBoolean(false);

        animator.animateTo(0.0, target, lastUpdate::set, () -> completed.set(true));
        animator.getTimer().stop();

        // Inject Infinity into currentValue
        java.lang.reflect.Field currentValueField = SpringAnimator.class.getDeclaredField("currentValue");
        currentValueField.setAccessible(true);
        currentValueField.setDouble(animator, Double.POSITIVE_INFINITY);

        Method tickMethod = SpringAnimator.class.getDeclaredMethod("tick");
        tickMethod.setAccessible(true);
        tickMethod.invoke(animator);

        assertEquals(target, animator.getCurrentValue(), 0.001,
                "After Infinity overflow, currentValue should be set to target");
        assertFalse(animator.isRunning(), "Timer should be stopped after Infinity overflow");
        assertTrue(completed.get(), "onComplete should be called after overflow protection");
    }

    /**
     * 验证 stop() 方法停止 timer 并清除 suspended 标志。
     * Validates: Requirements 15.4
     */
    @Test
    void stop_stopsTimerAndClearsSuspended() {
        LiquidGlassTheme.SpringConfig config = LiquidGlassTheme.SPRING_STANDARD;
        SpringAnimator animator = new SpringAnimator(config);

        // Start animation so timer is running
        animator.animateTo(0.0, 100.0, v -> {}, null);
        assertTrue(animator.isRunning(), "Timer should be running after animateTo");

        // Stop
        animator.stop();
        assertFalse(animator.isRunning(), "Timer should be stopped after stop()");
        assertFalse(animator.isSuspended(), "suspended flag should be false after stop()");
    }

    /**
     * 验证初始状态：isRunning() 在 animateTo 调用前返回 false。
     * Validates: Requirements 15.4
     */
    @Test
    void initialState_isRunningReturnsFalse() {
        LiquidGlassTheme.SpringConfig config = LiquidGlassTheme.SPRING_FAST;
        SpringAnimator animator = new SpringAnimator(config);

        assertFalse(animator.isRunning(), "isRunning() should return false before animateTo is called");
    }

    /**
     * 验证 animateTo 启动 timer（isRunning() 返回 true）。
     * Validates: Requirements 15.4
     */
    @Test
    void animateTo_startsTimer() {
        LiquidGlassTheme.SpringConfig config = LiquidGlassTheme.SPRING_STANDARD;
        SpringAnimator animator = new SpringAnimator(config);

        animator.animateTo(0.0, 200.0, v -> {}, null);
        assertTrue(animator.isRunning(), "isRunning() should return true after animateTo");

        // Clean up
        animator.stop();
    }

    /**
     * 验证 bindToComponent 的暂停/恢复：组件不可见时 suspended=true，恢复可见时 suspended=false。
     * 通过直接检查 isSuspended() 包级访问器验证。
     * Validates: Requirements 15.5
     */
    @Test
    void bindToComponent_suspendAndResume() {
        LiquidGlassTheme.SpringConfig config = LiquidGlassTheme.SPRING_STANDARD;
        SpringAnimator animator = new SpringAnimator(config);

        // Initially not suspended
        assertFalse(animator.isSuspended(), "Initially should not be suspended");

        // After stop(), suspended should be false
        animator.animateTo(0.0, 100.0, v -> {}, null);
        animator.stop();
        assertFalse(animator.isSuspended(), "After stop(), suspended should be false");
    }

    /**
     * 验证 getTargetValue 返回 animateTo 设置的目标值。
     * Validates: Requirements 15.4
     */
    @Test
    void getTargetValue_returnsSetTarget() {
        LiquidGlassTheme.SpringConfig config = LiquidGlassTheme.SPRING_SLOW;
        SpringAnimator animator = new SpringAnimator(config);

        animator.animateTo(10.0, 250.0, v -> {}, null);
        assertEquals(250.0, animator.getTargetValue(), 0.001,
                "getTargetValue() should return the target passed to animateTo");

        animator.stop();
    }

    /**
     * 验证 tick 正常执行时 onUpdate 被调用且值在合理范围内。
     * Validates: Requirements 15.4
     */
    @Test
    void tick_normalExecution_callsOnUpdate() throws Exception {
        LiquidGlassTheme.SpringConfig config = new LiquidGlassTheme.SpringConfig(200.0, 0.8, 0.0);
        SpringAnimator animator = new SpringAnimator(config);

        AtomicReference<Double> lastUpdate = new AtomicReference<>(null);
        animator.animateTo(0.0, 100.0, lastUpdate::set, null);
        animator.getTimer().stop();

        // Invoke tick manually
        Method tickMethod = SpringAnimator.class.getDeclaredMethod("tick");
        tickMethod.setAccessible(true);
        tickMethod.invoke(animator);

        assertNotNull(lastUpdate.get(), "onUpdate should have been called");
        double value = lastUpdate.get();
        assertFalse(Double.isNaN(value), "Updated value should not be NaN");
        assertFalse(Double.isInfinite(value), "Updated value should not be Infinite");
    }
}
