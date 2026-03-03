package io.github.eottabom.aiagents.providers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

final class CliStreamParsers {

    private CliStreamParsers() {
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    static StreamChunk parseClaudeLine(String line) {
        var obj = CliJsonUtils.tryParseJson(line);
        if (obj == null) {
            return null;
        }

        var type = CliJsonUtils.stringField(obj, "type");
        var sessionId = CliJsonUtils.stringField(obj, "session_id");

        if ("assistant".equals(type) && obj.has("message") && obj.get("message").isJsonObject()) {
            var chunk = parseClaudeAssistantMessage(obj.getAsJsonObject("message"));
            if (chunk != null) {
                return chunk;
            }
        }
        if (sessionId != null) {
            return StreamChunk.done(sessionId);
        }
        return null;
    }

    static StreamChunk parseGeminiLine(String line) {
        var obj = CliJsonUtils.tryParseJson(line);
        if (obj == null) {
            return null;
        }

        var type = CliJsonUtils.stringField(obj, "type");
        switch (nullToEmpty(type).toLowerCase(Locale.ROOT)) {
            case "error":
                return parseGeminiError(obj);
            case "init":
                var sessionId = CliJsonUtils.stringField(obj, "session_id");
                if (sessionId != null) {
                    return StreamChunk.done(sessionId);
                }
                return null;
            case "message":
                return parseGeminiMessage(obj);
            default:
                break;
        }

        var text = CliJsonUtils.stringField(obj, "text");
        if (text != null) {
            return StreamChunk.text(text);
        }
        var delta = CliJsonUtils.stringField(obj, "delta");
        if (delta != null) {
            return StreamChunk.text(delta);
        }
        return null;
    }

    static StreamChunk parseCodexLine(String line) {
        var obj = CliJsonUtils.tryParseJson(line);
        if (obj == null) {
            return null;
        }

        var type = CliJsonUtils.stringField(obj, "type");
        switch (nullToEmpty(type)) {
            case "thread.started":
                var threadId = CliJsonUtils.stringField(obj, "thread_id");
                if (threadId != null) {
                    return StreamChunk.done(threadId);
                }
                return null;
            case "turn.started":
            case "item.started":
                return null;
            case "error":
                var message = CliJsonUtils.stringField(obj, "message");
                if (message != null) {
                    return StreamChunk.error(message);
                }
                return null;
            case "item.completed":
                return parseCodexItem(obj);
            case "turn.completed":
                return StreamChunk.done(null);
            default:
                break;
        }

        var text = CliJsonUtils.stringField(obj, "text");
        if (text != null) {
            return StreamChunk.text(text);
        }
        var threadId = CliJsonUtils.stringField(obj, "thread_id");
        if (threadId != null) {
            return StreamChunk.done(threadId);
        }
        return null;
    }

    private static StreamChunk parseClaudeAssistantMessage(JsonObject message) {
        if (!message.has("content") || !message.get("content").isJsonArray()) {
            return null;
        }
        for (JsonElement el : message.getAsJsonArray("content")) {
            if (!el.isJsonObject()) {
                continue;
            }
            var item = el.getAsJsonObject();
            var itemType = CliJsonUtils.stringField(item, "type");
            if ("text".equals(itemType)) {
                var text = CliJsonUtils.stringField(item, "text");
                if (text != null) {
                    return StreamChunk.text(text);
                }
            }
            if ("tool_use".equals(itemType)) {
                var toolName = CliJsonUtils.stringField(item, "name");
                if (toolName != null) {
                    return StreamChunk.toolUse(toolName);
                }
            }
        }
        return null;
    }

    private static StreamChunk parseGeminiError(JsonObject obj) {
        var msg = obj.get("message");
        if (msg == null) {
            return null;
        }
        if (msg.isJsonPrimitive()) {
            return StreamChunk.error(msg.getAsString());
        }
        if (msg.isJsonObject()) {
            var text = CliJsonUtils.stringField(msg.getAsJsonObject(), "text");
            if (text != null) {
                return StreamChunk.error(text);
            }
        }
        return StreamChunk.error(msg.toString());
    }

    private static StreamChunk parseGeminiMessage(JsonObject obj) {
        var role = CliJsonUtils.stringField(obj, "role");
        var isAssistantRole = "assistant".equalsIgnoreCase(role);
        var isModelRole = "model".equalsIgnoreCase(role);
        var isSupportedRole = isAssistantRole || isModelRole;
        if (!isSupportedRole) {
            return null;
        }
        if (!obj.has("content")) {
            return null;
        }
        var text = CliJsonUtils.extractGeminiText(obj.get("content"));
        if (text == null || text.isBlank()) {
            return null;
        }
        return StreamChunk.text(text);
    }

    private static StreamChunk parseCodexItem(JsonObject obj) {
        if (!obj.has("item") || !obj.get("item").isJsonObject()) {
            return null;
        }
        var item = obj.getAsJsonObject("item");
        var itemType = CliJsonUtils.stringField(item, "type");
        return switch (nullToEmpty(itemType)) {
			case "agent_message" -> {
                var text = CliJsonUtils.stringField(item, "text");
				if (text != null) {
					yield StreamChunk.text(text);
				}
				yield null;
			}
			case "command_execution" -> {
                var output = CliJsonUtils.stringField(item, "aggregated_output");
				if (output != null) {
                    if (output.isBlank()) {
                        yield null;
                    }
                    yield StreamChunk.text(output.trim());
				}
				yield null;
			}
			case "reasoning" -> StreamChunk.toolUse("codex:reasoning");
			default -> null;
		};
    }
}
