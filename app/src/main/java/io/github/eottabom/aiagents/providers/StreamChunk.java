package io.github.eottabom.aiagents.providers;

public record StreamChunk(ChunkType type, String content, String toolName, String sessionId) {

	public static StreamChunk text(String content) {
		return new StreamChunk(ChunkType.TEXT, content, null, null);
	}

	public static StreamChunk toolUse(String name) {
		return new StreamChunk(ChunkType.TOOL_USE, null, name, null);
	}

	public static StreamChunk done(String sessionId) {
		return new StreamChunk(ChunkType.DONE, "", null, sessionId);
	}

	public static StreamChunk error(String message) {
		return new StreamChunk(ChunkType.ERROR, message, null, null);
	}
}
