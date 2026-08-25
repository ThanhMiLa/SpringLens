package vn.io.codelearning.springapitester.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import vn.io.codelearning.springapitester.model.EndpointModel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EndpointTreePanel extends JPanel {

    private final Project project;
    private final Tree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final SearchTextField searchField;
    private final JButton reloadBtn;

    private List<EndpointModel> currentEndpoints;
    private final Consumer<EndpointModel> onEndpointSelected;
    private final Runnable onReloadClicked;

    public EndpointTreePanel(Project project, Consumer<EndpointModel> onEndpointSelected, Runnable onReloadClicked) {
        this.project = project;
        this.onEndpointSelected = onEndpointSelected;
        this.onReloadClicked = onReloadClicked;

        setLayout(new BorderLayout());

        // 1. Top Panel: Search + Reload Button
        JPanel topPanel = new JPanel(new BorderLayout());
        searchField = new SearchTextField();
        reloadBtn = new JButton("Reload");
        reloadBtn.addActionListener(e -> {
            if (this.onReloadClicked != null) {
                this.onReloadClicked.run();
            }
        });

        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(reloadBtn, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // 2. Tree
        rootNode = new DefaultMutableTreeNode("APIs");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new EndpointTreeCellRenderer());

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode != null && selectedNode.getUserObject() instanceof EndpointModel) {
                EndpointModel endpoint = (EndpointModel) selectedNode.getUserObject();
                if (this.onEndpointSelected != null) {
                    this.onEndpointSelected.accept(endpoint);
                }
            }
        });

        add(new JBScrollPane(tree), BorderLayout.CENTER);
    }

    public void updateEndpoints(List<EndpointModel> endpoints) {
        this.currentEndpoints = endpoints;
        rootNode.removeAllChildren();

        if (endpoints != null) {
            // Group by Controller Name
            Map<String, List<EndpointModel>> grouped = endpoints.stream()
                    .collect(Collectors.groupingBy(e -> 
                        (e.getControllerName() != null && !e.getControllerName().isEmpty()) ? e.getControllerName() : "Unknown"
                    ));

            for (Map.Entry<String, List<EndpointModel>> entry : grouped.entrySet()) {
                DefaultMutableTreeNode controllerNode = new DefaultMutableTreeNode(entry.getKey());
                for (EndpointModel ep : entry.getValue()) {
                    DefaultMutableTreeNode epNode = new DefaultMutableTreeNode(ep);
                    controllerNode.add(epNode);
                }
                rootNode.add(controllerNode);
            }
        }
        
        treeModel.reload();
        // Expand all
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}
