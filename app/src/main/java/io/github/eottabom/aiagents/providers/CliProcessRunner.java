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

		var state = new RunState();
		var stderrThread = drainStderr(provider, process, state);
		var watchdogThread = startWatchdog(provider, process, state, onChunk);
		var cancelThread = watchForCancellation(provider, process, state);

		var outputBuf = new StringBuilder();
		try (var reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				state.lastOutputAt.set(System.currentTimeMillis());
				if (Thread.currentThread().isInterrupted()) {
					terminateProcess(process);
					break;
				}
				var trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				if (isNoiseLine(trimmed)) {
					continue;
				}
				if (outputBuf.length() < SUBCOMMAND_OUTPUT_BUFFER_LIMIT) {
					if (!outputBuf.isEmpty()) {
						outputBuf.append('\n');
					}
					outputBuf.append(trimmed);
				}
			}
		} catch (Exception ex) {
			logger.debug("Error reading stdout for {} {}: {}", provider.cliName, subcommand, ex.getMessage());
		}

		var exitCode = awaitExit(process, stderrThread);
		watchdogThread.interrupt();
		joinQuietly(watchdogThread);
		cancelThread.interrupt();
		joinQuietly(cancelThread);

		if (state.timedOut.get() || state.cancelled.get()) {
			return;
		}

		var output = outputBuf.toString().trim();
		var stderr = state.stderrBuf.toString().trim();

		if (exitCode != 0) {
			if (!stderr.isBlank()) {
				onChunk.accept(StreamChunk.error(stderr));
			} else {
				onChunk.accept(StreamChunk.error(
						provider.cliName + " " + subcommand + " exited with code " + exitCode));
			}
			return;
		}

		if (!output.isBlank()) {
			onChunk.accept(StreamChunk.text(output));
		} else if (!stderr.isBlank()) {
			onChunk.accept(StreamChunk.text(stderr));
		}

		onChunk.accept(StreamChunk.done(null));
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

		var state = new RunState();
		var stderrThread = drainStderr(provider, process, state);
		var watchdogThread = startWatchdog(provider, process, state, onChunk);
		var cancelThread = watchForCancellation(provider, process, state);

		var lastSessionId = readStdout(provider, process, state, onChunk);

		var exitCode = awaitExit(process, stderrThread);
		watchdogThread.interrupt();
		joinQuietly(watchdogThread);
		cancelThread.interrupt();
		joinQuietly(cancelThread);

		if (!state.timedOut.get() && !state.cancelled.get()) {
			finalizeRun(provider, exitCode, lastSessionId, state, onChunk);
		}
	}

	private static Process createProcess(AiProvider provider, List<String> argv, String workDir) {
		var command = wrapForShell(argv);
		try {
			var pb = new ProcessBuilder(command);
			pb.directory(new File(resolveWorkDir(workDir)));
			pb.environment().putAll(System.getenv());
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
						state.stderrBuf.append(line).append('\n');
					}
				}
			} catch (Exception ex) {
				logger.warn("Error reading stderr for {}: {}", provider.cliName, ex.getMessage());
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
				if (waitForExitOrInterrupt(process, WATCHDOG_POLL_MS)) {
					return;
				}
				var idleMs = System.currentTimeMillis() - state.lastOutputAt.get();
				if (idleMs > timeout && state.timedOut.compareAndSet(false, true)) {
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
			var plainText = plainTextBuf.toString().trim();
			if (!plainText.isBlank()) {
				onChunk.accept(StreamChunk.text(plainText));
				state.sawOutput.set(true);
			}
		}

		return lastSessionId;
	}

	private static int awaitExit(Process process, Thread stderrThread) {
		try {
			var code = process.waitFor();
			stderrThread.join(200);
			return code;
		} catch (InterruptedException ex) {
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
		var stderr = state.stderrBuf.toString().trim();

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
			thread.join((long) 200);
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
		return List.of("/bin/bash", "-l", "-c", shellCommand.toString());
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
