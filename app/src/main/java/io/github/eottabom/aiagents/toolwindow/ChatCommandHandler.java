package io.github.eottabom.aiagents.toolwindow;

import io.github.eottabom.aiagents.providers.AiProvider;
import io.github.eottabom.aiagents.providers.StreamChunk;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

/**
 * 채팅 명령 처리 로직 (JsBridge에서 분리)
 */
class ChatCommandHandler {

    private static final Pattern AGENT_PREFIX_PATTERN =
            Pattern.compile("^\\s*@(?<cli>claude|gemini|codex)\\b\\s*(?<prompt>[\\s\\S]*)$", Pattern.CASE_INSENSITIVE);

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

    void handleChat(BridgeMessage msg, String workDir) {
        ParsedCommand parsed = parseCommand(msg.cli(), msg.prompt());
        String providerName = normalizeProviderName(parsed.providerName());
        if (providerName == null) {
            notifier.sendError(null, "Invalid provider. Use one of: claude, gemini, codex.");
            return;
        }
        var rawPrompt = "";
        if (parsed.prompt() != null) {
            rawPrompt = parsed.prompt().trim();
        }
        if ("/doctor".equals(rawPrompt)) {
            runDoctor(providerName, workDir);
            return;
        }

        var mode = "normal";
        if (msg.mode() != null) {
            mode = msg.mode().trim().toLowerCase(Locale.ROOT);
        }

        String prompt;
        if ("plan".equals(mode)) {
            prompt = wrapAsPlan(parsed.prompt());
        } else {
            prompt = parsed.prompt();
        }

        if (prompt == null || prompt.isBlank()) {
            notifier.sendError(null, "Prompt is empty.");
            return;
        }

        cancelRunning(providerName);
        final String finalPrompt = prompt;
        final String sessionId = sessionStore.get(providerName);
        runningTasks.put(providerName, executor.submit(() -> {
            AiProvider provider = AiProvider.fromName(providerName);
            if (provider == null) {
                notifier.sendError(providerName, providerName + " CLI is not installed.");
                return;
            }
            provider.run(
                    finalPrompt,
                    sessionId,
                    workDir,
                    chunk -> handleChunk(providerName, chunk));
        }));
    }

    void runDoctor(String providerName, String workDir) {
        cancelRunning(providerName);

        final DoctorRunState state = new DoctorRunState();

        runningTasks.put(providerName, executor.submit(() -> {
            AiProvider provider = AiProvider.fromName(providerName);
            if (provider == null) {
                notifier.sendError(providerName, providerName + " CLI is not installed.");
                return;
            }

            provider.run("/doctor", null, workDir, chunk -> handleDoctorChunk(providerName, chunk, state));
        }));
    }

    void handleChunk(String providerName, StreamChunk chunk) {
        switch (chunk.type()) {
            case TEXT -> notifier.sendChunk(providerName, chunk.content());
            case TOOL_USE -> notifier.sendProgress(providerName, "\uD83D\uDD27 " + chunk.toolName());
            case DONE -> {
                if (chunk.sessionId() != null) {
                    sessionStore.save(providerName, chunk.sessionId());
                }
                runningTasks.remove(providerName);
                notifier.sendDone(providerName);
            }
            case ERROR -> {
                runningTasks.remove(providerName);
                notifier.sendError(providerName, chunk.content());
            }
        }
    }

    void handleDoctorChunk(String providerName, StreamChunk chunk, DoctorRunState state) {
        switch (chunk.type()) {
            case TEXT -> {
                if (!chunk.content().isBlank()) {
                    if (!state.output.isEmpty()) {
                        state.output.append('\n');
                    }
                    state.output.append(chunk.content().trim());
                }
            }
            case TOOL_USE -> {
                // /doctor는 성공/실패 요약만 노출
            }
            case ERROR -> {
                runningTasks.remove(providerName);
                var details = state.output.toString().trim();
                if (!details.isBlank()) {
                    notifier.sendChunk(providerName, details);
                }
                notifier.sendError(providerName, chunk.content());
            }
            case DONE -> {
                runningTasks.remove(providerName);
                var details = state.output.toString().trim();
                if (details.isBlank()) {
                    notifier.sendChunk(providerName, providerName.toUpperCase() + " CLI 사용 가능");
                } else if (looksHealthyDoctorOutput(details)) {
                    notifier.sendChunk(providerName, providerName.toUpperCase() + " CLI 사용 가능");
                } else {
                    notifier.sendChunk(providerName, details);
                }
                notifier.sendDone(providerName);
            }
        }
    }

    void cancelRunning(String providerName) {
        var task = runningTasks.remove(providerName);
        if (task != null) {
            task.cancel(true);
        }
    }

    ParsedCommand parseCommand(String fallbackCli, String rawPrompt) {
        var normalizedFallbackCli = normalizeProviderName(fallbackCli);
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

    String normalizeProviderName(String providerName) {
        if (providerName == null) {
            return null;
        }
        var normalized = providerName.trim().toLowerCase(Locale.ROOT);
        if (AiProvider.fromName(normalized) != null) {
            return normalized;
        }
        return null;
    }

    String wrapAsPlan(String prompt) {
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

    private boolean looksHealthyDoctorOutput(String output) {
        var lower = output.toLowerCase(Locale.ROOT);
        if (lower.contains("error") || lower.contains("failed") || lower.contains("not found")) {
            return false;
        }
        return lower.contains("ok")
                || lower.contains("ready")
                || lower.contains("healthy")
                || lower.contains("authenticated")
                || lower.contains("logged in")
                || lower.contains("installed")
                || lower.contains("doctor");
    }

    record ParsedCommand(String providerName, String prompt) {}

    static final class DoctorRunState {
        final StringBuilder output = new StringBuilder();
    }
}
