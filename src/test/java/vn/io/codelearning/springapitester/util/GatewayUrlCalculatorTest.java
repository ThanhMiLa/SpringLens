package vn.io.codelearning.springapitester.util;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;

public class GatewayUrlCalculatorTest {

    @Test
    public void testDiscoveryLocatorMode() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";
        config.discoveryLocatorEnabled = true;

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/users/123", "UserController", "com.example", "getUser");
        endpoint.setModuleName("user-service");
        endpoint.setDirectBaseUrl("http://localhost:8081");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888/user-service", parts[0]);
        Assert.assertEquals("/users/123", parts[1]);
        Assert.assertEquals("http://localhost:8888/user-service/users/123", GatewayUrlCalculator.calculate(endpoint, config));
    }

    @Test
    public void testDiscoveryLocatorWithContextPath() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";
        config.discoveryLocatorEnabled = true;

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/users/123", "UserController", "com.example", "getUser");
        endpoint.setModuleName("user-service");
        endpoint.setDirectBaseUrl("http://localhost:8081/api/v1");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888/user-service/api/v1", parts[0]);
        Assert.assertEquals("/users/123", parts[1]);
        Assert.assertEquals("http://localhost:8888/user-service/api/v1/users/123", GatewayUrlCalculator.calculate(endpoint, config));
    }

    @Test
    public void testMatchByRouteId() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";

        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("profile-service");
        route.setUri("http://localhost:8082");
        route.getPathPredicates().add("/profile/**");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/profile/details", "ProfileController", "com.example", "getDetails");
        endpoint.setModuleName("profile-service");
        endpoint.setDirectBaseUrl("http://localhost:8082");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888", parts[0]);
        Assert.assertEquals("/profile/details", parts[1]);
        Assert.assertEquals("http://localhost:8888/profile/details", GatewayUrlCalculator.calculate(endpoint, config));
    }

    @Test
    public void testMatchByExactUri() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";

        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("custom-route-1");
        route.setUri("http://localhost:8085");
        route.getPathPredicates().add("/orders/**");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/orders/create", "OrderController", "com.example", "createOrder");
        endpoint.setModuleName("order-service-different-name");
        endpoint.setDirectBaseUrl("http://localhost:8085");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888", parts[0]);
        Assert.assertEquals("/orders/create", parts[1]);
        Assert.assertEquals("http://localhost:8888/orders/create", GatewayUrlCalculator.calculate(endpoint, config));
    }

    @Test
    public void testMatchByUriContains() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";

        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("route-auth");
        route.setUri("lb://auth-service");
        route.getPathPredicates().add("/auth/**");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/auth/login", "AuthController", "com.example", "login");
        endpoint.setModuleName("auth-service");
        endpoint.setDirectBaseUrl("http://localhost:8080");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888", parts[0]);
        Assert.assertEquals("/auth/login", parts[1]);
    }

    @Test
    public void testStripPrefixFilter() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";

        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("inventory-service");
        route.setUri("http://localhost:8083");
        route.getPathPredicates().add("/api/v1/inventory/**");
        route.setStripPrefix(1); // Strips /api
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/inventory/items", "InventoryController", "com.example", "getItems");
        endpoint.setModuleName("inventory-service");
        endpoint.setDirectBaseUrl("http://localhost:8083");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888/api", parts[0]);
        Assert.assertEquals("/inventory/items", parts[1]);
        Assert.assertEquals("http://localhost:8888/api/inventory/items", GatewayUrlCalculator.calculate(endpoint, config));
    }

    @Test
    public void testPrefixPathFilter() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";

        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("product-service");
        route.setUri("http://localhost:8084");
        route.getPathPredicates().add("/products/**");
        route.setPrefixPath("/v2");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/v2/products/list", "ProductController", "com.example", "getProducts");
        endpoint.setModuleName("product-service");
        endpoint.setDirectBaseUrl("http://localhost:8084");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888", parts[0]);
        Assert.assertEquals("/products/list", parts[1]);
    }

    @Test
    public void testRewritePathFilter() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";

        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("payment-service");
        route.setUri("http://localhost:8089");
        route.getPathPredicates().add("/gateway-payment/**");
        route.setRewritePathRegex("/gateway-payment/(?<segment>.*)");
        route.setRewritePathReplacement("/${segment}");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/pay", "PaymentController", "com.example", "pay");
        endpoint.setModuleName("payment-service");
        endpoint.setDirectBaseUrl("http://localhost:8089");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888/gateway-payment", parts[0]);
        Assert.assertEquals("/pay", parts[1]);
    }

    @Test
    public void testFallbackWhenNoRouteMatches() {
        GatewayConfigReader.GatewayConfig config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/unknown/path", "UnknownController", "com.example", "test");
        endpoint.setModuleName("my-service");
        endpoint.setDirectBaseUrl("http://localhost:9999");

        String[] parts = GatewayUrlCalculator.calculateFull(endpoint, config);
        Assert.assertEquals("http://localhost:8888/my-service", parts[0]);
        Assert.assertEquals("/unknown/path", parts[1]);
    }
}
