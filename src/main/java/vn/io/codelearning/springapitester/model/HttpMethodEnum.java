package vn.io.codelearning.springapitester.model;

import java.util.Locale;

/**
 * Enum đại diện cho các phương thức HTTP được Spring Controller hỗ trợ.
 */
public enum HttpMethodEnum {
    GET("#28A745", "GET"),
    POST("#FF8C00", "POST"),
    PUT("#007BFF", "PUT"),
    DELETE("#DC3545", "DELETE"),
    PATCH("#6F42C1", "PATCH"),
    HEAD("#6C757D", "HEAD"),
    OPTIONS("#17A2B8", "OPTIONS");

    private final String colorHex;
    private final String label;

    HttpMethodEnum(String colorHex, String label) {
        this.colorHex = colorHex;
        this.label = label;
    }

    public String getColorHex() {
        return colorHex;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Tự động nhận diện HTTP Method từ tên Annotation của Spring Boot.
     * Hỗ trợ cả Short name (GetMapping) và Qualified name (org.springframework.web.bind.annotation.GetMapping).
     */
    public static HttpMethodEnum fromAnnotation(String annotationName) {
        if (annotationName == null || annotationName.isBlank()) {
            return GET;
        }

        String simpleName = annotationName;
        int lastDotIndex = annotationName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < annotationName.length() - 1) {
            simpleName = annotationName.substring(lastDotIndex + 1);
        }

        return switch (simpleName) {
            case "PostMapping" -> POST;
            case "PutMapping" -> PUT;
            case "DeleteMapping" -> DELETE;
            case "PatchMapping" -> PATCH;
            case "RequestMapping", "GetMapping" -> GET;
            default -> {
                String upper = simpleName.toUpperCase(Locale.ROOT);
                if (upper.contains("POST")) yield POST;
                if (upper.contains("PUT")) yield PUT;
                if (upper.contains("DELETE")) yield DELETE;
                if (upper.contains("PATCH")) yield PATCH;
                yield GET;
            }
        };
    }

    /**
     * Parse từ chuỗi HTTP method (GET, POST, PUT...).
     */
    public static HttpMethodEnum fromString(String methodStr) {
        if (methodStr == null || methodStr.isBlank()) {
            return GET;
        }
        try {
            return HttpMethodEnum.valueOf(methodStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GET;
        }
    }
}
