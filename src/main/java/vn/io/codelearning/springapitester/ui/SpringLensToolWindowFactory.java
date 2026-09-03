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

        Runnable reloadTask = () -> {
            com.intellij.openapi.progress.ProgressManager.getInstance().run(
                new com.intellij.openapi.progress.Task.Backgroundable(project, "Scanning Spring Endpoints...", true) {
                    @Override
                    public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                        List<EndpointModel> scannedEndpoints = SpringEndpointScanner.getInstance().scanEndpoints(project);
                        vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig gatewayConfig = 
                                vn.io.codelearning.springapitester.util.GatewayConfigReader.findGatewayConfig(project);
                        String defaultBaseUrl = vn.io.codelearning.springapitester.util.SpringBootConfigReader.extractBaseUrl(project);

                        // Khôi phục trạng thái (Token, Body, Params) đã nhập trước đó
                        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
                        if (state != null) {
                            state.migrateLegacyKeys(scannedEndpoints);
                            for (EndpointModel ep : scannedEndpoints) {
                                state.restoreEndpoint(ep);
                            }
                        }
                        
                        // Update Tree and DetailPanel on EDT
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                            if (project.isDisposed()) return;
                            detailPanel.setGatewayConfig(gatewayConfig);
                            detailPanel.setDefaultBaseUrl(defaultBaseUrl);
                            endpoints = scannedEndpoints;
                            if (treePanelHolder[0] != null) {
                                treePanelHolder[0].updateEndpoints(endpoints);
                            }
                            detailPanel.refreshEndpoint();
                        });
                    }
                }
            );
        };

        treePanelHolder[0] = new EndpointTreePanel(project, 
            endpoint -> {
                // When an endpoint is selected
                detailPanel.displayEndpoint(endpoint);
            },
            reloadTask
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
                    state.updateSavedAuthConfig(storedEp, authConfig);
                }
                for (vn.io.codelearning.springapitester.state.EndpointSavedState storedManualEp : state.manualEndpoints) {
                    state.updateSavedAuthConfig(storedManualEp, authConfig);
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

        // Auto-scan when project is smart (indexes ready)
        com.intellij.openapi.project.DumbService.getInstance(project).runWhenSmart(reloadTask);
    }
}
