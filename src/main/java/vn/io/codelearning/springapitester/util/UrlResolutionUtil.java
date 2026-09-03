package vn.io.codelearning.springapitester.util;

import okhttp3.HttpUrl;

public final class UrlResolutionUtil {

    private UrlResolutionUtil() {
    }

    public static boolean isAbsoluteUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return false;
        }
        HttpUrl parsed = HttpUrl.parse(trimmed);
        return parsed != null && parsed.scheme() != null && parsed.host() != null;
    }

    public static String resolveFullUrl(String baseUrl, String path, boolean isAbsolute) {
        if (path == null) path = "";
        String trimmedPath = path.trim();
        if (isAbsolute || isAbsoluteUrl(trimmedPath)) {
            return trimmedPath;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return trimmedPath.startsWith("/") ? trimmedPath : "/" + trimmedPath;
        }
        String cleanBase = baseUrl.trim();
        while (cleanBase.endsWith("/")) {
            cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
        }
        String cleanPath = trimmedPath.startsWith("/") ? trimmedPath : "/" + trimmedPath;
        return cleanBase + cleanPath;
    }

    public static String sanitizeCorruptedUrl(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "";
        String p = rawPath.trim();
        int httpIdx = p.indexOf("http://", 1);
        if (httpIdx < 0) httpIdx = p.indexOf("https://", 1);
        if (httpIdx < 0) httpIdx = p.indexOf("http:/", 1);
        if (httpIdx < 0) httpIdx = p.indexOf("https:/", 1);
        if (httpIdx > 0) {
            String clean = p.substring(httpIdx);
            if (clean.startsWith("http:/") && !clean.startsWith("http://")) {
                clean = "http://" + clean.substring(6);
            }
            if (clean.startsWith("https:/") && !clean.startsWith("https://")) {
                clean = "https://" + clean.substring(7);
            }
            return clean;
        }
        return p;
    }
}
