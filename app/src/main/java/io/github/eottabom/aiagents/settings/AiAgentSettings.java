package io.github.eottabom.aiagents.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.github.eottabom.aiagents.refs.DirPathNormalizer;

import java.util.LinkedHashSet;
import java.util.Set;

@Service(Service.Level.APP)
@State(name = "AiAgentSettings", storages = @Storage("aiagents.xml"))
public final class AiAgentSettings implements PersistentStateComponent<AiAgentSettings.State> {

    public static final class State {
        public String refsConfigPath = ".aiagents/refs-config.json";
        public String extraIgnoredDirs = "";
        public boolean skipPermissions = false;
        public boolean bypassApprovals = false;
        public boolean geminiYoloMode = false;
        public int claudeTimeoutSec = 180;
        public int geminiTimeoutSec = 60;
        public int codexTimeoutSec = 30;
        public int projectRefsScanDepth = 6;
    }

    private State state = new State();

    public static AiAgentSettings getInstance() {
        return ApplicationManager.getApplication().getService(AiAgentSettings.class);
    }

    public static AiAgentSettings getInstanceOrDefaults() {
        var instance = ApplicationManager.getApplication().getService(AiAgentSettings.class);
        if (instance != null) {
            return instance;
        }
        var fallback = new AiAgentSettings();
        fallback.loadState(new State());
        return fallback;
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
            String normalized = DirPathNormalizer.normalize(token);
            if (normalized != null) {
                dirs.add(normalized);
            }
        }
        return dirs;
    }

    public boolean isSkipPermissions() {
        return state.skipPermissions;
    }

    public void setSkipPermissions(boolean value) {
        state.skipPermissions = value;
    }

    public boolean isBypassApprovals() {
        return state.bypassApprovals;
    }

    public void setBypassApprovals(boolean value) {
        state.bypassApprovals = value;
    }

    public boolean isGeminiYoloMode() {
        return state.geminiYoloMode;
    }

    public void setGeminiYoloMode(boolean value) {
        state.geminiYoloMode = value;
    }

    public int getClaudeTimeoutSec() {
        return positiveOrDefault(state.claudeTimeoutSec, 180);
    }

    public void setClaudeTimeoutSec(int sec) {
        state.claudeTimeoutSec = Math.max(10, sec);
    }

    public int getGeminiTimeoutSec() {
        return positiveOrDefault(state.geminiTimeoutSec, 60);
    }

    public void setGeminiTimeoutSec(int sec) {
        state.geminiTimeoutSec = Math.max(10, sec);
    }

    public int getCodexTimeoutSec() {
        return positiveOrDefault(state.codexTimeoutSec, 30);
    }

    public void setCodexTimeoutSec(int sec) {
        state.codexTimeoutSec = Math.max(10, sec);
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static final int MAX_SCAN_DEPTH = 20;

    public int getProjectRefsScanDepth() {
        if (state.projectRefsScanDepth <= 0) {
            return 6;
        }
        return Math.min(state.projectRefsScanDepth, MAX_SCAN_DEPTH);
    }

    public void setProjectRefsScanDepth(int depth) {
        state.projectRefsScanDepth = Math.max(1, depth);
    }
}
