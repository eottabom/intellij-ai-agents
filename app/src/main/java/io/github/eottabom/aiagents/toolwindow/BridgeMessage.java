package io.github.eottabom.aiagents.toolwindow;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * React → Java 브릿지 메시지 (JCEF JsBridge 전용)
 * 예: { "type": "chat", "cli": "claude", "prompt": "Hello", "mode": "normal" }
 */
record BridgeMessage(
		String type,    // chat | cancel | getSession | clearSession | clearAllSessions | getProjectRefs
		String cli,     // claude | gemini | codex
		String prompt,
		String mode     // normal | plan
) {
	private static final Gson GSON = new Gson();

	static BridgeMessage fromJson(String json) {
		try {
			return GSON.fromJson(json, BridgeMessage.class);
		} catch (JsonSyntaxException ex) {
			return new BridgeMessage(null, null, null, null);
		}
	}
}
