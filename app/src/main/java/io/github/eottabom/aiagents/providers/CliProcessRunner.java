package io.github.eottabom.aiagents.providers;

import io.github.eottabom.aiagents.util.OsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

final class CliProcessRunner {

	private static final Logger logger = LoggerFactory.getLogger(CliProcessRunner.class);
	private static final int STDERR_BUFFER_LIMIT = 8_000;
	private static final int PLAINTEXT_BUFFER_LIMIT = 8_000;
	private static final int SUBCOMMAND_OUTPUT_BUFFER_LIMIT = 16_000;
	private static final long WATCHDOG_POLL_MS = 1_000;
	private static final long CANCEL_POLL_MS = 100;

	private CliProcessRunner() {
	}

	private static Consumer<StreamChunk> serialized(Consumer<StreamChunk> onChunk) {
		var lock = new Object();
		var terminated = new AtomicBoolean(false);
		return chunk -> {
			synchronized (lock) {
				if (terminated.get()) {
					return;
				}
				if (chunk.type() == ChunkType.ERROR || chunk.type() == ChunkType.DONE) {
					terminated.set(true);
				}
				onChunk.accept(chunk);
			}
		};
	}

	static void runSubcommand(
			AiProvider provider,
			String subcommand,
			String workDir,
			Consumer<StreamChunk> onChunk
	) {
		var argv = new ArrayList<String>();
		argv.add(provider.cliName);
		argv.add(subcommand);

		logger.info("run subcommand start={} subcommand={}", provider.cliName, subcommand);

		var process = createProcess(provider, argv, workDir);
		if (process == null) {
			onChunk.accept(StreamChunk.error(
					"Failed to start " + provider.cliName + " " + subcommand));
			return;
		}

		var safeChunk = serialized(onChunk);
		var state = new RunState();
		var stderrThread = drainStderr(provider, process, state);
		var watchdogThread = startWatchdog(provider, process, state, safeChunk);
		var cancelThread = watchForCancellation(provider, process, state);

		var outputBuf = new StringBuilder();
		try (var reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				state.lastOutputAt.set(System.currentTimeMillis());
				if (Thread.currentThread().isInterrupted()) {
					state.cancelled.set(true);
					terminateProcess(process);
					break;
				}
				if (line.isBlank()) {
					continue;
				}
				var normalizedLine = line.stripTrailing();
				if (isNoiseLine(normalizedLine.trim())) {
					continue;
				}
				if (outputBuf.length() < SUBCOMMAND_OUTPUT_BUFFER_LIMIT) {
					if (!outputBuf.isEmpty()) {
						outputBuf.append('\n');
					}
					var remaining = SUBCOMMAND_OUTPUT_BUFFER_LIMIT - outputBuf.length();
					outputBuf.append(normalizedLine, 0, Math.min(normalizedLine.length(), remaining));
				}
			}
		} catch (Exception ex) {
			logger.debug("Error reading stdout for {} {}: {}", provider.cliName, subcommand, ex.getMessage());
		}

		var exitCode = awaitExit(process, stderrThread, state);
		watchdogThread.interrupt();
		joinQuietly(watchdogThread);
		cancelThread.interrupt();
		joinQuietly(cancelThread);

		if (state.timedOut.get()) {
			return;
		}
		if (state.cancelled.get()) {
			safeChunk.accept(StreamChunk.done(null));
			return;
		}

		var output = outputBuf.toString().stripTrailing();
		var stderr = state.stderrBuf.toString().stripTrailing();

		if (exitCode != 0) {
			if (!stderr.isBlank()) {
				safeChunk.accept(StreamChunk.error(stderr));
			} else {
				safeChunk.accept(StreamChunk.error(
						provider.cliName + " " + subcommand + " exited with code " + exitCode));
			}
			return;
		}

		if (!output.isBlank()) {
			safeChunk.accept(StreamChunk.text(output));
		} else if (!stderr.isBlank()) {
			safeChunk.accept(StreamChunk.text(stderr));
		}

