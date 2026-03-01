package io.github.eottabom.aiagents.providers;

import io.github.eottabom.aiagents.settings.AiAgentSettings;

import java.util.List;
import java.util.function.Consumer;

public enum AiProvider {

    CLAUDE("claude") {

        @Override
        protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
            var settings = AiAgentSettings.getInstance();
            boolean skip = settings != null && settings.isSkipPermissions();
            return AiProviderArgsBuilder.buildClaudeArgs(prompt, sessionId, workDir, skip);
        }

        @Override
        public StreamChunk parseLine(String line) {
            return AiProviderParsers.parseClaudeLine(line);
        }

        @Override
        public long timeoutMs() {
            var settings = AiAgentSettings.getInstance();
            return settings != null ? settings.getClaudeTimeoutSec() * 1000L : 180_000;
        }
    },

    GEMINI("gemini") {

        @Override
        protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
            var settings = AiAgentSettings.getInstance();
            boolean yolo = settings != null && settings.isGeminiYoloMode();
            return AiProviderArgsBuilder.buildGeminiArgs(prompt, sessionId, yolo);
        }

        @Override
        public StreamChunk parseLine(String line) {
            return AiProviderParsers.parseGeminiLine(line);
        }

        @Override
        public long timeoutMs() {
            var settings = AiAgentSettings.getInstance();
            return settings != null ? settings.getGeminiTimeoutSec() * 1000L : 60_000;
        }
    },

    CODEX("codex") {

        @Override
        protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
            var settings = AiAgentSettings.getInstance();
            boolean bypass = settings != null && settings.isBypassApprovals();
            return AiProviderArgsBuilder.buildCodexArgs(prompt, sessionId, bypass);
        }

        @Override
        public StreamChunk parseLine(String line) {
            return AiProviderParsers.parseCodexLine(line);
        }

        @Override
        public long timeoutMs() {
            var settings = AiAgentSettings.getInstance();
            return settings != null ? settings.getCodexTimeoutSec() * 1000L : 30_000;
        }
    };

    public final String cliName;

    AiProvider(String cliName) {
        this.cliName = cliName;
    }

    protected abstract List<String> buildRunArgs(String prompt, String sessionId, String workDir);
    public abstract StreamChunk parseLine(String line);

    public long timeoutMs() {
        return 30_000;
    }

    public void run(String prompt, String sessionId, String workDir, Consumer<StreamChunk> onChunk) {
        AiProviderProcessRunner.run(this, prompt, sessionId, workDir, onChunk);
    }

    public void runDoctor(String workDir, Consumer<StreamChunk> onChunk) {
        AiProviderProcessRunner.runSubcommand(this, "--version", workDir, onChunk);
    }

    public static AiProvider fromName(String name) {
        for (AiProvider provider : values()) {
            if (provider.cliName.equals(name)) {
                return provider;
            }
        }
        return null;
    }
}
