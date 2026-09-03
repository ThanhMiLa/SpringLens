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
        if (name.contains("\r") || name.contains("\n") || (value != null && (value.contains("\r") || value.contains("\n")))) {
            throw new IllegalArgumentException("Header name or value contains illegal newline characters: " + name);
        }
    }

    public static void validateCookie(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cookie name must not be blank");
        }
        if (name.contains("\r") || name.contains("\n") || (value != null && (value.contains("\r") || value.contains("\n")))) {
            throw new IllegalArgumentException("Cookie name or value contains illegal newline characters: " + name);
        }
        if (name.contains(";") || name.contains("=")) {
            throw new IllegalArgumentException("Cookie name contains illegal characters: " + name);
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
        String[] paths = value.split("[,;\\n\\r]+");
        for (String p : paths) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                files.add(new java.io.File(trimmed));
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
