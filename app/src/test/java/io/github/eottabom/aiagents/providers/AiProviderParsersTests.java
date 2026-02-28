package io.github.eottabom.aiagents.providers;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderParsersTests {

    @Test
    void parseGeminiLineParsesInitEventUnderTurkishLocale() {
        var previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            var line = "{\"type\":\"INIT\",\"session_id\":\"session-123\"}";
            var chunk = AiProviderParsers.parseGeminiLine(line);

            assertNotNull(chunk);
            assertEquals(ChunkType.DONE, chunk.type());
            assertEquals("session-123", chunk.sessionId());
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void parseGeminiLineReturnsNullForUnsupportedMessageRole() {
        var line = """
                {"type":"message","role":"user","content":{"text":"hello"}}
                """;

        var chunk = AiProviderParsers.parseGeminiLine(line);

        assertNull(chunk);
    }

    @Test
    void parseCodexLineParsesCommandExecutionOutput() {
        var line = """
                {"type":"item.completed","item":{"type":"command_execution","aggregated_output":"  build ok  "}}
                """;

        var chunk = AiProviderParsers.parseCodexLine(line);

        assertNotNull(chunk);
        assertEquals(ChunkType.TEXT, chunk.type());
        assertEquals("build ok", chunk.content());
    }
}
