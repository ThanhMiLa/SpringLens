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
    private Runnable onReloadClicked;
    private Runnable onModeChanged;
    private com.intellij.openapi.ui.ComboBox<String> gatewayComboBox;

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
        JButton clearBtn = new JButton("🗑");
        clearBtn.setToolTipText("Clear all cached data and collections");
        clearBtn.addActionListener(e -> {
            int result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                    project,
                    "Are you sure you want to clear all cached data, including manual collections, request bodies, and responses? This action cannot be undone.",
                    "Clear All Data",
                    "Clear",
                    "Cancel",
                    com.intellij.openapi.ui.Messages.getWarningIcon()
            );
            if (result == com.intellij.openapi.ui.Messages.YES) {
                if (this.onEndpointSelected != null) {
                    this.onEndpointSelected.accept(null); // Clear panel first, which saves the current endpoint
                }
                vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
                if (state != null) {
                    state.clearAllData(); // Then wipe everything, including the one we just saved
                }
                vn.io.codelearning.springapitester.client.HttpClientService http = vn.io.codelearning.springapitester.client.HttpClientService.getInstance(project);
                if (http != null) {
                    http.clearCookies();
                }
                // Xóa sạch danh sách hiển thị trên UI, không quét lại
                updateEndpoints(new java.util.ArrayList<>());
            }
        });
        
        actionPanel.add(addBtn);
        actionPanel.add(clearBtn);
        actionPanel.add(reloadBtn);
        
        topPanel.add(actionPanel, BorderLayout.EAST);
        
        gatewayComboBox = new com.intellij.openapi.ui.ComboBox<>(new String[]{"🎯 Direct Services", "🌐 API Gateway"});
        gatewayComboBox.setVisible(false);
        vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
        if (state != null) {
            gatewayComboBox.setSelectedIndex(state.gatewayModeEnabled ? 1 : 0);
        }
        
        gatewayComboBox.addActionListener(e -> {
            if (state != null) {
                state.gatewayModeEnabled = (gatewayComboBox.getSelectedIndex() == 1);
            }
            if (onModeChanged != null) {
                onModeChanged.run();
            }
        });
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(topPanel, BorderLayout.NORTH);
        headerPanel.add(gatewayComboBox, BorderLayout.SOUTH);
        
        add(headerPanel, BorderLayout.NORTH);

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
                    if (bounds != null) {
                        javax.swing.tree.TreePath path = tree.getPathForRow(row);
                        if (path != null) {
                            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                            Object userObject = node.getUserObject();
                            
                            // Check if click is on the far right (for adding new manual request)
                            if (e.getX() > bounds.x + bounds.width - 35) {
                                if (userObject instanceof vn.io.codelearning.springapitester.model.FolderModel) {
                                    handleAddNewRequest((vn.io.codelearning.springapitester.model.FolderModel) userObject);
                                }
                            } 
                            // Check if click is on the icon area (left side)
                            else if (e.getX() >= bounds.x && e.getX() <= bounds.x + 22) {
                                if (userObject instanceof vn.io.codelearning.springapitester.model.EndpointModel) {
                                    vn.io.codelearning.springapitester.model.EndpointModel ep = (vn.io.codelearning.springapitester.model.EndpointModel) userObject;
                                    ep.setSecured(!ep.isSecured());
                                    
                                    vn.io.codelearning.springapitester.state.SpringLensState state = vn.io.codelearning.springapitester.state.SpringLensState.getInstance(project);
                                    if (state != null) {
                                        state.saveEndpoint(ep);
                                        vn.io.codelearning.springapitester.state.EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(ep));
                                        if (saved != null) {
                                            saved.hasSecuredOverride = true;
                                        }
                                    }
                                    tree.repaint();
                                    
                                    // Trigger detail panel update if this endpoint is currently selected
                                    if (onEndpointSelected != null && tree.getSelectionPath() != null) {
                                        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent();
                                        if (selectedNode == node) {
                                            onEndpointSelected.accept(ep);
                                        }
                                    }
                                }
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

    public void setOnModeChanged(Runnable onModeChanged) {
        this.onModeChanged = onModeChanged;
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

                        // Delegate to restoreEndpoint — handles isAbsoluteUrl, TLS consent,
                        // secret decryption, security overrides, and response cache
                        state.restoreEndpoint(manualEp);

                        DefaultMutableTreeNode epNode = new DefaultMutableTreeNode(manualEp);
                        folderNode.add(epNode);
                    }
                }
                rootNode.add(folderNode);
            }
        }

        // 2. Build Scanned Controllers
        if (endpoints != null && !endpoints.isEmpty()) {
            boolean hasGateway = false;
            try {
                com.intellij.openapi.module.Module[] modules = com.intellij.openapi.module.ModuleManager.getInstance(project).getModules();
                for (com.intellij.openapi.module.Module m : modules) {
                    if (m != null && !m.isDisposed() && vn.io.codelearning.springapitester.util.GatewayConfigReader.hasGatewayDependency(m)) {
                        hasGateway = true; break;
                    }
                }
            } catch (Throwable t) {
                // ignore
            }
            
            if (gatewayComboBox != null) {
                gatewayComboBox.setVisible(hasGateway);
            }
            
            java.util.Set<String> moduleNames = endpoints.stream().map(e -> e.getModuleName() != null ? e.getModuleName() : "Unknown").collect(Collectors.toSet());
            boolean useModuleLevel = moduleNames.size() > 1 || hasGateway;

            if (useModuleLevel) {
                Map<String, List<EndpointModel>> moduleGrouped = endpoints.stream()
                        .collect(Collectors.groupingBy(e -> e.getModuleName() != null ? e.getModuleName() : "Unknown"));
                
                for (Map.Entry<String, List<EndpointModel>> modEntry : moduleGrouped.entrySet()) {
                    String modName = modEntry.getKey();
                    String directUrl = modEntry.getValue().isEmpty() ? "" : modEntry.getValue().get(0).getDirectBaseUrl();
                    vn.io.codelearning.springapitester.model.ServiceModel service = new vn.io.codelearning.springapitester.model.ServiceModel(modName, directUrl, false);
                    DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(service);
                    
                    Map<String, List<EndpointModel>> ctrlGrouped = modEntry.getValue().stream()
                            .collect(Collectors.groupingBy(e -> (e.getControllerName() != null && !e.getControllerName().isEmpty()) ? e.getControllerName() : "Unknown"));
                    
                    for (Map.Entry<String, List<EndpointModel>> ctrlEntry : ctrlGrouped.entrySet()) {
                        DefaultMutableTreeNode ctrlNode = new DefaultMutableTreeNode(ctrlEntry.getKey());
                        for (EndpointModel ep : ctrlEntry.getValue()) {
                            ctrlNode.add(new DefaultMutableTreeNode(ep));
                        }
                        moduleNode.add(ctrlNode);
                    }
                    rootNode.add(moduleNode);
                }
            } else {
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
                    java.util.List<vn.io.codelearning.springapitester.state.EndpointSavedState> toRemove = state.manualEndpoints.stream()
                            .filter(ep -> folder.getId().equals(ep.folderId))
                            .toList();
                    for (vn.io.codelearning.springapitester.state.EndpointSavedState ep : toRemove) {
                        state.deleteManualEndpoint(ep.id);
                    }
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
            
            javax.swing.JMenuItem markPublicItem = new javax.swing.JMenuItem("Mark as Public (No Lock)");
            markPublicItem.addActionListener(ev -> {
                if (state != null) {
                    endpoint.setSecured(false);
                    state.saveEndpoint(endpoint); // saveEndpoint sets hasSecuredOverride and isSecuredOverride
                    // Wait, we need to explicitly set the override
                    vn.io.codelearning.springapitester.state.EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(endpoint));
                    if (saved == null) {
                        state.saveEndpoint(endpoint);
                        saved = state.endpoints.get(state.getEndpointKey(endpoint));
                    }
                    if (saved != null) {
                        saved.hasSecuredOverride = true;
                        saved.isSecuredOverride = false;
                    }
                    tree.repaint();
                }
            });

            javax.swing.JMenuItem markPrivateItem = new javax.swing.JMenuItem("Mark as Private (Lock)");
            markPrivateItem.addActionListener(ev -> {
                if (state != null) {
                    endpoint.setSecured(true);
                    vn.io.codelearning.springapitester.state.EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(endpoint));
                    if (saved == null) {
                        state.saveEndpoint(endpoint);
                        saved = state.endpoints.get(state.getEndpointKey(endpoint));
                    }
                    if (saved != null) {
                        saved.hasSecuredOverride = true;
                        saved.isSecuredOverride = true;
                    }
                    tree.repaint();
                }
            });

            javax.swing.JMenuItem resetSecurityItem = new javax.swing.JMenuItem("Reset Security Check (Auto)");
            resetSecurityItem.addActionListener(ev -> {
                if (state != null) {
                    vn.io.codelearning.springapitester.state.EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(endpoint));
                    if (saved != null) {
                        saved.hasSecuredOverride = false;
                        // We need to re-evaluate the auto state. 
                        // An easy way is to trigger a rescan or just repaint and let the scanner handle it on next refresh.
                        // But wait, the model's current state is overridden. Let's just ask user to click Reload.
                        com.intellij.openapi.ui.Messages.showInfoMessage("Security check reset to Auto. Please click Reload to re-scan.", "Reset Security");
                    }
                }
            });

            popup.add(markPublicItem);
            popup.add(markPrivateItem);
            popup.add(resetSecurityItem);
            
            if (endpoint.isManual()) {
                popup.addSeparator();
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
                        state.deleteManualEndpoint(endpoint.getId());
                        updateEndpoints(this.currentEndpoints);
                    }
                });
                
                popup.add(renameItem);
                popup.add(deleteItem);
            }
            popup.show(tree, e.getX(), e.getY());
        }
    }
}
