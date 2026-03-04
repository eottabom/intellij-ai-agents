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
	void scanDepthDefaultIsWithinBounds() {
		var settings = new AiAgentSettings();

		assertThat(settings.getProjectRefsScanDepth())
				.isGreaterThan(0)
				.isLessThanOrEqualTo(20);
	}

	@Test
	void scanDepthGetterClampsExcessiveValue() {
		var settings = new AiAgentSettings();
		settings.setProjectRefsScanDepth(100);

		assertThat(settings.getProjectRefsScanDepth()).isLessThanOrEqualTo(20);
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

		assertThat(settings.getClaudeTimeoutSec()).as("claude min").isGreaterThanOrEqualTo(10);
		assertThat(settings.getGeminiTimeoutSec()).as("gemini min").isGreaterThanOrEqualTo(10);
		assertThat(settings.getCodexTimeoutSec()).as("codex min").isGreaterThanOrEqualTo(10);
	}
}
