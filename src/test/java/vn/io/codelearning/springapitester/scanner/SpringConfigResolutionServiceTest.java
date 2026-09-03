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
