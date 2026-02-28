package io.github.eottabom.aiagents.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service(Service.Level.APP)
@State(name = "AiAgentSettings", storages = @Storage("aiagents.xml"))
public final class AiAgentSettings implements PersistentStateComponent<AiAgentSettings.State> {

    public static final class State {
        public String refsConfigPath = ".aiagents/refs-config.json";
        public String extraIgnoredDirs = "";
    }

    private State state = new State();

    public static AiAgentSettings getInstance() {
        return ApplicationManager.getApplication().getService(AiAgentSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getRefsConfigPath() {
        var path = "";
        if (state.refsConfigPath != null) {
            path = state.refsConfigPath.trim();
        }
        return path.isBlank() ? ".aiagents/refs-config.json" : path;
    }

    public void setRefsConfigPath(String path) {
        if (path == null) {
            state.refsConfigPath = "";
            return;
        }
        state.refsConfigPath = path.trim();
    }

    public String getExtraIgnoredDirsRaw() {
        if (state.extraIgnoredDirs == null) {
            return "";
        }
        return state.extraIgnoredDirs;
    }

    public void setExtraIgnoredDirsRaw(String raw) {
        if (raw == null) {
            state.extraIgnoredDirs = "";
            return;
        }
        state.extraIgnoredDirs = raw;
    }

    public Set<String> getExtraIgnoredDirs() {
        Set<String> dirs = new LinkedHashSet<>();
        for (String token : getExtraIgnoredDirsRaw().split("[,\\n\\r\\t ]+")) {
            String normalized = normalizeDirToken(token);
            if (normalized != null) {
                dirs.add(normalized);
            }
        }
        return dirs;
    }

    private String normalizeDirToken(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim();
        if (value.isBlank()) {
            return null;
        }
        value = value.replace("\\", "/");
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
