package io.github.eottabom.aiagents.providers;

import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /default-models.json 리소스에서 프로바이더별 기본 모델 목록을 로드
 */
final class DefaultModelLoader {

	private static final Logger logger = LoggerFactory.getLogger(DefaultModelLoader.class);
	private static final String RESOURCE_PATH = "/default-models.json";
	private static final Map<String, List<AiModel>> CACHE = new ConcurrentHashMap<>();
	private static volatile boolean loaded = false;

	private DefaultModelLoader() {
	}

	static List<AiModel> getModels(String providerName) {
		if (!loaded) {
			load();
		}
		return CACHE.getOrDefault(providerName, List.of());
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		try (var stream = DefaultModelLoader.class.getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				logger.warn("기본 모델 리소스를 찾을 수 없음: {}", RESOURCE_PATH);
				loaded = true;
				return;
			}
			var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
			var root = JsonParser.parseReader(reader).getAsJsonObject();
			for (var entry : root.entrySet()) {
				var providerName = entry.getKey();
				var modelsArray = entry.getValue().getAsJsonArray();
				var models = new ArrayList<AiModel>();
				for (var element : modelsArray) {
					var obj = element.getAsJsonObject();
					var id = obj.get("id").getAsString();
					var displayName = obj.get("displayName").getAsString();
					models.add(new AiModel(id, displayName));
				}
				CACHE.put(providerName, Collections.unmodifiableList(models));
			}
			loaded = true;
		} catch (Exception ex) {
			logger.warn("기본 모델 목록 로드 실패", ex);
		}
	}
}
