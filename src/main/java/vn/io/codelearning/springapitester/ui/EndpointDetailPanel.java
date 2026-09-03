package vn.io.codelearning.springapitester.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import okhttp3.Request;
import vn.io.codelearning.springapitester.client.HttpClientService;
import vn.io.codelearning.springapitester.client.HttpRequestBuilder;
import vn.io.codelearning.springapitester.generator.DtoJsonGenerator;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.ParameterModel;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EndpointDetailPanel extends JPanel {

    private final Project project;
    private EndpointModel currentEndpoint;

    private final com.intellij.openapi.ui.ComboBox<vn.io.codelearning.springapitester.model.HttpMethodEnum> methodComboBox;
    private final JBTextField urlField;
    private final JButton sendBtn;
    private final JCheckBox insecureTlsCheckBox;
    private final JCheckBox persistRequestBodiesCheckBox;
    private final JCheckBox persistResponseHistoryCheckBox;

    private final ParamTablePanel paramPanel;
    private final ParamTablePanel headerParamPanel;
    private final ParamTablePanel cookiePanel;
    private ParamTablePanel formDataPanel;
    private final HeaderTablePanel headerPanel;
    private final AuthPanel authPanel;
    private Editor requestBodyEditor;
    private Editor responseBodyEditor;
    
    private final JPanel requestBodyPanel;
    private final JPanel responseBodyPanel;
    private JPanel bodyCards;
    private JRadioButton jsonRadio;
    private JRadioButton formRadio;
    private JButton syncBtn;
    
    private final JBLabel statusLabel;
    private final JTextArea responseHeadersArea;
    private JToggleButton wrapToggleBtn;
    private JBTabbedPane requestTabs;

    // Default local base URL for testing
    private String baseUrl = "http://localhost:8080";
    private vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig cachedGatewayConfig;
    
    private Runnable onEndpointUpdated;
    private java.util.function.Consumer<vn.io.codelearning.springapitester.model.AuthConfig> onApplyToAllAuth;

    private boolean isUpdatingUI = false;
    private volatile boolean disposed = false;
    private final vn.io.codelearning.springapitester.client.RequestExecutionTracker requestTracker =
            new vn.io.codelearning.springapitester.client.RequestExecutionTracker();
    private final java.util.concurrent.atomic.AtomicLong uiGenerationCounter =
            new java.util.concurrent.atomic.AtomicLong();
    private final Set<HttpClientService.RequestHandle> activeRequests = ConcurrentHashMap.newKeySet();

    public void setOnEndpointUpdated(Runnable onEndpointUpdated) {
        this.onEndpointUpdated = onEndpointUpdated;
    }

    public void setOnApplyToAllAuth(java.util.function.Consumer<vn.io.codelearning.springapitester.model.AuthConfig> onApplyToAllAuth) {
        this.onApplyToAllAuth = onApplyToAllAuth;
    }

    public void setGatewayConfig(vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig gatewayConfig) {
        this.cachedGatewayConfig = gatewayConfig;
    }

    public vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig getGatewayConfig() {
        return this.cachedGatewayConfig;
    }

    public void setDefaultBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.baseUrl = baseUrl;
        }
    }

    public void refreshGatewayConfigAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig config = 
                        vn.io.codelearning.springapitester.util.GatewayConfigReader.findGatewayConfig(project);
                String extractedBase = vn.io.codelearning.springapitester.util.SpringBootConfigReader.extractBaseUrl(project);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (project.isDisposed()) return;
                    this.cachedGatewayConfig = config;
                    setDefaultBaseUrl(extractedBase);
                });
            } catch (Throwable t) {
                // ignore
            }
        });
    }

    public EndpointDetailPanel(Project project) {
        this.project = project;
        Disposer.register(project, () -> {
            disposed = true;
            requestTracker.dispose();
            activeRequests.forEach(HttpClientService.RequestHandle::cancel);
            activeRequests.clear();
            CodeEditorUtil.releaseEditor(requestBodyEditor);
            CodeEditorUtil.releaseEditor(responseBodyEditor);
        });
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(100, 100));

        // Asynchronously load initial base URL and gateway config
        refreshGatewayConfigAsync();

        // 1. Top Bar
        JPanel topBar = new JPanel(new BorderLayout(5, 5));
        topBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        methodComboBox = new com.intellij.openapi.ui.ComboBox<>(vn.io.codelearning.springapitester.model.HttpMethodEnum.values());
        methodComboBox.setPreferredSize(new Dimension(105, methodComboBox.getPreferredSize().height));
        methodComboBox.setRenderer(new com.intellij.ui.ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@org.jetbrains.annotations.NotNull JList<? extends vn.io.codelearning.springapitester.model.HttpMethodEnum> list, vn.io.codelearning.springapitester.model.HttpMethodEnum value, int index, boolean selected, boolean hasFocus) {
                if (value != null) {
                    Color methodColor = Color.decode(value.getColorHex());
                    com.intellij.ui.SimpleTextAttributes attr = new com.intellij.ui.SimpleTextAttributes(com.intellij.ui.SimpleTextAttributes.STYLE_BOLD, methodColor);
                    append(value.getLabel(), attr);
                }
            }
        });
        updateMethodComboColor(vn.io.codelearning.springapitester.model.HttpMethodEnum.GET);

        methodComboBox.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                vn.io.codelearning.springapitester.model.HttpMethodEnum method = (vn.io.codelearning.springapitester.model.HttpMethodEnum) e.getItem();
                updateMethodComboColor(method);
                if (!isUpdatingUI && currentEndpoint != null) {
                    currentEndpoint.setHttpMethod(method);
                    if (onEndpointUpdated != null) {
                        onEndpointUpdated.run();
                    }
                }
            }
        });
        
        urlField = new JBTextField();
        urlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                if (!isUpdatingUI && currentEndpoint != null) {
                    String url = urlField.getText().trim();
                    if (vn.io.codelearning.springapitester.util.ManualUrlResolver.isAbsoluteUrl(url)) {
                        currentEndpoint.setAbsoluteUrl(true);
                        currentEndpoint.setPath(url);
                    } else {
                        currentEndpoint.setAbsoluteUrl(false);
                        String effectiveBaseUrl = getEffectiveBaseUrl(currentEndpoint);
                        String relativePath = vn.io.codelearning.springapitester.util.ManualUrlResolver
                                .extractRelativePathAndQuery(url, effectiveBaseUrl);
                        currentEndpoint.setPath(relativePath);
                    }
                    if (currentEndpoint.getInsecureTlsConsent() != null) {
                        okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(url);
                        String host = parsed != null ? parsed.host() : "";
                        if (!currentEndpoint.getInsecureTlsConsent().matchesHost(host)) {
                            currentEndpoint.revokeInsecureTlsConsent();
                            insecureTlsCheckBox.setSelected(false);
                        }
                    }
                    if (onEndpointUpdated != null) {
                        onEndpointUpdated.run();
                    }
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        
        sendBtn = new JButton("Send") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor;
                Color textColor;
                if (!isEnabled()) {
                    bgColor = new Color(0x70, 0x98, 0xD4);
                    textColor = new Color(0xDD, 0xDD, 0xDD);
                } else if (getModel().isPressed()) {
                    bgColor = new Color(0x09, 0x4D, 0xB5);
                    textColor = Color.WHITE;
                } else if (getModel().isRollover()) {
                    bgColor = new Color(0x1C, 0x6E, 0xF2);
                    textColor = Color.WHITE;
                } else {
                    bgColor = new Color(0x0C, 0x63, 0xE7); // Postman brand blue
                    textColor = Color.WHITE;
                }

                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);

                g2.setColor(textColor);
                g2.setFont(getFont().deriveFont(Font.BOLD));
                FontMetrics fm = g2.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), textX, textY);

                g2.dispose();
            }
        };
        sendBtn.setOpaque(false);
        sendBtn.setContentAreaFilled(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.setPreferredSize(new Dimension(75, methodComboBox.getPreferredSize().height));
        sendBtn.addActionListener(e -> onSendClicked());

        JButton curlBtn = new JButton("cURL ▾");
        curlBtn.setToolTipText("Copy as cURL or PowerShell command");
        JPopupMenu curlMenu = new JPopupMenu();
        JMenuItem copyBashRedacted = new JMenuItem("Copy cURL (Bash/Zsh - Redacted)");
        copyBashRedacted.addActionListener(ev -> {
            if (currentEndpoint == null) return;
            collectDataToModel();
            String curl = vn.io.codelearning.springapitester.client.CurlBuilder.buildCurl(currentEndpoint, urlField.getText(), false);
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(new java.awt.datatransfer.StringSelection(curl));
            com.intellij.openapi.ui.Messages.showInfoMessage(project, "cURL (Bash/Zsh, redacted) copied to clipboard!", "Success");
        });
        JMenuItem copyBashWithCreds = new JMenuItem("Copy cURL (Bash/Zsh - Include Credentials)");
        copyBashWithCreds.addActionListener(ev -> {
            if (currentEndpoint == null) return;
            collectDataToModel();
            String curl = vn.io.codelearning.springapitester.client.CurlBuilder.buildCurl(currentEndpoint, urlField.getText(), true);
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(new java.awt.datatransfer.StringSelection(curl));
            com.intellij.openapi.ui.Messages.showInfoMessage(project, "cURL (Bash/Zsh, with credentials) copied to clipboard!", "Success");
        });

        JMenuItem copyCmdRedacted = new JMenuItem("Copy cURL (Windows CMD - Redacted)");
        copyCmdRedacted.addActionListener(ev -> {
            if (currentEndpoint == null) return;
            collectDataToModel();
            String cmd = vn.io.codelearning.springapitester.client.CurlBuilder.buildWindowsCmd(currentEndpoint, urlField.getText(), false);
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(new java.awt.datatransfer.StringSelection(cmd));
            com.intellij.openapi.ui.Messages.showInfoMessage(project, "cURL (Windows CMD, redacted) copied to clipboard!", "Success");
        });
        JMenuItem copyCmdWithCreds = new JMenuItem("Copy cURL (Windows CMD - Include Credentials)");
        copyCmdWithCreds.addActionListener(ev -> {
            if (currentEndpoint == null) return;
            collectDataToModel();
            String cmd = vn.io.codelearning.springapitester.client.CurlBuilder.buildWindowsCmd(currentEndpoint, urlField.getText(), true);
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(new java.awt.datatransfer.StringSelection(cmd));
            com.intellij.openapi.ui.Messages.showInfoMessage(project, "cURL (Windows CMD, with credentials) copied to clipboard!", "Success");
        });

        JMenuItem copyPowerShellRedacted = new JMenuItem("Copy PowerShell (Redacted)");
        copyPowerShellRedacted.addActionListener(ev -> {
            if (currentEndpoint == null) return;
            collectDataToModel();
            String ps = vn.io.codelearning.springapitester.client.CurlBuilder.buildPowerShell(currentEndpoint, urlField.getText(), false);
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(new java.awt.datatransfer.StringSelection(ps));
            com.intellij.openapi.ui.Messages.showInfoMessage(project, "PowerShell command (redacted) copied to clipboard!", "Success");
        });
        JMenuItem copyPowerShellWithCreds = new JMenuItem("Copy PowerShell (Include Credentials)");
        copyPowerShellWithCreds.addActionListener(ev -> {
            if (currentEndpoint == null) return;
            collectDataToModel();
            String ps = vn.io.codelearning.springapitester.client.CurlBuilder.buildPowerShell(currentEndpoint, urlField.getText(), true);
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(new java.awt.datatransfer.StringSelection(ps));
            com.intellij.openapi.ui.Messages.showInfoMessage(project, "PowerShell command (with credentials) copied to clipboard!", "Success");
        });

        curlMenu.add(copyBashRedacted);
        curlMenu.add(copyBashWithCreds);
        curlMenu.addSeparator();
        curlMenu.add(copyCmdRedacted);
        curlMenu.add(copyCmdWithCreds);
        curlMenu.addSeparator();
        curlMenu.add(copyPowerShellRedacted);
        curlMenu.add(copyPowerShellWithCreds);

        curlBtn.addActionListener(e -> curlMenu.show(curlBtn, 0, curlBtn.getHeight()));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        btnPanel.add(curlBtn);
        btnPanel.add(sendBtn);

        topBar.add(methodComboBox, BorderLayout.WEST);
        topBar.add(urlField, BorderLayout.CENTER);
        topBar.add(btnPanel, BorderLayout.EAST);
        
        JPanel headerPanelWrap = new JPanel(new BorderLayout());
        headerPanelWrap.add(topBar, BorderLayout.CENTER);

        insecureTlsCheckBox = new JCheckBox("Allow insecure TLS for localhost/loopback only");
        insecureTlsCheckBox.setToolTipText("Trust self-signed certificates only for local development hosts");
        insecureTlsCheckBox.addActionListener(e -> {
            if (isUpdatingUI || currentEndpoint == null) return;
            if (insecureTlsCheckBox.isSelected()) {
                String fullUrl = urlField.getText().trim();
                okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(fullUrl);
                String host = parsed != null ? parsed.host() : "localhost";
                if (!HttpClientService.isLocalDevelopmentHost(host)) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                            project,
                            "Insecure TLS is only allowed for localhost or loopback development hosts.",
                            "Insecure TLS Prohibited"
                    );
                    insecureTlsCheckBox.setSelected(false);
                    currentEndpoint.revokeInsecureTlsConsent();
                    return;
                }
                int choice = com.intellij.openapi.ui.Messages.showYesNoDialog(
                        project,
                        "This disables certificate validation for host '" + host + "' and can expose credentials. Enable it only for a trusted development server.",
                        "Enable Insecure Local TLS",
                        "Enable",
                        "Cancel",
                        com.intellij.openapi.ui.Messages.getWarningIcon()
                );
                if (choice == com.intellij.openapi.ui.Messages.YES) {
                    currentEndpoint.grantInsecureTlsConsent(host);
                } else {
                    insecureTlsCheckBox.setSelected(false);
                    currentEndpoint.revokeInsecureTlsConsent();
                }
            } else {
                currentEndpoint.revokeInsecureTlsConsent();
            }
        });
        JPanel privacyOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        privacyOptions.add(insecureTlsCheckBox);
        persistRequestBodiesCheckBox = new JCheckBox("Persist request bodies");
        persistResponseHistoryCheckBox = new JCheckBox("Persist response history");
        vn.io.codelearning.springapitester.state.SpringLensState privacyState =
                vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
        if (privacyState != null) {
            persistRequestBodiesCheckBox.setSelected(privacyState.persistRequestBodies);
            persistResponseHistoryCheckBox.setSelected(privacyState.persistResponseHistory);
        }
        persistRequestBodiesCheckBox.setToolTipText("Persist request body across IDE restarts (enabled by default)");
        persistResponseHistoryCheckBox.setToolTipText("Persist response history across IDE restarts (enabled by default)");
        persistRequestBodiesCheckBox.addActionListener(e -> {
            vn.io.codelearning.springapitester.state.SpringLensState state =
                    vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
            if (state != null) state.persistRequestBodies = persistRequestBodiesCheckBox.isSelected();
        });
        persistResponseHistoryCheckBox.addActionListener(e -> {
            vn.io.codelearning.springapitester.state.SpringLensState state =
                    vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
            if (state != null) state.persistResponseHistory = persistResponseHistoryCheckBox.isSelected();
        });
        privacyOptions.add(persistRequestBodiesCheckBox);
        privacyOptions.add(persistResponseHistoryCheckBox);
        headerPanelWrap.add(privacyOptions, BorderLayout.SOUTH);
        
        add(headerPanelWrap, BorderLayout.NORTH);

        // 2. Splitter for Request / Response
        JBSplitter mainSplitter = new JBSplitter(true, 0.5f);
        mainSplitter.setShowDividerControls(true);
        mainSplitter.setDividerWidth(7);
        mainSplitter.setShowDividerIcon(true);
        
        // --- 2.1 Request Tabs ---
        requestTabs = new JBTabbedPane();
        paramPanel = new ParamTablePanel(java.util.List.of(
            vn.io.codelearning.springapitester.model.ParamTypeEnum.PATH_VARIABLE,
            vn.io.codelearning.springapitester.model.ParamTypeEnum.QUERY_PARAM
        ));
        headerParamPanel = new ParamTablePanel(java.util.List.of(
            vn.io.codelearning.springapitester.model.ParamTypeEnum.HEADER
        ));
        cookiePanel = new ParamTablePanel(java.util.List.of(
            vn.io.codelearning.springapitester.model.ParamTypeEnum.COOKIE
        ));
        headerPanel = new HeaderTablePanel();
        authPanel = new AuthPanel();
        authPanel.setOnSecurityToggled(isSecured -> {
            if (currentEndpoint != null) {
                currentEndpoint.setSecured(isSecured);
                // Save immediately so that if user clicks Reload, it restores the correct state
                vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
                if (state != null) {
                    state.saveEndpoint(currentEndpoint);
                    // Explicitly lock the security state because the user manually toggled it in the UI
                    vn.io.codelearning.springapitester.state.EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(currentEndpoint));
                    if (saved != null) {
                        saved.hasSecuredOverride = true;
                    }
                }
                if (onEndpointUpdated != null) {
                    onEndpointUpdated.run();
                }
            }
        });
        authPanel.setOnApplyToAllClicked(authConfig -> {
            if (onApplyToAllAuth != null) {
                onApplyToAllAuth.accept(authConfig);
            }
        });
        
        // Request Body with Toolbar
        requestBodyPanel = new JPanel(new BorderLayout());
        JPanel bodyToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        // Body Type Switcher
        JRadioButton jsonRadio = new JRadioButton("JSON");
        JRadioButton formRadio = new JRadioButton("Form-Data");
        ButtonGroup bodyTypeGroup = new ButtonGroup();
        bodyTypeGroup.add(jsonRadio);
        bodyTypeGroup.add(formRadio);
        jsonRadio.setSelected(true); // default
        
        bodyToolbar.add(new JLabel("Type: "));
        bodyToolbar.add(jsonRadio);
        bodyToolbar.add(formRadio);
        
        JButton syncBtn = new JButton("🔄 Sync Schema");
        syncBtn.addActionListener(e -> onSyncSchemaClicked());
        bodyToolbar.add(syncBtn);
        requestBodyPanel.add(bodyToolbar, BorderLayout.NORTH);
        
        // Editors / Panels for Body
        JPanel bodyCards = new JPanel(new CardLayout());
        requestBodyEditor = CodeEditorUtil.createEditor(project, "", "json", false);
        bodyCards.add(requestBodyEditor.getComponent(), "JSON");
        
        formDataPanel = new ParamTablePanel(java.util.List.of(
            vn.io.codelearning.springapitester.model.ParamTypeEnum.FORM_DATA,
            vn.io.codelearning.springapitester.model.ParamTypeEnum.MULTIPART_FILE,
            vn.io.codelearning.springapitester.model.ParamTypeEnum.MODEL_ATTRIBUTE
        ));
        bodyCards.add(formDataPanel, "FORM_DATA");
        
        requestBodyPanel.add(bodyCards, BorderLayout.CENTER);
        
        // Logic switch
        jsonRadio.addActionListener(e -> {
            ((CardLayout) bodyCards.getLayout()).show(bodyCards, "JSON");
            if (currentEndpoint != null) currentEndpoint.setBodyType(vn.io.codelearning.springapitester.model.RequestBodyType.JSON);
            syncBtn.setVisible(true);
        });
        formRadio.addActionListener(e -> {
            ((CardLayout) bodyCards.getLayout()).show(bodyCards, "FORM_DATA");
            if (currentEndpoint != null) currentEndpoint.setBodyType(vn.io.codelearning.springapitester.model.RequestBodyType.FORM_DATA);
            syncBtn.setVisible(false); // No sync for form data table since scanner populates it directly
        });

        // Initialize default selection based on currentEndpoint later
        this.jsonRadio = jsonRadio;
        this.formRadio = formRadio;
        this.bodyCards = bodyCards;
        this.syncBtn = syncBtn;

        JPanel headersTabPanel = new JPanel(new BorderLayout());
        JBSplitter headersSplitter = new JBSplitter(true, 0.5f);
        headersSplitter.setShowDividerControls(true);
        headersSplitter.setDividerWidth(5);

        JPanel headerParamWrapper = new JPanel(new BorderLayout());
        headerParamWrapper.setBorder(BorderFactory.createTitledBorder("Request Headers (@RequestHeader)"));
        headerParamWrapper.add(headerParamPanel, BorderLayout.CENTER);

        JPanel customHeadersWrapper = new JPanel(new BorderLayout());
        customHeadersWrapper.setBorder(BorderFactory.createTitledBorder("Custom Headers"));
        customHeadersWrapper.add(headerPanel, BorderLayout.CENTER);

        headersSplitter.setFirstComponent(headerParamWrapper);
        headersSplitter.setSecondComponent(customHeadersWrapper);
        headersTabPanel.add(headersSplitter, BorderLayout.CENTER);

        JPanel cookiesTabPanel = new JPanel(new BorderLayout());
        cookiesTabPanel.setBorder(BorderFactory.createTitledBorder("Cookie Values (@CookieValue)"));
        cookiesTabPanel.add(cookiePanel, BorderLayout.CENTER);

        JPanel cookieToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        JButton clearCookiesBtn = new JButton("Clear Session Cookies");
        clearCookiesBtn.setToolTipText("Clear in-memory session cookies for this project");
        clearCookiesBtn.addActionListener(e -> {
            HttpClientService http = HttpClientService.getInstance(project);
            if (http != null) {
                http.clearCookies();
                com.intellij.openapi.ui.Messages.showInfoMessage(project, "Session cookies cleared for this project.", "Cookies Cleared");
            }
        });
        cookieToolbar.add(clearCookiesBtn);
        cookiesTabPanel.add(cookieToolbar, BorderLayout.SOUTH);

        requestTabs.addTab("Params", paramPanel);
        requestTabs.addTab("Headers", headersTabPanel);
        requestTabs.addTab("Cookies", cookiesTabPanel);
        requestTabs.addTab("Auth", authPanel);
        requestTabs.addTab("Body", requestBodyPanel);
        
        mainSplitter.setFirstComponent(requestTabs);
        
        // --- 2.2 Response Tabs ---
        JPanel responseContainer = new JPanel(new BorderLayout());
        JPanel statusBar = new JPanel(new BorderLayout());
        statusLabel = new JBLabel("Ready");
        statusBar.add(statusLabel, BorderLayout.WEST);
        
        // Toolbar for Response (Language Dropdown & Wrap Toggle)
        JPanel responseToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        wrapToggleBtn = new JToggleButton("Wrap", true);
        wrapToggleBtn.setToolTipText("Toggle Soft Wrap / Word Wrap (Postman UX)");
        wrapToggleBtn.setPreferredSize(new Dimension(65, 24));
        wrapToggleBtn.addActionListener(e -> {
            boolean isWrap = wrapToggleBtn.isSelected();
            if (responseBodyEditor != null && !responseBodyEditor.isDisposed()) {
                responseBodyEditor.getSettings().setUseSoftWraps(isWrap);
            }
        });
        responseToolbar.add(wrapToggleBtn);

        responseLanguageCombo = new com.intellij.openapi.ui.ComboBox<>(new String[]{"JSON", "HTML", "XML", "TEXT"});
        responseLanguageCombo.setSelectedItem("JSON");
        responseLanguageCombo.addActionListener(e -> changeResponseLanguage());
        responseToolbar.add(new JLabel("Format: "));
        responseToolbar.add(responseLanguageCombo);

        JButton saveResponseBtn = new JButton("Save to File...");
        saveResponseBtn.setToolTipText("Save response body to a file");
        saveResponseBtn.addActionListener(e -> onSaveResponseToFile());
        responseToolbar.add(saveResponseBtn);
        statusBar.add(responseToolbar, BorderLayout.EAST);

        responseContainer.add(statusBar, BorderLayout.NORTH);

        JBTabbedPane responseTabs = new JBTabbedPane();
        responseBodyPanel = new JPanel(new BorderLayout());
        responseBodyEditor = CodeEditorUtil.createEditor(project, "", "json", true);
        responseBodyPanel.add(responseBodyEditor.getComponent(), BorderLayout.CENTER);
        
        responseHeadersArea = new JTextArea();
        responseHeadersArea.setEditable(false);
        responseHeadersArea.setLineWrap(true);
        responseHeadersArea.setWrapStyleWord(true);

        responseTabs.addTab("Response Body", responseBodyPanel);
        responseTabs.addTab("Response Headers", new JScrollPane(responseHeadersArea));
        
        responseContainer.add(responseTabs, BorderLayout.CENTER);
        mainSplitter.setSecondComponent(responseContainer);

        add(mainSplitter, BorderLayout.CENTER);
    }

    private com.intellij.openapi.ui.ComboBox<String> responseLanguageCombo;
    
    private void changeResponseLanguage() {
        String lang = (String) responseLanguageCombo.getSelectedItem();
        if (lang == null) lang = "JSON";
        
        if (!isUpdatingUI && currentEndpoint != null) {
            currentEndpoint.setLastResponseFormat(lang);
            vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
            if (state != null) {
                state.saveEndpoint(currentEndpoint);
            }
        }
        
        String ext = lang.toLowerCase();
        if (ext.equals("text")) ext = "txt";
        
        // Save current text
        String currentText = responseBodyEditor != null ? responseBodyEditor.getDocument().getText() : "";
        
        // Release old editor safely
        if (responseBodyEditor != null) {
            CodeEditorUtil.releaseEditor(responseBodyEditor);
        }
        
        // Create new editor with new highlighting
        responseBodyEditor = CodeEditorUtil.createEditor(project, currentText, ext, true);
        if (wrapToggleBtn != null) {
            responseBodyEditor.getSettings().setUseSoftWraps(wrapToggleBtn.isSelected());
        }
        
        // Update Panel
        responseBodyPanel.removeAll();
        responseBodyPanel.add(responseBodyEditor.getComponent(), BorderLayout.CENTER);
        responseBodyPanel.revalidate();
        responseBodyPanel.repaint();
    }

    public void refreshEndpoint() {
        if (currentEndpoint != null) {
            displayEndpoint(currentEndpoint);
        }
    }

    public void displayEndpoint(EndpointModel endpoint) {
        // Collect old data before switching
        collectDataToModel();
        uiGenerationCounter.incrementAndGet();

        this.currentEndpoint = endpoint;
        if (endpoint == null) {
            isUpdatingUI = true;
            try {
                methodComboBox.setSelectedItem(vn.io.codelearning.springapitester.model.HttpMethodEnum.GET);
                urlField.setText("");
                paramPanel.setParameters(new java.util.ArrayList<>());
                headerParamPanel.setParameters(new java.util.ArrayList<>());
                cookiePanel.setParameters(new java.util.ArrayList<>());
                formDataPanel.setParameters(new java.util.ArrayList<>());
                headerPanel.setHeaders(new java.util.ArrayList<>());
                authPanel.setAuthConfig(new vn.io.codelearning.springapitester.model.AuthConfig());
                authPanel.setSecuredStatus(false);
                insecureTlsCheckBox.setSelected(false);
                ApplicationManager.getApplication().runWriteAction(() -> {
                    requestBodyEditor.getDocument().setText("");
                    responseBodyEditor.getDocument().setText("");
                });
                responseHeadersArea.setText("");
                statusLabel.setText("Ready");
                sendBtn.setText("Send");
                sendBtn.setEnabled(false);
            } finally {
                isUpdatingUI = false;
            }
            return;
        }

        isUpdatingUI = true;
        try {
            vn.io.codelearning.springapitester.client.RequestExecutionContext active =
                    requestTracker.getActiveContext(endpoint);
            if (active != null && !active.isTerminal()) {
                sendBtn.setText("Cancel");
                sendBtn.setEnabled(true);
                statusLabel.setText("Sending...");
            } else {
                sendBtn.setText("Send");
                sendBtn.setEnabled(true);
            }
            vn.io.codelearning.springapitester.model.HttpMethodEnum method = endpoint.getHttpMethod() != null ? endpoint.getHttpMethod() : vn.io.codelearning.springapitester.model.HttpMethodEnum.GET;
            methodComboBox.setSelectedItem(method);
            updateMethodComboColor(method);

            String effectiveBaseUrl = getEffectiveBaseUrl(endpoint);
            String fullUrl = vn.io.codelearning.springapitester.util.UrlResolutionUtil.resolveFullUrl(
                    effectiveBaseUrl, endpoint.getPath(), endpoint.isAbsoluteUrl());
            urlField.setText(fullUrl);

            vn.io.codelearning.springapitester.scanner.SpringConfigResolutionService configService =
                    project != null && !project.isDisposed() ? vn.io.codelearning.springapitester.scanner.SpringConfigResolutionService.getInstance(project) : null;
            if (configService != null && !endpoint.isAbsoluteUrl() && !endpoint.isManual()) {
                vn.io.codelearning.springapitester.scanner.SpringServerConfig serverConfig = configService.resolveServerConfig();
                if (serverConfig != null) {
                    if (serverConfig.hasUnresolvedPlaceholder()) {
                        urlField.setToolTipText("Warning: Configuration has unresolved placeholders; fallback port used.");
                    } else if (serverConfig.isFallback()) {
                        urlField.setToolTipText("Info: Default fallback port used (no server.port configured).");
                    } else {
                        urlField.setToolTipText("Resolved from: " + serverConfig.getSourceFile());
                    }
                }
            } else {
                urlField.setToolTipText(null);
            }

            paramPanel.setParameters(endpoint.getParameters());
            headerParamPanel.setParameters(endpoint.getParameters());
            cookiePanel.setParameters(endpoint.getParameters());
            formDataPanel.setParameters(endpoint.getParameters());
            headerPanel.setHeaders(endpoint.getCustomHeaders());
            authPanel.setAuthConfig(endpoint.getAuthConfig());
            authPanel.setSecuredStatus(endpoint.isSecured());
            insecureTlsCheckBox.setSelected(endpoint.isAllowInsecureTls());

            String json = endpoint.getRequestBodyJson() != null ? endpoint.getRequestBodyJson() : "";
            
            // Set Body Type UI
            if (endpoint.getBodyType() == vn.io.codelearning.springapitester.model.RequestBodyType.FORM_DATA) {
                formRadio.setSelected(true);
                ((CardLayout) bodyCards.getLayout()).show(bodyCards, "FORM_DATA");
                syncBtn.setVisible(false);
            } else {
                jsonRadio.setSelected(true);
                ((CardLayout) bodyCards.getLayout()).show(bodyCards, "JSON");
                syncBtn.setVisible(true);
            }

            // Restore or Reset Cached Response
            String respBody = endpoint.getLastResponseBody() != null ? endpoint.getLastResponseBody() : "";
            String respHeaders = endpoint.getLastResponseHeaders() != null ? endpoint.getLastResponseHeaders() : "";
            String respFormat = (endpoint.getLastResponseFormat() != null && !endpoint.getLastResponseFormat().isBlank()) 
                    ? endpoint.getLastResponseFormat() 
                    : "JSON";

            if (!respFormat.equals(responseLanguageCombo.getSelectedItem())) {
                responseLanguageCombo.setSelectedItem(respFormat);
            }

            // Safely update editor document
            ApplicationManager.getApplication().runWriteAction(() -> {
                requestBodyEditor.getDocument().setText(json);
                responseBodyEditor.getDocument().setText(respBody);
            });

            responseHeadersArea.setText(respHeaders);

            // Update status label
            if (endpoint.getLastResponseStatusCode() > 0) {
                int code = endpoint.getLastResponseStatusCode();
                String colorHex = "#7A7A7A"; // Default gray
                if (code >= 200 && code < 300) colorHex = "#5CB85C"; // Green
                else if (code >= 300 && code < 400) colorHex = "#5BC0DE"; // Cyan
                else if (code >= 400 && code < 600) colorHex = "#D9534F"; // Red

                String msg = endpoint.getLastResponseStatusMessage();
                if (msg == null || msg.isBlank()) {
                    msg = getHttpStatusMessage(code);
                }

                String html = String.format("<html><span style='background-color: %s; color: white;'>&nbsp;<b>%d %s</b>&nbsp;</span><font color='gray'> &nbsp; %d ms</font></html>", 
                                            colorHex, code, msg, endpoint.getLastResponseTimeTakenMs());
                statusLabel.setText(html);
            } else if (endpoint.getLastResponseStatusMessage() != null && !endpoint.getLastResponseStatusMessage().isBlank()) {
                statusLabel.setText(endpoint.getLastResponseStatusMessage());
            } else {
                statusLabel.setText("Ready");
            }
        } catch (Throwable t) {
            // Safe fallback to prevent broken UI state
        } finally {
            isUpdatingUI = false;
        }
    }

    private void collectDataToModel() {
        if (currentEndpoint == null) return;
        // Collect all parameters across panels while preserving unmanaged parameters
        java.util.List<ParameterModel> allParams = new java.util.ArrayList<>();
        if (currentEndpoint.getParameters() != null) {
            for (ParameterModel p : currentEndpoint.getParameters()) {
                if (p.getParamType() == ParamTypeEnum.FRAMEWORK_INTERNAL || p.getParamType() == ParamTypeEnum.REQUEST_BODY) {
                    allParams.add(p);
                }
            }
        }
        allParams.addAll(paramPanel.getParameters());
        allParams.addAll(headerParamPanel.getParameters());
        allParams.addAll(cookiePanel.getParameters());
        allParams.addAll(formDataPanel.getParameters());
        currentEndpoint.setParameters(allParams);

        vn.io.codelearning.springapitester.model.HttpMethodEnum method = (vn.io.codelearning.springapitester.model.HttpMethodEnum) methodComboBox.getSelectedItem();
        if (method != null) {
            currentEndpoint.setHttpMethod(method);
        }
        
        currentEndpoint.setCustomHeaders(headerPanel.getHeaders());
        currentEndpoint.setAuthConfig(authPanel.getAuthConfig());
        currentEndpoint.setRequestBodyJson(requestBodyEditor.getDocument().getText());
        currentEndpoint.setAllowInsecureTls(insecureTlsCheckBox.isSelected());
        
        // Tự động lưu trạng thái (State Persistence)
        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
        if (state != null) {
            state.saveEndpoint(currentEndpoint);
        }
    }

    private void onSyncSchemaClicked() {
        if (currentEndpoint == null || currentEndpoint.getRequestBodyClassFqn() == null) return;

        // Run in background to avoid freezing UI (especially PSI resolving)
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            // Generate New Schema
            String newSchema = DtoJsonGenerator.generateJsonTemplate(
                    currentEndpoint.getRequestBodyClassFqn(), project);

            ApplicationManager.getApplication().invokeLater(() -> {
                // Read current old JSON from editor
                String oldJson = requestBodyEditor.getDocument().getText();
                
                // Smart Merge
                String merged = SmartMergeUtil.mergeJson(oldJson, newSchema);

                // Update Editor safely
                ApplicationManager.getApplication().runWriteAction(() -> {
                    requestBodyEditor.getDocument().setText(merged);
                });
            });
        });
    }

    private String getHttpStatusMessage(int code) {
        return switch (code) {
            case 100 -> "Continue";
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Unknown";
        };
    }

    private void onSendClicked() {
        if (currentEndpoint == null || disposed) return;

        // If current endpoint has an in-flight request, clicking acts as Cancel
        vn.io.codelearning.springapitester.client.RequestExecutionContext runningContext =
                requestTracker.getActiveContext(currentEndpoint);
        if (runningContext != null && !runningContext.isTerminal()) {
            runningContext.cancel();
            requestTracker.cancel(currentEndpoint);
            sendBtn.setText("Send");
            sendBtn.setEnabled(true);
            statusLabel.setText("Canceled");
            return;
        }

        collectDataToModel();

        EndpointModel requestEndpoint = currentEndpoint;
        String fullUrl = urlField.getText();
        String testUrl = fullUrl;
        for (ParameterModel param : requestEndpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.PATH_VARIABLE) {
                String value = param.getCurrentValue() != null ? param.getCurrentValue().trim() : "";
                if (!value.isEmpty()) {
                    testUrl = vn.io.codelearning.springapitester.scanner.SpringUrlUtils.replacePathVariable(
                            testUrl, param.getName(), value);
                }
            }
        }

        if (vn.io.codelearning.springapitester.scanner.SpringUrlUtils.hasUnresolvedPathVariables(testUrl)) {
            String missing = String.join(", ",
                    vn.io.codelearning.springapitester.scanner.SpringUrlUtils.getUnresolvedPathVariables(testUrl));
            JOptionPane.showMessageDialog(this,
                    "Please fill in value for Path Variable: {" + missing + "} in the Params tab before sending.",
                    "Missing Path Variable", JOptionPane.WARNING_MESSAGE);
            requestTabs.setSelectedIndex(0);
            return;
        }

        long uiGen = uiGenerationCounter.get();
        vn.io.codelearning.springapitester.client.RequestExecutionContext context =
                requestTracker.begin(requestEndpoint, uiGen);
        sendBtn.setText("Cancel");
        sendBtn.setEnabled(true);
        statusLabel.setText("Sending...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Request request = HttpRequestBuilder.buildRequest(requestEndpoint, fullUrl);
                HttpClientService.RequestHandle handle = HttpClientService.getInstance(project)
                        .execute(request, requestEndpoint.getInsecureTlsConsent());
                context.setRequestHandle(handle);
                activeRequests.add(handle);
                handle.future().whenComplete((response, error) -> {
                    activeRequests.remove(handle);
                    if (disposed || project.isDisposed()) {
                        context.dispose();
                        return;
                    }
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (disposed || project.isDisposed()) {
                            context.dispose();
                            return;
                        }
                        // Atomically update target endpoint model
                        if (error == null) {
                            applySuccessfulResponse(requestEndpoint, response, false);
                        } else {
                            applyFailedResponse(requestEndpoint, "Error: " + rootMessage(error), false);
                        }

                        vn.io.codelearning.springapitester.model.EndpointIdentity currentIdentity = currentEndpoint != null
                                ? vn.io.codelearning.springapitester.model.EndpointIdentity.fromEndpoint(currentEndpoint) : null;
                        long visibleGen = uiGenerationCounter.get();
                        boolean canRender = context.canRenderToUi(currentIdentity, visibleGen);

                        if (canRender) {
                            if (error == null) {
                                context.transitionToTerminal(vn.io.codelearning.springapitester.client.RequestExecutionState.SUCCESS);
                                applySuccessfulResponse(requestEndpoint, response, true);
                            } else {
                                context.transitionToTerminal(vn.io.codelearning.springapitester.client.RequestExecutionState.FAILED);
                                applyFailedResponse(requestEndpoint, "Error: " + rootMessage(error), true);
                            }
                            sendBtn.setText("Send");
                        } else {
                            context.transitionToTerminal(error == null
                                    ? vn.io.codelearning.springapitester.client.RequestExecutionState.SUCCESS
                                    : vn.io.codelearning.springapitester.client.RequestExecutionState.FAILED);
                        }
                    });
                });
            } catch (Exception error) {
                if (disposed || project.isDisposed()) {
                    context.dispose();
                    return;
                }
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed || project.isDisposed()) {
                        context.dispose();
                        return;
                    }
                    applyFailedResponse(requestEndpoint, "Failed to build request: " + rootMessage(error), false);
                    vn.io.codelearning.springapitester.model.EndpointIdentity currentIdentity = currentEndpoint != null
                            ? vn.io.codelearning.springapitester.model.EndpointIdentity.fromEndpoint(currentEndpoint) : null;
                    long visibleGen = uiGenerationCounter.get();
                    boolean canRender = context.canRenderToUi(currentIdentity, visibleGen);
                    if (canRender) {
                        context.transitionToTerminal(vn.io.codelearning.springapitester.client.RequestExecutionState.FAILED);
                        applyFailedResponse(requestEndpoint, "Failed to build request: " + rootMessage(error), true);
                        sendBtn.setText("Send");
                    } else {
                        context.transitionToTerminal(vn.io.codelearning.springapitester.client.RequestExecutionState.FAILED);
                    }
                });
            }
        });
    }

    private byte[] lastResponseRawBytes;

    private void onSaveResponseToFile() {
        if (responseBodyEditor == null) return;
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setDialogTitle("Save Response to File");
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File fileToSave = fileChooser.getSelectedFile();
            try {
                byte[] rawBytes = currentEndpoint != null && currentEndpoint.getLastResponseRawBytes().length > 0
                        ? currentEndpoint.getLastResponseRawBytes()
                        : lastResponseRawBytes;
                if (rawBytes != null && rawBytes.length > 0) {
                    java.nio.file.Files.write(fileToSave.toPath(), rawBytes);
                } else {
                    String text = responseBodyEditor.getDocument().getText();
                    java.nio.file.Files.writeString(fileToSave.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
                }
                com.intellij.openapi.ui.Messages.showInfoMessage(project, "Response saved to " + fileToSave.getAbsolutePath(), "Saved");
            } catch (Exception ex) {
                com.intellij.openapi.ui.Messages.showErrorDialog(project, "Failed to save file: " + ex.getMessage(), "Error");
            }
        }
    }

    private void applySuccessfulResponse(EndpointModel endpoint,
                                         vn.io.codelearning.springapitester.client.HttpResponseModel response,
                                         boolean showInUi) {
        int code = response.getStatusCode();
        String message = response.getStatusMessage();
        if (message == null || message.isBlank()) message = getHttpStatusMessage(code);
        String body = response.getBody() != null ? response.getBody() : "";
        String headers = formatResponseHeaders(response);
        String format = detectResponseFormat(response);
        this.lastResponseRawBytes = response.getRawBytes();
        endpoint.setLastResponseRawBytes(response.getRawBytes());

        endpoint.setLastResponseStatusCode(code);
        endpoint.setLastResponseStatusMessage(message);
        endpoint.setLastResponseTimeTakenMs(response.getTimeTakenMs());
        endpoint.setLastResponseBody(body);
        endpoint.setLastResponseHeaders(headers);
        endpoint.setLastResponseFormat(format);
        saveEndpointState(endpoint);

        if (!showInUi) return;
        String color = code >= 200 && code < 300 ? "#5CB85C"
                : code >= 300 && code < 400 ? "#5BC0DE"
                : code >= 400 && code < 600 ? "#D9534F" : "#7A7A7A";
        
        StringBuilder badge = new StringBuilder();
        if (response.isTruncated()) {
            badge.append("&nbsp;<span style='background-color: #F0AD4E; color: white;'>&nbsp;<b>Truncated</b>&nbsp;</span>");
        }
        if (response.isBinary()) {
            badge.append("&nbsp;<span style='background-color: #5BC0DE; color: white;'>&nbsp;<b>Binary</b>&nbsp;</span>");
        }

        statusLabel.setText(String.format(
                "<html><span style='background-color: %s; color: white;'>&nbsp;<b>%d %s</b>&nbsp;</span>%s<font color='gray'> &nbsp; %d ms</font></html>",
                color, code, message, badge.toString(), response.getTimeTakenMs()));
        isUpdatingUI = true;
        try {
            responseLanguageCombo.setSelectedItem(format);
        } finally {
            isUpdatingUI = false;
        }
        ApplicationManager.getApplication().runWriteAction(() -> responseBodyEditor.getDocument().setText(body));
        responseHeadersArea.setText(headers);
        sendBtn.setEnabled(true);
    }

    private void applyFailedResponse(EndpointModel endpoint, String message, boolean showInUi) {
        endpoint.setLastResponseStatusCode(0);
        endpoint.setLastResponseStatusMessage(message);
        endpoint.setLastResponseTimeTakenMs(0);
        endpoint.setLastResponseBody(message);
        endpoint.setLastResponseHeaders("");
        endpoint.setLastResponseFormat("TEXT");
        saveEndpointState(endpoint);
        if (!showInUi) return;
        statusLabel.setText(message);
        ApplicationManager.getApplication().runWriteAction(() -> responseBodyEditor.getDocument().setText(message));
        responseHeadersArea.setText("");
        sendBtn.setEnabled(true);
    }

    private void saveEndpointState(EndpointModel endpoint) {
        vn.io.codelearning.springapitester.state.SpringLensState state =
                vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
        if (state != null) state.saveEndpoint(endpoint);
    }

    private String detectResponseFormat(vn.io.codelearning.springapitester.client.HttpResponseModel response) {
        if (response.isBinary()) return "TEXT";
        String contentType = "";
        if (response.getHeaders() != null) {
            for (java.util.Map.Entry<String, java.util.List<String>> entry : response.getHeaders().entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                    contentType = entry.getValue().get(0).toLowerCase();
                    break;
                }
            }
        }
        if (contentType.contains("json")) return "JSON";
        if (contentType.contains("html")) return "HTML";
        if (contentType.contains("xml")) return "XML";
        return "TEXT";
    }

    private String formatResponseHeaders(vn.io.codelearning.springapitester.client.HttpResponseModel response) {
        StringBuilder result = new StringBuilder();
        if (response.getHeaders() != null) {
            response.getHeaders().forEach((key, values) -> {
                boolean sensitive = vn.io.codelearning.springapitester.state.CredentialStore.isSensitiveHeader(key);
                values.forEach(value -> {
                    String displayVal = sensitive ? "[REDACTED]" : value;
                    result.append(key).append(": ").append(displayVal).append('\n');
                });
            });
        }
        return result.toString();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private String getEffectiveBaseUrl(EndpointModel endpoint) {
        if (endpoint == null) return baseUrl != null ? baseUrl : "http://localhost:8080";
        if (endpoint.isAbsoluteUrl()) {
            return "";
        }
        
        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
        if (state != null && state.gatewayModeEnabled && cachedGatewayConfig != null) {
            String[] parts = vn.io.codelearning.springapitester.util.GatewayUrlCalculator.calculateFull(endpoint, cachedGatewayConfig);
            if (parts != null && parts.length > 0 && parts[0] != null && !parts[0].isBlank()) {
                return parts[0];
            }
        }
        return endpoint.getDirectBaseUrl() != null && !endpoint.getDirectBaseUrl().isBlank() 
                ? endpoint.getDirectBaseUrl() 
                : (baseUrl != null ? baseUrl : "http://localhost:8080");
    }

    private void updateMethodComboColor(vn.io.codelearning.springapitester.model.HttpMethodEnum method) {
        if (method != null && methodComboBox != null) {
            Color color = Color.decode(method.getColorHex());
            methodComboBox.setForeground(color);
            methodComboBox.setFont(methodComboBox.getFont().deriveFont(Font.BOLD, 12f));
            methodComboBox.repaint();
        }
    }
}
