package io.github.eottabom.aiagents.refs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * /refs-defaults.json 리소스에서 기본 ignoreDirs, refExtensions, classExtensions 를 로드
 */
public final class RefsDefaultsLoader {

	private static final Logger logger = LoggerFactory.getLogger(RefsDefaultsLoader.class);
	private static final String RESOURCE_PATH = "/refs-defaults.json";

	private static volatile boolean loaded = false;
	private static Set<String> ignoreDirs = Set.of();
	private static Set<String> refExtensions = Set.of();
	private static Set<String> classExtensions = Set.of();

	private RefsDefaultsLoader() {
	}

	public static Set<String> getIgnoreDirs() {
		ensureLoaded();
		return ignoreDirs;
	}

	public static Set<String> getRefExtensions() {
		ensureLoaded();
		return refExtensions;
	}

	public static Set<String> getClassExtensions() {
		ensureLoaded();
		return classExtensions;
	}

	public static String getRefExtensionsAsString() {
		ensureLoaded();
		return String.join(",", refExtensions);
	}

	public static String getClassExtensionsAsString() {
		ensureLoaded();
		return String.join(",", classExtensions);
	}

	private static void ensureLoaded() {
		if (!loaded) {
			load();
		}
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		try (var stream = RefsDefaultsLoader.class.getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				logger.warn("refs-defaults 리소스를 찾을 수 없음: {}", RESOURCE_PATH);
				loaded = true;
				return;
			}
			var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
			var root = JsonParser.parseReader(reader).getAsJsonObject();

			ignoreDirs = Collections.unmodifiableSet(readStringSet(root, "ignoreDirs", false));
			refExtensions = Collections.unmodifiableSet(readStringSet(root, "refExtensions", true));
			classExtensions = Collections.unmodifiableSet(readStringSet(root, "classExtensions", true));

			loaded = true;
		} catch (Exception ex) {
			logger.warn("refs-defaults 로드 실패", ex);
			loaded = true;
		}
	}

	public static Set<String> readStringSet(JsonObject obj, String field, boolean lowercase) {
		var result = new LinkedHashSet<String>();
		if (!obj.has(field) || !obj.get(field).isJsonArray()) {
			return result;
		}
		for (JsonElement el : obj.getAsJsonArray(field)) {
			if (!el.isJsonPrimitive()) {
				continue;
			}
			var value = el.getAsString().trim();
			if (value.isBlank()) {
				continue;
			}
			result.add(lowercase ? value.toLowerCase(Locale.ROOT) : value);
		}
		return result;
	}
}
