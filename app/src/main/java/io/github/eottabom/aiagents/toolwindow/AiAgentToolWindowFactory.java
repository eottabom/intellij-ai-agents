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

import io.github.eottabom.aiagents.util.OsUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            if (!project.isDisposed()) {
                panel.updateInstalledProviders(detected);
            }
        });
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    private List<String> detectInstalledProviders() {
        List<String> installed = new ArrayList<>();
        String whichCmd = OsUtils.isWindows() ? "where" : "which";
        for (String provider : ALL_PROVIDERS) {
            if (isCliInstalled(whichCmd, provider)) {
                installed.add(provider);
            }
        }
        if (installed.isEmpty()) {
            logger.warn("No AI CLI tools detected.");
            return Collections.emptyList();
        }
        return installed;
    }

    private boolean isCliInstalled(String whichCmd, String cliName) {
        try {
            var process = new ProcessBuilder(whichCmd, cliName)
                    .redirectErrorStream(true)
                    .start();
            var processFinished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!processFinished) {
                process.destroyForcibly();
                return false;
            }
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // consume
                }
            }
            return process.exitValue() == 0;
        } catch (Exception ex) {
            logger.debug("Failed to check CLI installation for {}: {}", cliName, ex.getMessage());
            return false;
        }
    }

}
