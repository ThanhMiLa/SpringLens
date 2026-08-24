package vn.io.codelearning.springapitester.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiện ích chuẩn hóa và ghép nối đường dẫn URL của Spring Endpoints.
 */
public final class SpringUrlUtils {
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)(?::[^}]+)?\\}");

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
}
