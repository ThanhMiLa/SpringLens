package vn.io.codelearning.springapitester.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.scanner.SpringConfigResolutionService;
import vn.io.codelearning.springapitester.scanner.SpringEndpointScanner;
import vn.io.codelearning.springapitester.scanner.SpringServerConfig;
import vn.io.codelearning.springapitester.state.EndpointSavedState;
import vn.io.codelearning.springapitester.state.SpringLensState;
import vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
        AtomicLong reloadGeneration = new AtomicLong();

        Runnable reloadTask = () -> {
            long generation = reloadGeneration.incrementAndGet();
            new Task.Backgroundable(project, "Scanning Spring Endpoints...", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    SpringConfigResolutionService configService = SpringConfigResolutionService.getInstance(project);
                    if (configService != null) {
                        configService.invalidateCache();
                    }
                    List<EndpointModel> scannedEndpoints = SpringEndpointScanner.getInstance().scanEndpoints(project);
                    indicator.checkCanceled();
                    if (generation != reloadGeneration.get()) return;

                    GatewayConfig gatewayConfig = configService != null
                            ? configService.resolveGatewayConfig()
                            : new GatewayConfig();
                    SpringServerConfig defaultServerConfig = configService != null
                            ? configService.resolveServerConfig()
                            : new SpringServerConfig();
                    indicator.checkCanceled();
                    if (generation != reloadGeneration.get()) return;

                    // Khôi phục trạng thái (Token, Body, Params) đã nhập trước đó
                    SpringLensState state = SpringLensState.getInstance(project);
                    if (state != null && !scannedEndpoints.isEmpty()) {
                        state.migrateLegacyKeys(scannedEndpoints);
                        state.pruneOrphanScannedEndpoints(scannedEndpoints);
                        for (EndpointModel endpoint : scannedEndpoints) {
                            state.restoreEndpoint(endpoint);
                        }
                    }

                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project.isDisposed() || generation != reloadGeneration.get()) return;
                        detailPanel.setGatewayConfig(gatewayConfig);
                        detailPanel.setDefaultBaseUrl(defaultServerConfig.getBaseUrl());
                        endpoints = scannedEndpoints;
                        if (treePanelHolder[0] != null) {
                            treePanelHolder[0].setGatewayAvailable(gatewayConfig.gatewayDetected);
                            treePanelHolder[0].updateEndpoints(endpoints);
                        }
                        detailPanel.refreshEndpoint();
                    });
                }
            }.queue();
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
            SpringLensState state = SpringLensState.getInstance(project);
            if (state != null) {
                // Apply to currently scanned endpoints in memory
                if (endpoints != null) {
                    for (EndpointModel ep : endpoints) {
                        ep.setAuthConfig(authConfig.cloneConfig());
                        state.saveEndpoint(ep);
                    }
                }
                // Apply to all stored endpoints in state
                for (EndpointSavedState storedEp : state.endpoints.values()) {
                    state.updateSavedAuthConfig(storedEp, authConfig);
                }
                for (EndpointSavedState storedManualEp : state.manualEndpoints) {
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
        DumbService.getInstance(project).runWhenSmart(reloadTask);
    }
}
