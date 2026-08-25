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

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class SpringLensToolWindowFactory implements ToolWindowFactory {

    private List<EndpointModel> endpoints = new ArrayList<>();

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Create the main panel
        JBSplitter mainSplitter = new JBSplitter(false, 0.3f);

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
                // TODO: Backup current state (Smart Merge preparation)
                endpoints = SpringEndpointScanner.getInstance().scanEndpoints(project);
                // TODO: Restore state
                
                // Update Tree
                treePanelHolder[0].updateEndpoints(endpoints);
            }
        );

        mainSplitter.setFirstComponent(treePanelHolder[0]);
        mainSplitter.setSecondComponent(detailPanel);

        // Register the content
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(mainSplitter, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
