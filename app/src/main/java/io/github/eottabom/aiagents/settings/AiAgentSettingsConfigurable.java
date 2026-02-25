package io.github.eottabom.aiagents.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Settings > Tools > AI Agents
 */
public class AiAgentSettingsConfigurable implements Configurable {

    @Nls
    @Override
    public String getDisplayName() {
        return "AI Agents";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel();
        panel.add(new JLabel("AI Agents settings — coming soon."));
        return panel;
    }

    @Override
    public boolean isModified() { return false; }

    @Override
    public void apply() {}
}
