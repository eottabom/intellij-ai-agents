package io.github.eottabom.aiagents.providers;

import java.util.ArrayList;
import java.util.List;

final class AiProviderArgsBuilder {

    private AiProviderArgsBuilder() {
    }

    static List<String> buildClaudeArgs(String prompt, String sessionId, String workDir, boolean skipPermissions) {
        List<String> args = new ArrayList<>(List.of(
                "--print", "--output-format", "stream-json", "--verbose"));
        if (skipPermissions) {
            args.add("--dangerously-skip-permissions");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            args.add("--resume");
            args.add(sessionId);
        }
        if (workDir != null) {
            args.add("--add-dir");
            args.add(workDir);
        }
        args.add("-p");
        args.add(prompt);
        return args;
    }

    static List<String> buildGeminiArgs(String prompt, String sessionId, boolean yoloMode) {
        List<String> args = new ArrayList<>();
        args.add("--output-format");
        args.add("stream-json");
        if (yoloMode) {
            args.add("--approval-mode");
            args.add("yolo");
            args.add("--no-sandbox");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            args.add("--resume");
            args.add(sessionId);
        }
        args.add("-p");
        args.add(prompt);
        return args;
    }

    static List<String> buildCodexArgs(String prompt, String sessionId, boolean bypassApprovals) {
        List<String> args = new ArrayList<>();
        args.add("exec");
        if (bypassApprovals) {
            args.add("--dangerously-bypass-approvals-and-sandbox");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            args.add("resume");
            args.add("--json");
            args.add("--skip-git-repo-check");
            args.add(sessionId);
            args.add(prompt);
            return args;
        }

        args.add("--json");
        args.add("--skip-git-repo-check");
        args.add(prompt);
        return args;
    }
}