		safeChunk.accept(StreamChunk.done(null));
	}

	static void run(
			AiProvider provider,
			String prompt,
			String sessionId,
			String workDir,
			Consumer<StreamChunk> onChunk
	) {
		var argv = new ArrayList<String>();
		argv.add(provider.cliName);
		argv.addAll(provider.buildRunArgs(prompt, sessionId, workDir));

		var promptLength = prompt != null ? prompt.length() : 0;
		logger.info("run start={} promptLen={}", provider.cliName, promptLength);

		var process = createProcess(provider, argv, workDir);
		if (process == null) {
			onChunk.accept(StreamChunk.error(
					"Failed to start " + provider.cliName));
			return;
		}

		var safeChunk = serialized(onChunk);
		var state = new RunState();
		var stderrThread = drainStderr(provider, process, state);
		var watchdogThread = startWatchdog(provider, process, state, safeChunk);
		var cancelThread = watchForCancellation(provider, process, state);

		var lastSessionId = readStdout(provider, process, state, safeChunk);

		var exitCode = awaitExit(process, stderrThread, state);
		watchdogThread.interrupt();
		joinQuietly(watchdogThread);
		cancelThread.interrupt();
		joinQuietly(cancelThread);

		if (state.timedOut.get()) {
			return;
		}
		if (state.cancelled.get()) {
			safeChunk.accept(StreamChunk.done(lastSessionId));
			return;
		}
		finalizeRun(provider, exitCode, lastSessionId, state, safeChunk);
	}

	private static Process createProcess(AiProvider provider, List<String> argv, String workDir) {
		var command = wrapForShell(argv);
		try {
			var pb = new ProcessBuilder(command);
			pb.directory(new File(resolveWorkDir(workDir)));
			pb.redirectErrorStream(false);

			var process = pb.start();
			closeStdin(process);
			return process;
		} catch (Exception ex) {
			logger.warn("Failed to create process for {}: {}", provider.cliName, ex.getMessage());
			return null;
		}
	}

	private static void closeStdin(Process process) {
		try {
			process.getOutputStream().close();
		} catch (Exception ex) {
			logger.debug("Failed to close stdin: {}", ex.getMessage());
		}
	}

	private static Thread drainStderr(AiProvider provider, Process process, RunState state) {
		var thread = new Thread(() -> {
			try (var reader = new BufferedReader(
					new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.isBlank()) {
						continue;
					}
					if (isNoiseLine(line)) {
						continue;
					}
					state.lastOutputAt.set(System.currentTimeMillis());
					if (state.stderrBuf.length() < STDERR_BUFFER_LIMIT) {
						var remaining = STDERR_BUFFER_LIMIT - state.stderrBuf.length();
						if (line.length() + 1 <= remaining) {
							state.stderrBuf.append(line).append('\n');
						} else {
							state.stderrBuf.append(line, 0, Math.min(line.length(), remaining));
						}
					}
				}
			} catch (Exception ex) {
				if (state.timedOut.get() || state.cancelled.get()) {
					logger.debug("Stderr stream closed (cancelled/timed out) for {}: {}", provider.cliName, ex.getMessage());
				} else {
					logger.warn("Error reading stderr for {}: {}", provider.cliName, ex.getMessage());
				}
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
		var thread = new Thread(() -> {
			var timeout = provider.timeoutMs();
			while (process.isAlive()) {
				if (state.cancelled.get()) {
					return;
				}
				if (waitForExitOrInterrupt(process, WATCHDOG_POLL_MS)) {
					return;
				}
				if (state.cancelled.get()) {
					return;
				}
				var idleMs = System.currentTimeMillis() - state.lastOutputAt.get();
				if (idleMs > timeout && state.timedOut.compareAndSet(false, true)) {
					if (state.cancelled.get()) {
						return;
					}
					logger.warn("timeout name={} idleMs={}", provider.cliName, idleMs);
					terminateProcess(process);
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

	private static Thread watchForCancellation(AiProvider provider, Process process, RunState state) {
		var caller = Thread.currentThread();

		var thread = new Thread(() -> {
			while (process.isAlive()) {
				if (caller.isInterrupted()) {
					state.cancelled.set(true);
					logger.warn("cancel requested name={}", provider.cliName);
					terminateProcess(process);
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
		var plainTextBuf = new StringBuilder();

		try (var reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (state.timedOut.get()) {
					return lastSessionId;
				}
				state.lastOutputAt.set(System.currentTimeMillis());
				if (Thread.currentThread().isInterrupted()) {
					state.cancelled.set(true);
					terminateProcess(process);
					return null;
				}

				var trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				if (isNoiseLine(trimmed)) {
					continue;
				}

				var chunk = provider.parseLine(trimmed);
				if (chunk == null) {
					if (trimmed.startsWith("{") || state.sawStructuredOutput.get()) {
						continue;
					}
					var normalizedLine = line.stripTrailing();
					if (plainTextBuf.length() < PLAINTEXT_BUFFER_LIMIT) {
						var remaining = PLAINTEXT_BUFFER_LIMIT - plainTextBuf.length();
						if (normalizedLine.length() + 1 <= remaining) {
							plainTextBuf.append(normalizedLine).append('\n');
						} else {
							plainTextBuf.append(normalizedLine, 0, Math.min(normalizedLine.length(), remaining));
						}
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

				if (state.timedOut.get()) {
					return lastSessionId;
				}
				onChunk.accept(chunk);
				state.sawOutput.set(true);
			}
		} catch (Exception ex) {
			logger.debug("Error reading stdout for {}: {}", provider.cliName, ex.getMessage());
		}

		if (state.timedOut.get()) {
			return lastSessionId;
		}
		if (!state.sawOutput.get()) {
			var plainText = plainTextBuf.toString().stripTrailing();
			if (!plainText.isBlank()) {
				onChunk.accept(StreamChunk.text(plainText));
				state.sawOutput.set(true);
			}
		}

		return lastSessionId;
	}

	private static int awaitExit(Process process, Thread stderrThread, RunState state) {
		try {
			var code = process.waitFor();
			stderrThread.join(200);
			return code;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			state.cancelled.set(true);
			terminateProcess(process);
			joinQuietly(stderrThread);
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
		var stderr = state.stderrBuf.toString().stripTrailing();

		if (exitCode != 0) {
			if (!stderr.isBlank()) {
				onChunk.accept(StreamChunk.error(stderr));
			} else {
				onChunk.accept(StreamChunk.error(provider.cliName + " exited with code " + exitCode));
			}
			return;
		}

		if (!state.sawOutput.get() && !stderr.isBlank()) {
			onChunk.accept(StreamChunk.text(stderr));
			state.sawOutput.set(true);
		}

		onChunk.accept(StreamChunk.done(lastSessionId));
	}

	private static String resolveWorkDir(String workDir) {
		if (workDir != null && new File(workDir).isDirectory()) {
			return workDir;
		}
		if (workDir != null) {
			logger.warn("workDir does not exist, falling back to user.home: {}", workDir);
		}
		return System.getProperty("user.home");
	}

	private static void joinQuietly(Thread thread) {
		try {
			thread.join(200);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void terminateProcess(Process process) {
		try {
			process.toHandle().descendants().forEach(processHandle -> {
				if (processHandle.isAlive()) {
					processHandle.destroyForcibly();
				}
			});
		} catch (Exception ignored) {
			// best-effort child process cleanup
		}
		try {
			if (process.isAlive()) {
				process.destroyForcibly();
			}
		} catch (Exception ignored) {
			// best-effort process cleanup
		}
		closeProcessStreams(process);
	}

	private static void closeProcessStreams(Process process) {
		closeQuietly(process.getInputStream());
		closeQuietly(process.getErrorStream());
		closeQuietly(process.getOutputStream());
	}

	private static void closeQuietly(java.io.Closeable closeable) {
		try {
			closeable.close();
		} catch (Exception ignored) {
			// no-op
		}
	}

	// On Windows, ProcessBuilder passes argv directly to CreateProcess without a shell,
	// so no shell-injection risk exists and no escaping is needed.
	private static List<String> wrapForShell(List<String> argv) {
		if (OsUtils.isWindows()) {
			return argv;
		}

		var shellCommand = new StringBuilder();
		for (var argumentIndex = 0; argumentIndex < argv.size(); argumentIndex++) {
			if (argumentIndex > 0) {
				shellCommand.append(' ');
			}
			shellCommand.append('\'').append(argv.get(argumentIndex).replace("'", "'\"'\"'")).append('\'');
		}
		var bash = new File("/bin/bash");
		if (bash.canExecute()) {
			return List.of(bash.getAbsolutePath(), "-l", "-c", shellCommand.toString());
		}
		return List.of("/bin/sh", "-c", shellCommand.toString());
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
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return true;
		}
	}

	private static final class RunState {
		final AtomicBoolean timedOut = new AtomicBoolean(false);
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final AtomicBoolean sawOutput = new AtomicBoolean(false);
		final AtomicBoolean sawStructuredOutput = new AtomicBoolean(false);
		final AtomicLong lastOutputAt = new AtomicLong(System.currentTimeMillis());
		final StringBuffer stderrBuf = new StringBuffer();
	}
}
