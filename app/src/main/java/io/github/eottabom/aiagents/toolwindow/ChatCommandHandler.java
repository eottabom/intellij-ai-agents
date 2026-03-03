package io.github.eottabom.aiagents.toolwindow;

import io.github.eottabom.aiagents.providers.AiProvider;
import io.github.eottabom.aiagents.providers.StreamChunk;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 채팅 명령 처리 로직 (JsBridge에서 분리)
 */
class ChatCommandHandler {

    private final JsBridgeClientNotifier notifier;
    private final SessionStore sessionStore;
    private final ExecutorService executor;
    private final Map<String, Future<?>> runningTasks;

    ChatCommandHandler(
            JsBridgeClientNotifier notifier,
            SessionStore sessionStore,
            ExecutorService executor,
            Map<String, Future<?>> runningTasks
    ) {
        this.notifier = notifier;
        this.sessionStore = sessionStore;
        this.executor = executor;
        this.runningTasks = runningTasks;
    }

    private static final Pattern AGENT_PREFIX_PATTERN =
            Pattern.compile("^\\s*@(?<cli>claude|gemini|codex)\\b\\s*(?<prompt>[\\s\\S]*)$", Pattern.CASE_INSENSITIVE);

    void handleChat(BridgeMessage msg, String workDir) {
        var command = resolveCommand(msg);
        if (command == null) {
            return;
        }
        if (command.isDoctor()) {
            dispatchDoctor(command.providerName(), workDir);
            return;
        }
        dispatchChat(command, msg.mode(), workDir);
    }

    private ResolvedCommand resolveCommand(BridgeMessage msg) {
        var parsed = parseCommand(msg.cli(), msg.prompt());
        var providerName = resolveProviderName(parsed.providerName());
        if (providerName == null) {
            notifier.sendError(null, "Invalid provider. Use one of: claude, gemini, codex.");
            return null;
        }
        return new ResolvedCommand(providerName, parsed.prompt());
    }

    private void dispatchChat(ResolvedCommand command, String mode, String workDir) {
        var chatMode = ChatMode.fromString(mode);

        var prompt = (chatMode == ChatMode.PLAN)
                ? wrapAsPlan(command.prompt())
                : command.prompt();

        if (prompt == null || prompt.isBlank()) {
            notifier.sendError(null, "Prompt is empty.");
            return;
        }

        submitProviderTask(command.providerName(), provider -> {
            var sessionId = sessionStore.get(command.providerName());
            provider.run(prompt, sessionId, workDir,
                    chunk -> onStreamChunk(command.providerName(), chunk));
        });
    }

    void dispatchDoctor(String providerName, String workDir) {
        var state = new DoctorOutputBuffer();
        submitProviderTask(providerName, provider ->
                provider.runDoctor(workDir, chunk -> onDoctorChunk(providerName, chunk, state)));
    }

    private void submitProviderTask(String providerName, Consumer<AiProvider> action) {
        synchronized (runningTasks) {
            cancelTask(providerName);
            runningTasks.put(providerName, executor.submit(() -> {
                var provider = AiProvider.fromName(providerName);
                if (provider.isEmpty()) {
                    removeRunningTask(providerName);
                    notifier.sendError(providerName, providerName + " CLI is not installed.");
                    return;
                }
                try {
                    action.accept(provider.get());
                } catch (Exception exception) {
                    removeRunningTask(providerName);
                    var message = exception.getMessage();
                    if (message == null || message.isBlank()) {
                        message = "Unknown error";
                    }
                    notifier.sendError(providerName, "Failed to execute provider: " + message);
                    return;
                }
                if (removeRunningTaskIfPresent(providerName)) {
                    notifier.sendDone(providerName);
                }
            }));
        }
    }

    private void removeRunningTask(String providerName) {
        synchronized (runningTasks) {
            runningTasks.remove(providerName);
        }
    }

