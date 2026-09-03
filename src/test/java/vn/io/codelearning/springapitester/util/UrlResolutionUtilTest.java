package vn.io.codelearning.springapitester.util;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.state.EndpointSavedState;
import vn.io.codelearning.springapitester.state.SpringLensState;

public class UrlResolutionUtilTest {

    @Test
    public void testResolveFullUrlWithAbsoluteUrl() {
        String base = "http://localhost:8080";
        String absolute = "https://api.example.com:8443/v1/data?filter=active#section";

        String result1 = UrlResolutionUtil.resolveFullUrl(base, absolute, true);
        Assert.assertEquals(absolute, result1);

        String result2 = UrlResolutionUtil.resolveFullUrl(base, absolute, false);
        Assert.assertEquals(absolute, result2);
    }

    @Test
    public void testResolveFullUrlWithRelativePath() {
        // Trailing slash on base, leading slash on path
        Assert.assertEquals("http://localhost:8080/api/v1/users",
                UrlResolutionUtil.resolveFullUrl("http://localhost:8080/", "/api/v1/users", false));

        // No trailing slash on base, no leading slash on path
        Assert.assertEquals("http://localhost:8080/api/v1/users",
                UrlResolutionUtil.resolveFullUrl("http://localhost:8080", "api/v1/users", false));

        // Multiple trailing slashes on base
        Assert.assertEquals("http://localhost:8080/api/v1/users",
                UrlResolutionUtil.resolveFullUrl("http://localhost:8080///", "/api/v1/users", false));
    }

    @Test
    public void testResolveFullUrlWithIPv6AndPort() {
        String base = "http://[::1]:9090";
        String path = "/metrics?format=prometheus";
        Assert.assertEquals("http://[::1]:9090/metrics?format=prometheus",
                UrlResolutionUtil.resolveFullUrl(base, path, false));
    }

    @Test
    public void testResolveFullUrlPreservesQueryFragmentAndEncoding() {
        String base = "http://localhost:8080";
        String path = "/search?q=hello%20world&tag=c%2B%2B#top";
        Assert.assertEquals("http://localhost:8080/search?q=hello%20world&tag=c%2B%2B#top",
                UrlResolutionUtil.resolveFullUrl(base, path, false));
    }

    @Test
    public void testSanitizeCorruptedLegacyUrl() {
        Assert.assertEquals("https://api.github.com/users",
                UrlResolutionUtil.sanitizeCorruptedUrl("http://localhost:8080/https://api.github.com/users"));

        Assert.assertEquals("http://api.example.com/data",
                UrlResolutionUtil.sanitizeCorruptedUrl("http://localhost:8080/http:/api.example.com/data"));

        Assert.assertEquals("https://api.example.com/v2",
                UrlResolutionUtil.sanitizeCorruptedUrl("http://localhost:8080/https:/api.example.com/v2"));

        Assert.assertEquals("/api/normal",
                UrlResolutionUtil.sanitizeCorruptedUrl("/api/normal"));
    }

    @Test
    public void testManualAbsoluteUrlStatePersistenceAndReload() {
        SpringLensState state = new SpringLensState();

        EndpointModel manualEp = new EndpointModel(HttpMethodEnum.POST, "https://external.api.io/orders", "", "", "");
        manualEp.setManual(true);
        manualEp.setAbsoluteUrl(true);

        state.saveEndpoint(manualEp);

        EndpointModel restored = new EndpointModel(HttpMethodEnum.POST, "", "", "", "");
        restored.setManual(true);
        restored.setId(manualEp.getId());

        state.restoreEndpoint(restored);

        Assert.assertTrue(restored.isAbsoluteUrl());
        Assert.assertEquals("https://external.api.io/orders", restored.getPath());
    }

    @Test
    public void testLegacyManualUrlMigrationInState() {
        SpringLensState state = new SpringLensState();

        EndpointSavedState legacyManual = new EndpointSavedState();
        legacyManual.id = "manual-1";
        legacyManual.isManual = true;
        legacyManual.path = "http://localhost:8080/https://api.stripe.com/v1/charges";
        legacyManual.isAbsoluteUrl = false;

        SpringLensState savedState = new SpringLensState();
        savedState.manualEndpoints.add(legacyManual);

        state.loadState(savedState);

        EndpointSavedState migrated = state.manualEndpoints.get(0);
        Assert.assertTrue(migrated.isAbsoluteUrl);
        Assert.assertEquals("https://api.stripe.com/v1/charges", migrated.path);
    }

