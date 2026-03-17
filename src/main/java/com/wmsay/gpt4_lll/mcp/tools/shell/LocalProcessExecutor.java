package com.wmsay.gpt4_lll.mcp.tools.shell;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 本地进程执行器。
 * 使用 ProcessBuilder 启动子进程，并发读取 stdout/stderr，处理超时与进程终止。
 * <p>
 * Phase 1 使用 ProcessHandle.descendants() 递归终止子进程树（方案 A）。
 */
public class LocalProcessExecutor {

    private static final Logger LOG = Logger.getInstance(LocalProcessExecutor.class);

    private static final long GRACEFUL_SHUTDOWN_MS = 2000;

    /**
     * 执行命令并返回结构化结果。
     *
     * @param request     已校验的执行请求
     * @param riskLevel   评估后的风险等级
     * @param projectRoot 项目根目录
     * @return 执行结果
     */
    public ShellExecResult execute(ShellExecRequest request, ShellRiskLevel riskLevel, Path projectRoot) {
        long startTime = System.currentTimeMillis();
        Charset charset = request.getOutputEncoding();

        ProcessBuilder pb = new ProcessBuilder(request.getCommand());
        pb.directory(request.getWorkingDirectory().toFile());
        applyEnvironment(pb, request);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        final Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            long durationMs = System.currentTimeMillis() - startTime;
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "process_spawn_failed");
            error.put("message", "Failed to start process: " + e.getMessage());
            error.put("suggestion", "Check that the executable exists and is on PATH. "
                    + "Verify the working directory is accessible.");

