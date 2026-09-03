package vn.io.codelearning.springapitester.client;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ProjectSessionIsolationTest {

    @Test
    public void testTwoProjectsDoNotShareCookies() {
        HttpUrl url = HttpUrl.parse("http://localhost:8080/api/v1");
        Assert.assertNotNull(url);

        // Simulate two distinct IntelliJ projects
        HttpClientService projectA = new HttpClientService();
        HttpClientService projectB = new HttpClientService();

        // Project A receives a session cookie from localhost
        Cookie cookieA = new Cookie.Builder()
                .domain("localhost")
                .name("JSESSIONID")
                .value("SESSION-PROJECT-A")
                .build();
        projectA.getCookieJar().saveFromResponse(url, List.of(cookieA));

        // Project A must have the cookie
        List<Cookie> cookiesA = projectA.getCookieJar().loadForRequest(url);
        Assert.assertEquals(1, cookiesA.size());
        Assert.assertEquals("SESSION-PROJECT-A", cookiesA.get(0).value());

        // Project B must NOT see Project A's cookie
        List<Cookie> cookiesB = projectB.getCookieJar().loadForRequest(url);
        Assert.assertTrue(cookiesB.isEmpty());
    }

    @Test
    public void testSameProjectEndpointsRetainCookieBehavior() {
        HttpUrl url = HttpUrl.parse("http://localhost:8080/api/users");
        HttpClientService projectClient = new HttpClientService();

        Cookie sessionCookie = new Cookie.Builder()
                .domain("localhost")
                .name("SID")
                .value("user-12345")
                .build();
        projectClient.getCookieJar().saveFromResponse(url, List.of(sessionCookie));

        // Subsequent call in same project to different endpoint on same host retains cookie
        HttpUrl otherEndpointUrl = HttpUrl.parse("http://localhost:8080/api/orders");
        List<Cookie> cookies = projectClient.getCookieJar().loadForRequest(otherEndpointUrl);
        Assert.assertEquals(1, cookies.size());
        Assert.assertEquals("user-12345", cookies.get(0).value());
    }

    @Test
    public void testClearCookiesReleasesOnlyCurrentProject() {
        HttpUrl url = HttpUrl.parse("http://localhost:8080/auth");
        HttpClientService projectA = new HttpClientService();
        HttpClientService projectB = new HttpClientService();

        Cookie cookieA = new Cookie.Builder().domain("localhost").name("A_TOKEN").value("aaa").build();
        Cookie cookieB = new Cookie.Builder().domain("localhost").name("B_TOKEN").value("bbb").build();

        projectA.getCookieJar().saveFromResponse(url, List.of(cookieA));
        projectB.getCookieJar().saveFromResponse(url, List.of(cookieB));

        // Clear Project A's cookies
        projectA.clearCookies();

        Assert.assertTrue(projectA.getCookieJar().loadForRequest(url).isEmpty());
        Assert.assertEquals(1, projectB.getCookieJar().loadForRequest(url).size());
        Assert.assertEquals("bbb", projectB.getCookieJar().loadForRequest(url).get(0).value());
    }

    @Test
    public void testDisposalReleasesOnlyCurrentProject() {
        HttpUrl url = HttpUrl.parse("http://localhost:8080/auth");
        HttpClientService projectA = new HttpClientService();
        HttpClientService projectB = new HttpClientService();

        Cookie cookieA = new Cookie.Builder().domain("localhost").name("A_TOKEN").value("aaa").build();
        Cookie cookieB = new Cookie.Builder().domain("localhost").name("B_TOKEN").value("bbb").build();

        projectA.getCookieJar().saveFromResponse(url, List.of(cookieA));
        projectB.getCookieJar().saveFromResponse(url, List.of(cookieB));

        // Dispose Project A
        projectA.dispose();

        Assert.assertTrue(projectA.getCookieJar().loadForRequest(url).isEmpty());
        Assert.assertEquals(1, projectB.getCookieJar().loadForRequest(url).size());
        Assert.assertEquals("bbb", projectB.getCookieJar().loadForRequest(url).get(0).value());
    }
}
