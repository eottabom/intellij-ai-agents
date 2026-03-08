package io.github.eottabom.aiagents.toolwindow;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * React → Java 브릿지 메시지 (JCEF JsBridge 전용)
 * 예: { "type": "chat", "cli": "claude", "prompt": "Hello", "mode": "normal", "model": "claude-sonnet" }
 */
record BridgeMessage(
		String type,    // chat | cancel | getSession | clearSession | clearAllSessions | getProjectRefs | getModels | setModel
		String cli,     // claude | gemini | codex
		String prompt,
		String mode,    // normal | plan
		String model    // 모델 ID (setModel 시 사용)
) {
	private static final Gson GSON = new Gson();

	static BridgeMessage fromJson(String json) {
		if (json == null || json.isBlank()) {
			return new BridgeMessage(null, null, null, null, null);
		}
		try {
			return GSON.fromJson(json, BridgeMessage.class);
		} catch (JsonSyntaxException ex) {
			return new BridgeMessage(null, null, null, null, null);
		}
	}
}
