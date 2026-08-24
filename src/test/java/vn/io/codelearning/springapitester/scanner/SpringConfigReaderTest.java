package vn.io.codelearning.springapitester.scanner;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class SpringConfigReaderTest {

    @Test
    public void testParseProperties() {
        String propertiesContent = """
                server.port=8088
                server.servlet.context-path=/my-app
                server.ssl.enabled=true
                spring.profiles.active=dev
                """;

        SpringServerConfig config = new SpringServerConfig();
        SpringConfigReader.parsePropertiesContent(propertiesContent, config);

        Assert.assertEquals(8088, config.getPort());
        Assert.assertEquals("/my-app", config.getContextPath());
        Assert.assertTrue(config.isSslEnabled());
        Assert.assertEquals("dev", config.getActiveProfile());
        Assert.assertEquals("https://localhost:8088/my-app", config.getBaseUrl());
    }

    @Test
    public void testParseYamlWithIndentation() {
        String yamlContent = """
                spring:
                  application:
                    name: demo-service
                  profiles:
                    active: local
                server:
                  port: 9090
                  servlet:
                    context-path: /api/v1
                  ssl:
                    enabled: false
                database:
                  port: 5432
                redis:
                  port: 6379
                """;

        SpringServerConfig config = new SpringServerConfig();
        SpringConfigReader.parseYamlContent(yamlContent, config);

        // Đảm bảo không bị match nhầm port 5432 của database hoặc 6379 của redis
        Assert.assertEquals(9090, config.getPort());
        Assert.assertEquals("/api/v1", config.getContextPath());
        Assert.assertFalse(config.isSslEnabled());
        Assert.assertEquals("local", config.getActiveProfile());
        Assert.assertEquals("http://localhost:9090/api/v1", config.getBaseUrl());
    }

    @Test
    public void testFlattenYaml() {
        String yaml = """
                server:
                  port: 8080
                  servlet:
                    context-path: /test
                """;

        Map<String, String> map = SpringConfigReader.flattenYaml(yaml);
        Assert.assertEquals("8080", map.get("server.port"));
        Assert.assertEquals("/test", map.get("server.servlet.context-path"));
    }
}
