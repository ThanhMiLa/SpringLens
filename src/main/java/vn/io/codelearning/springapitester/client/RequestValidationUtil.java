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
}
