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
        setMinimumSize(new Dimension(100, 100));

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
        
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        
        JButton addBtn = new JButton("+");
        addBtn.setToolTipText("New Collection");
        addBtn.addActionListener(e -> {
            vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
            if (state != null) {
                state.manualFolders.add(new vn.io.codelearning.springapitester.model.FolderModel("Collection"));
                updateEndpoints(this.currentEndpoints);
            }
        });
        
        actionPanel.add(addBtn);
        actionPanel.add(reloadBtn);
        
        topPanel.add(actionPanel, BorderLayout.EAST);
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
        
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int row = tree.getRowForLocation(e.getX(), e.getY());
                if (row == -1) {
                    if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                        tree.clearSelection();
                        showRootContextMenu(e);
                    }
                    return;
                }

                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    tree.setSelectionRow(row);
                    showContextMenu(e);
                } else if (javax.swing.SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    tree.setSelectionRow(row);
                    handleDoubleClickRename();
                } else if (javax.swing.SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    java.awt.Rectangle bounds = tree.getRowBounds(row);
                    if (bounds != null && e.getX() > bounds.x + bounds.width - 35) {
                        javax.swing.tree.TreePath path = tree.getPathForRow(row);
                        if (path != null) {
                            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                            if (node.getUserObject() instanceof vn.io.codelearning.springapitester.model.FolderModel) {
                                handleAddNewRequest((vn.io.codelearning.springapitester.model.FolderModel) node.getUserObject());
                            }
                        }
                    }
                }
            }
        });

        add(new JBScrollPane(tree), BorderLayout.CENTER);
    }
    
    private void handleAddNewRequest(vn.io.codelearning.springapitester.model.FolderModel folder) {
        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
        if (state != null) {
            vn.io.codelearning.springapitester.state.EndpointSavedState newEp = new vn.io.codelearning.springapitester.state.EndpointSavedState();
            newEp.id = java.util.UUID.randomUUID().toString();
            newEp.name = "New Request";
            newEp.isManual = true;
            newEp.folderId = folder.getId();
            newEp.httpMethod = vn.io.codelearning.springapitester.model.HttpMethodEnum.GET;
            newEp.path = "/new-api";
            state.manualEndpoints.add(newEp);
            updateEndpoints(this.currentEndpoints);
        }
    }
    
    private void handleDoubleClickRename() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selectedNode == null) return;
        
        Object userObject = selectedNode.getUserObject();
        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
        
        if (userObject instanceof vn.io.codelearning.springapitester.model.FolderModel) {
            vn.io.codelearning.springapitester.model.FolderModel folder = (vn.io.codelearning.springapitester.model.FolderModel) userObject;
            String newName = com.intellij.openapi.ui.Messages.showInputDialog(project, "Enter new folder name:", "Rename Folder", null, folder.getName(), null);
            if (newName != null && !newName.trim().isEmpty()) {
                folder.setName(newName.trim());
                updateEndpoints(this.currentEndpoints);
            }
        } else if (userObject instanceof EndpointModel) {
            EndpointModel endpoint = (EndpointModel) userObject;
            if (endpoint.isManual()) {
                String newName = com.intellij.openapi.ui.Messages.showInputDialog(project, "Enter new request name:", "Rename Request", null, endpoint.getName() != null ? endpoint.getName() : "New Request", null);
                if (newName != null && !newName.trim().isEmpty()) {
                    if (state != null) {
                        state.manualEndpoints.stream().filter(ep -> ep.id.equals(endpoint.getId())).findFirst().ifPresent(ep -> ep.name = newName.trim());
                    }
                    updateEndpoints(this.currentEndpoints);
                }
            }
        }
    }

    public void updateEndpoints(List<EndpointModel> endpoints) {
        this.currentEndpoints = endpoints;
        rootNode.removeAllChildren();

        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);

        if (state != null) {
            // 1. Build Manual Folders
            for (vn.io.codelearning.springapitester.model.FolderModel folder : state.manualFolders) {
                DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(folder);
                
                // Find manual endpoints in this folder
                for (vn.io.codelearning.springapitester.state.EndpointSavedState savedEp : state.manualEndpoints) {
                    if (folder.getId().equals(savedEp.folderId)) {
                        EndpointModel manualEp = new EndpointModel();
                        manualEp.setId(savedEp.id);
                        manualEp.setName(savedEp.name);
                        manualEp.setManual(true);
                        manualEp.setFolderId(savedEp.folderId);
                        manualEp.setHttpMethod(savedEp.httpMethod);
                        manualEp.setPath(savedEp.path);
                        manualEp.setAuthConfig(savedEp.authConfig);
                        manualEp.setCustomHeaders(savedEp.customHeaders);
                        manualEp.setRequestBodyJson(savedEp.requestBodyJson);
                        manualEp.setBodyType(savedEp.bodyType);
                        if (savedEp.manualParameters != null) {
                            manualEp.getParameters().addAll(savedEp.manualParameters);
                        }
                        
                        DefaultMutableTreeNode epNode = new DefaultMutableTreeNode(manualEp);
                        folderNode.add(epNode);
                    }
                }
                rootNode.add(folderNode);
            }
        }

        // 2. Build Scanned Controllers
        if (endpoints != null) {
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
    
    public void repaintTree() {
        tree.repaint();
    }
    
    private void showRootContextMenu(java.awt.event.MouseEvent e) {
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem addFolderItem = new javax.swing.JMenuItem("New Folder");
        addFolderItem.addActionListener(ev -> {
            vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
            if (state != null) {
                state.manualFolders.add(new vn.io.codelearning.springapitester.model.FolderModel("Collection"));
                updateEndpoints(this.currentEndpoints);
            }
        });
        popup.add(addFolderItem);
        popup.show(tree, e.getX(), e.getY());
    }

    private void showContextMenu(java.awt.event.MouseEvent e) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selectedNode == null) return;
        
        Object userObject = selectedNode.getUserObject();
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
        
        if (userObject instanceof vn.io.codelearning.springapitester.model.FolderModel) {
            vn.io.codelearning.springapitester.model.FolderModel folder = (vn.io.codelearning.springapitester.model.FolderModel) userObject;
            
            javax.swing.JMenuItem addRequestItem = new javax.swing.JMenuItem("New Request");
            addRequestItem.addActionListener(ev -> handleAddNewRequest(folder));
            
            javax.swing.JMenuItem renameItem = new javax.swing.JMenuItem("Rename Folder");
            renameItem.addActionListener(ev -> {
                String newName = com.intellij.openapi.ui.Messages.showInputDialog(project, "Enter new folder name:", "Rename Folder", null, folder.getName(), null);
                if (newName != null && !newName.trim().isEmpty()) {
                    folder.setName(newName.trim());
                    updateEndpoints(this.currentEndpoints);
                }
            });
            
            javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete Folder");
            deleteItem.addActionListener(ev -> {
                if (state != null) {
                    state.manualFolders.removeIf(f -> f.getId().equals(folder.getId()));
                    state.manualEndpoints.removeIf(ep -> folder.getId().equals(ep.folderId));
                    updateEndpoints(this.currentEndpoints);
                }
            });
            
            popup.add(addRequestItem);
            popup.addSeparator();
            popup.add(renameItem);
            popup.add(deleteItem);
            popup.show(tree, e.getX(), e.getY());
            
        } else if (userObject instanceof EndpointModel) {
            EndpointModel endpoint = (EndpointModel) userObject;
            if (endpoint.isManual()) {
                javax.swing.JMenuItem renameItem = new javax.swing.JMenuItem("Rename Request");
                renameItem.addActionListener(ev -> {
                    String newName = com.intellij.openapi.ui.Messages.showInputDialog(project, "Enter new request name:", "Rename Request", null, endpoint.getName(), null);
                    if (newName != null && !newName.trim().isEmpty()) {
                        if (state != null) {
                            state.manualEndpoints.stream().filter(ep -> ep.id.equals(endpoint.getId())).findFirst().ifPresent(ep -> ep.name = newName.trim());
                        }
                        updateEndpoints(this.currentEndpoints);
                    }
                });
                
                javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete Request");
                deleteItem.addActionListener(ev -> {
                    if (state != null) {
                        state.manualEndpoints.removeIf(ep -> ep.id.equals(endpoint.getId()));
                        updateEndpoints(this.currentEndpoints);
                    }
                });
                
                popup.add(renameItem);
                popup.add(deleteItem);
                popup.show(tree, e.getX(), e.getY());
            }
        }
    }
}
