package vn.io.codelearning.springapitester.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.GatewayEndpointResolution;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;
import vn.io.codelearning.springapitester.model.GatewayRouteStatus;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;

public class GatewayEndpointResolverTest {

    private GatewayConfigReader.GatewayConfig config;

    @Before
    public void setUp() {
        config = new GatewayConfigReader.GatewayConfig();
        config.port = "8888";
        config.gatewayDetected = true;
    }

    @Test
    public void testServiceWithoutRouteIsNotRoutable() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("identity-service");
        route.setUri("http://localhost:8082");
        route.getPathPredicates().add("/identity/**");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/profile/me", "ProfileController", "com.example", "getMe");
        endpoint.setModuleName("profile-service");
        endpoint.setDirectBaseUrl("http://localhost:8081");

        GatewayEndpointResolution resolution = GatewayEndpointResolver.resolve(endpoint, config);
        Assert.assertEquals(GatewayRouteStatus.NOT_ROUTABLE, resolution.getStatus());
        Assert.assertFalse(resolution.isRoutable());
        Assert.assertEquals("http://localhost:8081/profile/me", resolution.getFullDirectUrl());
    }

    @Test
    public void testServiceWithRouteMatchingPathIsRoutable() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("identity-service");
        route.setUri("http://localhost:8082");
        route.getPathPredicates().add("/identity/**");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/identity/auth/login", "AuthController", "com.example", "login");
        endpoint.setModuleName("identity-service");
        endpoint.setDirectBaseUrl("http://localhost:8082");

        GatewayEndpointResolution resolution = GatewayEndpointResolver.resolve(endpoint, config);
        Assert.assertEquals(GatewayRouteStatus.ROUTABLE, resolution.getStatus());
        Assert.assertTrue(resolution.isRoutable());
        Assert.assertEquals("http://localhost:8888/identity/auth/login", resolution.getFullGatewayUrl());
    }

    @Test
    public void testServiceWithRouteNotMatchingPathIsInternal() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("profile-service");
        route.setUri("http://localhost:8081");
        route.getPathPredicates().add("/profile/users/**");
        config.routes.add(route);

        EndpointModel publicEndpoint1 = new EndpointModel(HttpMethodEnum.GET, "/profile/users/{id}", "ProfileController", "com.example", "getUser");
        publicEndpoint1.setModuleName("profile-service");
        publicEndpoint1.setDirectBaseUrl("http://localhost:8081");

        EndpointModel publicEndpoint2 = new EndpointModel(HttpMethodEnum.GET, "/profile/users/search", "ProfileController", "com.example", "search");
        publicEndpoint2.setModuleName("profile-service");
        publicEndpoint2.setDirectBaseUrl("http://localhost:8081");

        EndpointModel internalEndpoint = new EndpointModel(HttpMethodEnum.POST, "/profile/internal/sync", "ProfileController", "com.example", "sync");
        internalEndpoint.setModuleName("profile-service");
        internalEndpoint.setDirectBaseUrl("http://localhost:8081");

        GatewayEndpointResolution res1 = GatewayEndpointResolver.resolve(publicEndpoint1, config);
        GatewayEndpointResolution res2 = GatewayEndpointResolver.resolve(publicEndpoint2, config);
        GatewayEndpointResolution resInternal = GatewayEndpointResolver.resolve(internalEndpoint, config);

        Assert.assertTrue(res1.isRoutable());
        Assert.assertEquals("http://localhost:8888/profile/users/{id}", res1.getFullGatewayUrl());

        Assert.assertTrue(res2.isRoutable());
        Assert.assertEquals("http://localhost:8888/profile/users/search", res2.getFullGatewayUrl());

        Assert.assertFalse(resInternal.isRoutable());
        Assert.assertEquals(GatewayRouteStatus.NOT_ROUTABLE, resInternal.getStatus());
        Assert.assertEquals("http://localhost:8081/profile/internal/sync", resInternal.getFullDirectUrl());
    }

    @Test
    public void testMultipleRoutesForSameService() {
        GatewayRouteModel route1 = new GatewayRouteModel();
        route1.setId("profile-users");
        route1.setUri("lb://profile-service");
        route1.getPathPredicates().add("/profile/users/**");
        config.routes.add(route1);

        GatewayRouteModel route2 = new GatewayRouteModel();
        route2.setId("profile-public");
        route2.setUri("lb://profile-service");
        route2.getPathPredicates().add("/profile/public/**");
        config.routes.add(route2);

        EndpointModel userEp = new EndpointModel(HttpMethodEnum.GET, "/profile/users/1", "Ctrl", "pkg", "m");
        userEp.setModuleName("profile-service");
        userEp.setDirectBaseUrl("http://localhost:8081");

        EndpointModel publicEp = new EndpointModel(HttpMethodEnum.GET, "/profile/public/info", "Ctrl", "pkg", "m");
        publicEp.setModuleName("profile-service");
        publicEp.setDirectBaseUrl("http://localhost:8081");

        EndpointModel privateEp = new EndpointModel(HttpMethodEnum.GET, "/profile/private/data", "Ctrl", "pkg", "m");
        privateEp.setModuleName("profile-service");
        privateEp.setDirectBaseUrl("http://localhost:8081");

        Assert.assertTrue(GatewayEndpointResolver.resolve(userEp, config).isRoutable());
        Assert.assertTrue(GatewayEndpointResolver.resolve(publicEp, config).isRoutable());
        Assert.assertFalse(GatewayEndpointResolver.resolve(privateEp, config).isRoutable());
    }

    @Test
    public void testMultiplePatternsInSamePredicate() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("notification-service");
        route.setUri("http://localhost:8083");
        route.getPathPredicates().add("/notifications/**");
        route.getPathPredicates().add("/alerts/**");
        config.routes.add(route);

        EndpointModel ep1 = new EndpointModel(HttpMethodEnum.GET, "/notifications/recent", "Ctrl", "pkg", "m");
        ep1.setModuleName("notification-service");
        ep1.setDirectBaseUrl("http://localhost:8083");

        EndpointModel ep2 = new EndpointModel(HttpMethodEnum.GET, "/alerts/active", "Ctrl", "pkg", "m");
        ep2.setModuleName("notification-service");
        ep2.setDirectBaseUrl("http://localhost:8083");

        EndpointModel ep3 = new EndpointModel(HttpMethodEnum.GET, "/settings/email", "Ctrl", "pkg", "m");
        ep3.setModuleName("notification-service");
        ep3.setDirectBaseUrl("http://localhost:8083");

        Assert.assertTrue(GatewayEndpointResolver.resolve(ep1, config).isRoutable());
        Assert.assertTrue(GatewayEndpointResolver.resolve(ep2, config).isRoutable());
        Assert.assertFalse(GatewayEndpointResolver.resolve(ep3, config).isRoutable());
    }

    @Test
    public void testMatchByDirectUriAndPort() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("custom-route-99");
        route.setUri("http://localhost:8099");
        route.getPathPredicates().add("/api/**");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/status", "Ctrl", "pkg", "m");
        endpoint.setModuleName("unrelated-module-name");
        endpoint.setDirectBaseUrl("http://localhost:8099");

        GatewayEndpointResolution resolution = GatewayEndpointResolver.resolve(endpoint, config);
        Assert.assertTrue(resolution.isRoutable());
        Assert.assertEquals("http://localhost:8888/api/status", resolution.getFullGatewayUrl());
    }

    @Test
    public void testDoesNotMistakenlyMatchSimilarServiceNames() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("profile-service");
        route.setUri("lb://profile-service");
        route.getPathPredicates().add("/profile/**");
        config.routes.add(route);

        EndpointModel adminEndpoint = new EndpointModel(HttpMethodEnum.GET, "/profile/admin/dashboard", "Ctrl", "pkg", "m");
        adminEndpoint.setModuleName("profile-service-admin");
        adminEndpoint.setDirectBaseUrl("http://localhost:8090");

        GatewayEndpointResolution resolution = GatewayEndpointResolver.resolve(adminEndpoint, config);
        Assert.assertFalse(resolution.isRoutable());
        Assert.assertEquals(GatewayRouteStatus.NOT_ROUTABLE, resolution.getStatus());
    }

    @Test
    public void testRouteWithoutPathPredicatesExposesAllEndpointsOfService() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("legacy-service");
        route.setUri("http://localhost:8077");
        config.routes.add(route);

        EndpointModel ep1 = new EndpointModel(HttpMethodEnum.GET, "/any/path", "Ctrl", "pkg", "m");
        ep1.setModuleName("legacy-service");
        ep1.setDirectBaseUrl("http://localhost:8077");

        EndpointModel ep2 = new EndpointModel(HttpMethodEnum.POST, "/internal/task", "Ctrl", "pkg", "m");
        ep2.setModuleName("legacy-service");
        ep2.setDirectBaseUrl("http://localhost:8077");

        Assert.assertTrue(GatewayEndpointResolver.resolve(ep1, config).isRoutable());
        Assert.assertTrue(GatewayEndpointResolver.resolve(ep2, config).isRoutable());
    }

    @Test
    public void testManualAndAbsoluteEndpointsAreNotRoutable() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("identity-service");
        route.setUri("http://localhost:8082");
        route.getPathPredicates().add("/identity/**");
        config.routes.add(route);

        EndpointModel manualEp = new EndpointModel(HttpMethodEnum.GET, "/identity/test", "Ctrl", "pkg", "m");
        manualEp.setModuleName("identity-service");
        manualEp.setManual(true);
        Assert.assertFalse(GatewayEndpointResolver.resolve(manualEp, config).isRoutable());

        EndpointModel absEp = new EndpointModel(HttpMethodEnum.GET, "https://api.github.com/user", "Ctrl", "pkg", "m");
        absEp.setModuleName("identity-service");
        absEp.setAbsoluteUrl(true);
        Assert.assertFalse(GatewayEndpointResolver.resolve(absEp, config).isRoutable());
    }

    @Test
    public void testDiscoveryLocatorExposesAllServiceEndpoints() {
        config.discoveryLocatorEnabled = true;
        config.routes.clear();

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/users/active", "Ctrl", "pkg", "m");
        endpoint.setModuleName("customer-service");
        endpoint.setDirectBaseUrl("http://localhost:8085");

        GatewayEndpointResolution resolution = GatewayEndpointResolver.resolve(endpoint, config);
        Assert.assertTrue(resolution.isRoutable());
        Assert.assertEquals("http://localhost:8888/customer-service/users/active", resolution.getFullGatewayUrl());
    }

    @Test
    public void testStripPrefixRequiresPublicPathToMatchPredicate() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("inventory-service");
        route.setUri("http://localhost:8083");
        route.getPathPredicates().add("/api/v1/inventory/**");
        route.setStripPrefix(1);
        config.routes.add(route);

        EndpointModel exposedEndpoint = new EndpointModel(HttpMethodEnum.GET, "/v1/inventory/items", "Ctrl", "pkg", "m");
        exposedEndpoint.setModuleName("inventory-service");
        exposedEndpoint.setDirectBaseUrl("http://localhost:8083");
        EndpointModel internalEndpoint = new EndpointModel(HttpMethodEnum.GET, "/inventory/items", "Ctrl", "pkg", "m");
        internalEndpoint.setModuleName("inventory-service");
        internalEndpoint.setDirectBaseUrl("http://localhost:8083");

        Assert.assertEquals(
                "http://localhost:8888/api/v1/inventory/items",
                GatewayEndpointResolver.resolve(exposedEndpoint, config).getFullGatewayUrl());
        Assert.assertFalse(GatewayEndpointResolver.resolve(internalEndpoint, config).isRoutable());
    }

    @Test
    public void testPrefixPathDoesNotMatchPartialSegment() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("product-service");
        route.setUri("http://localhost:8084");
        route.getPathPredicates().add("/products/**");
        route.setPrefixPath("/v2");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/v2beta/products", "Ctrl", "pkg", "m");
        endpoint.setModuleName("product-service");
        endpoint.setDirectBaseUrl("http://localhost:8084");

        Assert.assertFalse(GatewayEndpointResolver.resolve(endpoint, config).isRoutable());
    }

    @Test
    public void testContextPathIsExtractedWithoutExplicitPort() {
        Assert.assertEquals("/api", GatewayEndpointResolver.extractContextPath("http://localhost/api"));
    }

    @Test
    public void testContextPathParticipatesInGatewayPathMatching() {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("post-service");
        route.setUri("http://localhost:8084");
        route.getPathPredicates().add("/post/**");
        config.routes.add(route);

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/create-post", "PostController", "pkg", "createPost");
        endpoint.setModuleName("post-service");
        endpoint.setDirectBaseUrl("http://localhost:8084/post");

        GatewayEndpointResolution resolution = GatewayEndpointResolver.resolve(endpoint, config);

        Assert.assertTrue(resolution.isRoutable());
        Assert.assertEquals("http://localhost:8888/post/create-post", resolution.getFullGatewayUrl());
    }
}
