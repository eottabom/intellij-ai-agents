package io.github.eottabom.aiagents.refs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import io.github.eottabom.aiagents.settings.AiAgentSettings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProjectRefsCollector {

	private static final Logger logger = LoggerFactory.getLogger(ProjectRefsCollector.class);
	private static final Gson GSON = new Gson();
	private static final int MAX_REFS = 800;
	private static final Pattern HASHED_FILENAME_PATTERN = Pattern.compile(".*-[0-9a-f]{6,}\\.[A-Za-z0-9]+$", Pattern.CASE_INSENSITIVE);

	private ProjectRefsCollector() {
	}

	public static String collect(Project project) {
		var basePath = project.getBasePath();
		if (basePath == null || basePath.isBlank()) {
			return null;
		}

		var root = Path.of(basePath);
		if (!Files.isDirectory(root)) {
			return null;
		}

		var settings = AiAgentSettings.getInstanceOrDefaults();
		var ignoredDirs = buildIgnoredDirs(root, settings);
		var finalRefExtensions = settings.getRefExtensions();
		var finalClassExtensions = settings.getClassExtensions();
		applyConfigFileExtensions(root, settings.getRefsConfigPath(), finalRefExtensions, finalClassExtensions);

		int scanDepth = Math.max(1, settings.getProjectRefsScanDepth());
		var array = new JsonArray();
		try {
			Files.walkFileTree(root, Set.of(), scanDepth, new SimpleFileVisitor<>() {
				private int emittedRefsCount = 0;

				@Override
				public @NotNull FileVisitResult preVisitDirectory(@NotNull Path directory, @NotNull BasicFileAttributes basicFileAttributes) {
					if (!directory.equals(root) && isIgnored(root, directory, ignoredDirs)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes basicFileAttributes) {
					if (!basicFileAttributes.isRegularFile()) {
						return FileVisitResult.CONTINUE;
					}
					if (isIgnored(root, file, ignoredDirs)) {
						return FileVisitResult.CONTINUE;
					}
					if (!isRefCandidate(file, finalRefExtensions)) {
						return FileVisitResult.CONTINUE;
					}

					array.add(toRefJson(root, file, finalClassExtensions));
					emittedRefsCount++;
					if (emittedRefsCount >= MAX_REFS) {
						return FileVisitResult.TERMINATE;
					}
					return FileVisitResult.CONTINUE;
				}
			});
			return GSON.toJson(array);
		} catch (IOException ex) {
			logger.warn("Failed to collect project refs", ex);
			return null;
		}
	}

	private static boolean isIgnored(Path root, Path path, Set<String> ignoredDirs) {
		var normalizedRelativePath = root.relativize(path).toString().replace("\\", "/").toLowerCase(Locale.ROOT);
		var pathSegments = normalizedRelativePath.split("/");
		var currentPath = new StringBuilder();

		for (String segment : pathSegments) {
			if (segment.isBlank()) {
				continue;
			}

			if (!currentPath.isEmpty()) {
				currentPath.append('/');
			}
			currentPath.append(segment);

			if (ignoredDirs.contains(segment)) {
				return true;
			}

			if (ignoredDirs.contains(currentPath.toString())) {
				return true;
			}
		}

		return false;
	}

	private static boolean isRefCandidate(Path path, Set<String> refExtensions) {
		var fileName = path.getFileName().toString();
		var ext = extensionOf(fileName);
		if (!refExtensions.contains(ext)) {
			return false;
		}
		if (fileName.endsWith(".min.js")) {
			return false;
		}
		return !HASHED_FILENAME_PATTERN.matcher(fileName).matches();
	}

	private static JsonObject toRefJson(Path root, Path file, Set<String> classExtensions) {
		var rel = root.relativize(file);
		var relPath = rel.toString().replace("\\", "/");
		var fileName = file.getFileName().toString();
		var dot = fileName.lastIndexOf('.');
		var symbol = fileName;
		if (dot > 0) {
			symbol = fileName.substring(0, dot);
		}
		var ext = extensionOf(fileName);
		var kind = classExtensions.contains(ext) ? "class" : "file";

		var obj = new JsonObject();
		obj.addProperty("symbol", symbol);
		obj.addProperty("path", relPath);
		obj.addProperty("kind", kind);
		return obj;
	}

	private static String extensionOf(String fileName) {
		var dot = fileName.lastIndexOf('.');
		if (dot <= 0 || dot == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static Set<String> buildIgnoredDirs(Path root, AiAgentSettings settings) {
		var merged = new LinkedHashSet<>(RefsDefaultsLoader.getIgnoreDirs());
		merged.addAll(settings.getExtraIgnoredDirs());
		merged.addAll(loadIgnoredDirsFromConfig(root, settings.getRefsConfigPath()));
		return merged;
	}

	/**
	 * refs-config.json 에서 refExtensions / classExtensions 배열이 있으면
	 * Settings 값을 대체(프로젝트별 오버라이드).
	 */
	private static void applyConfigFileExtensions(
			Path root, String relativeConfigPath,
			Set<String> refExtensions, Set<String> classExtensions) {
		var obj = readConfigJson(root, relativeConfigPath);
		if (obj == null) {
			return;
		}
		var refArray = RefsDefaultsLoader.readStringSet(obj, "refExtensions", true);
		if (!refArray.isEmpty()) {
			refExtensions.clear();
			refExtensions.addAll(refArray);
		}
		var classArray = RefsDefaultsLoader.readStringSet(obj, "classExtensions", true);
		if (!classArray.isEmpty()) {
			classExtensions.clear();
			classExtensions.addAll(classArray);
		}
	}

	private static JsonObject readConfigJson(Path root, String relativeConfigPath) {
		if (relativeConfigPath == null || relativeConfigPath.isBlank()) {
			return null;
		}
		var cfg = root.resolve(relativeConfigPath).normalize();
		if (!cfg.startsWith(root) || !Files.isRegularFile(cfg)) {
			return null;
		}
		try {
			var json = Files.readString(cfg, StandardCharsets.UTF_8);
			return GSON.fromJson(json, JsonObject.class);
		} catch (Exception exception) {
			logger.warn("Failed to load refs config from {}", cfg, exception);
			return null;
		}
	}

	private static Set<String> loadIgnoredDirsFromConfig(Path root, String relativeConfigPath) {
		var dirs = new LinkedHashSet<String>();
		var obj = readConfigJson(root, relativeConfigPath);
		if (obj == null) {
			return dirs;
		}
		readDirArray(obj, "ignoreDirs", dirs);
		readDirArray(obj, "excludeDirs", dirs);
		return dirs;
	}

	private static void readDirArray(JsonObject obj, String field, Set<String> out) {
		if (!obj.has(field) || !obj.get(field).isJsonArray()) {
			return;
		}
		var arr = obj.getAsJsonArray(field);
		for (JsonElement el : arr) {
			if (!el.isJsonPrimitive()) {
				continue;
			}
			var normalized = DirPathNormalizer.normalize(el.getAsString());
			if (normalized != null) {
				out.add(normalized);
			}
		}
	}
}
