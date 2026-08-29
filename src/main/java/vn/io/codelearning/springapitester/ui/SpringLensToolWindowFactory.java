package vn.io.codelearning.springapitester.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.scanner.SpringEndpointScanner;
import vn.io.codelearning.springapitester.client.HttpClientService;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class SpringLensToolWindowFactory implements ToolWindowFactory {

    private List<EndpointModel> endpoints = new ArrayList<>();

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Create the main panel
        JBSplitter mainSplitter = new JBSplitter(false, 0.3f);
        mainSplitter.setShowDividerControls(true); // Hiển thị nút < > để kéo thả
        mainSplitter.setDividerWidth(7); // Tăng độ dày để dễ cầm kéo
        mainSplitter.setShowDividerIcon(true);

        EndpointDetailPanel detailPanel = new EndpointDetailPanel(project);
        
        // Need an array trick to let the lambda reference the treePanel
        final EndpointTreePanel[] treePanelHolder = new EndpointTreePanel[1];

        treePanelHolder[0] = new EndpointTreePanel(project, 
            endpoint -> {
                // When an endpoint is selected
                detailPanel.displayEndpoint(endpoint);
            },
            () -> {
                // When Reload is clicked
                endpoints = SpringEndpointScanner.getInstance().scanEndpoints(project);
                
                // Khôi phục trạng thái (Token, Body, Params) đã nhập trước đó
                vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
                if (state != null) {
                    for (EndpointModel ep : endpoints) {
                        state.restoreEndpoint(ep);
                    }
                }
                
                // Update Tree
                treePanelHolder[0].updateEndpoints(endpoints);
            }
        );
        
        treePanelHolder[0].setOnModeChanged(() -> {
            detailPanel.refreshEndpoint();
        });

        detailPanel.setOnApplyToAllAuth(authConfig -> {
            vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
            if (state != null) {
                // Apply to currently scanned endpoints in memory
                if (endpoints != null) {
                    for (EndpointModel ep : endpoints) {
                        ep.setAuthConfig(authConfig.cloneConfig());
                        state.saveEndpoint(ep);
                    }
                }
                // Apply to all stored endpoints in state
                for (vn.io.codelearning.springapitester.state.EndpointSavedState storedEp : state.endpoints.values()) {
                    storedEp.authConfig = authConfig.cloneConfig();
                }
                for (vn.io.codelearning.springapitester.state.EndpointSavedState storedManualEp : state.manualEndpoints) {
                    storedManualEp.authConfig = authConfig.cloneConfig();
                }
            }
        });

        mainSplitter.setFirstComponent(treePanelHolder[0]);
        mainSplitter.setSecondComponent(detailPanel);
        
        detailPanel.setOnEndpointUpdated(() -> {
            if (treePanelHolder[0] != null) {
                treePanelHolder[0].repaintTree();
            }
        });

        // Register the content
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(mainSplitter, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