    @Test
    public void testGatewayModeCannotAlterManualAbsoluteUrls() {
        String gatewayBase = "http://api-gateway:8000";
        String manualAbsolute = "https://thirdparty.service.com/webhook";

        String resolved = UrlResolutionUtil.resolveFullUrl(gatewayBase, manualAbsolute, true);
        Assert.assertEquals("https://thirdparty.service.com/webhook", resolved);
    }

    @Test
    public void testSchemeValidationRejectsNonHttpSchemes() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
                ManualUrlResolver.validateScheme("file:///etc/passwd"));
        Assert.assertThrows(IllegalArgumentException.class, () ->
                ManualUrlResolver.validateScheme("javascript:alert(1)"));
        Assert.assertThrows(IllegalArgumentException.class, () ->
                ManualUrlResolver.validateScheme("ftp://ftp.example.com/data"));
        Assert.assertThrows(IllegalArgumentException.class, () ->
                ManualUrlResolver.validateScheme("data:text/html;base64,PHNjcmlwdD4="));

        // Valid http / https must not throw
        ManualUrlResolver.validateScheme("http://localhost:8080/api");
        ManualUrlResolver.validateScheme("https://api.example.com");
        // Path variable with regex colon must not throw
        ManualUrlResolver.validateScheme("/users/{id:[0-9]+}");
    }

    @Test
    public void testPathVariableReplacementInAbsoluteManualUrl() {
        String manualUrl = "https://api.example.com/v1/{tenant}/users/{id:[0-9]+}";
        String replaced = vn.io.codelearning.springapitester.scanner.SpringUrlUtils.replacePathVariable(
                manualUrl, "tenant", "acme-corp");
        replaced = vn.io.codelearning.springapitester.scanner.SpringUrlUtils.replacePathVariable(
                replaced, "id", "12345");

        Assert.assertEquals("https://api.example.com/v1/acme-corp/users/12345", replaced);
        Assert.assertFalse(vn.io.codelearning.springapitester.scanner.SpringUrlUtils.hasUnresolvedPathVariables(replaced));
    }

    @Test
    public void testExtractRelativePathAndQuery() {
        String fullUrl = "http://localhost:8080/api/v1/search?q=spring&filter=active#page2";
        String relative = ManualUrlResolver.extractRelativePathAndQuery(fullUrl, "http://localhost:8080");

        Assert.assertEquals("/api/v1/search?q=spring&filter=active#page2", relative);
    }

    @Test
    public void testSanitizeCorruptedUrlDoesNotCorruptProxyQueryParameters() {
        String urlWithTargetQuery = "/proxy?url=https://example.com/api";
        Assert.assertEquals("/proxy?url=https://example.com/api",
                UrlResolutionUtil.sanitizeCorruptedUrl(urlWithTargetQuery));

        String urlWithMultipleHttpInQuery = "/forward?dest=http://remote.host:8080/v1&callback=https://callback.io";
        Assert.assertEquals("/forward?dest=http://remote.host:8080/v1&callback=https://callback.io",
                UrlResolutionUtil.sanitizeCorruptedUrl(urlWithMultipleHttpInQuery));
    }

    @Test
    public void testResolveUrlPreservesContextPathWhenResolvingRelative() {
        String baseWithContext = "http://localhost:8080/myapp";
        String path = "/api/v1/users";
        String resolved = ManualUrlResolver.resolveUrl(baseWithContext, path, false);
        Assert.assertEquals("http://localhost:8080/myapp/api/v1/users", resolved);

        // Base with query and trailing slash
        String baseWithQuery = "http://localhost:8080/myapp/?oldParam=1";
        String resolvedClean = ManualUrlResolver.resolveUrl(baseWithQuery, "/api/v1/users", false);
        Assert.assertEquals("http://localhost:8080/myapp/api/v1/users", resolvedClean);
    }
}
