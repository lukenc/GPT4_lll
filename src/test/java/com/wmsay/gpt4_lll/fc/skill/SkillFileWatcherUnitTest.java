package com.wmsay.gpt4_lll.fc.skill;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillFileWatcher 单元测试。
 * 验证文件监听、防抖合并、守护线程和资源释放。
 */
class SkillFileWatcherUnitTest {

    private SkillFileWatcher watcher;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        watcher = new SkillFileWatcher();
    }

    @AfterEach
    void tearDown() {
        watcher.stop();
    }

    @Test
    void start_detectsFileCreation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        watcher.start(tempDir, latch::countDown);

        // 创建文件触发 CREATE 事件
        Files.writeString(tempDir.resolve("test.md"), "hello");

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Callback should be invoked after file creation");
    }

    @Test
    void start_detectsFileModification() throws Exception {
        // 先创建文件
        Path file = tempDir.resolve("existing.md");
        Files.writeString(file, "original");

        CountDownLatch latch = new CountDownLatch(1);
        watcher.start(tempDir, latch::countDown);

        // 短暂等待确保 watcher 已就绪
        Thread.sleep(200);

        // 修改文件触发 MODIFY 事件
        Files.writeString(file, "modified");

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Callback should be invoked after file modification");
    }

    @Test
    void start_detectsFileDeletion() throws Exception {
        // 先创建文件
        Path file = tempDir.resolve("toDelete.md");
        Files.writeString(file, "content");

        CountDownLatch latch = new CountDownLatch(1);
        watcher.start(tempDir, latch::countDown);

        // 短暂等待确保 watcher 已就绪
        Thread.sleep(200);

        // 删除文件触发 DELETE 事件
        Files.delete(file);

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Callback should be invoked after file deletion");
    }

    @Test
    void debounce_mergesMultipleChangesIntoOneCallback() throws Exception {
        AtomicInteger callbackCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        watcher.start(tempDir, () -> {
            callbackCount.incrementAndGet();
            latch.countDown();
        });

        // 快速连续创建多个文件（应被防抖合并为一次回调）
        for (int i = 0; i < 5; i++) {
            Files.writeString(tempDir.resolve("file" + i + ".md"), "content" + i);
            Thread.sleep(50); // 50ms 间隔，远小于 500ms 防抖
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "At least one callback should be invoked");

        // 等待额外时间确保没有多余回调
        Thread.sleep(1500);

        // 防抖应合并多次变更，回调次数应远少于文件数
        assertTrue(callbackCount.get() <= 2,
                "Debounce should merge rapid changes; got " + callbackCount.get() + " callbacks");
    }

    @Test
    void stop_releasesResources() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        watcher.start(tempDir, latch::countDown);

        // 确保 watcher 已启动
        Thread.sleep(200);

        // 停止 watcher
        watcher.stop();

        // 停止后创建文件不应触发回调
        Files.writeString(tempDir.resolve("afterStop.md"), "content");
        assertFalse(latch.await(2, TimeUnit.SECONDS),
                "Callback should NOT be invoked after stop()");
    }

    @Test
    void stop_canBeCalledMultipleTimes() {
        watcher.start(tempDir, () -> {});
        // 多次调用 stop 不应抛异常
        assertDoesNotThrow(() -> {
            watcher.stop();
            watcher.stop();
        });
    }

    @Test
    void start_nullDirectoryDoesNotThrow() {
        assertDoesNotThrow(() -> watcher.start(null, () -> {}));
    }

    @Test
    void start_nullCallbackDoesNotThrow() {
        assertDoesNotThrow(() -> watcher.start(tempDir, null));
    }

    @Test
    void watchThread_isDaemon() throws Exception {
        watcher.start(tempDir, () -> {});
        // 短暂等待线程启动
        Thread.sleep(200);

        // 查找 SkillFileWatcher 线程
        Thread watcherThread = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> "SkillFileWatcher".equals(t.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(watcherThread, "Watcher thread should exist");
        assertTrue(watcherThread.isDaemon(), "Watcher thread should be a daemon thread");
    }

    @Test
    void start_restartAfterStop() throws Exception {
        // 第一次启动和停止
        watcher.start(tempDir, () -> {});
        Thread.sleep(200);
        watcher.stop();

        // 第二次启动应正常工作
        CountDownLatch latch = new CountDownLatch(1);
        watcher.start(tempDir, latch::countDown);

        Files.writeString(tempDir.resolve("restart.md"), "content");

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Callback should work after restart");
    }

    @Test
    void callbackException_doesNotStopWatcher() throws Exception {
        AtomicInteger callbackCount = new AtomicInteger(0);
        CountDownLatch secondCall = new CountDownLatch(2);

        watcher.start(tempDir, () -> {
            int count = callbackCount.incrementAndGet();
            secondCall.countDown();
            if (count == 1) {
                throw new RuntimeException("Simulated callback error");
            }
        });

        // 第一次变更 — 回调会抛异常
        Files.writeString(tempDir.resolve("first.md"), "content");
        Thread.sleep(1500);

        // 第二次变更 — watcher 应仍在运行
        Files.writeString(tempDir.resolve("second.md"), "content");

        assertTrue(secondCall.await(5, TimeUnit.SECONDS),
                "Watcher should continue running after callback exception");
    }
}
