package vn.io.codelearning.springapitester.ui;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.util.GatewayConfigReader;

import java.util.Arrays;
import java.util.List;

public class EndpointTreePanelTest {

    @Test
    public void testFilterEndpointsBySourceFileReturnsAllEndpointsForAllFiles() {
        List<EndpointModel> endpoints = Arrays.asList(
                endpoint("/project/orders/OrderController.java", "/orders"),
                endpoint("/project/users/UserController.java", "/users")
        );

        List<EndpointModel> filteredEndpoints = EndpointTreePanel.filterEndpointsBySourceFile(endpoints, null);

        Assert.assertEquals(endpoints, filteredEndpoints);
    }

    @Test
    public void testFilterEndpointsBySourceFileReturnsOnlyMatchingFile() {
        EndpointModel orderEndpoint = endpoint("/project/orders/OrderController.java", "/orders");
        List<EndpointModel> endpoints = Arrays.asList(
                orderEndpoint,
                endpoint("/project/users/UserController.java", "/users"),
                endpoint("/project/orders/OrderController.java", "/orders/{id}")
        );

        List<EndpointModel> filteredEndpoints = EndpointTreePanel.filterEndpointsBySourceFile(endpoints, "/project/orders/OrderController.java");

        Assert.assertEquals(2, filteredEndpoints.size());
        Assert.assertSame(orderEndpoint, filteredEndpoints.get(0));
        Assert.assertEquals("/project/orders/OrderController.java", filteredEndpoints.get(1).getSourceFilePath());
    }

    @Test
    public void testFilterEndpointsBySourceFileReturnsNoEndpointsForUnknownFile() {
        List<EndpointModel> endpoints = Arrays.asList(
                endpoint("/project/orders/OrderController.java", "/orders"),
                endpoint("/project/users/UserController.java", "/users")
        );

        List<EndpointModel> filteredEndpoints = EndpointTreePanel.filterEndpointsBySourceFile(endpoints, "/project/billing/BillingController.java");

        Assert.assertTrue(filteredEndpoints.isEmpty());
    }

    @Test
    public void testGatewaySelectorIsShownOnlyForExposedEndpoint() {
        GatewayConfigReader.GatewayConfig gatewayConfig = gatewayConfig();
        EndpointModel publicEndpoint = gatewayEndpoint("/profile/users/{id}");
        EndpointModel internalEndpoint = gatewayEndpoint("/profile/internal/sync");

        Assert.assertTrue(EndpointTreePanel.shouldShowGatewaySelector(publicEndpoint, gatewayConfig));
        Assert.assertFalse(EndpointTreePanel.shouldShowGatewaySelector(internalEndpoint, gatewayConfig));
    }

    @Test
    public void testGatewaySelectorIsHiddenForManualAndAbsoluteEndpoints() {
        GatewayConfigReader.GatewayConfig gatewayConfig = gatewayConfig();
        EndpointModel manualEndpoint = gatewayEndpoint("/profile/users/1");
        manualEndpoint.setManual(true);
        EndpointModel absoluteEndpoint = gatewayEndpoint("https://example.com/profile/users/1");
        absoluteEndpoint.setAbsoluteUrl(true);

        Assert.assertFalse(EndpointTreePanel.shouldShowGatewaySelector(manualEndpoint, gatewayConfig));
        Assert.assertFalse(EndpointTreePanel.shouldShowGatewaySelector(absoluteEndpoint, gatewayConfig));
        Assert.assertFalse(EndpointTreePanel.shouldShowGatewaySelector(null, gatewayConfig));
    }

    private EndpointModel endpoint(String sourceFilePath, String path) {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, path, "ApiController", "com.example", "getApi");
        endpoint.setSourceFilePath(sourceFilePath);
        return endpoint;
    }

    private EndpointModel gatewayEndpoint(String path) {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, path, "ProfileController", "com.example", "getApi");
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
}
