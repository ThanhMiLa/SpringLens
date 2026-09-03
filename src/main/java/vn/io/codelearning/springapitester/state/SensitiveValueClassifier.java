package vn.io.codelearning.springapitester.state;

import vn.io.codelearning.springapitester.model.ParamTypeEnum;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Phân loại các trường nhạy cảm (secrets, tokens, credentials, cookies, headers)
 * để bảo vệ và lưu trữ riêng biệt trong PasswordSafe thay vì ghi đè vào file XML cấu hình.
 */
public final class SensitiveValueClassifier {

    private static final Set<String> DEFAULT_EXACT_NAMES = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-auth-token",
            "x-api-key",
            "api-key",
            "apikey",
            "access_token",
            "refresh_token",
            "id_token",
            "token",
            "secret",
            "password",
            "passwd",
            "pwd",
            "client_secret",
            "session",
            "sessionid",
            "jsessionid"
    );

    private static final List<String> DEFAULT_SUBSTRING_KEYWORDS = List.of(
            "token",
            "secret",
            "password",
            "passwd",
            "apikey",
            "api-key",
            "api_key",
            "credential",
            "private-key",
            "private_key",
            "session"
    );

    private static final Set<String> customPatterns = Collections.synchronizedSet(new LinkedHashSet<>());

    private SensitiveValueClassifier() {}

    public static void addCustomPattern(String pattern) {
        if (pattern != null && !pattern.isBlank()) {
            customPatterns.add(pattern.trim().toLowerCase(Locale.ROOT));
        }
    }

    public static void clearCustomPatterns() {
        customPatterns.clear();
    }

    public static Set<String> getCustomPatterns() {
        return Collections.unmodifiableSet(customPatterns);
    }

    /**
     * Kiểm tra một tham số (Query, Header, Cookie, Path) có phải là giá trị nhạy cảm hay không.
     */
    public static boolean isSensitive(String name, ParamTypeEnum type) {
        if (name == null || name.isBlank()) return false;
        String normalized = name.trim().toLowerCase(Locale.ROOT);

        // Header và Cookie luôn được kiểm tra kỹ
        if (type == ParamTypeEnum.HEADER && isSensitiveHeader(name)) {
            return true;
        }
        if (type == ParamTypeEnum.COOKIE) {
            // Cookie xác thực phiên làm việc hoặc bảo mật
            if (isSensitiveName(normalized) || normalized.contains("session") || normalized.contains("auth") || normalized.contains("token")) {
                return true;
            }
        }

        return isSensitiveName(normalized);
    }

    /**
     * Kiểm tra tên header có nhạy cảm hay không.
     */
    public static boolean isSensitiveHeader(String name) {
        if (name == null || name.isBlank()) return false;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (DEFAULT_EXACT_NAMES.contains(normalized)) return true;
        return isSensitiveName(normalized);
    }

    /**
     * Kiểm tra tên bất kỳ (header, query, form, cookie, param) dựa trên từ khóa và custom patterns.
     */
    public static boolean isSensitiveName(String name) {
        if (name == null || name.isBlank()) return false;
        String normalized = name.trim().toLowerCase(Locale.ROOT);

        if (DEFAULT_EXACT_NAMES.contains(normalized)) {
            return true;
        }

        for (String custom : customPatterns) {
            if (normalized.equals(custom) || normalized.contains(custom)) {
                return true;
            }
        }

        for (String keyword : DEFAULT_SUBSTRING_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Redact các trường nhạy cảm trong chuỗi JSON (ví dụ response body).
     */
    public static String redactSensitiveJson(String json) {
        if (json == null || json.isBlank()) return json;
        String result = json;
        for (String keyword : DEFAULT_SUBSTRING_KEYWORDS) {
            // Match pattern: "key" : "value"
            Pattern p = Pattern.compile("(\"(?:[^\"]*" + Pattern.quote(keyword) + "[^\"]*)\"\\s*:\\s*\")([^\"]*)(\")", Pattern.CASE_INSENSITIVE);
            result = p.matcher(result).replaceAll("$1[REDACTED]$3");
        }
        return result;
    }
}
