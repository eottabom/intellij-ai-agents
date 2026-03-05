package io.github.eottabom.aiagents.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

final class CliJsonUtils {

	private CliJsonUtils() {
	}

	static JsonObject tryParseJson(String line) {
		if (line == null) {
			return null;
		}
		var trimmedLine = line.stripLeading();
		if (!trimmedLine.startsWith("{")) {
			return null;
		}
		try {
			return JsonParser.parseString(trimmedLine).getAsJsonObject();
		} catch (Exception ignored) {
			return null;
		}
	}

	static String stringField(JsonObject obj, String key) {
		if (!obj.has(key)) {
			return null;
		}
		var value = obj.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return null;
		}
		if (!value.getAsJsonPrimitive().isString()) {
			return null;
		}
		return value.getAsString();
	}

	static String extractGeminiText(JsonElement jsonElement) {
		if (jsonElement == null || jsonElement.isJsonNull()) {
			return null;
		}

		return switch (elementKind(jsonElement)) {
			case PRIMITIVE -> extractPrimitiveText(jsonElement.getAsJsonPrimitive());
			case ARRAY -> extractArrayText(jsonElement.getAsJsonArray());
			case OBJECT -> extractObjectText(jsonElement.getAsJsonObject());
			case OTHER -> null;
		};
	}

	private static String extractPrimitiveText(JsonPrimitive primitive) {
		if (!primitive.isString()) {
			return null;
		}
		return primitive.getAsString();
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
		if (sb.isEmpty()) {
			return null;
		}
		return sb.toString();
	}

	private static String extractObjectText(JsonObject obj) {
		var textElement = obj.get("text");
		if (textElement != null && textElement.isJsonPrimitive() && textElement.getAsJsonPrimitive().isString()) {
			var textValue = textElement.getAsString();
			if (!textValue.isBlank()) {
				return textValue;
			}
		}

		String text = extractFirstNonNull(obj, "delta", "content");
		if (text != null && !text.isBlank()) {
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
			if (text != null && !text.isBlank()) {
				return text;
			}
		}
		return null;
	}

	private static ElementKind elementKind(JsonElement jsonElement) {
		if (jsonElement.isJsonPrimitive()) {
			return ElementKind.PRIMITIVE;
		}
		if (jsonElement.isJsonArray()) {
			return ElementKind.ARRAY;
		}
		if (jsonElement.isJsonObject()) {
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
