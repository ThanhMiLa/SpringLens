package vn.io.codelearning.springapitester.client;

import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.state.EndpointSavedState;
import vn.io.codelearning.springapitester.state.SpringLensState;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;

public class SecureTlsValidationTest {

    private MockWebServer server;
    private HttpClientService clientService;

    @Before
    public void setUp() {
        clientService = new HttpClientService();
    }

    @After
    public void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
        if (clientService != null) {
            clientService.dispose();
        }
    }

    @Test
    public void testSecureTlsRejectsSelfSignedCertificateByDefault() throws Exception {
        // Generate a self-signed certificate for localhost
        HeldCertificate localhostCertificate = new HeldCertificate.Builder()
                .addSubjectAlternativeName("localhost")
                .build();

        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(localhostCertificate)
                .build();

        server = new MockWebServer();
        server.useHttps(serverCertificates.sslSocketFactory(), false);
        server.enqueue(new MockResponse().setBody("secure response"));
        server.start();

        Request request = new Request.Builder()
                .url(server.url("/api/secure"))
                .build();

        // Secure mode should fail due to untrusted self-signed certificate
        ExecutionException ex = Assert.assertThrows(
                ExecutionException.class,
                () -> clientService.executeAsync(request, (InsecureTlsConsent) null).get()
        );
        Assert.assertTrue(ex.getCause() instanceof javax.net.ssl.SSLException);
    }

    @Test
    public void testInsecureTlsAcceptsLocalhostWithExplicitMatchingConsent() throws Exception {
        HeldCertificate localhostCertificate = new HeldCertificate.Builder()
                .addSubjectAlternativeName("localhost")
                .build();

        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(localhostCertificate)
                .build();

        server = new MockWebServer();
        server.useHttps(serverCertificates.sslSocketFactory(), false);
        server.enqueue(new MockResponse().setBody("insecure allowed"));
        server.start();

        Request request = new Request.Builder()
                .url(server.url("/api/local"))
                .build();

        InsecureTlsConsent consent = new InsecureTlsConsent(request.url().host());
        HttpResponseModel response = clientService.executeAsync(request, consent).get();

        Assert.assertEquals(200, response.getStatusCode());
        Assert.assertEquals("insecure allowed", response.getBody());
    }

    @Test
    public void testConsentHostMismatchThrowsSecurityException() {
        Request request = new Request.Builder()
                .url("https://localhost:8443/api")
                .build();

        // Consent given for 127.0.0.1, but request is directed to localhost
        InsecureTlsConsent mismatchedConsent = new InsecureTlsConsent("127.0.0.1");

        SecurityException ex = Assert.assertThrows(
                SecurityException.class,
                () -> clientService.execute(request, mismatchedConsent)
        );
        Assert.assertTrue(ex.getMessage().contains("does not match request host"));
    }

    @Test
    public void testRemoteHostInsecureTlsThrowsIllegalArgumentException() {
        Request remoteRequest = new Request.Builder()
                .url("https://example.com/api")
                .build();

        InsecureTlsConsent consent = new InsecureTlsConsent("example.com");

        IllegalArgumentException ex = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> clientService.execute(remoteRequest, consent)
        );
        Assert.assertTrue(ex.getMessage().contains("localhost or loopback"));
    }

    @Test
    public void testConsentIsSessionOnlyAndNotPreservedAcrossStateSaveAndLoad() {
        SpringLensState state = new SpringLensState();
        EndpointModel ep = new EndpointModel(HttpMethodEnum.GET, "/api/test", "TestCtrl", "com.example", "testMethod");
        ep.grantInsecureTlsConsent("localhost");

        Assert.assertTrue(ep.isAllowInsecureTls());
        Assert.assertNotNull(ep.getInsecureTlsConsent());

        // Save endpoint state
        state.saveEndpoint(ep);

        // Saved state stores consent host and policy version
        EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(ep));
        Assert.assertNotNull(saved);
        Assert.assertTrue(saved.allowInsecureTls);
        Assert.assertEquals("localhost", saved.insecureTlsConsentHost);
        Assert.assertEquals(InsecureTlsConsent.CURRENT_POLICY_VERSION, saved.insecureTlsConsentVersion);

        // Reloading endpoint restores consent bounded strictly to the stored host
        EndpointModel restoredEp = new EndpointModel(HttpMethodEnum.GET, "/api/test", "TestCtrl", "com.example", "testMethod");
        state.restoreEndpoint(restoredEp);
        Assert.assertTrue(restoredEp.isAllowInsecureTls());
        Assert.assertNotNull(restoredEp.getInsecureTlsConsent());
        Assert.assertTrue(restoredEp.getInsecureTlsConsent().matchesHost("localhost"));
        Assert.assertFalse(restoredEp.getInsecureTlsConsent().matchesHost("127.0.0.1"));
        Assert.assertFalse(restoredEp.getInsecureTlsConsent().matchesHost("example.com"));

        // If host changes or user revokes consent
        restoredEp.revokeInsecureTlsConsent();
        Assert.assertFalse(restoredEp.isAllowInsecureTls());
        Assert.assertNull(restoredEp.getInsecureTlsConsent());
    }
}
