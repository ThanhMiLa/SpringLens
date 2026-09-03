package vn.io.codelearning.springapitester.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiện ích chuẩn hóa và ghép nối đường dẫn URL của Spring Endpoints.
 */
public final class SpringUrlUtils {
    private static final Pattern PATH_VARIABLE_PATTERN =
            Pattern.compile("(?:\\{|%7B)([a-zA-Z0-9_]+)(?::[^}%]+)?(?:\\}|%7D)", Pattern.CASE_INSENSITIVE);

    private SpringUrlUtils() {}

    /**
     * Chuẩn hóa URL: Loại bỏ dấu gạch chéo kép (// -> /), đảm bảo có / ở đầu và bỏ / ở cuối (trừ khi chỉ là "/").
     */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.trim().replace("\\", "/");
        normalized = normalized.replaceAll("/+", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Ghép nối Class Base Path với Method Sub Path.
     * Ví dụ: ("/api/v1", "/users/{id}") -> "/api/v1/users/{id}"
     */
    public static String combinePaths(String classPath, String methodPath) {
        String normalizedClass = normalizePath(classPath);
        String normalizedMethod = normalizePath(methodPath);

        if (normalizedClass.isEmpty() || normalizedClass.equals("/")) {
            return normalizedMethod.isEmpty() ? "/" : normalizedMethod;
        }
        if (normalizedMethod.isEmpty() || normalizedMethod.equals("/")) {
            return normalizedClass;
        }

        return normalizePath(normalizedClass + normalizedMethod);
    }

    /**
     * Bóc tách danh sách các tên biến Path Variable có trong URL.
     * Hỗ trợ cả regex trong path variable: "/users/{userId:[0-9]+}" -> ["userId"]
     */
    public static List<String> extractPathVariableNames(String path) {
        List<String> result = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return result;
        }
        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(path);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    /**
     * Thay thế một Path Variable trong URL (hỗ trợ cả pattern có regex như {id:[0-9]+}).
     */
    public static String replacePathVariable(String url, String paramName, String value) {
        if (url == null || paramName == null) return url;
        String val = value != null ? value : "";
        String pattern = "(?i)(?:\\{|%7B)" + Pattern.quote(paramName) + "(?::[^}%]+)?(?:\\}|%7D)";
        return url.replaceAll(pattern, Matcher.quoteReplacement(val));
    }

    /**
     * Kiểm tra xem URL còn chứa Path Variable nào chưa được điền giá trị hay không.
     */
    public static boolean hasUnresolvedPathVariables(String url) {
        if (url == null || url.isBlank()) return false;
        return PATH_VARIABLE_PATTERN.matcher(url).find();
    }

    /**
     * Lấy danh sách các Path Variable chưa được thay thế trong URL.
     */
    public static List<String> getUnresolvedPathVariables(String url) {
        return extractPathVariableNames(url);
    }

    /**
     * Thuật toán so sánh đường dẫn tương tự AntPathMatcher của Spring.
     * Hỗ trợ: ? (khớp 1 ký tự), * (khớp 1 thư mục), ** (khớp nhiều thư mục).
     */
    public static boolean antPathMatch(String pattern, String path) {
        if (pattern == null || path == null) return false;
        if (pattern.equals(path)) return true;
        
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                if (i < pattern.length() - 1 && pattern.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++; // skip next asterisk
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append(".");
            } else if (".[]{}()\\+^$|".indexOf(c) != -1) {
                regex.append("\\").append(c);
            } else {
                regex.append(c);
            }
        }
        
        String regexStr = regex.toString();
        // Nếu pattern kết thúc bằng ".*" (từ **), ta cho phép khớp chính xác
        if (!regexStr.endsWith(".*")) {
            regexStr += "/?"; // Optional trailing slash
        }
        
        return Pattern.compile("^" + regexStr + "$").matcher(path).matches();
    }
}
