package vn.io.codelearning.springapitester.ui;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.openapi.editor.Editor;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.model.RequestTab;
import vn.io.codelearning.springapitester.model.ServerConfigMetadata;
import vn.io.codelearning.springapitester.scanner.SpringConfigResolutionService;
import vn.io.codelearning.springapitester.state.SpringLensState;
import vn.io.codelearning.springapitester.util.GatewayConfigReader;

import javax.swing.JTextField;
import java.lang.reflect.Field;

public class EndpointDetailPanelTabMemoryTest extends BasePlatformTestCase {

    public void testEachEndpointRestoresItsOwnLastRequestTab() throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            EndpointDetailPanel panel = new EndpointDetailPanel(getProject());
            try {
                EndpointModel endpointA = manualEndpoint("a");
                EndpointModel endpointB = manualEndpoint("b");
                JBTabbedPane requestTabs = requestTabs(panel);

                panel.displayEndpoint(endpointA);
                assertEquals(0, requestTabs.getSelectedIndex());

                requestTabs.setSelectedIndex(EndpointDetailPanel.requestTabIndex(RequestTab.HEADERS));
                assertEquals(RequestTab.HEADERS, endpointA.getSelectedRequestTab());

                panel.displayEndpoint(endpointB);
                assertEquals(0, requestTabs.getSelectedIndex());

                requestTabs.setSelectedIndex(EndpointDetailPanel.requestTabIndex(RequestTab.BODY));
                assertEquals(RequestTab.BODY, endpointB.getSelectedRequestTab());

                panel.displayEndpoint(endpointA);
                assertEquals(EndpointDetailPanel.requestTabIndex(RequestTab.HEADERS), requestTabs.getSelectedIndex());

                panel.displayEndpoint(endpointB);
                assertEquals(EndpointDetailPanel.requestTabIndex(RequestTab.BODY), requestTabs.getSelectedIndex());
            } finally {
                releaseEditor(panel, "requestBodyEditor");
                releaseEditor(panel, "responseBodyEditor");
            }
        });
    }

    public void testDisplayScannedEndpointUsesPreparedConfigMetadataOnEdt() throws Exception {
        SpringConfigResolutionService.getInstance(getProject()).invalidateCache();

        EdtTestUtil.runInEdtAndWait(() -> {
            EndpointDetailPanel panel = new EndpointDetailPanel(getProject());
            try {
                EndpointModel endpoint = new EndpointModel(
                        HttpMethodEnum.GET, "/orders", "OrderController", "demo", "findOrders");
                endpoint.setDirectBaseUrl("http://localhost:8081");
                endpoint.setServerConfigMetadata(new ServerConfigMetadata(
                        "/project/src/main/resources/application.yml", false, false));

                panel.displayEndpoint(endpoint);

                JTextField urlField = (JTextField) readField(panel, "urlField");
                assertEquals("Resolved from: /project/src/main/resources/application.yml", urlField.getToolTipText());
            } finally {
                releaseEditor(panel, "requestBodyEditor");
                releaseEditor(panel, "responseBodyEditor");
            }
        });
    }

    public void testGatewayModeFallsBackToDirectUrlForInternalEndpoint() throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            EndpointDetailPanel panel = new EndpointDetailPanel(getProject());
            SpringLensState state = SpringLensState.getInstance(getProject());
            boolean originalGatewayMode = state.gatewayModeEnabled;
            try {
                state.gatewayModeEnabled = true;
                panel.setGatewayConfig(profileGatewayConfig());

                EndpointModel publicEndpoint = scannedEndpoint("/profile/users/1");
                EndpointModel internalEndpoint = scannedEndpoint("/profile/internal/sync");
                JTextField urlField = (JTextField) readField(panel, "urlField");

                panel.displayEndpoint(publicEndpoint);
                assertEquals("http://localhost:8888/profile/users/1", urlField.getText());

                panel.displayEndpoint(internalEndpoint);
                assertEquals("http://localhost:8081/profile/internal/sync", urlField.getText());
                assertTrue(state.gatewayModeEnabled);
            } finally {
                state.gatewayModeEnabled = originalGatewayMode;
                releaseEditor(panel, "requestBodyEditor");
                releaseEditor(panel, "responseBodyEditor");
            }
        });
    }

    public void testGatewayModeUsesResolvedPrefixPathUrl() throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            EndpointDetailPanel panel = new EndpointDetailPanel(getProject());
            SpringLensState state = SpringLensState.getInstance(getProject());
            boolean originalGatewayMode = state.gatewayModeEnabled;
            try {
                state.gatewayModeEnabled = true;
                GatewayConfigReader.GatewayConfig gatewayConfig = new GatewayConfigReader.GatewayConfig();
                gatewayConfig.gatewayDetected = true;
                gatewayConfig.port = "8888";
                GatewayRouteModel route = new GatewayRouteModel();
                route.setId("profile-service");
                route.setUri("http://localhost:8081");
                route.getPathPredicates().add("/profile/users/**");
                route.setPrefixPath("/v2");
                gatewayConfig.routes.add(route);
                panel.setGatewayConfig(gatewayConfig);

                panel.displayEndpoint(scannedEndpoint("/v2/profile/users/1"));

                JTextField urlField = (JTextField) readField(panel, "urlField");
                assertEquals("http://localhost:8888/profile/users/1", urlField.getText());
            } finally {
                state.gatewayModeEnabled = originalGatewayMode;
                releaseEditor(panel, "requestBodyEditor");
                releaseEditor(panel, "responseBodyEditor");
            }
        });
    }

    private EndpointModel manualEndpoint(String id) {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/" + id, "", "", id);
        endpoint.setManual(true);
        endpoint.setId("request-tab-" + id);
        return endpoint;
    }

    private EndpointModel scannedEndpoint(String path) {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, path, "ProfileController", "demo", "getProfile");
        endpoint.setModuleName("profile-service");
        endpoint.setDirectBaseUrl("http://localhost:8081");
        return endpoint;
    }

    private GatewayConfigReader.GatewayConfig profileGatewayConfig() {
        GatewayConfigReader.GatewayConfig gatewayConfig = new GatewayConfigReader.GatewayConfig();
        gatewayConfig.gatewayDetected = true;
        gatewayConfig.port = "8888";
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("profile-service");
        route.setUri("http://localhost:8081");
        route.getPathPredicates().add("/profile/users/**");
        gatewayConfig.routes.add(route);
        return gatewayConfig;
    }

    private JBTabbedPane requestTabs(EndpointDetailPanel panel) {
        return (JBTabbedPane) readField(panel, "requestTabs");
    }

    private void releaseEditor(EndpointDetailPanel panel, String fieldName) {
        CodeEditorUtil.releaseEditor((Editor) readField(panel, fieldName));
    }

    private Object readField(EndpointDetailPanel panel, String fieldName) {
        try {
            Field field = EndpointDetailPanel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(panel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access EndpointDetailPanel field: " + fieldName, e);
        }
    }
}
