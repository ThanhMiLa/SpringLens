package vn.io.codelearning.springapitester.client;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý Cookie/Session tự động cho OkHttpClient tuân thủ chuẩn RFC 6265.
 * Hỗ trợ gộp cookie theo (Name, Domain, Path) và tự động lọc bỏ cookie hết hạn.
 */
public class InMemoryCookieJar implements CookieJar {
    // Lưu trữ toàn bộ cookie, dùng synchronized thay vì ConcurrentHashMap vì thao tác xoá/gộp cần nguyên tử (atomic) trên list.
    private final List<Cookie> cookieStore = new ArrayList<>();

    @Override
    public synchronized void saveFromResponse(@NotNull HttpUrl url, @NotNull List<Cookie> cookies) {
        long now = System.currentTimeMillis();
        for (Cookie newCookie : cookies) {
            // Xoá cookie cũ nếu trùng identity (name + domain + path)
            cookieStore.removeIf(oldCookie ->
                    oldCookie.name().equals(newCookie.name()) &&
                    oldCookie.domain().equalsIgnoreCase(newCookie.domain()) &&
                    oldCookie.path().equals(newCookie.path())
            );

            // Thêm cookie mới nếu chưa hết hạn
            if (newCookie.expiresAt() > now) {
                cookieStore.add(newCookie);
            }
        }
    }

    @NotNull
    @Override
    public synchronized List<Cookie> loadForRequest(@NotNull HttpUrl url) {
        long now = System.currentTimeMillis();
        
        // Loại bỏ các cookie đã hết hạn
        cookieStore.removeIf(cookie -> cookie.expiresAt() <= now);

        // Lọc các cookie hợp lệ cho request hiện tại (kiểm tra domain và path match)
        List<Cookie> validCookies = new ArrayList<>();
        for (Cookie cookie : cookieStore) {
            if (cookie.matches(url)) {
                validCookies.add(cookie);
            }
        }
        return validCookies;
    }
    
    public synchronized void clearAll() {
        cookieStore.clear();
    }

    public synchronized int size() {
        return cookieStore.size();
    }

    public synchronized List<Cookie> getAllCookies() {
        return new ArrayList<>(cookieStore);
    }
}
