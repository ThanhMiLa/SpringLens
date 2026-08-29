package vn.io.codelearning.springapitester.ui;

import com.intellij.openapi.ui.ComboBox;
import vn.io.codelearning.springapitester.model.AuthConfig;
import vn.io.codelearning.springapitester.model.AuthTypeEnum;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

public class AuthPanel extends JPanel {
    private final ComboBox<AuthTypeEnum> authTypeCombo;
    private final JCheckBox isSecuredCheck;
    private final JPanel cardsPanel;
    private final CardLayout cardLayout;
    
    private java.util.function.Consumer<Boolean> onSecurityToggled;
    private java.util.function.Consumer<AuthConfig> onApplyToAllClicked;

    // Bearer fields
    private final com.intellij.ui.components.JBTextField bearerTokenField;

    // Basic fields
    private final com.intellij.ui.components.JBTextField basicUserField;
    private final JPasswordField basicPassField;

    // API Key fields
    private final com.intellij.ui.components.JBTextField apiKeyNameField;
    private final com.intellij.ui.components.JBTextField apiKeyValueField;
    private final JCheckBox apiKeyInHeaderCheck;

    private AuthConfig currentConfig;

    public AuthPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Combo Box
        authTypeCombo = new ComboBox<>(AuthTypeEnum.values());
        
        isSecuredCheck = new JCheckBox("Endpoint requires authentication (Secured)");
        isSecuredCheck.addActionListener(e -> {
            if (onSecurityToggled != null) {
                onSecurityToggled.accept(isSecuredCheck.isSelected());
            }
        });

