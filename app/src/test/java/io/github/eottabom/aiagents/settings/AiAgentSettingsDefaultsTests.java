package io.github.eottabom.aiagents.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiAgentSettingsDefaultsTests {

	@Test
	void dangerousFlagsAreDisabledByDefault() {
		var state = new AiAgentSettings.State();

		assertFalse(state.skipPermissions, "skipPermissions must default to false");
		assertFalse(state.bypassApprovals, "bypassApprovals must default to false");
		assertFalse(state.geminiYoloMode, "geminiYoloMode must default to false");
	}

	@Test
	void scanDepthDefaultIsWithinBounds() {
		var settings = new AiAgentSettings();

		var depth = settings.getProjectRefsScanDepth();

		assertTrue(depth > 0, "scan depth must be positive");
		assertTrue(depth <= 20, "scan depth must not exceed 20");
	}

	@Test
	void scanDepthGetterClampsExcessiveValue() {
		var settings = new AiAgentSettings();
		settings.setProjectRefsScanDepth(100);

		var depth = settings.getProjectRefsScanDepth();

		assertTrue(depth <= 20, "scan depth must be clamped to 20 max");
	}

	@Test
	void scanDepthGetterFallsBackOnZeroOrNegative() {
		var settings = new AiAgentSettings();

		var corruptedState = new AiAgentSettings.State();
		corruptedState.projectRefsScanDepth = 0;
		settings.loadState(corruptedState);

		assertEquals(6, settings.getProjectRefsScanDepth(), "zero should fall back to default 6");

		corruptedState.projectRefsScanDepth = -5;
		settings.loadState(corruptedState);

		assertEquals(6, settings.getProjectRefsScanDepth(), "negative should fall back to default 6");
	}

	@Test
	void timeoutGettersFallBackOnZeroOrNegative() {
		var settings = new AiAgentSettings();

		var corruptedState = new AiAgentSettings.State();
		corruptedState.claudeTimeoutSec = 0;
		corruptedState.geminiTimeoutSec = -1;
		corruptedState.codexTimeoutSec = 0;
		settings.loadState(corruptedState);

		assertEquals(180, settings.getClaudeTimeoutSec(), "claude timeout should fall back to 180");
		assertEquals(60, settings.getGeminiTimeoutSec(), "gemini timeout should fall back to 60");
		assertEquals(30, settings.getCodexTimeoutSec(), "codex timeout should fall back to 30");
	}

	@Test
	void timeoutGettersClampSmallPositiveValues() {
		var settings = new AiAgentSettings();

		settings.setClaudeTimeoutSec(5);
		settings.setGeminiTimeoutSec(1);
		settings.setCodexTimeoutSec(9);

		assertTrue(settings.getClaudeTimeoutSec() >= 10, "claude timeout should be clamped to at least 10");
		assertTrue(settings.getGeminiTimeoutSec() >= 10, "gemini timeout should be clamped to at least 10");
		assertTrue(settings.getCodexTimeoutSec() >= 10, "codex timeout should be clamped to at least 10");
	}

	@Test
	void timeoutSettersEnforceMinimum() {
		var settings = new AiAgentSettings();

		settings.setClaudeTimeoutSec(1);
		settings.setGeminiTimeoutSec(-5);
		settings.setCodexTimeoutSec(0);

		assertTrue(settings.getClaudeTimeoutSec() >= 10, "claude timeout min is 10");
		assertTrue(settings.getGeminiTimeoutSec() >= 10, "gemini timeout min is 10");
		assertTrue(settings.getCodexTimeoutSec() >= 10, "codex timeout min is 10");
	}
}
