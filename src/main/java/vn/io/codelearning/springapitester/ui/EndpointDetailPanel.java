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

import javax.swing.*;
import java.awt.*;

public class EndpointDetailPanel extends JPanel {

    private final Project project;
    private EndpointModel currentEndpoint;

    private final JBLabel methodLabel;
    private final JBTextField urlField;
    private final JButton sendBtn;

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

    // Default local base URL for testing
    private String baseUrl;

    public EndpointDetailPanel(Project project) {
        this.project = project;
        this.baseUrl = vn.io.codelearning.springapitester.util.SpringBootConfigReader.extractBaseUrl(project);
        setLayout(new BorderLayout());

        // 1. Top Bar
        JPanel topBar = new JPanel(new BorderLayout(5, 5));
        topBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        methodLabel = new JBLabel("GET");
        methodLabel.setFont(methodLabel.getFont().deriveFont(Font.BOLD));
        
        urlField = new JBTextField();
        
        sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> onSendClicked());

        topBar.add(methodLabel, BorderLayout.WEST);
        topBar.add(urlField, BorderLayout.CENTER);
        topBar.add(sendBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 2. Splitter for Request / Response
        JBSplitter mainSplitter = new JBSplitter(true, 0.5f);
        
        // --- 2.1 Request Tabs ---
        JBTabbedPane requestTabs = new JBTabbedPane();
        paramPanel = new ParamTablePanel(java.util.List.of(
            vn.io.codelearning.springapitester.model.ParamTypeEnum.PATH_VARIABLE,
            vn.io.codelearning.springapitester.model.ParamTypeEnum.QUERY_PARAM
        ));
        headerPanel = new HeaderTablePanel();
        authPanel = new AuthPanel();
        
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
        
        // Toolbar for Response (Language Dropdown)
        JPanel responseToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
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
        
        // Update Panel
        responseBodyPanel.removeAll();
        responseBodyPanel.add(responseBodyEditor.getComponent(), BorderLayout.CENTER);
        responseBodyPanel.revalidate();
        responseBodyPanel.repaint();
    }

    public void displayEndpoint(EndpointModel endpoint) {
        // Collect old data before switching
        collectDataToModel();

        this.currentEndpoint = endpoint;
        if (endpoint == null) {
            methodLabel.setText("");
            urlField.setText("");
            return;
        }

        methodLabel.setText(endpoint.getHttpMethod().getLabel());
        urlField.setText(baseUrl + endpoint.getPath());

        paramPanel.setParameters(endpoint.getParameters());
        formDataPanel.setParameters(endpoint.getParameters());
        headerPanel.setHeaders(endpoint.getCustomHeaders());
        authPanel.setAuthConfig(endpoint.getAuthConfig());

        String json = endpoint.getRequestBodyJson() != null ? endpoint.getRequestBodyJson() : "";
        
        // Safely update editor document
        ApplicationManager.getApplication().runWriteAction(() -> {
            requestBodyEditor.getDocument().setText(json);
            responseBodyEditor.getDocument().setText("");
        });
        
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
        
        statusLabel.setText("Ready");
        responseHeadersArea.setText("");
    }

    private void collectDataToModel() {
        if (currentEndpoint == null) return;
        // We don't overwrite currentEndpoint.setParameters() because the table models mutate the Param objects directly.
        // If we do, we will lose internal framework params and form data params.
        // We just leave endpoint.getParameters() intact.
        
        currentEndpoint.setCustomHeaders(headerPanel.getHeaders());
        currentEndpoint.setAuthConfig(authPanel.getAuthConfig());
        currentEndpoint.setRequestBodyJson(requestBodyEditor.getDocument().getText());
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

    private void onSendClicked() {
        if (currentEndpoint == null) return;
        collectDataToModel();

        sendBtn.setEnabled(false);
        statusLabel.setText("Sending...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String fullUrl = urlField.getText();
                Request request = HttpRequestBuilder.buildRequest(currentEndpoint, fullUrl);
                
                HttpClientService.getInstance().executeAsync(request).thenAccept(response -> {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        statusLabel.setText("Status: " + response.getStatusCode() + " " + response.getStatusMessage() + " | Time: " + response.getTimeTakenMs() + "ms");
                        
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
                            
                            // Prevent infinite loop by temporary removing listener?
                            // No, just setSelectedItem will trigger it, which is fine, it handles the editor recreate!
                            
                            if (!targetFormat.equals(responseLanguageCombo.getSelectedItem())) {
                                responseLanguageCombo.setSelectedItem(targetFormat);
                            }
                            
                            responseBodyEditor.getDocument().setText(response.getBody() != null ? response.getBody() : "");
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
                        responseHeadersArea.setText(headersStr.toString());
                        sendBtn.setEnabled(true);
                    });
                }).exceptionally(ex -> {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        statusLabel.setText("Error: " + ex.getMessage());
                        sendBtn.setEnabled(true);
                    });
                    return null;
                });
                
            } catch (Exception ex) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    statusLabel.setText("Failed to build request: " + ex.getMessage());
                    sendBtn.setEnabled(true);
                });
            }
        });
    }
}
