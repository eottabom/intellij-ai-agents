package io.github.eottabom.aiagents.providers;

import java.util.ArrayList;
import java.util.List;

final class CliArgsBuilder {

    private CliArgsBuilder() {
    }

    static List<String> buildClaudeArgs(String prompt, String sessionId, String workDir, boolean skipPermissions) {
        var args = new ArrayList<>(List.of(
                "--print", "--output-format", "stream-json", "--verbose"));
        if (skipPermissions) {
            args.add("--dangerously-skip-permissions");
        }
        if (hasSession(sessionId)) {
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
        var args = new ArrayList<String>();
        args.add("--output-format");
        args.add("stream-json");
        if (yoloMode) {
            args.add("--approval-mode");
            args.add("yolo");
            args.add("--no-sandbox");
        }
        if (hasSession(sessionId)) {
            args.add("--resume");
            args.add(sessionId);
        }
        args.add("-p");
        args.add(prompt);
        return args;
    }

    static List<String> buildCodexArgs(String prompt, String sessionId, boolean bypassApprovals) {
        var args = new ArrayList<String>();
        args.add("exec");
        if (bypassApprovals) {
            args.add("--dangerously-bypass-approvals-and-sandbox");
        }
        if (hasSession(sessionId)) {
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

    private static boolean hasSession(String sessionId) {
        return sessionId != null && !sessionId.isBlank();
    }
}
