package io.github.eottabom.aiagents.toolwindow;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java ↔ JavaScript 브릿지 (AiAgentPanel 전용 내부 구현)
 * JBCefJSQuery는 loadURL 호출 전에 반드시 생성해야 함
 */
class JsBridge implements Disposable {

    private final JBCefBrowser browser;
    private final Project project;
    private final SessionStore sessionStore;
    private final JsBridgeClientNotifier notifier;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final AtomicReference<String> projectRefsCache = new AtomicReference<>();
    private final AtomicBoolean projectRefsScanInProgress = new AtomicBoolean(false);
    private final JBCefJSQuery jsQuery;
    private final ChatCommandHandler commandHandler;

    JsBridge(JBCefBrowser browser, Project project) {
        this.browser = browser;
        this.project = project;
        this.sessionStore = new SessionStore();
        this.notifier = new JsBridgeClientNotifier(browser);
        this.commandHandler = new ChatCommandHandler(notifier, sessionStore, executor, runningTasks);
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
            var bridgeMessage = BridgeMessage.fromJson(json);
            switch (bridgeMessage.type()) {
                case "chat"             -> commandHandler.handleChat(bridgeMessage, project.getBasePath());
                case "cancel"           -> handleCancel(bridgeMessage);
                case "getSession"       -> handleGetSession(bridgeMessage);
                case "clearSession"     -> handleClearSession(bridgeMessage);
                case "clearAllSessions" -> sessionStore.clearAll();
                case "getProjectRefs"   -> sendProjectRefs();
            }
        } catch (Exception bridgeException) {
            var errorMessage = bridgeException.getMessage();
            if (errorMessage == null) {
                errorMessage = "Unknown error";
            }
            notifier.sendError(null, errorMessage);
        }
    }

    private void handleCancel(BridgeMessage msg) {
        String providerName = commandHandler.normalizeProviderName(msg.cli());
        if (providerName == null) {
            notifier.sendError(null, "Invalid provider. Use one of: claude, gemini, codex.");
            return;
        }
        commandHandler.cancelRunning(providerName);
        notifier.sendDone(providerName);
    }

    private void handleGetSession(BridgeMessage msg) {
        String providerName = commandHandler.normalizeProviderName(msg.cli());
        if (providerName == null) {
            notifier.sendError(null, "Invalid provider. Use one of: claude, gemini, codex.");
            return;
        }
        var sessionId = sessionStore.get(providerName);
        notifier.sendSession(providerName, sessionId);
    }

    private void handleClearSession(BridgeMessage msg) {
        String providerName = commandHandler.normalizeProviderName(msg.cli());
        if (providerName == null) {
            notifier.sendError(null, "Invalid provider. Use one of: claude, gemini, codex.");
            return;
        }
        sessionStore.clear(providerName);
        notifier.sendSessionCleared(providerName);
    }

    void sendInstalledProviders(List<String> providers) {
        notifier.sendInstalledProviders(providers);
    }

    void sendProjectRefs() {
        var cachedRefsJson = projectRefsCache.get();
        if (cachedRefsJson != null) {
            notifier.sendProjectRefs(cachedRefsJson);
            return;
        }
        if (projectRefsScanInProgress.compareAndSet(false, true)) {
            executor.submit(this::scanAndSendProjectRefs);
        }
    }

    private void scanAndSendProjectRefs() {
        try {
            var projectRefsJson = ProjectRefsCollector.collect(project);
            if (projectRefsJson == null || projectRefsJson.isBlank()) {
                return;
            }
            projectRefsCache.set(projectRefsJson);
            notifier.sendProjectRefs(projectRefsJson);
        } finally {
            projectRefsScanInProgress.set(false);
        }
    }

    @Override
    public void dispose() {
        executor.shutdownNow();
        jsQuery.dispose();
    }
}
