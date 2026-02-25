package io.github.eottabom.aiagents.toolwindow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ProjectRefsCollector {

    private static final Gson GSON = new Gson();

    private ProjectRefsCollector() {
    }

    static String collect(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            return null;
        }

        Path root = Path.of(basePath);
        if (!Files.isDirectory(root)) {
            return null;
        }

        try (var stream = Files.walk(root, 6)) {
            List<String> refs = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(root, path))
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

    private static boolean isIgnored(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            String n = part.toString();
            if (n.equals(".git")
                    || n.equals(".idea")
                    || n.equals(".gradle")
                    || n.equals("build")
                    || n.equals("dist")
                    || n.equals("node_modules")
                    || n.equals(".intellijPlatform")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRefCandidate(Path path) {
        String n = path.getFileName().toString().toLowerCase();
        return n.endsWith(".java")
                || n.endsWith(".kt")
                || n.endsWith(".kts")
                || n.endsWith(".ts")
                || n.endsWith(".tsx")
                || n.endsWith(".js")
                || n.endsWith(".jsx")
                || n.endsWith(".json")
                || n.endsWith(".md")
                || n.endsWith(".xml")
                || n.endsWith(".yml")
                || n.endsWith(".yaml")
                || n.endsWith(".properties");
    }

    private static String toRefJson(Path root, Path file) {
        Path rel = root.relativize(file);
        String relPath = rel.toString().replace("\\", "/");
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String symbol = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot).toLowerCase() : "";
        String kind = (ext.equals(".java") || ext.equals(".kt") || ext.equals(".kts")) ? "class" : "file";

        JsonObject obj = new JsonObject();
        obj.addProperty("symbol", symbol);
        obj.addProperty("path", relPath);
        obj.addProperty("kind", kind);
        return GSON.toJson(obj);
    }
}
