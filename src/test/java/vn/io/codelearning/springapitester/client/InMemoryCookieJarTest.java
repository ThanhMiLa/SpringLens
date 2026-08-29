package vn.io.codelearning.springapitester.client;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class InMemoryCookieJarTest {

    @Test
    public void testSaveAndLoadCookiesForHost() {
        InMemoryCookieJar jar = new InMemoryCookieJar();
        HttpUrl url = HttpUrl.parse("http://localhost:8080/api/v1");

        Cookie c1 = new Cookie.Builder().name("JSESSIONID").value("sess-12345").domain("localhost").build();
        Cookie c2 = new Cookie.Builder().name("theme").value("dark").domain("localhost").build();

        jar.saveFromResponse(url, List.of(c1, c2));

        List<Cookie> cookies = jar.loadForRequest(url);
        Assert.assertEquals(2, cookies.size());
        Assert.assertEquals("JSESSIONID", cookies.get(0).name());
        Assert.assertEquals("sess-12345", cookies.get(0).value());
    }

    @Test
    public void testMultipleHostsIsolation() {
        InMemoryCookieJar jar = new InMemoryCookieJar();
        HttpUrl urlHost1 = HttpUrl.parse("http://host1.com/api");
        HttpUrl urlHost2 = HttpUrl.parse("http://host2.com/api");

        Cookie c1 = new Cookie.Builder().name("token1").value("val1").domain("host1.com").build();
        Cookie c2 = new Cookie.Builder().name("token2").value("val2").domain("host2.com").build();

        jar.saveFromResponse(urlHost1, List.of(c1));
        jar.saveFromResponse(urlHost2, List.of(c2));

        List<Cookie> cookies1 = jar.loadForRequest(urlHost1);
        Assert.assertEquals(1, cookies1.size());
        Assert.assertEquals("token1", cookies1.get(0).name());

        List<Cookie> cookies2 = jar.loadForRequest(urlHost2);
        Assert.assertEquals(1, cookies2.size());
        Assert.assertEquals("token2", cookies2.get(0).name());
    }

    @Test
    public void testCookieExpiration() {
        InMemoryCookieJar jar = new InMemoryCookieJar();
        HttpUrl url = HttpUrl.parse("http://localhost:8080/api");

        // Expired cookie in the past
        Cookie expired = new Cookie.Builder()
                .name("expiredCookie")
                .value("expiredVal")
                .domain("localhost")
                .expiresAt(System.currentTimeMillis() - 100000)
                .build();

        jar.saveFromResponse(url, List.of(expired));

        List<Cookie> cookies = jar.loadForRequest(url);
        Assert.assertTrue(cookies.isEmpty()); // Expired cookie should be filtered out
    }

    @Test
    public void testClearAllCookies() {
        InMemoryCookieJar jar = new InMemoryCookieJar();
        HttpUrl url = HttpUrl.parse("http://localhost:8080");

        Cookie c = new Cookie.Builder().name("cookie").value("val").domain("localhost").build();
        jar.saveFromResponse(url, List.of(c));
        Assert.assertEquals(1, jar.loadForRequest(url).size());

        jar.clearAll();
        Assert.assertEquals(0, jar.loadForRequest(url).size());
    }
}
