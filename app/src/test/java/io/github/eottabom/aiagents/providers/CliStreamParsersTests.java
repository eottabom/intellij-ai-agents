package io.github.eottabom.aiagents.providers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CliStreamParsersTests {

	@Test
	@ResourceLock(Resources.LOCALE)
	void parseGeminiLineParsesInitEventUnderTurkishLocale() {
		var previousLocale = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));

			var line = "{\"type\":\"INIT\",\"session_id\":\"session-123\"}";
			var chunk = CliStreamParsers.parseGeminiLine(line);

			assertThat(chunk).isNotNull();
			assertThat(chunk.type()).isEqualTo(ChunkType.DONE);
			assertThat(chunk.sessionId()).isEqualTo("session-123");
		} finally {
			Locale.setDefault(previousLocale);
		}
	}

	@Test
	void parseGeminiLineReturnsNullForUnsupportedMessageRole() {
		var line = """
				{"type":"message","role":"user","content":{"text":"hello"}}
				""";

		var chunk = CliStreamParsers.parseGeminiLine(line);

		assertThat(chunk).isNull();
	}

	@Test
	void parseCodexLineParsesCommandExecutionOutput() {
		var line = """
				{"type":"item.completed","item":{"type":"command_execution","aggregated_output":"  build ok  "}}
				""";

		var chunk = CliStreamParsers.parseCodexLine(line);

		assertThat(chunk).isNotNull();
		assertThat(chunk.type()).isEqualTo(ChunkType.TEXT);
		assertThat(chunk.content()).isEqualTo("build ok");
	}
}
