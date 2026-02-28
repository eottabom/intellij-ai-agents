package io.github.eottabom.aiagents.providers;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiProviderJsonUtilsTests {

    @Test
    void extractGeminiTextExtractsFromPartsArray() {
        var element = JsonParser.parseString("""
                {"parts":[{"text":"line1"},{"text":"line2"}]}
                """);

        var text = AiProviderJsonUtils.extractGeminiText(element);

        assertEquals("line1\nline2", text);
    }

    @Test
    void extractGeminiTextReturnsNullForNonStringPrimitive() {
        var element = JsonParser.parseString("123");

        var text = AiProviderJsonUtils.extractGeminiText(element);

        assertNull(text);
    }
}
