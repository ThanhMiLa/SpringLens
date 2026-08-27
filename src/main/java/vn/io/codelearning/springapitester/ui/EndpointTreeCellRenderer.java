package vn.io.codelearning.springapitester.ui;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class EndpointTreeCellRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof DefaultMutableTreeNode node)) return;

        Object userObject = node.getUserObject();

        if (userObject instanceof String) {
            // Scanned Controller Node
            setIcon(com.intellij.icons.AllIcons.Nodes.Folder);
            append((String) userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        } else if (userObject instanceof vn.io.codelearning.springapitester.model.FolderModel) {
            // Manual Folder Node
            vn.io.codelearning.springapitester.model.FolderModel folder = (vn.io.codelearning.springapitester.model.FolderModel) userObject;
            setIcon(com.intellij.icons.AllIcons.Nodes.Folder);
            append(folder.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            // Append [+] symbol
            append("   [+]", SimpleTextAttributes.GRAY_ATTRIBUTES);
        } else if (userObject instanceof EndpointModel) {
            // Endpoint Node
            EndpointModel endpoint = (EndpointModel) userObject;
            
            if (endpoint.isSecured()) {
                setIcon(com.intellij.icons.AllIcons.Nodes.Padlock); // Khóa
            } else {
                setIcon(null); // Bỏ trống không hiển thị icon cho API public
            }
            
            if (endpoint.isManual()) {
                HttpMethodEnum method = endpoint.getHttpMethod();
                if (method == null) method = HttpMethodEnum.GET;

                Color methodColor = Color.decode(method.getColorHex());
                SimpleTextAttributes methodAttr = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, methodColor);
                
                String methodStr = String.format("%-7s", method.getLabel());
                append(methodStr, methodAttr);
                
                append(endpoint.getName() != null ? endpoint.getName() : "New Request", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else {
                HttpMethodEnum method = endpoint.getHttpMethod();
                if (method == null) method = HttpMethodEnum.GET;

                // Draw HTTP Method with color
                Color methodColor = Color.decode(method.getColorHex());
                SimpleTextAttributes methodAttr = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, methodColor);
                
                String methodStr = String.format("%-7s", method.getLabel());
                append(methodStr, methodAttr);

                // Tên Java Method
                if (endpoint.getMethodName() != null && !endpoint.getMethodName().isBlank()) {
                    append(endpoint.getMethodName() + "   ", SimpleTextAttributes.GRAY_ATTRIBUTES);
                }

                // Draw Path in Regular
                append(endpoint.getPath() != null ? endpoint.getPath() : "", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        }
    }
}
