package vn.io.codelearning.springapitester.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.JBTabbedPane;
import vn.io.codelearning.springapitester.model.EndpointModel;

import javax.swing.*;
import java.awt.*;

public class EndpointDetailPanel extends JPanel {

    private final Project project;
    private EndpointModel currentEndpoint;

    private final JBLabel methodLabel;
    private final JBTextField urlField;
    private final JButton sendBtn;

    // TODO: We will need tables and editors here

    public EndpointDetailPanel(Project project) {
        this.project = project;
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
        
        // 2.1 Request Tabs
        JBTabbedPane requestTabs = new JBTabbedPane();
        requestTabs.addTab("Params", new JPanel()); // Placeholder
        requestTabs.addTab("Headers", new JPanel()); // Placeholder
        requestTabs.addTab("Auth", new JPanel()); // Placeholder
        requestTabs.addTab("Body", new JPanel()); // Placeholder
        
        mainSplitter.setFirstComponent(requestTabs);
        
        // 2.2 Response Tabs
        JBTabbedPane responseTabs = new JBTabbedPane();
        responseTabs.addTab("Response Body", new JPanel()); // Placeholder
        responseTabs.addTab("Response Headers", new JPanel()); // Placeholder
        
        mainSplitter.setSecondComponent(responseTabs);

        add(mainSplitter, BorderLayout.CENTER);
    }

    public void displayEndpoint(EndpointModel endpoint) {
        this.currentEndpoint = endpoint;
        if (endpoint == null) {
            methodLabel.setText("");
            urlField.setText("");
            return;
        }

        methodLabel.setText(endpoint.getHttpMethod().getLabel());
        urlField.setText(endpoint.getPath()); // Temporary: should be full URL

        // TODO: update tabs data
    }

    private void onSendClicked() {
        if (currentEndpoint == null) return;
        // TODO: trigger Http client
    }
}
