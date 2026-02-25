package io.github.eottabom.aiagents.providers;

import java.util.List;
import java.util.function.Consumer;

public enum AiProvider {

    CLAUDE("claude") {

        @Override
        protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
            return AiProviderArgsBuilder.buildClaudeArgs(prompt, sessionId, workDir);
        }

        @Override
        public StreamChunk parseLine(String line) {
            return AiProviderParsers.parseClaudeLine(line);
        }

        @Override
        public long timeoutMs() {
            return 180_000;
        }
    },

    GEMINI("gemini") {

        @Override
        protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
            return AiProviderArgsBuilder.buildGeminiArgs(prompt, sessionId);
        }

        @Override
        public StreamChunk parseLine(String line) {
            return AiProviderParsers.parseGeminiLine(line);
        }

        @Override
        public long timeoutMs() {
            return 60_000;
        }
    },

    CODEX("codex") {

        @Override
        protected List<String> buildRunArgs(String prompt, String sessionId, String workDir) {
            return AiProviderArgsBuilder.buildCodexArgs(prompt, sessionId);
        }

        @Override
        public StreamChunk parseLine(String line) {
            return AiProviderParsers.parseCodexLine(line);
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

    public static AiProvider fromName(String name) {
        for (AiProvider provider : values()) {
            if (provider.cliName.equals(name)) {
                return provider;
            }
        }
        return null;
    }
}
