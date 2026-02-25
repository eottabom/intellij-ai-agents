package io.github.eottabom.aiagents.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

final class AiProviderJsonUtils {

    private AiProviderJsonUtils() {
    }

    static JsonObject tryParseJson(String line) {
        if (!line.startsWith("{")) {
            return null;
        }
        try {
            return JsonParser.parseString(line).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    static String stringField(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : null;
    }

    static String extractGeminiText(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }

        return switch (elementKind(el)) {
            case PRIMITIVE -> extractPrimitiveText(el.getAsJsonPrimitive());
            case ARRAY -> extractArrayText(el.getAsJsonArray());
            case OBJECT -> extractObjectText(el.getAsJsonObject());
            case OTHER -> null;
        };
    }

    private static String extractPrimitiveText(JsonPrimitive primitive) {
        return primitive.isString() ? primitive.getAsString() : null;
    }

    private static String extractArrayText(JsonArray array) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement item : array) {
            String text = extractGeminiText(item);
            if (text == null || text.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(text);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String extractObjectText(JsonObject obj) {
        if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
            return obj.get("text").getAsString();
        }

        String text = extractFirstNonNull(obj, "delta", "content");
        if (text != null) {
            return text;
        }

        if (obj.has("parts")) {
            return extractGeminiText(obj.get("parts"));
        }
        if (obj.has("candidates")) {
            return extractGeminiText(obj.get("candidates"));
        }
        return null;
    }

    private static String extractFirstNonNull(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) {
                continue;
            }
            String text = extractGeminiText(obj.get(key));
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static ElementKind elementKind(JsonElement el) {
        if (el.isJsonPrimitive()) {
            return ElementKind.PRIMITIVE;
        }
        if (el.isJsonArray()) {
            return ElementKind.ARRAY;
        }
        if (el.isJsonObject()) {
            return ElementKind.OBJECT;
        }
        return ElementKind.OTHER;
    }

    private enum ElementKind {
        PRIMITIVE,
        ARRAY,
        OBJECT,
        OTHER
    }
}
