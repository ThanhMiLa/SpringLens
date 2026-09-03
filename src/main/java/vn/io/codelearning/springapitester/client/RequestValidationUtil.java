package vn.io.codelearning.springapitester.client;

import vn.io.codelearning.springapitester.model.ParameterModel;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestValidationUtil {

    private RequestValidationUtil() {
    }

    public static String resolveParamValue(ParameterModel param) {
        if (param == null) return "";
        if (param.getCurrentValue() != null && !param.getCurrentValue().isEmpty()) {
            return param.getCurrentValue();
        }
        if (param.getDefaultValue() != null && !param.getDefaultValue().isEmpty()) {
            return param.getDefaultValue();
        }
        return "";
    }

    public static void validateHeader(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Header name must not be blank");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 32 || c >= 127 || c == ':') {
                throw new IllegalArgumentException("Header name contains invalid character: '" + c + "' in " + name);
            }
        }
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\r' || c == '\n') {
                    throw new IllegalArgumentException("Header value contains illegal newline characters in header: " + name);
                }
                if (c < 32 && c != '\t') {
                    throw new IllegalArgumentException("Header value contains illegal control character in header: " + name);
                }
            }
        }
    }

    public static void validateCookie(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cookie name must not be blank");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 32 || c >= 127 || c == ';' || c == '=' || c == ',' || c == '"') {
                throw new IllegalArgumentException("Cookie name contains invalid character: '" + c + "' in " + name);
            }
        }
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\r' || c == '\n') {
                    throw new IllegalArgumentException("Cookie value contains illegal newline characters in cookie: " + name);
                }
                if (c < 32 || c == 127 || c == ';') {
                    throw new IllegalArgumentException("Cookie value contains invalid character in cookie: " + name);
                }
            }
        }
    }

    public static Map<String, String> parseCookieHeader(String cookieHeader) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return cookies;
        }
        for (String pair : cookieHeader.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = pair.substring(0, eq).trim();
                String v = pair.substring(eq + 1).trim();
                if (!k.isEmpty()) {
                    cookies.put(k, v);
                }
            }
        }
        return cookies;
    }

    public static String formatCookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    public static String detectMimeType(java.io.File file) {
        if (file == null) return "application/octet-stream";
        try {
            String probe = java.nio.file.Files.probeContentType(file.toPath());
            if (probe != null && !probe.isBlank()) {
                return probe;
            }
        } catch (Throwable ignored) {
        }
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".xml")) return "application/xml";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".csv")) return "text/csv";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
        if (name.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    public static java.util.List<java.io.File> parseFilePaths(String value) {
        java.util.List<java.io.File> files = new java.util.ArrayList<>();
        if (value == null || value.isBlank()) return files;
        String trimmed = value.trim();

        // 1. If entire string matches an existing filesystem path (e.g. filename contains commas/spaces)
        java.io.File wholeFile = new java.io.File(trimmed);
        if (wholeFile.exists()) {
            files.add(wholeFile);
            return files;
        }

        // 2. If structured JSON array of strings
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                String[] parsed = new com.google.gson.Gson().fromJson(trimmed, String[].class);
                if (parsed != null) {
                    for (String path : parsed) {
                        if (path != null && !path.trim().isEmpty()) {
                            files.add(new java.io.File(path.trim()));
                        }
                    }
                    return files;
                }
            } catch (Exception ignored) {}
        }

        // 3. If contains newline delimiter (clean multi-line format)
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            for (String line : trimmed.split("[\\r\\n]+")) {
                String lineTrimmed = line.trim();
                if (!lineTrimmed.isEmpty()) {
                    files.add(new java.io.File(lineTrimmed));
                }
            }
            return files;
        }

        // 4. Fallback comma or semicolon delimiter
        String[] paths = trimmed.split("[,;]+");
        for (String p : paths) {
            String pTrimmed = p.trim();
            if (!pTrimmed.isEmpty()) {
                files.add(new java.io.File(pTrimmed));
            }
        }
        return files;
    }

    public static boolean isJson(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
