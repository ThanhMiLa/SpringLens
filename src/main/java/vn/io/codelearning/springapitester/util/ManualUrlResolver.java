package vn.io.codelearning.springapitester.util;

import okhttp3.HttpUrl;

import java.net.URI;
import java.util.Locale;

/**
 * Tiện ích phân giải và kiểm tra URL an toàn cho Manual Endpoints và Route resolution.
 */
public final class ManualUrlResolver {

    private ManualUrlResolver() {
    }

    /**
     * Xác thực protocol scheme. Chỉ cho phép http và https.
     * Từ chối file:, javascript:, ftp:, data:, ...
     */
    public static void validateScheme(String url) {
        if (url == null || url.isBlank()) return;
        String trimmed = url.trim();

        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String potentialScheme = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
            // Ignore path variable regex containing colons like /users/{id:[0-9]+}
            if (!potentialScheme.contains("/") && !potentialScheme.contains("{") && !potentialScheme.contains("}")) {
                if (!"http".equals(potentialScheme) && !"https".equals(potentialScheme)) {
                    throw new IllegalArgumentException("Unsupported protocol scheme: '" + potentialScheme + "'. Only HTTP and HTTPS are supported.");
                }
            }
        }
    }

    public static boolean isAbsoluteUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        HttpUrl parsed = HttpUrl.parse(trimmed);
        return parsed != null && parsed.host() != null && !parsed.host().isBlank();
    }

    /**
     * Phân giải URL hoàn chỉnh từ Base URL và Path một cách an toàn và nhất quán.
     */
    public static String resolveUrl(String baseUrl, String path, boolean isAbsolute) {
        if (path == null) path = "";
        String trimmedPath = path.trim();

        // 1. Kiểm tra scheme
        validateScheme(trimmedPath);

        if (isAbsolute || isAbsoluteUrl(trimmedPath)) {
            HttpUrl parsed = HttpUrl.parse(trimmedPath);
            if (parsed == null) {
                // If contains path variables like {id}, attempt placeholder normalization
                String normalized = trimmedPath.replace("{", "%7B").replace("}", "%7D");
                HttpUrl parsedNorm = HttpUrl.parse(normalized);
                if (parsedNorm == null) {
                    throw new IllegalArgumentException("Invalid absolute URL: " + trimmedPath);
                }
                return trimmedPath;
            }
            return parsed.toString();
        }

        // 2. Relative path -> Resolve against baseUrl
        String effectiveBase = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "http://localhost:8080";
        validateScheme(effectiveBase);

        HttpUrl base = HttpUrl.parse(effectiveBase);
        if (base == null) {
            throw new IllegalArgumentException("Invalid base URL: " + effectiveBase);
        }

        // Clean base URL so query parameters or fragments on the base URL do not bleed into the endpoint URL
        HttpUrl cleanBaseUrl = base.newBuilder().query(null).fragment(null).build();
        String baseStr = cleanBaseUrl.toString();
        while (baseStr.endsWith("/")) {
            baseStr = baseStr.substring(0, baseStr.length() - 1);
        }
        HttpUrl baseDirectory = HttpUrl.parse(baseStr + "/");
        if (baseDirectory == null) {
            throw new IllegalArgumentException("Cannot create base directory URL: " + baseStr);
        }

        String relativePath = trimmedPath.startsWith("/") ? trimmedPath.substring(1) : trimmedPath;
        String escapedRelative = relativePath.replace("{", "%7B").replace("}", "%7D");
        HttpUrl resolved = baseDirectory.resolve(escapedRelative);
        if (resolved == null) {
            throw new IllegalArgumentException("Cannot resolve URL: " + trimmedPath + " against " + effectiveBase);
        }

        return resolved.toString().replace("%7B", "{").replace("%7D", "}");
    }

    /**
     * Trích xuất relative path cùng với query string và fragment khi chuyển từ Absolute sang Relative.
     */
    public static String extractRelativePathAndQuery(String fullUrl, String baseUrl) {
        if (fullUrl == null || fullUrl.isBlank()) return "";
        String trimmed = fullUrl.trim();
        if (baseUrl != null && !baseUrl.isBlank()) {
            String cleanBase = baseUrl.trim();
            while (cleanBase.endsWith("/")) {
                cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
            }
            if (trimmed.startsWith(cleanBase)) {
                String sub = trimmed.substring(cleanBase.length());
                return sub.startsWith("/") ? sub : "/" + sub;
            }
        }
        try {
            URI uri = URI.create(trimmed.replace("{", "%7B").replace("}", "%7D"));
            String rawPath = uri.getRawPath() != null ? uri.getRawPath() : "";
            String rawQuery = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
            String rawFragment = uri.getRawFragment() != null ? "#" + uri.getRawFragment() : "";
            String res = rawPath + rawQuery + rawFragment;
            return res.replace("%7B", "{").replace("%7D", "}");
        } catch (Exception e) {
            return trimmed;
        }
    }
}