            return ShellExecResult.builder()
                    .status(ShellExecResult.Status.FAILED)
                    .command(request.getCommand())
                    .displayCommand(request.getDisplayCommand())
                    .mode(request.getMode().name().toLowerCase())
                    .workingDirectory(request.getWorkingDirectory().toString())
                    .durationMs(durationMs)
                    .error(error)
                    .metadata(buildMetadata("", charset))
                    .riskLevel(riskLevel)
                    .build();
        }

        try {
            try { process.getOutputStream().close(); } catch (IOException ignored) { }

            OutputAccumulator stdoutAcc = new OutputAccumulator(request.getMaxOutputBytes());
            OutputAccumulator stderrAcc = new OutputAccumulator(request.getMaxOutputBytes());

            Thread stdoutThread = new Thread(() -> {
                try {
                    stdoutAcc.consume(process.getInputStream(), charset);
                } catch (IOException e) {
                    LOG.debug("stdout reader interrupted: " + e.getMessage());
                }
            }, "shell-exec-stdout");
            stdoutThread.setDaemon(true);

            Thread stderrThread = null;
            if (request.isCaptureStderr()) {
                stderrThread = new Thread(() -> {
                    try {
                        stderrAcc.consume(process.getErrorStream(), charset);
                    } catch (IOException e) {
                        LOG.debug("stderr reader interrupted: " + e.getMessage());
                    }
                }, "shell-exec-stderr");
                stderrThread.setDaemon(true);
                stderrThread.start();
            }
            stdoutThread.start();

            boolean completed = process.waitFor(request.getTimeoutMs(), TimeUnit.MILLISECONDS);

            if (!completed) {
                String killSignal = terminateProcessTree(process);
                waitForReaderThreads(stdoutThread, stderrThread);

                long durationMs = System.currentTimeMillis() - startTime;
                return buildResult(request, riskLevel, ShellExecResult.Status.TIMEOUT,
                        null, stdoutAcc, stderrAcc, durationMs, true, killSignal, charset);
            }

            waitForReaderThreads(stdoutThread, stderrThread);

            int exitCode = process.exitValue();
            long durationMs = System.currentTimeMillis() - startTime;
            ShellExecResult.Status status = (exitCode == 0)
                    ? ShellExecResult.Status.SUCCESS
                    : ShellExecResult.Status.FAILED;

            return buildResult(request, riskLevel, status,
                    exitCode, stdoutAcc, stderrAcc, durationMs, false, "", charset);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String killSignal = terminateProcessTree(process);
            long durationMs = System.currentTimeMillis() - startTime;
            return ShellExecResult.builder()
                    .status(ShellExecResult.Status.CANCELLED)
                    .command(request.getCommand())
                    .displayCommand(request.getDisplayCommand())
                    .mode(request.getMode().name().toLowerCase())
                    .workingDirectory(request.getWorkingDirectory().toString())
                    .durationMs(durationMs)
                    .metadata(buildMetadata(killSignal, charset))
                    .riskLevel(riskLevel)
                    .build();
        }
    }

    /**
     * 递归终止进程树（方案 A: ProcessHandle.descendants()）。
     * 先尝试 destroy()（SIGTERM），等待宽限期后 destroyForcibly()（SIGKILL）。
     */
    private String terminateProcessTree(Process process) {
        try {
            process.descendants().forEach(ph -> {
                try {
                    ph.destroyForcibly();
                } catch (Exception e) {
                    LOG.debug("Failed to kill descendant process " + ph.pid() + ": " + e.getMessage());
                }
            });

            process.destroy();

            boolean exited = process.waitFor(GRACEFUL_SHUTDOWN_MS, TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                return "SIGKILL";
            }
            return "SIGTERM";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "SIGKILL";
        }
    }

    private void applyEnvironment(ProcessBuilder pb, ShellExecRequest request) {
        Map<String, String> env = pb.environment();
        Map<String, String> overrides = request.getEnvironmentOverrides();
        if (overrides != null) {
            env.putAll(overrides);
        }
    }

    private void waitForReaderThreads(Thread stdoutThread, Thread stderrThread) {
        try {
            if (stdoutThread != null) {
                stdoutThread.join(3000);
            }
            if (stderrThread != null) {
                stderrThread.join(3000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ShellExecResult buildResult(ShellExecRequest request, ShellRiskLevel riskLevel,
                                        ShellExecResult.Status status, Integer exitCode,
                                        OutputAccumulator stdoutAcc, OutputAccumulator stderrAcc,
                                        long durationMs, boolean timedOut,
                                        String killSignal, Charset charset) {
        boolean truncated = stdoutAcc.isTruncated() || stderrAcc.isTruncated();

        ShellExecResult.Builder builder = ShellExecResult.builder()
                .status(status)
                .command(request.getCommand())
                .displayCommand(request.getDisplayCommand())
                .mode(request.getMode().name().toLowerCase())
                .workingDirectory(request.getWorkingDirectory().toString())
                .exitCode(exitCode)
                .stdout(stdoutAcc.getContent())
                .stderr(stderrAcc.getContent())
                .stdoutBytes(stdoutAcc.getTotalBytes())
                .stderrBytes(stderrAcc.getTotalBytes())
                .durationMs(durationMs)
                .timedOut(timedOut)
                .truncated(truncated)
                .riskLevel(riskLevel)
                .metadata(buildMetadata(killSignal, charset));

        if (status == ShellExecResult.Status.FAILED && exitCode != null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "non_zero_exit");
            error.put("message", "Process exited with code " + exitCode);
            error.put("suggestion", "Inspect stderr for error details.");
            builder.error(error);
        }

        if (timedOut) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "process_timeout");
            error.put("message", "Process timed out after " + request.getTimeoutMs() + "ms");
            error.put("suggestion", "Increase timeoutMs or simplify the command.");
            builder.error(error);
        }

        return builder.build();
    }

    private Map<String, Object> buildMetadata(String killSignal, Charset charset) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("spawnMethod", "ProcessBuilder");
        meta.put("policyVersion", "v1");
        meta.put("killSignal", killSignal != null ? killSignal : "");
        meta.put("workspaceBound", true);
        meta.put("outputEncoding", charset.name());
        return meta;
    }
}
