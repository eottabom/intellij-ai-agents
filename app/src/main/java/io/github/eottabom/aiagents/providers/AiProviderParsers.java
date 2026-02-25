package io.github.eottabom.aiagents.providers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class AiProviderParsers {

    private AiProviderParsers() {
    }

    static StreamChunk parseClaudeLine(String line) {
        JsonObject obj = AiProviderJsonUtils.tryParseJson(line);
        if (obj == null) {
            return null;
        }

        String type = AiProviderJsonUtils.stringField(obj, "type");
        String sessionId = AiProviderJsonUtils.stringField(obj, "session_id");

        if ("assistant".equals(type) && obj.has("message")) {
            StreamChunk chunk = parseClaudeAssistantMessage(obj.getAsJsonObject("message"));
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
        JsonObject obj = AiProviderJsonUtils.tryParseJson(line);
        if (obj == null) {
            return null;
        }

        String type = AiProviderJsonUtils.stringField(obj, "type");

        if ("error".equalsIgnoreCase(type)) {
            return parseGeminiError(obj);
        }
        if ("init".equalsIgnoreCase(type) && obj.has("session_id")) {
            return StreamChunk.done(obj.get("session_id").getAsString());
        }
        if ("message".equalsIgnoreCase(type)) {
            return parseGeminiMessage(obj);
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
        JsonObject obj = AiProviderJsonUtils.tryParseJson(line);
        if (obj == null) {
            return null;
        }

        String type = AiProviderJsonUtils.stringField(obj, "type");

        if ("thread.started".equals(type) && obj.has("thread_id")) {
            return StreamChunk.done(obj.get("thread_id").getAsString());
        }
        if ("turn.started".equals(type)) {
            return null;
        }
        if ("error".equals(type) && obj.has("message")) {
            return StreamChunk.error(obj.get("message").getAsString());
        }
        if ("item.started".equals(type)) {
            return null;
        }
        if ("item.completed".equals(type)) {
            return parseCodexItem(obj);
        }
        if ("turn.completed".equals(type)) {
            return StreamChunk.done(null);
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
        if (!message.has("content")) {
            return null;
        }
        for (JsonElement el : message.getAsJsonArray("content")) {
            JsonObject item = el.getAsJsonObject();
            String itemType = AiProviderJsonUtils.stringField(item, "type");
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
        JsonElement msg = obj.get("message");
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
        String role = AiProviderJsonUtils.stringField(obj, "role");
        if (!("assistant".equalsIgnoreCase(role) || "model".equalsIgnoreCase(role))) {
            return null;
        }
        if (!obj.has("content")) {
            return null;
        }
        String text = AiProviderJsonUtils.extractGeminiText(obj.get("content"));
        return (text != null && !text.isBlank()) ? StreamChunk.text(text) : null;
    }

    private static StreamChunk parseCodexItem(JsonObject obj) {
        if (!obj.has("item") || !obj.get("item").isJsonObject()) {
            return null;
        }
        JsonObject item = obj.getAsJsonObject("item");
        String itemType = AiProviderJsonUtils.stringField(item, "type");
        if ("agent_message".equals(itemType) && item.has("text")) {
            return StreamChunk.text(item.get("text").getAsString());
        }
        if ("command_execution".equals(itemType) && item.has("aggregated_output")) {
            String output = item.get("aggregated_output").getAsString();
            return output.isBlank() ? null : StreamChunk.text(output.trim());
        }
        if ("reasoning".equals(itemType)) {
            return StreamChunk.toolUse("codex:reasoning");
        }
        return null;
    }
}
