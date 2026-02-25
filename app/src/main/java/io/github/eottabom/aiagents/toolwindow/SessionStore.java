package io.github.eottabom.aiagents.toolwindow;

import com.intellij.ide.util.PropertiesComponent;
import io.github.eottabom.aiagents.providers.AiProvider;

/**
 * CLI 세션 ID 영구 저장 (IntelliJ PropertiesComponent 기반)
 */
class SessionStore {

    private static final String KEY_PREFIX = "aiagents.session.";

    void save(String providerName, String sessionId) {
        PropertiesComponent.getInstance().setValue(KEY_PREFIX + providerName, sessionId);
    }

    String get(String providerName) {
        return PropertiesComponent.getInstance().getValue(KEY_PREFIX + providerName);
    }

    void clear(String providerName) {
        PropertiesComponent.getInstance().unsetValue(KEY_PREFIX + providerName);
    }

    void clearAll() {
        for (AiProvider provider : AiProvider.values()) {
            clear(provider.cliName);
        }
    }
}
