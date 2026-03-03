package io.github.eottabom.aiagents.toolwindow;

import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.jcef.JBCefBrowser;

import java.util.List;
import java.util.Locale;

/**
 * JS 브릿지 콜백 호출 전용 객체.
 * <p>
 * 단순 데이터 캐리어가 아니라 호출/정규화 로직을 가진 동작 객체이므로,
 * record로 축약하지 않고 class로 유지한다.
 */
class JsBridgeClientNotifier {

	private static final Gson GSON = new Gson();

	private final JBCefBrowser browser;

	JsBridgeClientNotifier(JBCefBrowser browser) {
		this.browser = browser;
	}

	void sendSession(String cli, String sessionId) {
		var resolvedSessionId = "";
		if (sessionId != null) {
			resolvedSessionId = sessionId;
		}
		js("window.__onSession && window.__onSession(%s,%s)"
				.formatted(GSON.toJson(cli), GSON.toJson(resolvedSessionId)));
	}

	void sendSessionCleared(String cli) {
		js("window.__onSessionCleared && window.__onSessionCleared(%s)".formatted(GSON.toJson(cli)));
	}

	void sendInstalledProviders(List<String> providers) {
		js("window.__onInstalledClis && window.__onInstalledClis(%s)".formatted(GSON.toJson(providers)));
	}

	void sendProjectRefs(String refsJson) {
		js("window.__onProjectRefs && window.__onProjectRefs(JSON.parse(%s))"
				.formatted(GSON.toJson(refsJson)));
	}

	void sendChunk(String cli, String text) {
		js("window.__onChunk && window.__onChunk(%s, JSON.parse(%s))"
				.formatted(GSON.toJson(cli), jsStringLiteral(text)));
	}

	void sendProgress(String cli, String text) {
		js("window.__onProgress && window.__onProgress(%s, JSON.parse(%s))"
				.formatted(GSON.toJson(cli), jsStringLiteral(text)));
	}

	void sendDone(String cli) {
		js("window.__onDone && window.__onDone(%s)".formatted(GSON.toJson(cli)));
	}

	void sendError(String cli, String error) {
		var msg = normalize(cli, error);
		if (cli == null || cli.isBlank()) {
			js("window.__onError && window.__onError(JSON.parse(%s))"
					.formatted(jsStringLiteral(msg)));
			return;
		}
		js("window.__onError && window.__onError(%s, JSON.parse(%s))"
				.formatted(GSON.toJson(cli), jsStringLiteral(msg)));
	}

	/**
	 * 값을 JSON 문자열로 직렬화한 뒤, 그 결과를 다시 JSON 문자열로 감싼다.
	 * JS 쪽에서 JSON.parse()로 한 번 벗기면 원래 문자열이 복원된다.
	 * 예: "hello" → "\"hello\"" → "\"\\\"hello\\\"\""
	 */
	private static String jsStringLiteral(String value) {
		return GSON.toJson(GSON.toJson(value));
	}

	private void js(String script) {
		ApplicationManager.getApplication().invokeLater(() ->
				browser.getCefBrowser().executeJavaScript(script, "", 0));
	}

	private String normalize(String cli, String error) {
		var raw = "Unknown error";
		if (error != null) {
			raw = error.trim();
		}
		if (raw.isBlank()) {
			return "Unknown error";
		}

		var lower = raw.toLowerCase(Locale.ROOT);
		if (isTimeoutError(lower)) {
			return "Request stopped due to inactivity timeout. Retry with a simpler prompt.";
		}
		if (isApprovalError(lower)) {
			return "Request stopped: tool call required approval. Try a simpler request.";
		}
		if (isGeminiAuthError(cli, lower)) {
			return "Gemini CLI: run `gemini` in your terminal to complete auth setup, then retry.";
		}
		if (isClaudeOrCodexAuthError(cli, lower)) {
			return cli + " CLI: run `" + cli + "` in your terminal to login, then retry.";
		}
		if (raw.length() > 500) {
			return raw.substring(0, 500) + "...";
		}
		return raw;
	}

	private static boolean isTimeoutError(String lower) {
		return lower.contains("produced no output for");
	}

	private static boolean isApprovalError(String lower) {
		return lower.contains("require approval");
	}

	private static boolean isGeminiAuthError(String cli, String lower) {
		if (!"gemini".equals(cli)) {
			return false;
		}
		return lower.contains("login") || lower.contains("auth") || lower.contains("unauthorized");
	}

	private static boolean isClaudeOrCodexAuthError(String cli, String lower) {
		if (!"claude".equals(cli) && !"codex".equals(cli)) {
			return false;
		}
		return lower.contains("login") || lower.contains("not authenticated");
	}
}
