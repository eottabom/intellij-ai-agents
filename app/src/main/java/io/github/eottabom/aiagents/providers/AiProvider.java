package io.github.eottabom.aiagents.providers;

import io.github.eottabom.aiagents.settings.AiAgentSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public enum AiProvider {

	CLAUDE("claude") {

		@Override
		protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
			var settings = AiAgentSettings.getInstanceOrDefaults();
			var isSkip = settings.isSkipPermissions();
			var model = settings.getSelectedModel(cliName);
			return CliArgsBuilder.buildClaudeArgs(prompt, sessionId, workDir, isSkip, model);
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
			var isYolo = settings.isGeminiYoloMode();
			var model = settings.getSelectedModel(cliName);
			return CliArgsBuilder.buildGeminiArgs(prompt, sessionId, isYolo, model);
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
			var isBypass = settings.isBypassApprovals();
			var model = settings.getSelectedModel(cliName);
			return CliArgsBuilder.buildCodexArgs(prompt, sessionId, isBypass, model);
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

	/**
	 * default-models.json 리소스에서 로드한 기본 모델 목록
	 */
	public List<AiModel> getDefaultModels() {
		return DefaultModelLoader.getModels(cliName);
	}

	/**
	 * 기본 모델 + 사용자 커스텀 모델을 합쳐서 반환
	 */
	public List<AiModel> getAllModels() {
		var defaults = getDefaultModels();
		var settings = AiAgentSettings.getInstanceOrDefaults();
		var customModels = settings.getCustomModels(cliName);
		if (customModels.isEmpty()) {
			return defaults;
		}
		var all = new ArrayList<>(defaults);
		all.addAll(customModels);
		return List.copyOf(all);
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
