package io.github.eottabom.aiagents.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tool Window 팩토리 — IntelliJ 우측 패널에 AI Agents 채팅 UI 등록
 */
public class AiAgentToolWindowFactory implements ToolWindowFactory {

    private static final Logger logger = LoggerFactory.getLogger(AiAgentToolWindowFactory.class);
    private static final List<String> ALL_PROVIDERS = List.of("claude", "gemini", "codex");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        var panel = new AiAgentPanel(project, ALL_PROVIDERS);
        Disposer.register(project, panel);
        var content = ContentFactory.getInstance()
                .createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            var detected = detectInstalledProviders();
            panel.updateInstalledProviders(detected);
        });
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    private List<String> detectInstalledProviders() {
        List<String> installed = new ArrayList<>();
        String whichCmd = isWindows() ? "where" : "which";
        for (String provider : ALL_PROVIDERS) {
            if (isCliInstalled(whichCmd, provider)) {
                installed.add(provider);
            }
        }
        if (installed.isEmpty()) {
            logger.warn("No AI CLI tools detected. Falling back to all providers.");
            return ALL_PROVIDERS;
        }
        return installed;
    }

    private boolean isCliInstalled(String whichCmd, String cliName) {
        try {
            var process = new ProcessBuilder(whichCmd, cliName)
                    .redirectErrorStream(true)
                    .start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // drain output
                while (reader.readLine() != null) { /* consume */ }
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.debug("Failed to check CLI installation for {}: {}", cliName, e.getMessage());
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
