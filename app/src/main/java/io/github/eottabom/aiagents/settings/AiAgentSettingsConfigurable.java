package io.github.eottabom.aiagents.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import io.github.eottabom.aiagents.providers.AiModel;
import io.github.eottabom.aiagents.providers.AiProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings > Tools > AI Agents
 */
public class AiAgentSettingsConfigurable implements Configurable {
	private static final int TIMEOUT_STEP_SECONDS = 1;

	private JTextField refsConfigPathField;
	private JTextArea extraIgnoredDirsArea;
	private JCheckBox skipPermissionsCheckBox;
	private JCheckBox bypassApprovalsCheckBox;
	private JCheckBox geminiYoloModeCheckBox;
	private JSpinner claudeTimeoutSpinner;
	private JSpinner geminiTimeoutSpinner;
	private JSpinner codexTimeoutSpinner;
	private JSpinner scanDepthSpinner;
	private final Map<String, JComboBox<AiModel>> modelComboBoxes = new LinkedHashMap<>();
	private final Map<String, JTextField> customModelFields = new LinkedHashMap<>();

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

		addModelSelectionSection(formPanel, constraints);
		addSecurityFlagsSection(formPanel, constraints);
		addTimeoutSection(formPanel, constraints);
		addScanDepthSection(formPanel, constraints);

		var scrollPane = new JScrollPane(formPanel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		mainPanel.add(scrollPane, BorderLayout.CENTER);
		return mainPanel;
	}

	private void addModelSelectionSection(JPanel formPanel, GridBagConstraints constraints) {
		constraints.gridy++;
		formPanel.add(Box.createVerticalStrut(12), constraints);
		constraints.gridy++;
		formPanel.add(new JLabel("Model Settings"), constraints);

		for (AiProvider provider : AiProvider.values()) {
			constraints.gridy++;
			var providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			providerPanel.add(new JLabel(provider.cliName + " model:"));

			var comboBox = new JComboBox<AiModel>();
			comboBox.addItem(null);
			for (AiModel model : provider.getAllModels()) {
				comboBox.addItem(model);
			}
			comboBox.setRenderer(new DefaultListCellRenderer() {
				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
					var text = value == null ? "(default)" : ((AiModel) value).displayName();
					return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
				}
			});
			providerPanel.add(comboBox);
			modelComboBoxes.put(provider.cliName, comboBox);
			formPanel.add(providerPanel, constraints);
		}

		constraints.gridy++;
		formPanel.add(Box.createVerticalStrut(8), constraints);
		constraints.gridy++;
		formPanel.add(new JLabel("<html>Custom models (format: <code>model-id</code> or <code>model-id:display-name</code>, comma separated)</html>"), constraints);

