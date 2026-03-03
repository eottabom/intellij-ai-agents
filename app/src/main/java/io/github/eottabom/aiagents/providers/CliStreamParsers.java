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
                if (obj.has("session_id")) {
                    return StreamChunk.done(obj.get("session_id").getAsString());
                }
                return null;
            case "message":
                return parseGeminiMessage(obj);
            default:
                break;
        }

        if (obj.has("text")) {
            return StreamChunk.text(obj.get("text").getAsString());
        }
        if (obj.has("delta") && obj.get("delta").isJsonPrimitive()) {
            return StreamChunk.text(obj.get("delta").getAsString());
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
                if (obj.has("thread_id")) {
                    return StreamChunk.done(obj.get("thread_id").getAsString());
                }
                return null;
            case "turn.started":
            case "item.started":
                return null;
            case "error":
                if (obj.has("message")) {
                    return StreamChunk.error(obj.get("message").getAsString());
                }
                return null;
            case "item.completed":
                return parseCodexItem(obj);
            case "turn.completed":
                return StreamChunk.done(null);
            default:
                break;
        }

        if (obj.has("text")) {
            return StreamChunk.text(obj.get("text").getAsString());
        }
        if (obj.has("thread_id")) {
            return StreamChunk.done(obj.get("thread_id").getAsString());
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
            if ("text".equals(itemType) && item.has("text")) {
                return StreamChunk.text(item.get("text").getAsString());
            }
            if ("tool_use".equals(itemType) && item.has("name")) {
                return StreamChunk.toolUse(item.get("name").getAsString());
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
        if (msg.isJsonObject() && msg.getAsJsonObject().has("text")) {
            return StreamChunk.error(msg.getAsJsonObject().get("text").getAsString());
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
				if (item.has("text")) {
					yield StreamChunk.text(item.get("text").getAsString());
				}
				yield null;
			}
			case "command_execution" -> {
				if (item.has("aggregated_output")) {
					var output = item.get("aggregated_output").getAsString();
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
