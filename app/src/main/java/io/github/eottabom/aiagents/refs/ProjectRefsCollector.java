package io.github.eottabom.aiagents.refs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import io.github.eottabom.aiagents.settings.AiAgentSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ProjectRefsCollector {

    private static final Gson GSON = new Gson();
    private static final Set<String> DEFAULT_IGNORED_DIRS = Set.of(
            ".git",
            ".idea",
            ".gradle",
            ".intellijplatform",
            "build",
            "dist",
            "node_modules",
            "target",
            "out"
    );
    private static final Set<String> REF_EXTENSIONS = Set.of(
            "java", "kt", "kts",
            "ts", "tsx", "js", "jsx",
            "json", "md", "xml",
            "yml", "yaml", "properties"
    );
    private static final Set<String> CLASS_EXTENSIONS = Set.of("java", "kt", "kts");

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
        var ignoredDirs = buildIgnoredDirs(root);

        var settings = AiAgentSettings.getInstance();
        int scanDepth = settings != null ? settings.getProjectRefsScanDepth() : 6;
        try (var stream = Files.walk(root, scanDepth)) {
            var refs = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(root, path, ignoredDirs))
                    .filter(ProjectRefsCollector::isRefCandidate)
                    .limit(800)
                    .map(path -> toRefJson(root, path))
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
            return "[" + String.join(",", refs) + "]";
        } catch (IOException ignored) {
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

    private static boolean isRefCandidate(Path path) {
        var fileName = path.getFileName().toString();
        var ext = extensionOf(fileName);
        if (!REF_EXTENSIONS.contains(ext)) {
            return false;
        }
        if (fileName.endsWith(".min.js")) {
            return false;
        }
        return !fileName.matches(".*-[A-Za-z0-9]{6,}\\.[A-Za-z0-9]+$");
    }

    private static String toRefJson(Path root, Path file) {
        var rel = root.relativize(file);
        var relPath = rel.toString().replace("\\", "/");
        var fileName = file.getFileName().toString();
        var dot = fileName.lastIndexOf('.');
        var symbol = fileName;
        if (dot > 0) {
            symbol = fileName.substring(0, dot);
        }
        var ext = extensionOf(fileName);
        var kind = "file";
        if (CLASS_EXTENSIONS.contains(ext)) {
            kind = "class";
        }

        var obj = new JsonObject();
        obj.addProperty("symbol", symbol);
        obj.addProperty("path", relPath);
        obj.addProperty("kind", kind);
        return GSON.toJson(obj);
    }

    private static String extensionOf(String fileName) {
        var dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Set<String> buildIgnoredDirs(Path root) {
        var merged = new LinkedHashSet<>(DEFAULT_IGNORED_DIRS);
        var settings = AiAgentSettings.getInstance();
        if (settings == null) {
            return merged;
        }
        merged.addAll(settings.getExtraIgnoredDirs());
        merged.addAll(loadIgnoredDirsFromConfig(root, settings.getRefsConfigPath()));
        return merged;
    }

    private static Set<String> loadIgnoredDirsFromConfig(Path root, String relativeConfigPath) {
        var dirs = new LinkedHashSet<String>();
        if (relativeConfigPath == null || relativeConfigPath.isBlank()) {
            return dirs;
        }
        var cfg = root.resolve(relativeConfigPath).normalize();
        if (!cfg.startsWith(root) || !Files.isRegularFile(cfg)) {
            return dirs;
        }
        try {
            var json = Files.readString(cfg, StandardCharsets.UTF_8);
            var obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) {
                return dirs;
            }
            readDirArray(obj, "ignoreDirs", dirs);
            readDirArray(obj, "excludeDirs", dirs);
            return dirs;
        } catch (Exception ignored) {
            return dirs;
        }
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
