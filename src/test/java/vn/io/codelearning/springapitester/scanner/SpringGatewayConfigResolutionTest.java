package vn.io.codelearning.springapitester.scanner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig;
import vn.io.codelearning.springapitester.util.GatewayEndpointResolver;

import java.util.concurrent.Callable;

public class SpringGatewayConfigResolutionTest extends BasePlatformTestCase {

    public void testDefaultProfileLoadsWebfluxGatewayPortAndRoutes() throws Exception {
        myFixture.addFileToProject("src/main/resources/application.yaml", """
                spring:
                  profiles:
                    default: dev
                """);
        myFixture.addFileToProject("src/main/resources/application-dev.yaml", """
                server:
                  port: 8888
                spring:
                  cloud:
                    gateway:
                      server:
                        webflux:
                          routes:
                            - id: user-service
                              uri: http://localhost:8080
                              predicates:
                                - Path=/user/**
                """);

        GatewayConfig config = resolveGatewayConfig();

        assertEquals("8888", config.port);
        assertFalse(config.isFallback);
        assertEquals(1, config.routes.size());
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/auth/login", "AuthController", "example", "login");
        endpoint.setModuleName("user-service");
        endpoint.setDirectBaseUrl("http://localhost:8080/user");
        assertEquals("http://localhost:8888/user/api/auth/login",
                GatewayEndpointResolver.resolve(endpoint, config).getFullGatewayUrl());
    }

    public void testActiveProfileOverridesDefaultProfileForLegacyRoutes() throws Exception {
        myFixture.addFileToProject("src/main/resources/application.yaml", """
                spring:
                  profiles:
                    default: dev
                    active: local
                """);
        myFixture.addFileToProject("src/main/resources/application-dev.yaml", "server:\n  port: 8888\n");
        myFixture.addFileToProject("src/main/resources/application-local.yaml", """
                server:
                  port: 9090
                spring:
                  cloud:
                    gateway:
                      routes:
                        - id: user-service
                          uri: lb://user-service
                          predicates:
                            - Path=/user/**
                """);

        GatewayConfig config = resolveGatewayConfig();

        assertEquals("9090", config.port);
        assertEquals(1, config.routes.size());
        assertEquals("lb://user-service", config.routes.get(0).getUri());
    }

    public void testWebfluxDiscoveryLocatorConfiguration() throws Exception {
        myFixture.addFileToProject("src/main/resources/application.yaml", """
                server:
                  port: 8888
                spring:
                  cloud:
                    gateway:
                      server:
                        webflux:
                          discovery:
                            locator:
                              enabled: true
                """);

        GatewayConfig config = resolveGatewayConfig();

        assertTrue(config.discoveryLocatorEnabled);
    }

    public void testJavaDefaultProfileLoadsStandaloneDevYaml() throws Exception {
        addApplicationWithDefaultProfiles("spring.profiles.default", "dev");
        myFixture.addFileToProject("src/main/resources/application-dev.yaml", """
                server:
                  port: 8888
                spring:
                  application:
                    name: api-gateway
                  cloud:
                    gateway:
                      server:
                        webflux:
                          routes:
                            - id: user-service
                              uri: http://localhost:8080
                              predicates:
                                - Path=/user/**
                """);

        GatewayConfig gateway = resolveGatewayConfig();
        SpringServerConfig server = resolveServerConfig();

        assertEquals("8888", gateway.port);
        assertEquals(1, gateway.routes.size());
        assertEquals(8888, server.getPort());
        assertEquals("dev", server.getActiveProfile());
        assertEquals("api-gateway", server.getAppName());
        assertFalse(server.isFallback());
    }

    public void testYamlDefaultProfileOverridesJavaDefaultProfile() throws Exception {
        addApplicationWithDefaultProfiles("spring.profiles.default", "dev");
        myFixture.addFileToProject("src/main/resources/application.yaml", """
                spring:
                  profiles:
                    default: local
                """);
        myFixture.addFileToProject("src/main/resources/application-dev.yaml", "server:\n  port: 8888\n");
        myFixture.addFileToProject("src/main/resources/application-local.yaml", "server:\n  port: 9090\n");

        assertEquals("9090", resolveGatewayConfig().port);
        assertEquals(9090, resolveServerConfig().getPort());
    }

    public void testYamlActiveProfileOverridesJavaActiveProfile() throws Exception {
        addApplicationWithDefaultProfiles("spring.profiles.active", "dev");
        myFixture.addFileToProject("src/main/resources/application.yaml", """
                spring:
                  profiles:
                    active: local
                """);
        myFixture.addFileToProject("src/main/resources/application-dev.yaml", "server:\n  port: 8888\n");
        myFixture.addFileToProject("src/main/resources/application-local.yaml", "server:\n  port: 9090\n");

        assertEquals("9090", resolveGatewayConfig().port);
    }

    public void testJavaActiveProfileTakesPriorityOverYamlDefaultProfile() throws Exception {
        addApplicationWithDefaultProfiles("spring.profiles.active", "dev");
        myFixture.addFileToProject("src/main/resources/application.yaml", """
                spring:
                  profiles:
                    default: local
                """);
        myFixture.addFileToProject("src/main/resources/application-dev.yml", "server:\n  port: 8888\n");
        myFixture.addFileToProject("src/main/resources/application-local.yaml", "server:\n  port: 9090\n");

        assertEquals("8888", resolveGatewayConfig().port);
    }

    public void testProfileFileRemainsInactiveWithoutProfileDeclaration() throws Exception {
        myFixture.addFileToProject("src/main/resources/application-dev.yaml", "server:\n  port: 8888\n");

        assertEquals("8080", resolveGatewayConfig().port);
        assertTrue(resolveServerConfig().isFallback());
    }

    private void addApplicationWithDefaultProfiles(String property, String profile) {
        myFixture.addFileToProject("src/main/java/org/springframework/boot/autoconfigure/SpringBootApplication.java", """
                package org.springframework.boot.autoconfigure;
                public @interface SpringBootApplication {}
                """);
        myFixture.addFileToProject("src/main/java/org/springframework/boot/SpringApplication.java", """
                package org.springframework.boot;
                public class SpringApplication {
                    public SpringApplication(Class<?> applicationClass) {}
                    public void setDefaultProperties(java.util.Map properties) {}
                    public void run(String[] args) {}
                }
                """);
        myFixture.addFileToProject("src/main/java/java/util/Map.java", """
                package java.util;
                public interface Map {
                    static Map of(String key, String value) { return null; }
                }
                """);
        myFixture.addFileToProject("src/main/java/example/ApiGatewayApplication.java", """
                package example;
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import java.util.Map;

                @SpringBootApplication
                public class ApiGatewayApplication {
                    public static void main(String[] args) {
                        SpringApplication application = new SpringApplication(ApiGatewayApplication.class);
                        application.setDefaultProperties(Map.of("%s", "%s"));
                        application.run(args);
                    }
                }
                """.formatted(property, profile));
    }

    private SpringServerConfig resolveServerConfig() throws Exception {
        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(getProject());
        service.invalidateCache();
        return ApplicationManager.getApplication().executeOnPooledThread(
                (Callable<SpringServerConfig>) service::resolveServerConfig
        ).get();
    }

    private GatewayConfig resolveGatewayConfig() throws Exception {
        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(getProject());
        service.invalidateCache();
        return ApplicationManager.getApplication().executeOnPooledThread(
                (Callable<GatewayConfig>) service::resolveGatewayConfig
        ).get();
    }
}
