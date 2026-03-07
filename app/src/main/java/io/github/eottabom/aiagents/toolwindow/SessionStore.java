package io.github.eottabom.aiagents.toolwindow;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import io.github.eottabom.aiagents.providers.AiProvider;

/**
 * CLI 세션 ID 영구 저장 (IntelliJ PropertiesComponent 기반, 프로젝트 단위)
 * <p>
 * key 정책과 저장/조회/삭제 동작을 캡슐화한 서비스 객체이므로,
 * 값 묶음 표현에 적합한 record 대신 class로 유지한다.
 */
class SessionStore {

	private static final String KEY_PREFIX = "aiagents.session.";

	private final Project project;

	SessionStore(Project project) {
		this.project = project;
	}

	void save(AiProvider provider, String sessionId) {
		PropertiesComponent.getInstance(project).setValue(KEY_PREFIX + provider.cliName, sessionId);
	}

	String get(AiProvider provider) {
		return PropertiesComponent.getInstance(project).getValue(KEY_PREFIX + provider.cliName);
	}

	void clear(AiProvider provider) {
		PropertiesComponent.getInstance(project).unsetValue(KEY_PREFIX + provider.cliName);
	}

	void clearAll() {
		for (AiProvider provider : AiProvider.values()) {
			clear(provider);
		}
	}
}
