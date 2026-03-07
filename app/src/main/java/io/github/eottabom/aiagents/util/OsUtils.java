package io.github.eottabom.aiagents.util;

import java.util.Locale;

public final class OsUtils {
	private static final boolean WINDOWS =
			System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

	private OsUtils() {
	}

	public static boolean isWindows() {
		return WINDOWS;
	}
}
