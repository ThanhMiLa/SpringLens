package vn.io.codelearning.springapitester.ui;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;

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

    private EndpointModel endpoint(String sourceFilePath, String path) {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, path, "ApiController", "com.example", "getApi");
        endpoint.setSourceFilePath(sourceFilePath);
        return endpoint;
    }
}
