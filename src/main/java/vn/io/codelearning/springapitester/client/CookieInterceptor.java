package vn.io.codelearning.springapitester.client;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OkHttp interceptor that manages cookies using InMemoryCookieJar while ensuring
 * explicit Cookie headers (from Cookie parameters or custom headers) take precedence.
 */
public class CookieInterceptor implements Interceptor {

    private final InMemoryCookieJar cookieJar;

    public CookieInterceptor(InMemoryCookieJar cookieJar) {
        this.cookieJar = cookieJar;
    }

    public InMemoryCookieJar getCookieJar() {
        return cookieJar;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        List<Cookie> jarCookies = cookieJar != null ? cookieJar.loadForRequest(url) : List.of();
        String explicitCookieHeader = request.header("Cookie");

        Request.Builder requestBuilder = request.newBuilder();

        if (explicitCookieHeader != null || !jarCookies.isEmpty()) {
            Map<String, String> mergedCookies = new LinkedHashMap<>();

            // 1. Add jar cookies first
            for (Cookie c : jarCookies) {
                mergedCookies.put(c.name(), c.value());
            }

            // 2. Explicit cookies take precedence on key collision
            if (explicitCookieHeader != null && !explicitCookieHeader.isBlank()) {
                Map<String, String> explicitParsed = RequestValidationUtil.parseCookieHeader(explicitCookieHeader);
                mergedCookies.putAll(explicitParsed);
            }

            if (!mergedCookies.isEmpty()) {
                requestBuilder.header("Cookie", RequestValidationUtil.formatCookieHeader(mergedCookies));
            }
        }

        Response response = chain.proceed(requestBuilder.build());

        if (cookieJar != null) {
            List<String> setCookieHeaders = response.headers("Set-Cookie");
            if (!setCookieHeaders.isEmpty()) {
                List<Cookie> receivedCookies = new ArrayList<>();
                for (String header : setCookieHeaders) {
                    Cookie cookie = Cookie.parse(url, header);
                    if (cookie != null) {
                        receivedCookies.add(cookie);
                    }
                }
                if (!receivedCookies.isEmpty()) {
                    cookieJar.saveFromResponse(url, receivedCookies);
                }
            }
        }

        return response;
    }
}
