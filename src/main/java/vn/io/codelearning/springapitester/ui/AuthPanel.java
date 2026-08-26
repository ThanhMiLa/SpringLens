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

    // Bearer fields
    private final JTextField bearerTokenField;

    // Basic fields
    private final JTextField basicUserField;
    private final JPasswordField basicPassField;

    // API Key fields
    private final JTextField apiKeyNameField;
    private final JTextField apiKeyValueField;
    private final JCheckBox apiKeyInHeaderCheck;

    private AuthConfig currentConfig;

    public AuthPanel() {
        setLayout(new BorderLayout(5, 5));

        // Combo Box
        authTypeCombo = new ComboBox<>(AuthTypeEnum.values());
        
        isSecuredCheck = new JCheckBox("Endpoint requires authentication (Secured)");
        isSecuredCheck.addActionListener(e -> {
            if (onSecurityToggled != null) {
                onSecurityToggled.accept(isSecuredCheck.isSelected());
            }
        });
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        topPanel.add(isSecuredCheck);
        
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        typePanel.add(new JLabel("Auth Type:"));
        typePanel.add(authTypeCombo);
        topPanel.add(typePanel);
        
        add(topPanel, BorderLayout.NORTH);

        // Card Layout for dynamic forms
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);

        // Card 1: No Auth / Inherit
        cardsPanel.add(new JPanel(), AuthTypeEnum.NO_AUTH.name());
        cardsPanel.add(new JPanel(), AuthTypeEnum.INHERIT.name());

        // Card 2: Bearer Token
        JPanel bearerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bearerTokenField = new JTextField(30);
        bearerPanel.add(new JLabel("Token:"));
        bearerPanel.add(bearerTokenField);
        cardsPanel.add(bearerPanel, AuthTypeEnum.BEARER_TOKEN.name());

        // Card 3: Basic Auth
        JPanel basicPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        basicUserField = new JTextField(15);
        basicPassField = new JPasswordField(15);
        basicPanel.add(new JLabel("Username:"));
        basicPanel.add(basicUserField);
        basicPanel.add(new JLabel("Password:"));
        basicPanel.add(basicPassField);
        cardsPanel.add(basicPanel, AuthTypeEnum.BASIC_AUTH.name());

        // Card 4: API Key
        JPanel apiPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        apiKeyNameField = new JTextField(10);
        apiKeyValueField = new JTextField(20);
        apiKeyInHeaderCheck = new JCheckBox("Add to Header", true);
        apiPanel.add(new JLabel("Key:"));
        apiPanel.add(apiKeyNameField);
        apiPanel.add(new JLabel("Value:"));
        apiPanel.add(apiKeyValueField);
        apiPanel.add(apiKeyInHeaderCheck);
        cardsPanel.add(apiPanel, AuthTypeEnum.API_KEY.name());

        add(cardsPanel, BorderLayout.CENTER);

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
}
