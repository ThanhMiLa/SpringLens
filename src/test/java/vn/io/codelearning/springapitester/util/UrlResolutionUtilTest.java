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
}
