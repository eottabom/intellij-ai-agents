package io.github.eottabom.aiagents.toolwindow;

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
        return switch (value.trim()) {
            case "chat" -> CHAT;
            case "cancel" -> CANCEL;
            case "getSession" -> GET_SESSION;
            case "clearSession" -> CLEAR_SESSION;
            case "clearAllSessions" -> CLEAR_ALL_SESSIONS;
            case "getProjectRefs" -> GET_PROJECT_REFS;
            default -> UNKNOWN;
        };
    }
}