		for (AiProvider provider : AiProvider.values()) {
			constraints.gridy++;
			var customPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			customPanel.add(new JLabel(provider.cliName + ":"));
			var textField = new JTextField(30);
			customPanel.add(textField);
			customModelFields.put(provider.cliName, textField);
			formPanel.add(customPanel, constraints);
		}
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
		claudeTimeoutSpinner = new JSpinner(new SpinnerNumberModel(180, AiAgentSettings.MIN_TIMEOUT_SECONDS, AiAgentSettings.MAX_TIMEOUT_SECONDS, TIMEOUT_STEP_SECONDS));
		timeoutPanel.add(claudeTimeoutSpinner);
		timeoutPanel.add(new JLabel("Gemini:"));
		geminiTimeoutSpinner = new JSpinner(new SpinnerNumberModel(60, AiAgentSettings.MIN_TIMEOUT_SECONDS, AiAgentSettings.MAX_TIMEOUT_SECONDS, TIMEOUT_STEP_SECONDS));
		timeoutPanel.add(geminiTimeoutSpinner);
		timeoutPanel.add(new JLabel("Codex:"));
		codexTimeoutSpinner = new JSpinner(new SpinnerNumberModel(30, AiAgentSettings.MIN_TIMEOUT_SECONDS, AiAgentSettings.MAX_TIMEOUT_SECONDS, TIMEOUT_STEP_SECONDS));
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
		if (isModelSettingsModified(settings)) {
			return true;
		}
		if (isSecurityFlagsModified(settings)) {
			return true;
		}
		if (isTimeoutsModified(settings)) {
			return true;
		}
		return clampScanDepth(settings.getProjectRefsScanDepth()) != (int) scanDepthSpinner.getValue();
	}

	private boolean isModelSettingsModified(AiAgentSettings settings) {
		for (AiProvider provider : AiProvider.values()) {
			var comboBox = modelComboBoxes.get(provider.cliName);
			if (comboBox != null) {
				var selected = (AiModel) comboBox.getSelectedItem();
				var selectedId = selected != null ? selected.id() : null;
				var savedId = settings.getSelectedModel(provider.cliName);
				if (!java.util.Objects.equals(selectedId, savedId)) {
					return true;
				}
			}
			var customField = customModelFields.get(provider.cliName);
			if (customField != null) {
				if (!settings.getCustomModelsRaw(provider.cliName).equals(customField.getText())) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isRefsConfigModified(AiAgentSettings settings) {
		var savedPath = normalizeRefsConfigPath(settings.getRefsConfigPath());
		var currentPath = normalizeRefsConfigPath(refsConfigPathField.getText());
		if (!savedPath.equals(currentPath)) {
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
		var normalizedClaudeTimeout = clamp(settings.getClaudeTimeoutSec());
		if (normalizedClaudeTimeout != (int) claudeTimeoutSpinner.getValue()) {
			return true;
		}
		var normalizedGeminiTimeout = clamp(settings.getGeminiTimeoutSec());
		if (normalizedGeminiTimeout != (int) geminiTimeoutSpinner.getValue()) {
			return true;
		}
		var normalizedCodexTimeout = clamp(settings.getCodexTimeoutSec());
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

		for (AiProvider provider : AiProvider.values()) {
			var customField = customModelFields.get(provider.cliName);
			if (customField != null) {
				settings.setCustomModelsRaw(provider.cliName, customField.getText());
			}
			var comboBox = modelComboBoxes.get(provider.cliName);
			if (comboBox != null) {
				var selected = (AiModel) comboBox.getSelectedItem();
				settings.setSelectedModel(provider.cliName, selected != null ? selected.id() : null);
			}
		}
	}

	@Override
	public void reset() {
		var settings = AiAgentSettings.getInstance();

		refsConfigPathField.setText(settings.getRefsConfigPath());
		extraIgnoredDirsArea.setText(settings.getExtraIgnoredDirsRaw());
		skipPermissionsCheckBox.setSelected(settings.isSkipPermissions());
		bypassApprovalsCheckBox.setSelected(settings.isBypassApprovals());
		geminiYoloModeCheckBox.setSelected(settings.isGeminiYoloMode());
		claudeTimeoutSpinner.setValue(clamp(settings.getClaudeTimeoutSec()));
		geminiTimeoutSpinner.setValue(clamp(settings.getGeminiTimeoutSec()));
		codexTimeoutSpinner.setValue(clamp(settings.getCodexTimeoutSec()));
		scanDepthSpinner.setValue(clampScanDepth(settings.getProjectRefsScanDepth()));

		for (AiProvider provider : AiProvider.values()) {
			var customField = customModelFields.get(provider.cliName);
			if (customField != null) {
				customField.setText(settings.getCustomModelsRaw(provider.cliName));
			}
			var comboBox = modelComboBoxes.get(provider.cliName);
			if (comboBox != null) {
				refreshModelComboBox(provider, comboBox, settings);
			}
		}
	}

	private void refreshModelComboBox(AiProvider provider, JComboBox<AiModel> comboBox, AiAgentSettings settings) {
		comboBox.removeAllItems();
		comboBox.addItem(null);
		for (AiModel model : provider.getAllModels()) {
			comboBox.addItem(model);
		}
		var selectedId = settings.getSelectedModel(provider.cliName);
		if (selectedId == null) {
			comboBox.setSelectedItem(null);
			return;
		}
		for (int i = 0; i < comboBox.getItemCount(); i++) {
			var item = comboBox.getItemAt(i);
			if (item != null && item.id().equals(selectedId)) {
				comboBox.setSelectedIndex(i);
				return;
			}
		}
		comboBox.setSelectedItem(null);
	}

	private static int clamp(int value) {
		return Math.max(AiAgentSettings.MIN_TIMEOUT_SECONDS, Math.min(AiAgentSettings.MAX_TIMEOUT_SECONDS, value));
	}

	private static int clampScanDepth(int value) {
		return Math.max(1, Math.min(20, value));
	}

	private static String normalizeRefsConfigPath(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
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
		modelComboBoxes.clear();
		customModelFields.clear();
	}

}
