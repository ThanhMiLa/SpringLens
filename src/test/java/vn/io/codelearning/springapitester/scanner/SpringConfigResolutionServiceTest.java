package vn.io.codelearning.springapitester.scanner;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpringConfigResolutionServiceTest {

    @Test
    public void testPlaceholderResolutionWithDefaults() {
        Map<String, String> props = new HashMap<>();
        props.put("custom.port", "8085");

        List<String> diagnostics = new ArrayList<>();

        // Key exists in properties
        Assert.assertEquals("8085",
                SpringConfigResolutionService.resolvePlaceholders("${custom.port:8080}", props, diagnostics));

        // Key does not exist, default value is used
        Assert.assertEquals("9090",
                SpringConfigResolutionService.resolvePlaceholders("${non.existent.port:9090}", props, diagnostics));

        // Nested placeholder resolution
        Assert.assertEquals("8085",
                SpringConfigResolutionService.resolvePlaceholders("${missing.port:${custom.port:8080}}", props, diagnostics));

        // Multiple placeholders in text
        Assert.assertEquals("http://localhost:8085/api",
                SpringConfigResolutionService.resolvePlaceholders("http://${host:localhost}:${custom.port}/api", props, diagnostics));
    }

    @Test
    public void testUnresolvedPlaceholderDiagnostic() {
        Map<String, String> props = new HashMap<>();
        List<String> diagnostics = new ArrayList<>();

        String result = SpringConfigResolutionService.resolvePlaceholders("${UNKNOWN_CONFIG_VAR}", props, diagnostics);

        Assert.assertEquals("${UNKNOWN_CONFIG_VAR}", result);
        Assert.assertEquals(1, diagnostics.size());
        Assert.assertTrue(diagnostics.get(0).contains("Unresolved placeholder: ${UNKNOWN_CONFIG_VAR}"));
    }

    @Test
    public void testDeterministicFilePrecedence() {
        int bootstrap = SpringConfigResolutionService.getFilePrecedence("bootstrap.yml");
        int bootstrapProfile = SpringConfigResolutionService.getFilePrecedence("bootstrap-dev.yml");
        int application = SpringConfigResolutionService.getFilePrecedence("application.properties");
        int appProfile = SpringConfigResolutionService.getFilePrecedence("application-dev.yml");

        Assert.assertTrue(bootstrap < bootstrapProfile);
        Assert.assertTrue(bootstrapProfile < application);
        Assert.assertTrue(application < appProfile);
    }

    @Test
    public void testExcludeGeneratedAndBuildFiles() {
        com.intellij.openapi.vfs.VirtualFile mockBuildFile = new MockVirtualFile("/workspace/my-app/build/resources/main/application.yml");
        com.intellij.openapi.vfs.VirtualFile mockTargetFile = new MockVirtualFile("/workspace/my-app/target/classes/application.properties");
        com.intellij.openapi.vfs.VirtualFile mockGradleFile = new MockVirtualFile("/workspace/my-app/.gradle/caches/application.yml");
        com.intellij.openapi.vfs.VirtualFile mockSourceFile = new MockVirtualFile("/workspace/my-app/src/main/resources/application.yml");

        Assert.assertTrue(SpringConfigResolutionService.isGeneratedOrBuildFile(mockBuildFile));
        Assert.assertTrue(SpringConfigResolutionService.isGeneratedOrBuildFile(mockTargetFile));
        Assert.assertTrue(SpringConfigResolutionService.isGeneratedOrBuildFile(mockGradleFile));
        Assert.assertFalse(SpringConfigResolutionService.isGeneratedOrBuildFile(mockSourceFile));
    }

    @Test
    public void testServerConfigDiagnosticsAndFallbackStatus() {
        SpringServerConfig config = new SpringServerConfig();
        Assert.assertTrue(config.isFallback());
        Assert.assertFalse(config.hasUnresolvedPlaceholder());

        config.setFallback(false);
        config.setHasUnresolvedPlaceholder(true);
        config.addDiagnostic("Warning: using default port");

        Assert.assertFalse(config.isFallback());
        Assert.assertTrue(config.hasUnresolvedPlaceholder());
        Assert.assertEquals(1, config.getDiagnostics().size());
        Assert.assertEquals("Warning: using default port", config.getDiagnostics().get(0));
    }

    @Test
    public void testProfileFilteringAndPrecedence() {
        Assert.assertTrue(SpringConfigResolutionService.isConfigFileActive("application.properties", "dev"));
        Assert.assertTrue(SpringConfigResolutionService.isConfigFileActive("application.yml", "dev"));
        Assert.assertTrue(SpringConfigResolutionService.isConfigFileActive("application-dev.properties", "dev"));
        Assert.assertTrue(SpringConfigResolutionService.isConfigFileActive("application-dev.yml", "dev"));
        Assert.assertFalse(SpringConfigResolutionService.isConfigFileActive("application-prod.properties", "dev"));
        Assert.assertFalse(SpringConfigResolutionService.isConfigFileActive("application-prod.yml", "dev"));
        Assert.assertFalse(SpringConfigResolutionService.isConfigFileActive("application-dev.properties", ""));

        // Properties overrides YAML at same level
        Assert.assertTrue(SpringConfigResolutionService.getFilePrecedence("application.yml") <
                SpringConfigResolutionService.getFilePrecedence("application.properties"));
        Assert.assertTrue(SpringConfigResolutionService.getFilePrecedence("application.properties") <
                SpringConfigResolutionService.getFilePrecedence("application-dev.yml"));
        Assert.assertTrue(SpringConfigResolutionService.getFilePrecedence("application-dev.yml") <
                SpringConfigResolutionService.getFilePrecedence("application-dev.properties"));
    }

    @Test
    public void testNestedAndDefaultPlaceholders() {
        Map<String, String> emptyProps = new HashMap<>();
        List<String> diagnostics = new ArrayList<>();

        // Nested placeholder with fallback to default
        String nested = SpringConfigResolutionService.resolvePlaceholders(
                "${BASE_PATH:${API_PREFIX:/api}}", emptyProps, diagnostics);
        Assert.assertEquals("/api", nested);

        // Simple default
        String port = SpringConfigResolutionService.resolvePlaceholders(
                "${PORT:9090}", emptyProps, diagnostics);
        Assert.assertEquals("9090", port);

        // Default with colons (URL)
        String url = SpringConfigResolutionService.resolvePlaceholders(
                "${APP_URL:http://localhost:8080}", emptyProps, diagnostics);
        Assert.assertEquals("http://localhost:8080", url);
    }

    @Test
    public void testDeterministicGatewayRouteMerging() {
        List<vn.io.codelearning.springapitester.model.GatewayRouteModel> routes = new ArrayList<>();

        vn.io.codelearning.springapitester.model.GatewayRouteModel route1 = new vn.io.codelearning.springapitester.model.GatewayRouteModel();
        route1.setId("order-service");
        route1.setUri("lb://order-service");

        vn.io.codelearning.springapitester.model.GatewayRouteModel route2 = new vn.io.codelearning.springapitester.model.GatewayRouteModel();
        route2.setId("user-service");
        route2.setUri("lb://user-service");

        vn.io.codelearning.springapitester.model.GatewayRouteModel route3 = new vn.io.codelearning.springapitester.model.GatewayRouteModel();
        route3.setId("order-service"); // Same ID as route 1
        route3.setUri("http://localhost:8082");

        SpringConfigResolutionService.mergeRoute(routes, route1);
        SpringConfigResolutionService.mergeRoute(routes, route2);
        SpringConfigResolutionService.mergeRoute(routes, route3);

        // Deduplicated and merged by ID: 2 routes total
        Assert.assertEquals(2, routes.size());
        Assert.assertEquals("order-service", routes.get(0).getId());
        Assert.assertEquals("http://localhost:8082", routes.get(0).getUri());
        Assert.assertEquals("user-service", routes.get(1).getId());
    }

    @Test
    public void testMatchesProfileMultipleActiveProfiles() {
        // Active profiles: dev,local
        Assert.assertTrue(SpringConfigResolutionService.matchesProfile("dev", "dev,local"));
        Assert.assertTrue(SpringConfigResolutionService.matchesProfile("local", "dev,local"));
        Assert.assertTrue(SpringConfigResolutionService.matchesProfile("dev,staging", "dev,local"));
        Assert.assertFalse(SpringConfigResolutionService.matchesProfile("prod", "dev,local"));
        Assert.assertFalse(SpringConfigResolutionService.matchesProfile("qa", "dev,local"));
    }

    @Test
    public void testMatchesProfileYamlListFormat() {
        // Active profiles: [dev, local]
        Assert.assertTrue(SpringConfigResolutionService.matchesProfile("dev", "[dev, local]"));
        Assert.assertTrue(SpringConfigResolutionService.matchesProfile("local", "[dev, local]"));
        Assert.assertFalse(SpringConfigResolutionService.matchesProfile("prod", "[dev, local]"));
    }

    @Test
    public void testSpringServerConfigAppName() {
        SpringServerConfig config = new SpringServerConfig();
        Assert.assertEquals("", config.getAppName());
        config.setAppName("order-service");
        Assert.assertEquals("order-service", config.getAppName());
    }

    @Test
    public void testFindContextPathInProps() {
        Map<String, String> propsKebab = Map.of("server.servlet.context-path", "/api/kebab");
        Assert.assertEquals("/api/kebab", SpringConfigResolutionService.findContextPathInProps(propsKebab));

        Map<String, String> propsCamel = Map.of("server.servlet.contextPath", "/api/camel");
        Assert.assertEquals("/api/camel", SpringConfigResolutionService.findContextPathInProps(propsCamel));

        Map<String, String> propsSnake = Map.of("server.servlet.context_path", "/api/snake");
        Assert.assertEquals("/api/snake", SpringConfigResolutionService.findContextPathInProps(propsSnake));

        Map<String, String> propsLegacy = Map.of("server.context-path", "/api/legacy");
        Assert.assertEquals("/api/legacy", SpringConfigResolutionService.findContextPathInProps(propsLegacy));

        Map<String, String> propsWebflux = Map.of("spring.webflux.base-path", "/webflux");
        Assert.assertEquals("/webflux", SpringConfigResolutionService.findContextPathInProps(propsWebflux));

        Assert.assertNull(SpringConfigResolutionService.findContextPathInProps(java.util.Collections.emptyMap()));
        Assert.assertNull(SpringConfigResolutionService.findContextPathInProps(null));
    }



    // Lightweight MockVirtualFile for testing path exclusion
    private static class MockVirtualFile extends com.intellij.mock.MockVirtualFile {
        private final String fullPath;

        public MockVirtualFile(String fullPath) {
            super(fullPath.substring(fullPath.lastIndexOf('/') + 1));
            this.fullPath = fullPath;
        }

        @Override
        public String getPath() {
            return fullPath;
        }
    }
}
