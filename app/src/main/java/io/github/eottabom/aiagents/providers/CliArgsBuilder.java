package io.github.eottabom.aiagents.providers;

import java.util.ArrayList;
import java.util.List;

final class CliArgsBuilder {

	private CliArgsBuilder() {
	}

	static List<String> buildClaudeArgs(String prompt, String sessionId, String workDir, boolean skipPermissions, String model) {
		var args = new ArrayList<>(List.of(
				"--print", "--output-format", "stream-json", "--verbose"));
		if (skipPermissions) {
			args.add("--dangerously-skip-permissions");
		}
		if (hasModel(model)) {
			args.add("--model");
			args.add(model);
		}
		if (hasSession(sessionId)) {
			args.add("--resume");
			args.add(sessionId);
		}
		if (workDir != null && !workDir.isBlank()) {
			args.add("--add-dir");
			args.add(workDir);
		}
		args.add("-p");
		args.add(prompt);
		return args;
	}

	static List<String> buildGeminiArgs(String prompt, String sessionId, boolean yoloMode, String model) {
		var args = new ArrayList<String>();
		args.add("--output-format");
		args.add("stream-json");
		if (hasModel(model)) {
			args.add("-m");
			args.add(model);
		}
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

	static List<String> buildCodexArgs(String prompt, String sessionId, boolean bypassApprovals, String model) {
		var args = new ArrayList<String>();
		args.add("exec");
		// --dangerously-bypass-approvals-and-sandbox 는 exec 서브커맨드의 전역 옵션
		if (bypassApprovals) {
			args.add("--dangerously-bypass-approvals-and-sandbox");
		}
		if (hasModel(model)) {
			args.add("--model");
			args.add(model);
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

	private static boolean hasModel(String model) {
		return model != null && !model.isBlank();
	}
}
