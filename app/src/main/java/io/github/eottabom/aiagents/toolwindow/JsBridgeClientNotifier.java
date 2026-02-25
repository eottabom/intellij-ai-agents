package io.github.eottabom.aiagents.toolwindow;

import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.jcef.JBCefBrowser;

import java.util.List;

record JsBridgeClientNotifier(JBCefBrowser browser) {

	private static final Gson GSON = new Gson();

	void sendSession(String cli, String sessionId) {
		js("window.__onSession && window.__onSession('%s','%s')"
				.formatted(cli, sessionId != null ? sessionId : ""));
	}

	void sendSessionCleared(String cli) {
		js("window.__onSessionCleared && window.__onSessionCleared('%s')".formatted(cli));
	}

	void sendInstalledProviders(List<String> providers) {
		js("window.__onInstalledClis && window.__onInstalledClis(%s)".formatted(GSON.toJson(providers)));
	}

	void sendProjectRefs(String refsJson) {
		js("window.__onProjectRefs && window.__onProjectRefs(%s)".formatted(refsJson));
	}

	void sendChunk(String cli, String text) {
		String escaped = text.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$");
		js("window.__onChunk && window.__onChunk('%s', `%s`)".formatted(esc(cli), escaped));
	}

	void sendProgress(String cli, String text) {
		js("window.__onProgress && window.__onProgress('%s', '%s')".formatted(esc(cli), esc(text)));
	}

	void sendDone(String cli) {
		js("window.__onDone && window.__onDone('%s')".formatted(esc(cli)));
	}

	void sendError(String cli, String error) {
		String msg = normalize(cli, error);
		String escaped = esc(msg).replace("\n", " ");
		if (cli == null || cli.isBlank()) {
			js("window.__onError && window.__onError('%s')".formatted(escaped));
			return;
		}
		js("window.__onError && window.__onError('%s', '%s')".formatted(esc(cli), escaped));
	}

	private void js(String script) {
		ApplicationManager.getApplication().invokeLater(() ->
				browser.getCefBrowser().executeJavaScript(script, "", 0));
	}

	private String normalize(String cli, String error) {
		String raw = error == null ? "Unknown error" : error.trim();
		if (raw.isBlank()) {
			return "Unknown error";
		}

		String lower = raw.toLowerCase();
		if (lower.contains("produced no output for")) {
			return "Request stopped due to inactivity timeout. Retry with a simpler prompt.";
		}
		if (lower.contains("require approval")) {
			return "Request stopped: tool call required approval. Try a simpler request.";
		}
		if ("gemini".equals(cli)
				&& (lower.contains("login") || lower.contains("auth") || lower.contains("unauthorized"))) {
			return "Gemini CLI: run `gemini` in your terminal to complete auth setup, then retry.";
		}
		if (("claude".equals(cli) || "codex".equals(cli))
				&& (lower.contains("login") || lower.contains("not authenticated"))) {
			return cli + " CLI: run `" + cli + "` in your terminal to login, then retry.";
		}
		return raw.length() > 500 ? raw.substring(0, 500) + "..." : raw;
	}

	private String esc(String text) {
		return text.replace("\\", "\\\\").replace("'", "\\'");
	}
}
