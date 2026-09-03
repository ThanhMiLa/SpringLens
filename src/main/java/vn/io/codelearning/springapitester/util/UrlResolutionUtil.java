package vn.io.codelearning.springapitester.util;

import okhttp3.HttpUrl;

public final class UrlResolutionUtil {

    private UrlResolutionUtil() {
    }

    public static boolean isAbsoluteUrl(String url) {
        return ManualUrlResolver.isAbsoluteUrl(url);
    }

    public static String resolveFullUrl(String baseUrl, String path, boolean isAbsolute) {
        return ManualUrlResolver.resolveUrl(baseUrl, path, isAbsolute);
    }

    public static String sanitizeCorruptedUrl(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "";
        String p = rawPath.trim();
        int queryIdx = p.indexOf('?');
        String pathPart = queryIdx >= 0 ? p.substring(0, queryIdx) : p;
        String queryPart = queryIdx >= 0 ? p.substring(queryIdx) : "";

        int httpIdx = pathPart.indexOf("http://", 1);
        if (httpIdx < 0) httpIdx = pathPart.indexOf("https://", 1);
        if (httpIdx < 0) httpIdx = pathPart.indexOf("http:/", 1);
        if (httpIdx < 0) httpIdx = pathPart.indexOf("https:/", 1);

        if (httpIdx > 0) {
            String clean = pathPart.substring(httpIdx);
            if (clean.startsWith("http:/") && !clean.startsWith("http://")) {
                clean = "http://" + clean.substring(6);
            }
            if (clean.startsWith("https:/") && !clean.startsWith("https://")) {
                clean = "https://" + clean.substring(7);
            }
            return clean + queryPart;
        }
        return p;
    }
}
