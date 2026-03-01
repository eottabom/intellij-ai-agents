package io.github.eottabom.aiagents.refs;

import java.util.Locale;

public final class DirPathNormalizer {

    private DirPathNormalizer() {
    }

    public static String normalize(String token) {
        if (token == null) {
            return null;
        }
        var value = token.trim();
        if (value.isBlank()) {
            return null;
        }
        value = value.replace("\\", "/");
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
