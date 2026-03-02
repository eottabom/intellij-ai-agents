package io.github.eottabom.aiagents.toolwindow;

import java.util.Locale;

enum ChatMode {
    NORMAL,
    PLAN;

    static ChatMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "plan" -> PLAN;
            default -> NORMAL;
        };
    }
}
