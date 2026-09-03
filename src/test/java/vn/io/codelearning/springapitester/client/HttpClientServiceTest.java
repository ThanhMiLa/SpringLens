package vn.io.codelearning.springapitester.client;

import okhttp3.Request;
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
        IllegalArgumentException error = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> HttpClientService.getInstance().executeAsync(request, true)
        );
        Assert.assertTrue(error.getMessage().contains("localhost"));
    }
}
