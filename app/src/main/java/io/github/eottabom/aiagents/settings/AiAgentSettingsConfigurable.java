package io.github.eottabom.aiagents.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Settings > Tools > AI Agents
 */
public class AiAgentSettingsConfigurable implements Configurable {
    private static final int MIN_TIMEOUT_SECONDS = 10;
    private static final int MAX_TIMEOUT_SECONDS = 600;


    private JTextField refsConfigPathField;
    private JTextArea extraIgnoredDirsArea;
    private JCheckBox skipPermissionsCheckBox;
    private JCheckBox bypassApprovalsCheckBox;
    private JCheckBox geminiYoloModeCheckBox;
    private JSpinner claudeTimeoutSpinner;
    private JSpinner geminiTimeoutSpinner;
    private JSpinner codexTimeoutSpinner;
    private JSpinner scanDepthSpinner;

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

        addSecurityFlagsSection(formPanel, constraints);
        addTimeoutSection(formPanel, constraints);
        addScanDepthSection(formPanel, constraints);

        mainPanel.add(formPanel, BorderLayout.NORTH);
        return mainPanel;
    }

    private void addSecurityFlagsSection(JPanel formPanel, GridBagConstraints constraints) {
        constraints.gridy++;
        formPanel.add(Box.createVerticalStrut(12), constraints);
        constraints.gridy++;
        formPanel.add(new JLabel("CLI Permission Flags"), constraints);

        constraints.gridy++;
        var warningLabel = new JLabel("<html><b>Warning:</b> These flags skip CLI safety prompts. Disable for untrusted projects.</html>");
        warningLabel.setForeground(JBColor.namedColor(
                "Label.warningForeground",
                new JBColor(new Color(204, 120, 50), new Color(204, 120, 50))));
        formPanel.add(warningLabel, constraints);

        constraints.gridy++;
        skipPermissionsCheckBox = new JCheckBox("Claude: --dangerously-skip-permissions");
        formPanel.add(skipPermissionsCheckBox, constraints);

        constraints.gridy++;
        bypassApprovalsCheckBox = new JCheckBox("Codex: --dangerously-bypass-approvals-and-sandbox");
        formPanel.add(bypassApprovalsCheckBox, constraints);

        constraints.gridy++;
        geminiYoloModeCheckBox = new JCheckBox("Gemini: --approval-mode yolo --no-sandbox");
        formPanel.add(geminiYoloModeCheckBox, constraints);
    }

    private void addTimeoutSection(JPanel formPanel, GridBagConstraints constraints) {
        constraints.gridy++;
        formPanel.add(Box.createVerticalStrut(12), constraints);
        constraints.gridy++;
        formPanel.add(new JLabel("Timeout Settings (seconds)"), constraints);

        constraints.gridy++;
        var timeoutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        timeoutPanel.add(new JLabel("Claude:"));
        claudeTimeoutSpinner = new JSpinner(new SpinnerNumberModel(180, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS, MIN_TIMEOUT_SECONDS));
        timeoutPanel.add(claudeTimeoutSpinner);
        timeoutPanel.add(new JLabel("Gemini:"));
        geminiTimeoutSpinner = new JSpinner(new SpinnerNumberModel(60, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS, MIN_TIMEOUT_SECONDS));
        timeoutPanel.add(geminiTimeoutSpinner);
        timeoutPanel.add(new JLabel("Codex:"));
        codexTimeoutSpinner = new JSpinner(new SpinnerNumberModel(30, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS, MIN_TIMEOUT_SECONDS));
        timeoutPanel.add(codexTimeoutSpinner);
        formPanel.add(timeoutPanel, constraints);
    }

    private void addScanDepthSection(JPanel formPanel, GridBagConstraints constraints) {
        constraints.gridy++;
        formPanel.add(Box.createVerticalStrut(12), constraints);
        constraints.gridy++;
        var depthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        depthPanel.add(new JLabel("Project refs scan depth:"));
        scanDepthSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 20, 1));
        depthPanel.add(scanDepthSpinner);
        formPanel.add(depthPanel, constraints);
    }

    @Override
    public boolean isModified() {
        var settings = AiAgentSettings.getInstance();

        if (isRefsConfigModified(settings)) {
            return true;
        }
        if (isSecurityFlagsModified(settings)) {
            return true;
        }
        if (isTimeoutsModified(settings)) {
            return true;
        }
        return settings.getProjectRefsScanDepth() != (int) scanDepthSpinner.getValue();
    }

    private boolean isRefsConfigModified(AiAgentSettings settings) {
        if (!settings.getRefsConfigPath().equals(refsConfigPathField.getText().trim())) {
            return true;
        }
        return !settings.getExtraIgnoredDirsRaw().equals(extraIgnoredDirsArea.getText());
    }

    private boolean isSecurityFlagsModified(AiAgentSettings settings) {
        if (settings.isSkipPermissions() != skipPermissionsCheckBox.isSelected()) {
            return true;
        }
        if (settings.isBypassApprovals() != bypassApprovalsCheckBox.isSelected()) {
            return true;
        }
        return settings.isGeminiYoloMode() != geminiYoloModeCheckBox.isSelected();
    }

    private boolean isTimeoutsModified(AiAgentSettings settings) {
        var normalizedClaudeTimeout = clamp(settings.getClaudeTimeoutSec(), MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
        if (normalizedClaudeTimeout != (int) claudeTimeoutSpinner.getValue()) {
            return true;
        }
        var normalizedGeminiTimeout = clamp(settings.getGeminiTimeoutSec(), MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
        if (normalizedGeminiTimeout != (int) geminiTimeoutSpinner.getValue()) {
            return true;
        }
        var normalizedCodexTimeout = clamp(settings.getCodexTimeoutSec(), MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
        return normalizedCodexTimeout != (int) codexTimeoutSpinner.getValue();
    }

    @Override
    public void apply() {
        var settings = AiAgentSettings.getInstance();

        settings.setRefsConfigPath(refsConfigPathField.getText().trim());
        settings.setExtraIgnoredDirsRaw(extraIgnoredDirsArea.getText());
        settings.setSkipPermissions(skipPermissionsCheckBox.isSelected());
        settings.setBypassApprovals(bypassApprovalsCheckBox.isSelected());
        settings.setGeminiYoloMode(geminiYoloModeCheckBox.isSelected());
        settings.setClaudeTimeoutSec((int) claudeTimeoutSpinner.getValue());
        settings.setGeminiTimeoutSec((int) geminiTimeoutSpinner.getValue());
        settings.setCodexTimeoutSec((int) codexTimeoutSpinner.getValue());
        settings.setProjectRefsScanDepth((int) scanDepthSpinner.getValue());
    }

    @Override
    public void reset() {
        var settings = AiAgentSettings.getInstance();

        refsConfigPathField.setText(settings.getRefsConfigPath());
        extraIgnoredDirsArea.setText(settings.getExtraIgnoredDirsRaw());
        skipPermissionsCheckBox.setSelected(settings.isSkipPermissions());
        bypassApprovalsCheckBox.setSelected(settings.isBypassApprovals());
        geminiYoloModeCheckBox.setSelected(settings.isGeminiYoloMode());
        claudeTimeoutSpinner.setValue(clamp(settings.getClaudeTimeoutSec(), MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS));
        geminiTimeoutSpinner.setValue(clamp(settings.getGeminiTimeoutSec(), MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS));
        codexTimeoutSpinner.setValue(clamp(settings.getCodexTimeoutSec(), MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS));
        scanDepthSpinner.setValue(settings.getProjectRefsScanDepth());
    }

    private static int clamp(int value, int minimumValue, int maximumValue) {
        return Math.max(minimumValue, Math.min(maximumValue, value));
    }

    @Override
    public void disposeUIResources() {
        refsConfigPathField = null;
        extraIgnoredDirsArea = null;
        skipPermissionsCheckBox = null;
        bypassApprovalsCheckBox = null;
        geminiYoloModeCheckBox = null;
        claudeTimeoutSpinner = null;
        geminiTimeoutSpinner = null;
        codexTimeoutSpinner = null;
        scanDepthSpinner = null;
    }

}
