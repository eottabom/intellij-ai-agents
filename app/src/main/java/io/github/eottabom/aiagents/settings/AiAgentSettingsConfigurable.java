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
        claudeTimeoutSpinner = new JSpinner(new SpinnerNumberModel(180, 10, 600, 10));
        timeoutPanel.add(claudeTimeoutSpinner);
        timeoutPanel.add(new JLabel("Gemini:"));
        geminiTimeoutSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 600, 10));
        timeoutPanel.add(geminiTimeoutSpinner);
        timeoutPanel.add(new JLabel("Codex:"));
        codexTimeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 10, 600, 10));
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
        if (!isUiReady()) {
            return false;
        }

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
        if (settings.getClaudeTimeoutSec() != (int) claudeTimeoutSpinner.getValue()) {
            return true;
        }
        if (settings.getGeminiTimeoutSec() != (int) geminiTimeoutSpinner.getValue()) {
            return true;
        }
        return settings.getCodexTimeoutSec() != (int) codexTimeoutSpinner.getValue();
    }

    @Override
    public void apply() {
        var settings = AiAgentSettings.getInstance();
        if (!isUiReady()) {
            return;
        }

        settings.setRefsConfigPath(refsConfigPathField.getText());
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
        if (!isUiReady()) {
            return;
        }

        refsConfigPathField.setText(settings.getRefsConfigPath());
        extraIgnoredDirsArea.setText(settings.getExtraIgnoredDirsRaw());
        skipPermissionsCheckBox.setSelected(settings.isSkipPermissions());
        bypassApprovalsCheckBox.setSelected(settings.isBypassApprovals());
        geminiYoloModeCheckBox.setSelected(settings.isGeminiYoloMode());
        claudeTimeoutSpinner.setValue(settings.getClaudeTimeoutSec());
        geminiTimeoutSpinner.setValue(settings.getGeminiTimeoutSec());
        codexTimeoutSpinner.setValue(settings.getCodexTimeoutSec());
        scanDepthSpinner.setValue(settings.getProjectRefsScanDepth());
    }

    private boolean isUiReady() {
        if (refsConfigPathField == null || extraIgnoredDirsArea == null) {
            return false;
        }
        if (skipPermissionsCheckBox == null || bypassApprovalsCheckBox == null || geminiYoloModeCheckBox == null) {
            return false;
        }
        if (claudeTimeoutSpinner == null || geminiTimeoutSpinner == null || codexTimeoutSpinner == null) {
            return false;
        }
        return scanDepthSpinner != null;
    }
}
