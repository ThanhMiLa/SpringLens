package vn.io.codelearning.springapitester.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
                    String url = urlField.getText();
                    String effectiveBaseUrl = getEffectiveBaseUrl(currentEndpoint);
                    if (url.startsWith(effectiveBaseUrl)) {
                        currentEndpoint.setPath(url.substring(effectiveBaseUrl.length()));
                    } else {
                        currentEndpoint.setPath(url);
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

        JButton curlBtn = new JButton("cURL");
        curlBtn.setToolTipText("Copy as cURL");
        curlBtn.addActionListener(e -> {
            if (currentEndpoint == null) return;
            collectDataToModel();
            String curl = vn.io.codelearning.springapitester.client.CurlBuilder.buildCurl(currentEndpoint, urlField.getText());
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(new java.awt.datatransfer.StringSelection(curl));
            com.intellij.openapi.ui.Messages.showInfoMessage(project, "cURL command copied to clipboard!", "Success");
        });

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
                int choice = com.intellij.openapi.ui.Messages.showYesNoDialog(
                        project,
                        "This disables certificate validation for local requests and can expose credentials. Enable it only for a trusted development server.",
                        "Enable Insecure Local TLS",
                        "Enable",
                        "Cancel",
                        com.intellij.openapi.ui.Messages.getWarningIcon()
                );
                if (choice != com.intellij.openapi.ui.Messages.YES) {
                    insecureTlsCheckBox.setSelected(false);
                }
            }
            currentEndpoint.setAllowInsecureTls(insecureTlsCheckBox.isSelected());
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
        persistRequestBodiesCheckBox.setToolTipText("Request bodies may contain sensitive data; disabled by default");
        persistResponseHistoryCheckBox.setToolTipText("Response snapshots may contain sensitive data; disabled by default");
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

        requestTabs.addTab("Params", paramPanel);
        requestTabs.addTab("Headers", headerPanel);
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

        this.currentEndpoint = endpoint;
        if (endpoint == null) {
            isUpdatingUI = true;
            try {
                methodComboBox.setSelectedItem(vn.io.codelearning.springapitester.model.HttpMethodEnum.GET);
                urlField.setText("");
                paramPanel.setParameters(new java.util.ArrayList<>());
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
            } finally {
                isUpdatingUI = false;
            }
            return;
        }

        isUpdatingUI = true;
        try {
            vn.io.codelearning.springapitester.model.HttpMethodEnum method = endpoint.getHttpMethod() != null ? endpoint.getHttpMethod() : vn.io.codelearning.springapitester.model.HttpMethodEnum.GET;
            methodComboBox.setSelectedItem(method);
            updateMethodComboColor(method);

            String effectiveBaseUrl = getEffectiveBaseUrl(endpoint);
            String fullUrl = effectiveBaseUrl + endpoint.getPath();
            urlField.setText(fullUrl.replace("//", "/").replace("http:/l", "http://l").replace("https:/l", "https://l"));

            paramPanel.setParameters(endpoint.getParameters());
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
        // We don't overwrite currentEndpoint.setParameters() because the table models mutate the Param objects directly.
        // If we do, we will lose internal framework params and form data params.
        // We just leave endpoint.getParameters() intact.
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
        if (currentEndpoint == null) return;
        collectDataToModel();

        String fullUrl = urlField.getText();
        
        // Validate Path Variables: Check if any unpopulated path variables remain
        String testUrl = fullUrl;
        if (currentEndpoint.getParameters() != null) {
            for (ParameterModel param : currentEndpoint.getParameters()) {
                if (param.getParamType() == ParamTypeEnum.PATH_VARIABLE) {
                    String val = param.getCurrentValue() != null ? param.getCurrentValue().trim() : "";
                    if (!val.isEmpty()) {
                        testUrl = vn.io.codelearning.springapitester.scanner.SpringUrlUtils.replacePathVariable(testUrl, param.getName(), val);
                    }
                }
            }
        }
        
        if (vn.io.codelearning.springapitester.scanner.SpringUrlUtils.hasUnresolvedPathVariables(testUrl)) {
            java.util.List<String> missing = vn.io.codelearning.springapitester.scanner.SpringUrlUtils.getUnresolvedPathVariables(testUrl);
            String missingNames = String.join(", ", missing);
            JOptionPane.showMessageDialog(this, 
                "Please fill in value for Path Variable: {" + missingNames + "} in the Params tab before sending.",
                "Missing Path Variable", 
                JOptionPane.WARNING_MESSAGE);
            if (requestTabs != null) {
                requestTabs.setSelectedIndex(0); // Switch to Params tab
            }
            return;
        }

        sendBtn.setEnabled(false);
        statusLabel.setText("Sending...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Request request = HttpRequestBuilder.buildRequest(currentEndpoint, fullUrl);
                
                HttpClientService.getInstance().executeAsync(request, currentEndpoint.isAllowInsecureTls()).thenAccept(response -> {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        int code = response.getStatusCode();
                        String colorHex = "#7A7A7A"; // Default gray
                        if (code >= 200 && code < 300) colorHex = "#5CB85C"; // Green
                        else if (code >= 300 && code < 400) colorHex = "#5BC0DE"; // Cyan
                        else if (code >= 400 && code < 600) colorHex = "#D9534F"; // Red
                        
                        String msg = response.getStatusMessage();
                        if (msg == null || msg.isBlank()) {
                            msg = getHttpStatusMessage(code);
                        }
                        
                        String html = String.format("<html><span style='background-color: %s; color: white;'>&nbsp;<b>%d %s</b>&nbsp;</span><font color='gray'> &nbsp; %d ms</font></html>", 
                                                    colorHex, code, msg, response.getTimeTakenMs());
                        statusLabel.setText(html);
                        
                        String bodyText = response.getBody() != null ? response.getBody() : "";

                        // Update Response Body Editor
                        ApplicationManager.getApplication().runWriteAction(() -> {
                            // Find Content-Type
                            String contentType = "";
                            if (response.getHeaders() != null) {
                                for (java.util.Map.Entry<String, java.util.List<String>> entry : response.getHeaders().entrySet()) {
                                    if ("Content-Type".equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                                        contentType = entry.getValue().get(0).toLowerCase();
                                        break;
                                    }
                                }
                            }
                            
                            // Auto-select dropdown without triggering action listener yet
                            String targetFormat = "TEXT";
                            if (contentType.contains("json")) targetFormat = "JSON";
                            else if (contentType.contains("html")) targetFormat = "HTML";
                            else if (contentType.contains("xml")) targetFormat = "XML";
                            
                            isUpdatingUI = true;
                            try {
                                if (!targetFormat.equals(responseLanguageCombo.getSelectedItem())) {
                                    responseLanguageCombo.setSelectedItem(targetFormat);
                                }
                            } finally {
                                isUpdatingUI = false;
                            }
                            
                            responseBodyEditor.getDocument().setText(bodyText);
                        });

                        // Update Headers
                        StringBuilder headersStr = new StringBuilder();
                        if (response.getHeaders() != null) {
                            response.getHeaders().forEach((k, vList) -> {
                                for (String v : vList) {
                                    headersStr.append(k).append(": ").append(v).append("\n");
                                }
                            });
                        }
                        String headersText = headersStr.toString();
                        responseHeadersArea.setText(headersText);

                        // Save Response Cache to Model & Persistent State
                        if (currentEndpoint != null) {
                            currentEndpoint.setLastResponseStatusCode(code);
                            currentEndpoint.setLastResponseStatusMessage(msg);
                            currentEndpoint.setLastResponseTimeTakenMs(response.getTimeTakenMs());
                            currentEndpoint.setLastResponseBody(bodyText);
                            currentEndpoint.setLastResponseHeaders(headersText);
                            String selectedFormat = (String) responseLanguageCombo.getSelectedItem();
                            currentEndpoint.setLastResponseFormat(selectedFormat != null ? selectedFormat : "JSON");

                            vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
                            if (state != null) {
                                state.saveEndpoint(currentEndpoint);
                            }
                        }

                        sendBtn.setEnabled(true);
                    });
                }).exceptionally(ex -> {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        String errorMsg = "Error: " + ex.getMessage();
                        statusLabel.setText(errorMsg);
                        ApplicationManager.getApplication().runWriteAction(() -> {
                            responseBodyEditor.getDocument().setText(errorMsg);
                        });
                        responseHeadersArea.setText("");

                        if (currentEndpoint != null) {
                            currentEndpoint.setLastResponseStatusCode(0);
                            currentEndpoint.setLastResponseStatusMessage(errorMsg);
                            currentEndpoint.setLastResponseTimeTakenMs(0);
                            currentEndpoint.setLastResponseBody(errorMsg);
                            currentEndpoint.setLastResponseHeaders("");
                            currentEndpoint.setLastResponseFormat("TEXT");

                            vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
                            if (state != null) {
                                state.saveEndpoint(currentEndpoint);
                            }
                        }
                        sendBtn.setEnabled(true);
                    });
                    return null;
                });
                
            } catch (Exception ex) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    String errorMsg = "Failed to build request: " + ex.getMessage();
                    statusLabel.setText(errorMsg);
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        responseBodyEditor.getDocument().setText(errorMsg);
                    });
                    responseHeadersArea.setText("");

                    if (currentEndpoint != null) {
                        currentEndpoint.setLastResponseStatusCode(0);
                        currentEndpoint.setLastResponseStatusMessage(errorMsg);
                        currentEndpoint.setLastResponseTimeTakenMs(0);
                        currentEndpoint.setLastResponseBody(errorMsg);
                        currentEndpoint.setLastResponseHeaders("");
                        currentEndpoint.setLastResponseFormat("TEXT");

                        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
                        if (state != null) {
                            state.saveEndpoint(currentEndpoint);
                        }
                    }
                    sendBtn.setEnabled(true);
                });
            }
        });
    }

    private String getEffectiveBaseUrl(EndpointModel endpoint) {
        if (endpoint == null) return baseUrl != null ? baseUrl : "http://localhost:8080";
        if (endpoint.isManual()) return baseUrl != null ? baseUrl : "http://localhost:8080";
        
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