        JButton applyToAllBtn = new JButton("Apply to All APIs");
        applyToAllBtn.setToolTipText("Copy this Auth config to all other APIs in project");
        applyToAllBtn.addActionListener(e -> {
            if (onApplyToAllClicked != null) {
                onApplyToAllClicked.accept(getAuthConfig());
                JOptionPane.showMessageDialog(this, "Authentication config applied to all APIs successfully!", "Apply to All", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // Top Controls Panel (Vertical stack)
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row1.add(isSecuredCheck);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        row2.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        row2.add(new JLabel("Type:"));
        row2.add(authTypeCombo);
        row2.add(Box.createHorizontalStrut(10));
        row2.add(applyToAllBtn);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        topPanel.add(row1);
        topPanel.add(row2);
        topPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
        
        add(topPanel, BorderLayout.NORTH);

        // Card Layout for dynamic forms
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        // Card 1: No Auth / Inherit
        JPanel noAuthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        noAuthPanel.add(new JLabel("This endpoint does not require authorization headers."));
        cardsPanel.add(noAuthPanel, AuthTypeEnum.NO_AUTH.name());

        JPanel inheritPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        inheritPanel.add(new JLabel("Inherit authentication from parent environment or project defaults."));
        cardsPanel.add(inheritPanel, AuthTypeEnum.INHERIT.name());

        // Card 2: Bearer Token
        JPanel bearerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        bearerPanel.add(new JLabel("Token:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        bearerTokenField = new com.intellij.ui.components.JBTextField();
        bearerTokenField.getEmptyText().setText("Paste Bearer JWT token here...");
        bearerPanel.add(bearerTokenField, gbc);
        
        // Filler to push everything to top
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        bearerPanel.add(new JPanel(), gbc);
        
        cardsPanel.add(bearerPanel, AuthTypeEnum.BEARER_TOKEN.name());

        // Card 3: Basic Auth
        JPanel basicPanel = new JPanel(new GridBagLayout());
        GridBagConstraints bGbc = new GridBagConstraints();
        bGbc.insets = new Insets(4, 4, 4, 4);
        bGbc.anchor = GridBagConstraints.WEST;
        
        bGbc.gridx = 0; bGbc.gridy = 0; bGbc.weightx = 0; bGbc.fill = GridBagConstraints.NONE;
        basicPanel.add(new JLabel("Username:"), bGbc);
        
        bGbc.gridx = 1; bGbc.gridy = 0; bGbc.weightx = 1.0; bGbc.fill = GridBagConstraints.HORIZONTAL;
        basicUserField = new com.intellij.ui.components.JBTextField();
        basicPanel.add(basicUserField, bGbc);
        
        bGbc.gridx = 0; bGbc.gridy = 1; bGbc.weightx = 0; bGbc.fill = GridBagConstraints.NONE;
        basicPanel.add(new JLabel("Password:"), bGbc);
        
        bGbc.gridx = 1; bGbc.gridy = 1; bGbc.weightx = 1.0; bGbc.fill = GridBagConstraints.HORIZONTAL;
        basicPassField = new JPasswordField();
        basicPanel.add(basicPassField, bGbc);
        
        bGbc.gridx = 0; bGbc.gridy = 2; bGbc.gridwidth = 2; bGbc.weighty = 1.0; bGbc.fill = GridBagConstraints.BOTH;
        basicPanel.add(new JPanel(), bGbc);
        
        cardsPanel.add(basicPanel, AuthTypeEnum.BASIC_AUTH.name());

        // Card 4: API Key
        JPanel apiPanel = new JPanel(new GridBagLayout());
        GridBagConstraints aGbc = new GridBagConstraints();
        aGbc.insets = new Insets(4, 4, 4, 4);
        aGbc.anchor = GridBagConstraints.WEST;
        
        aGbc.gridx = 0; aGbc.gridy = 0; aGbc.weightx = 0; aGbc.fill = GridBagConstraints.NONE;
        apiPanel.add(new JLabel("Key:"), aGbc);
        
        aGbc.gridx = 1; aGbc.gridy = 0; aGbc.weightx = 1.0; aGbc.fill = GridBagConstraints.HORIZONTAL;
        apiKeyNameField = new com.intellij.ui.components.JBTextField();
        apiPanel.add(apiKeyNameField, aGbc);
        
        aGbc.gridx = 0; aGbc.gridy = 1; aGbc.weightx = 0; aGbc.fill = GridBagConstraints.NONE;
        apiPanel.add(new JLabel("Value:"), aGbc);
        
        aGbc.gridx = 1; aGbc.gridy = 1; aGbc.weightx = 1.0; aGbc.fill = GridBagConstraints.HORIZONTAL;
        apiKeyValueField = new com.intellij.ui.components.JBTextField();
        apiPanel.add(apiKeyValueField, aGbc);
        
        aGbc.gridx = 1; aGbc.gridy = 2; aGbc.weightx = 1.0; aGbc.fill = GridBagConstraints.HORIZONTAL;
        apiKeyInHeaderCheck = new JCheckBox("Add to Header (otherwise Query Params)", true);
        apiPanel.add(apiKeyInHeaderCheck, aGbc);
        
        aGbc.gridx = 0; aGbc.gridy = 3; aGbc.gridwidth = 2; aGbc.weighty = 1.0; aGbc.fill = GridBagConstraints.BOTH;
        apiPanel.add(new JPanel(), aGbc);
        
        cardsPanel.add(apiPanel, AuthTypeEnum.API_KEY.name());

        add(new com.intellij.ui.components.JBScrollPane(cardsPanel), BorderLayout.CENTER);

        // Switch Card on Selection
        authTypeCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                AuthTypeEnum selected = (AuthTypeEnum) e.getItem();
                cardLayout.show(cardsPanel, selected.name());
            }
        });
    }
    
    public void setOnSecurityToggled(java.util.function.Consumer<Boolean> listener) {
        this.onSecurityToggled = listener;
    }
    
    public void setSecuredStatus(boolean isSecured) {
        this.isSecuredCheck.setSelected(isSecured);
    }

    public void setAuthConfig(AuthConfig config) {
        this.currentConfig = config != null ? config : new AuthConfig();
        authTypeCombo.setSelectedItem(this.currentConfig.getAuthType());
        
        bearerTokenField.setText(this.currentConfig.getBearerToken());
        basicUserField.setText(this.currentConfig.getUsername());
        basicPassField.setText(this.currentConfig.getPassword());
        apiKeyNameField.setText(this.currentConfig.getApiKeyName());
        apiKeyValueField.setText(this.currentConfig.getApiKeyValue());
        apiKeyInHeaderCheck.setSelected(this.currentConfig.isApiKeyInHeader());
    }

    public AuthConfig getAuthConfig() {
        if (currentConfig == null) {
            currentConfig = new AuthConfig();
        }
        currentConfig.setAuthType((AuthTypeEnum) authTypeCombo.getSelectedItem());
        currentConfig.setBearerToken(bearerTokenField.getText());
        currentConfig.setUsername(basicUserField.getText());
        currentConfig.setPassword(new String(basicPassField.getPassword()));
        currentConfig.setApiKeyName(apiKeyNameField.getText());
        currentConfig.setApiKeyValue(apiKeyValueField.getText());
        currentConfig.setApiKeyInHeader(apiKeyInHeaderCheck.isSelected());

        return currentConfig;
    }

    public void setOnApplyToAllClicked(java.util.function.Consumer<AuthConfig> onApplyToAllClicked) {
        this.onApplyToAllClicked = onApplyToAllClicked;
    }
}
