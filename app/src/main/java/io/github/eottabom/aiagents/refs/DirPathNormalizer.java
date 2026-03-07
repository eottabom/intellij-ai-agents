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
		value = value.replace("\\", "/").replaceAll("^/+|/+$", "");
		if (value.isBlank()) {
			return null;
		}
		if (value.contains("/../") || value.startsWith("../") || value.endsWith("/..") || value.equals("..")) {
			return null;
		}
		return value.toLowerCase(Locale.ROOT);
	}
}
