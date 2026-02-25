package io.github.eottabom.aiagents.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tool Window 팩토리 — IntelliJ 우측 패널에 AI Agents 채팅 UI 등록
 * CLI 설치 여부는 시작 시 확인하지 않음 — /doctor 명령으로 각 CLI에서 직접 확인
 */
public class AiAgentToolWindowFactory implements ToolWindowFactory {

    private static final List<String> ALL_PROVIDERS = List.of("claude", "gemini", "codex");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        AiAgentPanel panel = new AiAgentPanel(project, ALL_PROVIDERS);
        Content content = ContentFactory.getInstance()
                .createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
}
