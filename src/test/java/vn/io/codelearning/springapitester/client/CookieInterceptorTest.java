package vn.io.codelearning.springapitester.client;

import okhttp3.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CookieInterceptorTest {

    @Test
    public void testExplicitCookieOverridesJarCookie() throws IOException {
        InMemoryCookieJar jar = new InMemoryCookieJar();
        HttpUrl url = HttpUrl.parse("http://localhost:8080/test");
        jar.saveFromResponse(url, List.of(
                new Cookie.Builder().domain("localhost").path("/").name("session").value("from-jar").build(),
                new Cookie.Builder().domain("localhost").path("/").name("user").value("alice").build()
        ));

        CookieInterceptor interceptor = new CookieInterceptor(jar);

        AtomicReference<String> sentCookie = new AtomicReference<>();
        Interceptor mockServerInterceptor = chain -> {
            sentCookie.set(chain.request().header("Cookie"));
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create("ok", MediaType.parse("text/plain")))
                    .build();
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addInterceptor(mockServerInterceptor)
                .build();

        // Request has explicit session override
        Request request = new Request.Builder()
                .url(url)
                .header("Cookie", "session=from-explicit; extra=123")
                .build();

        try (Response response = client.newCall(request).execute()) {
            Assert.assertEquals(200, response.code());
        }

        String cookie = sentCookie.get();
        Assert.assertNotNull(cookie);
        // Explicit cookie value should override the jar cookie
        Assert.assertTrue(cookie.contains("session=from-explicit"));
        Assert.assertFalse(cookie.contains("session=from-jar"));
        Assert.assertTrue(cookie.contains("user=alice"));
        Assert.assertTrue(cookie.contains("extra=123"));
    }

    @Test
    public void testSetCookieSavedToJar() throws IOException {
        InMemoryCookieJar jar = new InMemoryCookieJar();
        HttpUrl url = HttpUrl.parse("http://localhost:8080/login");
        CookieInterceptor interceptor = new CookieInterceptor(jar);

        Interceptor mockServerInterceptor = chain -> new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .addHeader("Set-Cookie", "auth_token=secret999; Path=/; Domain=localhost")
                .body(ResponseBody.create("ok", MediaType.parse("text/plain")))
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addInterceptor(mockServerInterceptor)
                .build();

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            Assert.assertEquals(200, response.code());
        }

        List<Cookie> loaded = jar.loadForRequest(url);
        Assert.assertEquals(1, loaded.size());
        Assert.assertEquals("auth_token", loaded.get(0).name());
        Assert.assertEquals("secret999", loaded.get(0).value());
    }
}
