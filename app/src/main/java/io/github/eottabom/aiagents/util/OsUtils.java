package io.github.eottabom.aiagents.util;

import java.util.Locale;

public final class OsUtils {

    private OsUtils() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
