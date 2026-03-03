package io.github.eottabom.aiagents.providers;

import io.github.eottabom.aiagents.settings.AiAgentSettings;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public enum AiProvider {

	CLAUDE("claude") {

		@Override
		protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
			var settings = AiAgentSettings.getInstanceOrDefaults();
			boolean skip = settings.isSkipPermissions();
			return CliArgsBuilder.buildClaudeArgs(prompt, sessionId, workDir, skip);
		}

		@Override
		public StreamChunk parseLine(String line) {
			return CliStreamParsers.parseClaudeLine(line);
		}

		@Override
		public long timeoutMs() {
			return AiAgentSettings.getInstanceOrDefaults().getClaudeTimeoutSec() * 1000L;
		}
	},

	GEMINI("gemini") {

		@Override
		protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
			var settings = AiAgentSettings.getInstanceOrDefaults();
			boolean yolo = settings.isGeminiYoloMode();
			return CliArgsBuilder.buildGeminiArgs(prompt, sessionId, yolo);
		}

		@Override
		public StreamChunk parseLine(String line) {
			return CliStreamParsers.parseGeminiLine(line);
		}

		@Override
		public long timeoutMs() {
			return AiAgentSettings.getInstanceOrDefaults().getGeminiTimeoutSec() * 1000L;
		}
	},

	CODEX("codex") {

		@Override
		protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
			var settings = AiAgentSettings.getInstanceOrDefaults();
			boolean bypass = settings.isBypassApprovals();
			return CliArgsBuilder.buildCodexArgs(prompt, sessionId, bypass);
		}

		@Override
		public StreamChunk parseLine(String line) {
			return CliStreamParsers.parseCodexLine(line);
		}

		@Override
		public long timeoutMs() {
			return AiAgentSettings.getInstanceOrDefaults().getCodexTimeoutSec() * 1000L;
		}
	};

	public final String cliName;

	AiProvider(String cliName) {
		this.cliName = cliName;
	}

	protected abstract List<String> buildRunArgs(String prompt, String sessionId, String workDir);
	public abstract StreamChunk parseLine(String line);
	public abstract long timeoutMs();

	public void run(String prompt, String sessionId, String workDir, Consumer<StreamChunk> onChunk) {
		CliProcessRunner.run(this, prompt, sessionId, workDir, onChunk);
	}

	public void runDoctor(String workDir, Consumer<StreamChunk> onChunk) {
		CliProcessRunner.runSubcommand(this, "--version", workDir, onChunk);
	}

	public static Optional<AiProvider> fromName(String name) {
		if (name == null) {
			return Optional.empty();
		}
		for (AiProvider provider : values()) {
			if (provider.cliName.equals(name)) {
				return Optional.of(provider);
			}
		}
		return Optional.empty();
	}
}
