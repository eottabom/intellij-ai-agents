package io.github.eottabom.aiagents.toolwindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import io.github.eottabom.aiagents.refs.ProjectRefsCollector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java ↔ JavaScript 브릿지 (AiAgentPanel 전용 내부 구현)
 * JBCefJSQuery는 loadURL 호출 전에 반드시 생성해야 함
 */
class JsBridge implements Disposable {

    private static final int EXECUTOR_POOL_SIZE = 4;
    private static final long CACHE_INVALIDATION_DELAY_MS = 2000;

    private final JBCefBrowser browser;
    private final Project project;
    private final SessionStore sessionStore;
    private final JsBridgeClientNotifier notifier;
    private final ExecutorService executor = Executors.newFixedThreadPool(EXECUTOR_POOL_SIZE);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final AtomicReference<String> projectRefsCache = new AtomicReference<>();
    private final AtomicBoolean projectRefsScanInProgress = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> pendingInvalidation = new AtomicReference<>();
    private final JBCefJSQuery jsQuery;
    private final ChatCommandHandler commandHandler;

    JsBridge(JBCefBrowser browser, Project project) {
        this.browser = browser;
        this.project = project;
        this.sessionStore = new SessionStore(project);
        this.notifier = new JsBridgeClientNotifier(browser);
        this.commandHandler = new ChatCommandHandler(notifier, sessionStore, executor, runningTasks);
        jsQuery = JBCefJSQuery.create((JBCefBrowserBase) browser);
        jsQuery.addHandler(request -> {
            handleMessage(request);
            return null;
        });

        var connection = project.getMessageBus().connect(this);
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                invalidateProjectRefsCacheDebounced();
            }
        });
    }

    void inject() {
        var script = """
                window.__bridge = { send: function(json) { %s } };
                window.dispatchEvent(new Event('bridgeReady'));
                """.formatted(jsQuery.inject("json"));
        browser.getCefBrowser().executeJavaScript(script, "", 0);
    }

    private void handleMessage(String json) {
        try {
            var bridgeMessage = BridgeMessage.fromJson(json);
            var messageType = BridgeMessageType.fromString(bridgeMessage.type());
            switch (messageType) {
                case CHAT               -> commandHandler.handleChat(bridgeMessage, project.getBasePath());
                case CANCEL             -> handleCancel(bridgeMessage);
                case GET_SESSION        -> handleGetSession(bridgeMessage);
                case CLEAR_SESSION      -> handleClearSession(bridgeMessage);
                case CLEAR_ALL_SESSIONS -> sessionStore.clearAll();
                case GET_PROJECT_REFS   -> sendProjectRefs();
                case UNKNOWN            -> notifier.sendError(null, "Unknown message type: " + bridgeMessage.type());
            }
        } catch (Exception ex) {
            var errorMessage = ex.getMessage();
            if (errorMessage == null) {
                errorMessage = "Unknown error";
            }
            notifier.sendError(null, errorMessage);
        }
    }

    private void handleCancel(BridgeMessage msg) {
        var providerName = resolveProvider(msg.cli());
        if (providerName == null) {
            return;
        }
        commandHandler.cancelTask(providerName);
        notifier.sendDone(providerName);
    }

    private void handleGetSession(BridgeMessage msg) {
        var providerName = resolveProvider(msg.cli());
        if (providerName == null) {
            return;
        }
        var sessionId = sessionStore.get(providerName);
        notifier.sendSession(providerName, sessionId);
    }

    private void handleClearSession(BridgeMessage msg) {
        var providerName = resolveProvider(msg.cli());
        if (providerName == null) {
            return;
        }
        sessionStore.clear(providerName);
        notifier.sendSessionCleared(providerName);
    }

    private String resolveProvider(String cli) {
        var providerName = commandHandler.resolveProviderName(cli);
        if (providerName == null) {
            notifier.sendError(null, "Invalid provider. Use one of: claude, gemini, codex.");
        }
        return providerName;
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
            executor.submit(this::collectAndNotifyProjectRefs);
        }
    }

    private void invalidateProjectRefsCacheDebounced() {
        var prev = pendingInvalidation.getAndSet(
                scheduler.schedule(() -> projectRefsCache.set(null), CACHE_INVALIDATION_DELAY_MS, TimeUnit.MILLISECONDS)
        );
        if (prev != null) {
            prev.cancel(false);
        }
    }

    private void collectAndNotifyProjectRefs() {
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
        scheduler.shutdownNow();
        executor.shutdownNow();
        jsQuery.dispose();
    }
}
