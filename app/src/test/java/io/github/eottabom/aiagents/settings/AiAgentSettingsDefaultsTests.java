package io.github.eottabom.aiagents.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentSettingsDefaultsTests {

	@Test
	void dangerousFlagsAreDisabledByDefault() {
		var state = new AiAgentSettings.State();

		assertThat(state.skipPermissions).as("skipPermissions").isFalse();
		assertThat(state.bypassApprovals).as("bypassApprovals").isFalse();
		assertThat(state.geminiYoloMode).as("geminiYoloMode").isFalse();
	}

	@Test
	void scanDepthDefaultIsSix() {
		var settings = new AiAgentSettings();

		assertThat(settings.getProjectRefsScanDepth()).isEqualTo(6);
	}

	@Test
	void scanDepthGetterClampsExcessiveValue() {
		var settings = new AiAgentSettings();

		var corruptedState = new AiAgentSettings.State();
		corruptedState.projectRefsScanDepth = 100;
		settings.loadState(corruptedState);

		assertThat(settings.getProjectRefsScanDepth()).isEqualTo(20);
	}

	@Test
	void scanDepthGetterFallsBackOnZeroOrNegative() {
		var settings = new AiAgentSettings();

		var corruptedState = new AiAgentSettings.State();
		corruptedState.projectRefsScanDepth = 0;
		settings.loadState(corruptedState);

		assertThat(settings.getProjectRefsScanDepth()).as("zero fallback").isEqualTo(6);

		corruptedState.projectRefsScanDepth = -5;
		settings.loadState(corruptedState);

		assertThat(settings.getProjectRefsScanDepth()).as("negative fallback").isEqualTo(6);
	}

	@Test
	void timeoutGettersFallBackOnZeroOrNegative() {
		var settings = new AiAgentSettings();

		var corruptedState = new AiAgentSettings.State();
		corruptedState.claudeTimeoutSec = 0;
		corruptedState.geminiTimeoutSec = -1;
		corruptedState.codexTimeoutSec = 0;
		settings.loadState(corruptedState);

		assertThat(settings.getClaudeTimeoutSec()).as("claude fallback").isEqualTo(180);
		assertThat(settings.getGeminiTimeoutSec()).as("gemini fallback").isEqualTo(60);
		assertThat(settings.getCodexTimeoutSec()).as("codex fallback").isEqualTo(30);
	}

	@Test
	void timeoutGettersClampLoadedSmallPositiveValues() {
		var settings = new AiAgentSettings();

		var corruptedState = new AiAgentSettings.State();
		corruptedState.claudeTimeoutSec = 5;
		corruptedState.geminiTimeoutSec = 1;
		corruptedState.codexTimeoutSec = 9;
		settings.loadState(corruptedState);

		assertThat(settings.getClaudeTimeoutSec()).as("claude clamp").isEqualTo(10);
		assertThat(settings.getGeminiTimeoutSec()).as("gemini clamp").isEqualTo(10);
		assertThat(settings.getCodexTimeoutSec()).as("codex clamp").isEqualTo(10);
	}

	@Test
	void timeoutSettersEnforceMinimum() {
		var settings = new AiAgentSettings();

		settings.setClaudeTimeoutSec(1);
		settings.setGeminiTimeoutSec(-5);
		settings.setCodexTimeoutSec(0);

		assertThat(settings.getClaudeTimeoutSec()).as("claude min").isEqualTo(10);
		assertThat(settings.getGeminiTimeoutSec()).as("gemini min").isEqualTo(10);
		assertThat(settings.getCodexTimeoutSec()).as("codex min").isEqualTo(10);
	}

	@Test
	void timeoutGettersAndSettersClampMaximum() {
		var settings = new AiAgentSettings();

		var corruptedState = new AiAgentSettings.State();
		corruptedState.claudeTimeoutSec = 9999;
		corruptedState.geminiTimeoutSec = 1000;
		corruptedState.codexTimeoutSec = 601;
		settings.loadState(corruptedState);

		assertThat(settings.getClaudeTimeoutSec()).as("claude max").isEqualTo(600);
		assertThat(settings.getGeminiTimeoutSec()).as("gemini max").isEqualTo(600);
		assertThat(settings.getCodexTimeoutSec()).as("codex max").isEqualTo(600);

		settings.setClaudeTimeoutSec(10_000);
		settings.setGeminiTimeoutSec(10_000);
		settings.setCodexTimeoutSec(10_000);

		assertThat(settings.getClaudeTimeoutSec()).as("claude setter max").isEqualTo(600);
		assertThat(settings.getGeminiTimeoutSec()).as("gemini setter max").isEqualTo(600);
		assertThat(settings.getCodexTimeoutSec()).as("codex setter max").isEqualTo(600);
	}
}
