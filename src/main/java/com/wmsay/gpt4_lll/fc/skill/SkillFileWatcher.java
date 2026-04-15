package com.wmsay.gpt4_lll.fc.skill;

import java.io.IOException;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Skill 文件监听器。
 * 使用 Java NIO WatchService 监听目录变更（CREATE、MODIFY、DELETE）。
 * 500ms 防抖延迟，合并短时间内的多次变更为一次回调。
 * 守护线程运行，确保插件关闭时自动终止。
 */
public class SkillFileWatcher {

    private static final Logger LOG = Logger.getLogger(SkillFileWatcher.class.getName());
    private static final long DEBOUNCE_DELAY_MS = 500;

    private volatile WatchService watchService;
    private volatile Thread watchThread;
    private volatile boolean running;

    /**
     * 启动监听指定目录的文件变更事件。
     * 如果已在运行，先停止旧的监听再启动新的。
     *
     * @param directory        要监听的目录路径
     * @param onChangeCallback 文件变更后的回调（经过防抖延迟）
     */
    public void start(Path directory, Runnable onChangeCallback) {
        if (directory == null || onChangeCallback == null) {
            LOG.warning("[Skill] Cannot start watcher: directory or callback is null");
            return;
        }

        // 如果已在运行，先停止
        if (running) {
            stop();
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[Skill] Failed to create WatchService for directory: " + directory, e);
            return;
        }

        running = true;

        watchThread = new Thread(() -> watchLoop(onChangeCallback), "SkillFileWatcher");
        watchThread.setDaemon(true);
        watchThread.start();

        LOG.info("[Skill] File watcher started for directory: " + directory);
    }

    /**
     * 停止监听并释放 WatchService 资源和监听线程。
     */
    public void stop() {
        running = false;

        // 关闭 WatchService（会使 watchService.take() 抛出 ClosedWatchServiceException）
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "[Skill] Error closing WatchService", e);
            }
            watchService = null;
        }

        // 中断监听线程
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watchThread = null;
        }

        LOG.info("[Skill] File watcher stopped");
    }

    /**
     * 监听循环：等待事件 → 防抖延迟 → 触发回调。
     */
    private void watchLoop(Runnable onChangeCallback) {
        while (running) {
            try {
                // 阻塞等待事件
                WatchKey key = watchService.take();

                // 消费所有待处理事件
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    LOG.fine("[Skill] File event detected: " + kind.name()
                            + " - " + event.context());
                }

                boolean valid = key.reset();
                if (!valid) {
                    LOG.warning("[Skill] Watch key is no longer valid, stopping watcher");
                    running = false;
                    break;
                }

                // 防抖延迟：等待 500ms，合并短时间内的多次变更
                Thread.sleep(DEBOUNCE_DELAY_MS);

                // 消费防抖期间可能到达的额外事件
                drainPendingEvents();

                // 触发回调
                try {
                    onChangeCallback.run();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[Skill] Error in file change callback", e);
                }

            } catch (InterruptedException e) {
                // 线程被中断（stop() 调用），正常退出
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                // WatchService 已关闭（stop() 调用），正常退出
                break;
            } catch (Exception e) {
                if (running) {
                    LOG.log(Level.WARNING, "[Skill] Unexpected error in watch loop", e);
                }
            }
        }
    }

    /**
     * 消费防抖期间可能到达的额外事件，避免重复触发回调。
     */
    private void drainPendingEvents() {
        if (watchService == null) {
            return;
        }
        WatchKey key;
        while ((key = watchService.poll()) != null) {
            key.pollEvents(); // 消费事件
            key.reset();
        }
    }
}
