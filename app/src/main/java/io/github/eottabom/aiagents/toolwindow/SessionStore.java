package io.github.eottabom.aiagents.toolwindow;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import io.github.eottabom.aiagents.providers.AiProvider;

/**
 * CLI 세션 ID 영구 저장 (IntelliJ PropertiesComponent 기반, 프로젝트 단위)
 */
class SessionStore {

    private static final String KEY_PREFIX = "aiagents.session.";

    private final Project project;

    SessionStore(Project project) {
        this.project = project;
    }

    void save(String providerName, String sessionId) {
        PropertiesComponent.getInstance(project).setValue(KEY_PREFIX + providerName, sessionId);
    }

    String get(String providerName) {
        return PropertiesComponent.getInstance(project).getValue(KEY_PREFIX + providerName);
    }

    void clear(String providerName) {
        PropertiesComponent.getInstance(project).unsetValue(KEY_PREFIX + providerName);
    }

    void clearAll() {
        for (AiProvider provider : AiProvider.values()) {
            clear(provider.cliName);
        }
    }
}
