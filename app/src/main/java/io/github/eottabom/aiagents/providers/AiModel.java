package io.github.eottabom.aiagents.providers;

/**
 * AI 프로바이더별 선택 가능한 모델 정보
 */
public record AiModel(String id, String displayName) {

	@Override
	public String toString() {
		return displayName;
	}
}