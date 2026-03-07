package io.github.eottabom.aiagents.toolwindow;

import java.util.Locale;

enum BridgeMessageType {
	CHAT,
	CANCEL,
	GET_SESSION,
	CLEAR_SESSION,
	CLEAR_ALL_SESSIONS,
	GET_PROJECT_REFS,
	UNKNOWN;

	static BridgeMessageType fromString(String value) {
		if (value == null || value.isBlank()) {
			return UNKNOWN;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "chat" -> CHAT;
			case "cancel" -> CANCEL;
			case "getsession" -> GET_SESSION;
			case "clearsession" -> CLEAR_SESSION;
			case "clearallsessions" -> CLEAR_ALL_SESSIONS;
			case "getprojectrefs" -> GET_PROJECT_REFS;
			default -> UNKNOWN;
		};
	}
}
