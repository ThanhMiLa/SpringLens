package vn.io.codelearning.springapitester.client;

import okhttp3.Request;
import okhttp3.OkHttpClient;
import org.junit.Assert;
import org.junit.Test;

public class HttpClientServiceTest {

    @Test
    public void testLocalDevelopmentHostRecognition() {
        Assert.assertTrue(HttpClientService.isLocalDevelopmentHost("localhost"));
        Assert.assertTrue(HttpClientService.isLocalDevelopmentHost("LOCALHOST."));
        Assert.assertTrue(HttpClientService.isLocalDevelopmentHost("127.0.0.1"));
        Assert.assertTrue(HttpClientService.isLocalDevelopmentHost("127.12.34.56"));
        Assert.assertTrue(HttpClientService.isLocalDevelopmentHost("::1"));
        Assert.assertTrue(HttpClientService.isLocalDevelopmentHost("0:0:0:0:0:0:0:1"));
    }

    @Test
    public void testRemoteHostsCannotUseInsecureTls() {
        Assert.assertFalse(HttpClientService.isLocalDevelopmentHost("example.com"));
        Assert.assertFalse(HttpClientService.isLocalDevelopmentHost("localhost.example.com"));
        Assert.assertFalse(HttpClientService.isLocalDevelopmentHost("127.0.0.1.example.com"));

        Request request = new Request.Builder().url("https://example.com/api").build();
        HttpClientService service = new HttpClientService();
        IllegalArgumentException error = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> service.executeAsync(request, new InsecureTlsConsent(request.url().host()))
        );
        Assert.assertTrue(error.getMessage().contains("localhost"));
    }

    @Test
    public void testRequestHandleCancelsCallAndFuture() {
        okhttp3.Call call = new OkHttpClient().newCall(
                new Request.Builder().url("http://localhost/cancel").build());
        java.util.concurrent.CompletableFuture<HttpResponseModel> future = new java.util.concurrent.CompletableFuture<>();
        HttpClientService.RequestHandle handle = new HttpClientService.RequestHandle(call, future);

        handle.cancel();

        Assert.assertTrue(call.isCanceled());
        Assert.assertTrue(future.isCancelled());
        Assert.assertTrue(handle.isCanceled());
    }

    @Test
    public void testProjectMandatoryOnGetInstance() {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> HttpClientService.getInstance(null));
    }

    @Test
    public void testMultiProjectCookieIsolation() {
        HttpClientService project1Client = new HttpClientService();
        HttpClientService project2Client = new HttpClientService();

        okhttp3.HttpUrl url = okhttp3.HttpUrl.parse("https://api.example.com/v1/auth");
        okhttp3.Cookie cookie = okhttp3.Cookie.parse(url, "session=proj1_secret_token; Domain=example.com; Path=/");

        project1Client.getCookieJar().saveFromResponse(url, java.util.List.of(cookie));

        // Project 1 has cookie
        Assert.assertEquals(1, project1Client.getCookieJar().size());
        Assert.assertEquals(1, project1Client.getCookieJar().loadForRequest(url).size());

        // Project 2 must have ZERO cookies (strictly isolated)
        Assert.assertEquals(0, project2Client.getCookieJar().size());
        Assert.assertTrue(project2Client.getCookieJar().loadForRequest(url).isEmpty());
    }

    @Test
    public void testProjectDisposalLifecycleCleanup() {
        HttpClientService service = new HttpClientService();
        okhttp3.HttpUrl url = okhttp3.HttpUrl.parse("https://api.example.com/data");
        okhttp3.Cookie cookie = okhttp3.Cookie.parse(url, "token=xyz; Domain=example.com; Path=/");
        service.getCookieJar().saveFromResponse(url, java.util.List.of(cookie));

        Assert.assertEquals(1, service.getCookieJar().size());

        // Dispose service
        service.dispose();

        // Cookie jar cleared and connections evicted
        Assert.assertEquals(0, service.getCookieJar().size());
    }

    @Test
    public void testRfc6265DomainAndPathMatching() {
        InMemoryCookieJar jar = new InMemoryCookieJar();
        okhttp3.HttpUrl rootUrl = okhttp3.HttpUrl.parse("https://api.example.com/app");
        okhttp3.Cookie scopedCookie = okhttp3.Cookie.parse(rootUrl, "sid=123; Domain=example.com; Path=/app");
        jar.saveFromResponse(rootUrl, java.util.List.of(scopedCookie));

        // Matching path and subpath
        Assert.assertEquals(1, jar.loadForRequest(okhttp3.HttpUrl.parse("https://api.example.com/app/users")).size());
        // Different path under same domain -> not matching
        Assert.assertEquals(0, jar.loadForRequest(okhttp3.HttpUrl.parse("https://api.example.com/other")).size());
        // Different domain -> not matching
        Assert.assertEquals(0, jar.loadForRequest(okhttp3.HttpUrl.parse("https://other.com/app")).size());
    }

    @Test
    public void testTlsConsentIsolationAcrossEndpoints() {
        InsecureTlsConsent consentA = new InsecureTlsConsent("127.0.0.1");
        InsecureTlsConsent consentB = new InsecureTlsConsent("localhost");

        Assert.assertTrue(consentA.matchesHost("127.0.0.1"));
        Assert.assertFalse(consentA.matchesHost("127.0.0.2"));
        Assert.assertFalse(consentA.matchesHost("localhost"));

        Assert.assertTrue(consentB.matchesHost("localhost"));
        Assert.assertFalse(consentB.matchesHost("127.0.0.1"));
    }

    @Test
    public void testEndpointModelSetAllowInsecureTlsDoesNotAutoSynthesizeConsent() {
        vn.io.codelearning.springapitester.model.EndpointModel ep =
                new vn.io.codelearning.springapitester.model.EndpointModel(
                        vn.io.codelearning.springapitester.model.HttpMethodEnum.GET, "/api", "C", "P", "m");
        Assert.assertNull(ep.getInsecureTlsConsent());
        ep.setAllowInsecureTls(true);
        // Must NOT synthesize consent automatically
        Assert.assertNull(ep.getInsecureTlsConsent());

        ep.grantInsecureTlsConsent("localhost");
        Assert.assertNotNull(ep.getInsecureTlsConsent());
        Assert.assertTrue(ep.isAllowInsecureTls());

        ep.setAllowInsecureTls(false);
        Assert.assertNull(ep.getInsecureTlsConsent());
    }
}
