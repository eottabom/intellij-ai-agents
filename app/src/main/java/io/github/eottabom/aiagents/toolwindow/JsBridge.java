package io.github.eottabom.aiagents.toolwindow;

import io.github.eottabom.aiagents.providers.AiProvider;
import io.github.eottabom.aiagents.providers.StreamChunk;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java ↔ JavaScript 브릿지 (AiAgentPanel 전용 내부 구현)
 * JBCefJSQuery는 loadURL 호출 전에 반드시 생성해야 함
 */
class JsBridge implements Disposable {

    private static final Pattern AGENT_PREFIX_PATTERN =
            Pattern.compile("^\\s*@(?<cli>claude|gemini|codex)\\b\\s*(?<prompt>[\\s\\S]*)$", Pattern.CASE_INSENSITIVE);
    private final JBCefBrowser browser;
    private final Project project;
    private final SessionStore sessionStore;
    private final JsBridgeClientNotifier notifier;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final AtomicReference<String> projectRefsCache = new AtomicReference<>();
    private final JBCefJSQuery jsQuery;

    JsBridge(JBCefBrowser browser, Project project) {
        this.browser = browser;
        this.project = project;
        this.sessionStore = new SessionStore();
        this.notifier = new JsBridgeClientNotifier(browser);
        jsQuery = JBCefJSQuery.create((JBCefBrowserBase) browser);
        jsQuery.addHandler(request -> {
            handleMessage(request);
            return null;
        });
    }

    void inject() {
        String script = """
                window.__bridge = { send: function(json) { %s } };
                window.dispatchEvent(new Event('bridgeReady'));
                """.formatted(jsQuery.inject("json"));
        browser.getCefBrowser().executeJavaScript(script, "", 0);
    }

    private void handleMessage(String json) {
        try {
            BridgeMessage msg = BridgeMessage.fromJson(json);
            switch (msg.type()) {
                case "chat"             -> handleChat(msg);
                case "cancel"           -> handleCancel(msg);
                case "getSession"       -> handleGetSession(msg);
                case "clearSession"     -> handleClearSession(msg);
                case "clearAllSessions" -> sessionStore.clearAll();
                case "getProjectRefs"   -> sendProjectRefs();
            }
        } catch (Exception e) {
            notifier.sendError(null, e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    private void handleChat(BridgeMessage msg) {
        ParsedCommand parsed = parseCommand(msg.cli(), msg.prompt());
        String providerName = parsed.providerName();
        String rawPrompt = parsed.prompt() != null ? parsed.prompt().trim() : "";
        if ("/doctor".equals(rawPrompt)) {
            runDoctor(providerName);
            return;
        }

        String mode = msg.mode() != null ? msg.mode().trim().toLowerCase() : "normal";
        String prompt = "plan".equals(mode) ? wrapAsPlan(parsed.prompt()) : parsed.prompt();

        if (prompt == null || prompt.isBlank()) {
            notifier.sendError(null, "Prompt is empty.");
            return;
        }

        cancelRunning(providerName);
        final String finalPrompt = prompt;
        final String sessionId = sessionStore.get(providerName);
        final String workDir = project.getBasePath();
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

    private void runDoctor(String providerName) {
        cancelRunning(providerName);

        final String workDir = project.getBasePath();
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

    private void handleDoctorChunk(String providerName, StreamChunk chunk, DoctorRunState state) {
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
                String details = state.output.toString().trim();
                if (!details.isBlank()) {
                    notifier.sendChunk(providerName, details);
                }
                notifier.sendError(providerName, chunk.content());
            }
            case DONE -> {
                runningTasks.remove(providerName);
                String details = state.output.toString().trim();
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

    private boolean looksHealthyDoctorOutput(String output) {
        String lower = output.toLowerCase();
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

    private void handleChunk(String providerName, StreamChunk chunk) {
        switch (chunk.type()) {
            case TEXT -> notifier.sendChunk(providerName, chunk.content());
            case TOOL_USE -> notifier.sendProgress(providerName, "🔧 " + chunk.toolName());
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

    private void handleCancel(BridgeMessage msg) {
        cancelRunning(msg.cli());
        notifier.sendDone(msg.cli());
    }

    private void cancelRunning(String providerName) {
        Future<?> task = runningTasks.remove(providerName);
        if (task != null) {
            task.cancel(true);
        }
    }

    private void handleGetSession(BridgeMessage msg) {
        String id = sessionStore.get(msg.cli());
        notifier.sendSession(msg.cli(), id);
    }

    private void handleClearSession(BridgeMessage msg) {
        sessionStore.clear(msg.cli());
        notifier.sendSessionCleared(msg.cli());
    }

    void sendInstalledProviders(List<String> providers) {
        notifier.sendInstalledProviders(providers);
    }

    void sendProjectRefs() {
        String cached = projectRefsCache.get();
        if (cached != null) {
            notifier.sendProjectRefs(cached);
            return;
        }
        executor.submit(this::scanAndSendProjectRefs);
    }

    private void scanAndSendProjectRefs() {
        String json = ProjectRefsCollector.collect(project);
        if (json == null || json.isBlank()) {
            return;
        }
        projectRefsCache.set(json);
        notifier.sendProjectRefs(json);
    }

    @Override
    public void dispose() {
        executor.shutdownNow();
    }

    // Utilities

    private ParsedCommand parseCommand(String fallbackCli, String rawPrompt) {
        if (rawPrompt == null) {
            return new ParsedCommand(fallbackCli, "");
        }
        Matcher m = AGENT_PREFIX_PATTERN.matcher(rawPrompt);
        if (!m.matches()) {
            return new ParsedCommand(fallbackCli, rawPrompt);
        }
        String cli = m.group("cli").toLowerCase();
        String prompt = m.group("prompt");
        return new ParsedCommand(cli, prompt != null ? prompt.trim() : "");
    }

    private String wrapAsPlan(String prompt) {
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

    private record ParsedCommand(String providerName, String prompt) {}

    private static final class DoctorRunState {
        final StringBuilder output = new StringBuilder();
    }
}
