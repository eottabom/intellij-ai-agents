package io.github.eottabom.aiagents.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class AiProviderProcessRunner {

    private static final Logger logger = LoggerFactory.getLogger(AiProviderProcessRunner.class);
    private static final int STDERR_BUFFER_LIMIT = 8_000;
    private static final int PLAINTEXT_BUFFER_LIMIT = 8_000;
    private static final long WATCHDOG_POLL_MS = 1_000;
    private static final long CANCEL_POLL_MS = 100;

    private AiProviderProcessRunner() {
    }

    static void run(
            AiProvider provider,
            String prompt,
            String sessionId,
            String workDir,
            Consumer<StreamChunk> onChunk
    ) {
        Process process = startProcess(provider, prompt, sessionId, workDir, onChunk);
        if (process == null) {
            return;
        }

        RunState state = new RunState();
        Thread stderrThread = drainStderr(provider, process, state.stderrBuf);
        Thread watchdogThread = startWatchdog(provider, process, state, onChunk);
        Thread cancelThread = watchForCancellation(provider, process);

        String lastSessionId = readStdout(provider, process, state, onChunk);

        int exitCode = awaitExit(process, stderrThread);
        watchdogThread.interrupt();
        cancelThread.interrupt();

        if (!state.timedOut.get()) {
            finalizeRun(provider, exitCode, lastSessionId, state, onChunk);
        }
    }

    private static Process startProcess(
            AiProvider provider,
            String prompt,
            String sessionId,
            String workDir,
            Consumer<StreamChunk> onChunk
    ) {
        var argv = new ArrayList<String>();
        argv.add(provider.cliName);
        argv.addAll(provider.buildRunArgs(prompt, sessionId, workDir));
        var command = wrapForShell(argv);

        var promptLength = 0;
        if (prompt != null) {
            promptLength = prompt.length();
        }
        logger.warn("run start={} promptLen={}", provider.cliName, promptLength);

        try {
            var pb = new ProcessBuilder(command);
            var processWorkDir = System.getProperty("user.home");
            if (workDir != null) {
                processWorkDir = workDir;
            }
            pb.directory(new File(processWorkDir));
            pb.environment().putAll(System.getenv());
            pb.redirectErrorStream(false);

            var process = pb.start();
            closeStdin(process);
            return process;
        } catch (Exception startupException) {
            onChunk.accept(StreamChunk.error(
                    "Failed to start " + provider.cliName + ": " + startupException.getMessage()));
            return null;
        }
    }

    private static void closeStdin(Process process) {
        try {
            process.getOutputStream().close();
        } catch (Exception e) {
            logger.debug("Failed to close stdin: {}", e.getMessage());
        }
    }

    private static Thread drainStderr(AiProvider provider, Process process, StringBuilder buf) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank() && !isNoiseLine(line)) {
                        if (buf.length() < STDERR_BUFFER_LIMIT) {
                            buf.append(line).append('\n');
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error reading stderr for {}: {}", provider.cliName, e.getMessage());
            }
        }, provider.cliName + "-stderr");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static Thread startWatchdog(
            AiProvider provider,
            Process process,
            RunState state,
            Consumer<StreamChunk> onChunk
    ) {
        Thread thread = new Thread(() -> {
            long timeout = provider.timeoutMs();
            while (process.isAlive()) {
                if (waitForExitOrInterrupt(process, WATCHDOG_POLL_MS)) {
                    return;
                }
                long idleMs = System.currentTimeMillis() - state.lastOutputAt.get();
                if (idleMs > timeout && state.timedOut.compareAndSet(false, true)) {
                    logger.warn("timeout name={} idleMs={}", provider.cliName, idleMs);
                    process.destroyForcibly();
                    onChunk.accept(StreamChunk.error(
                            provider.cliName + " produced no output for "
                                    + (timeout / 1000) + "s. Check login/auth or PATH."));
                    return;
                }
            }
        }, provider.cliName + "-watchdog");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static Thread watchForCancellation(AiProvider provider, Process process) {
        Thread caller = Thread.currentThread();

        Thread thread = new Thread(() -> {
            while (process.isAlive()) {
                if (caller.isInterrupted()) {
                    logger.warn("cancel requested name={}", provider.cliName);
                    process.destroyForcibly();
                    return;
                }
                if (waitForExitOrInterrupt(process, CANCEL_POLL_MS)) {
                    return;
                }
            }
        }, provider.cliName + "-cancel");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static String readStdout(
            AiProvider provider,
            Process process,
            RunState state,
            Consumer<StreamChunk> onChunk
    ) {
        String lastSessionId = null;
        StringBuilder plainTextBuf = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                state.lastOutputAt.set(System.currentTimeMillis());
                if (Thread.currentThread().isInterrupted()) {
                    process.destroyForcibly();
                    return null;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty() || isNoiseLine(trimmed)) {
                    continue;
                }

                StreamChunk chunk = provider.parseLine(trimmed);
                if (chunk == null) {
                    if (trimmed.startsWith("{") || state.sawStructuredOutput.get()) {
                        continue;
                    }
                    if (plainTextBuf.length() < PLAINTEXT_BUFFER_LIMIT) {
                        plainTextBuf.append(trimmed).append('\n');
                    }
                    continue;
                }

                state.sawStructuredOutput.set(true);
                plainTextBuf.setLength(0);

                if (chunk.type() == ChunkType.DONE) {
                    if (chunk.sessionId() != null) {
                        lastSessionId = chunk.sessionId();
                    }
                    continue;
                }

                onChunk.accept(chunk);
                state.sawOutput.set(true);
            }
        } catch (Exception e) {
            logger.debug("Error reading stdout for {}: {}", provider.cliName, e.getMessage());
        }

        if (!state.sawOutput.get()) {
            String plainText = plainTextBuf.toString().trim();
            if (!plainText.isBlank()) {
                onChunk.accept(StreamChunk.text(plainText));
                state.sawOutput.set(true);
            }
        }

        return lastSessionId;
    }

    private static int awaitExit(Process process, Thread stderrThread) {
        try {
            int code = process.waitFor();
            stderrThread.join(200);
            return code;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static void finalizeRun(
            AiProvider provider,
            int exitCode,
            String lastSessionId,
            RunState state,
            Consumer<StreamChunk> onChunk
    ) {
        String stderr = state.stderrBuf.toString().trim();

        if (!state.sawOutput.get() && !stderr.isBlank()) {
            if (exitCode == 0) {
                onChunk.accept(StreamChunk.text(stderr));
                state.sawOutput.set(true);
            } else {
                onChunk.accept(StreamChunk.error(stderr));
                return;
            }
        }

        if (exitCode != 0 && !state.sawOutput.get()) {
            onChunk.accept(StreamChunk.error(provider.cliName + " exited with code " + exitCode));
            return;
        }

        onChunk.accept(StreamChunk.done(lastSessionId));
    }

    private static List<String> wrapForShell(List<String> argv) {
        if (isWindows()) {
            return argv;
        }

        var shellCommand = new StringBuilder();
        for (int argumentIndex = 0; argumentIndex < argv.size(); argumentIndex++) {
            if (argumentIndex > 0) {
                shellCommand.append(' ');
            }
            shellCommand.append('\'').append(argv.get(argumentIndex).replace("'", "'\"'\"'")).append('\'');
        }
        return List.of("/bin/bash", "-l", "-c", shellCommand.toString());
    }

    private static boolean isWindows() {
        var osName = System.getProperty("os.name", "");
        var normalizedOsName = osName.toLowerCase(Locale.ROOT);
        return normalizedOsName.contains("win");
    }

    private static boolean isNoiseLine(String line) {
        return line.startsWith("(node:")
                || line.contains("[DEP0")
                || line.contains("DeprecationWarning:")
                || line.startsWith("(Use `node --trace-deprecation")
                || line.startsWith("(Use node --trace-deprecation");
    }

    private static boolean waitForExitOrInterrupt(Process process, long pollMs) {
        try {
            return process.waitFor(pollMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static final class RunState {
        final AtomicBoolean timedOut = new AtomicBoolean(false);
        final AtomicBoolean sawOutput = new AtomicBoolean(false);
        final AtomicBoolean sawStructuredOutput = new AtomicBoolean(false);
        final AtomicLong lastOutputAt = new AtomicLong(System.currentTimeMillis());
        final StringBuilder stderrBuf = new StringBuilder();
    }
}
