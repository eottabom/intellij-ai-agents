package io.github.eottabom.aiagents.providers;

import com.intellij.openapi.diagnostic.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class AiProviderProcessRunner {

    private static final Logger logger = Logger.getInstance(AiProviderProcessRunner.class);
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
        List<String> argv = new ArrayList<>();
        argv.add(provider.cliName);
        argv.addAll(provider.buildRunArgs(prompt, sessionId, workDir));
        List<String> command = wrapForShell(argv);

        logger.warn("run start=" + provider.cliName + " promptLen=" + (prompt != null ? prompt.length() : 0));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(workDir != null ? workDir : System.getProperty("user.home")));
            pb.environment().putAll(System.getenv());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            closeStdin(process);
            return process;
        } catch (Exception e) {
            onChunk.accept(StreamChunk.error(
                    "Failed to start " + provider.cliName + ": " + e.getMessage()));
            return null;
        }
    }

    private static void closeStdin(Process process) {
        try {
            process.getOutputStream().close();
        } catch (Exception ignored) {
        }
    }

    private static Thread drainStderr(AiProvider provider, Process process, StringBuffer buf) {
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
            } catch (Exception ignored) {
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
                    logger.warn("timeout name=" + provider.cliName + " idleMs=" + idleMs);
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
        process.onExit().thenRun(caller::interrupt);

        Thread thread = new Thread(() -> {
            while (process.isAlive()) {
                if (caller.isInterrupted()) {
                    logger.warn("cancel requested name=" + provider.cliName);
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
                    if (trimmed.startsWith("{")) {
                        continue;
                    }
                    if (!state.sawStructuredOutput.get()) {
                        if (plainTextBuf.length() < PLAINTEXT_BUFFER_LIMIT) {
                            plainTextBuf.append(trimmed).append('\n');
                        }
                    } else {
                        onChunk.accept(StreamChunk.text(trimmed));
                        state.sawOutput.set(true);
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
        } catch (Exception ignored) {
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
        } catch (InterruptedException e) {
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

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argv.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append('\'').append(argv.get(i).replace("'", "'\"'\"'")).append('\'');
        }
        return List.of("/bin/bash", "-l", "-c", sb.toString());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static final class RunState {
        final AtomicBoolean timedOut = new AtomicBoolean(false);
        final AtomicBoolean sawOutput = new AtomicBoolean(false);
        final AtomicBoolean sawStructuredOutput = new AtomicBoolean(false);
        final AtomicLong lastOutputAt = new AtomicLong(System.currentTimeMillis());
        final StringBuffer stderrBuf = new StringBuffer();
    }
}
