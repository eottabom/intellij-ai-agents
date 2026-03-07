package io.github.eottabom.aiagents.toolwindow;

import java.util.Locale;

enum ChatMode {
	NORMAL,
	PLAN;

	static ChatMode fromString(String value) {
		if (value == null || value.isBlank()) {
			return NORMAL;
		}

		var normalizedValue = value.trim().toLowerCase(Locale.ROOT);
		if ("plan".equals(normalizedValue)) {
			return PLAN;
		}
		return NORMAL;
	}
}
