package io.github.eottabom.aiagents.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Settings > Tools > AI Agents
 */
public class AiAgentSettingsConfigurable implements Configurable {

    private JTextField refsConfigPathField;
    private JTextArea extraIgnoredDirsArea;

    @Nls
    @Override
    public String getDisplayName() {
        return "AI Agents";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        var mainPanel = new JPanel(new BorderLayout(8, 8));

        var formPanel = new JPanel(new GridBagLayout());
        var constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.gridx = 0;
        constraints.gridy = 0;

        formPanel.add(new JLabel("Project refs config path (relative to project root)"), constraints);
        constraints.gridy++;
        refsConfigPathField = new JTextField();
        formPanel.add(refsConfigPathField, constraints);

        constraints.gridy++;
        formPanel.add(new JLabel("Extra excluded directories (comma or newline separated)"), constraints);
        constraints.gridy++;
        extraIgnoredDirsArea = new JTextArea(6, 40);
        extraIgnoredDirsArea.setLineWrap(true);
        extraIgnoredDirsArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(extraIgnoredDirsArea), constraints);

        constraints.gridy++;
        var hintLabel = new JLabel("<html>Config file example: <code>{\"ignoreDirs\":[\"coverage\",\"tmp\"]}</code></html>");
        formPanel.add(hintLabel, constraints);

        mainPanel.add(formPanel, BorderLayout.NORTH);
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        var settings = AiAgentSettings.getInstance();
        if (settings == null) {
            return false;
        }

        if (!isUiReady()) {
            return false;
        }

        return !settings.getRefsConfigPath().equals(refsConfigPathField.getText().trim())
                || !settings.getExtraIgnoredDirsRaw().equals(extraIgnoredDirsArea.getText());
    }

    @Override
    public void apply() {
        var settings = AiAgentSettings.getInstance();
        if (settings == null) {
            return;
        }

        if (!isUiReady()) {
            return;
        }

        settings.setRefsConfigPath(refsConfigPathField.getText());
        settings.setExtraIgnoredDirsRaw(extraIgnoredDirsArea.getText());
    }

    @Override
    public void reset() {
        var settings = AiAgentSettings.getInstance();
        if (settings == null) {
            return;
        }

        if (!isUiReady()) {
            return;
        }

        refsConfigPathField.setText(settings.getRefsConfigPath());
        extraIgnoredDirsArea.setText(settings.getExtraIgnoredDirsRaw());
    }

    private boolean isUiReady() {
        var hasRefsConfigField = refsConfigPathField != null;
        var hasIgnoredDirsArea = extraIgnoredDirsArea != null;
        return hasRefsConfigField && hasIgnoredDirsArea;
    }
}