    private boolean removeRunningTaskIfPresent(String providerName) {
        synchronized (runningTasks) {
            return runningTasks.remove(providerName) != null;
        }
    }

    void onStreamChunk(String providerName, StreamChunk chunk) {
        switch (chunk.type()) {
            case TEXT -> notifier.sendChunk(providerName, chunk.content());
            case TOOL_USE -> notifier.sendProgress(providerName, "\uD83D\uDD27 " + chunk.toolName());
            case DONE -> {
                if (chunk.sessionId() != null) {
                    sessionStore.save(providerName, chunk.sessionId());
                }
                synchronized (runningTasks) {
                    runningTasks.remove(providerName);
                }
                notifier.sendDone(providerName);
            }
            case ERROR -> {
                synchronized (runningTasks) {
                    runningTasks.remove(providerName);
                }
                notifier.sendError(providerName, chunk.content());
            }
        }
    }

    void onDoctorChunk(String providerName, StreamChunk chunk, DoctorOutputBuffer buffer) {
        switch (chunk.type()) {
            case TEXT -> {
                if (!chunk.content().isBlank()) {
                    if (!buffer.output.isEmpty()) {
                        buffer.output.append('\n');
                    }
                    buffer.output.append(chunk.content().trim());
                }
            }
            case TOOL_USE -> { /* doctor는 성공/실패 요약만 노출 */ }
            case ERROR -> {
                synchronized (runningTasks) {
                    runningTasks.remove(providerName);
                }
                var details = buffer.output.toString().trim();
                if (!details.isBlank()) {
                    notifier.sendChunk(providerName, details);
                }
                notifier.sendError(providerName, chunk.content());
            }
            case DONE -> {
                synchronized (runningTasks) {
                    runningTasks.remove(providerName);
                }
                var version = buffer.output.toString().trim();
                var label = providerName.toUpperCase(Locale.ROOT) + " CLI 사용 가능";
                if (!version.isBlank()) {
                    label += " (v" + version + ")";
                }
                notifier.sendChunk(providerName, label);
                notifier.sendDone(providerName);
            }
        }
    }

    void cancelTask(String providerName) {
        synchronized (runningTasks) {
            var task = runningTasks.remove(providerName);
            if (task != null) {
                task.cancel(true);
            }
        }
    }

    ParsedCommand parseCommand(String fallbackCli, String rawPrompt) {
        var normalizedFallbackCli = resolveProviderName(fallbackCli);
        if (rawPrompt == null) {
            return new ParsedCommand(normalizedFallbackCli, "");
        }
        var commandMatcher = AGENT_PREFIX_PATTERN.matcher(rawPrompt);
        if (!commandMatcher.matches()) {
            return new ParsedCommand(normalizedFallbackCli, rawPrompt);
        }
        var commandCli = commandMatcher.group("cli").toLowerCase(Locale.ROOT);
        var prompt = commandMatcher.group("prompt");
        if (prompt == null) {
            return new ParsedCommand(commandCli, "");
        }
        return new ParsedCommand(commandCli, prompt.trim());
    }

    String resolveProviderName(String providerName) {
        if (providerName == null) {
            return null;
        }
        var normalized = providerName.trim().toLowerCase(Locale.ROOT);
        if (AiProvider.fromName(normalized).isPresent()) {
            return normalized;
        }
        return null;
    }

    static String wrapAsPlan(String prompt) {
        if (prompt == null) {
            return "";
        }
        return """
                Plan mode is enabled.
                Respond with a concrete implementation plan first.
                Do not claim to have executed changes or commands.

                User request:
                %s
                """.formatted(prompt);
    }

    record ParsedCommand(String providerName, String prompt) {}

    private record ResolvedCommand(String providerName, String prompt) {
        boolean isDoctor() {
            return "/doctor".equals(prompt != null ? prompt.trim() : "");
        }
    }

    static final class DoctorOutputBuffer {
        final StringBuilder output = new StringBuilder();
    }
}
