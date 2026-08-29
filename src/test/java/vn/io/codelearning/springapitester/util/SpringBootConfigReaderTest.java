package vn.io.codelearning.springapitester.util;

import org.junit.Assert;
import org.junit.Test;

public class SpringBootConfigReaderTest {

    @Test
    public void testResolvePlaceholders() {
        // Normal port without placeholder
        Assert.assertEquals("8080", SpringBootConfigReader.resolvePlaceholders("8080", "8080"));
        Assert.assertEquals("9090", SpringBootConfigReader.resolvePlaceholders("9090", "8080"));

        // Placeholder with default value ${PORT:8081}
        Assert.assertEquals("8081", SpringBootConfigReader.resolvePlaceholders("${PORT:8081}", "8080"));
        Assert.assertEquals("8888", SpringBootConfigReader.resolvePlaceholders("${SERVER_PORT:8888}", "8080"));

        // Placeholder without default value ${ENV_VAR}
        Assert.assertEquals("8080", SpringBootConfigReader.resolvePlaceholders("${CUSTOM_PORT}", "8080"));

        // Null value fallback
        Assert.assertEquals("8080", SpringBootConfigReader.resolvePlaceholders(null, "8080"));
    }

    @Test
    public void testAppConfigModel() {
        SpringBootConfigReader.AppConfig config = new SpringBootConfigReader.AppConfig("http://localhost:8081/api", "user-service");
        Assert.assertEquals("http://localhost:8081/api", config.baseUrl);
        Assert.assertEquals("user-service", config.appName);
    }
}
