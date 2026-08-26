package vn.io.codelearning.springapitester.client;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý Cookie/Session (vd: JSESSIONID) tự động cho OkHttpClient.
 * Sử dụng ConcurrentHashMap để đảm bảo an toàn Thread-safe khi có nhiều API gọi đồng thời.
 */
public class InMemoryCookieJar implements CookieJar {
    private final ConcurrentHashMap<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>();

    @Override
    public void saveFromResponse(@NotNull HttpUrl url, @NotNull List<Cookie> cookies) {
        cookieStore.put(url.host(), new ArrayList<>(cookies));
    }

    @NotNull
    @Override
    public List<Cookie> loadForRequest(@NotNull HttpUrl url) {
        return cookieStore.getOrDefault(url.host(), new ArrayList<>());
    }
    
    public void clearAll() {
        cookieStore.clear();
    }
}
