package vn.io.codelearning.springapitester.ui;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.treeStructure.Tree;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.util.GatewayConfigReader;

import javax.swing.JComboBox;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.lang.reflect.Field;
import java.util.List;

public class EndpointTreePanelGatewayVisibilityTest extends BasePlatformTestCase {

    public void testSelectionShowsGatewaySelectorOnlyForExposedEndpoint() throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            EndpointModel publicEndpoint = endpoint("/profile/users/1");
            EndpointModel internalEndpoint = endpoint("/profile/internal/sync");
            EndpointTreePanel panel = new EndpointTreePanel(getProject(), selected -> {}, () -> {});
            panel.setGatewayConfig(gatewayConfig());
            panel.updateEndpoints(List.of(publicEndpoint, internalEndpoint));

            Tree tree = (Tree) readField(panel, "tree");
            JComboBox<?> gatewayComboBox = (JComboBox<?>) readField(panel, "gatewayComboBox");

            tree.setSelectionPath(pathForEndpoint(tree, publicEndpoint));
            assertTrue(gatewayComboBox.isVisible());

            tree.setSelectionPath(pathForEndpoint(tree, internalEndpoint));
            assertFalse(gatewayComboBox.isVisible());
        });
    }

    public void testSelectionShowsGatewaySelectorWhenContextPathMatchesRoute() throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            EndpointModel endpoint = new EndpointModel(
                    HttpMethodEnum.POST, "/create-post", "PostController", "demo", "createPost");
            endpoint.setModuleName("post-service");
            endpoint.setDirectBaseUrl("http://localhost:8084/post");

            GatewayConfigReader.GatewayConfig gatewayConfig = new GatewayConfigReader.GatewayConfig();
            gatewayConfig.gatewayDetected = true;
            GatewayRouteModel route = new GatewayRouteModel();
            route.setId("post-service");
            route.setUri("http://localhost:8084");
            route.getPathPredicates().add("/post/**");
            gatewayConfig.routes.add(route);

            EndpointTreePanel panel = new EndpointTreePanel(getProject(), selected -> {}, () -> {});
            panel.setGatewayConfig(gatewayConfig);
            panel.updateEndpoints(List.of(endpoint));

            Tree tree = (Tree) readField(panel, "tree");
            JComboBox<?> gatewayComboBox = (JComboBox<?>) readField(panel, "gatewayComboBox");
            tree.setSelectionPath(pathForEndpoint(tree, endpoint));

            assertTrue(gatewayComboBox.isVisible());
        });
    }

    private EndpointModel endpoint(String path) {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, path, "ProfileController", "demo", "getProfile");
        endpoint.setModuleName("profile-service");
        endpoint.setDirectBaseUrl("http://localhost:8081");
        return endpoint;
    }

    private GatewayConfigReader.GatewayConfig gatewayConfig() {
        GatewayConfigReader.GatewayConfig gatewayConfig = new GatewayConfigReader.GatewayConfig();
        gatewayConfig.gatewayDetected = true;
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("profile-service");
        route.setUri("http://localhost:8081");
        route.getPathPredicates().add("/profile/users/**");
        gatewayConfig.routes.add(route);
        return gatewayConfig;
    }

    private TreePath pathForEndpoint(Tree tree, EndpointModel endpoint) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        java.util.Enumeration<?> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() == endpoint) {
                return new TreePath(node.getPath());
            }
        }
        throw new AssertionError("Endpoint node not found: " + endpoint.getPath());
    }

    private Object readField(EndpointTreePanel panel, String fieldName) {
        try {
            Field field = EndpointTreePanel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(panel);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access EndpointTreePanel field: " + fieldName, exception);
        }
    }
}
