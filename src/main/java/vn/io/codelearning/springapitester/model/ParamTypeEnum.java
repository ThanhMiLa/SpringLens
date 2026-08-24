package vn.io.codelearning.springapitester.model;

/**
 * Phân loại toàn diện các kiểu tham số của Spring Controller Method.
 */
public enum ParamTypeEnum {
    PATH_VARIABLE("Path Variable (@PathVariable)", true),
    QUERY_PARAM("Query Parameter (@RequestParam)", true),
    HEADER("Header (@RequestHeader)", true),
    COOKIE("Cookie Value (@CookieValue)", true),
    REQUEST_BODY("Request Body (@RequestBody)", true),
    MULTIPART_FILE("Multipart/File (@RequestPart / MultipartFile)", true),
    MODEL_ATTRIBUTE("Model Attribute (@ModelAttribute)", true),
    MATRIX_VARIABLE("Matrix Variable (@MatrixVariable)", true),
    FRAMEWORK_INTERNAL("Framework Internal (HttpServletRequest, Principal...)", false);

    private final String description;
    private final boolean userEditable; // Có cần hiển thị ô nhập liệu cho Dev trên UI không

    ParamTypeEnum(String description, boolean userEditable) {
        this.description = description;
        this.userEditable = userEditable;
    }

    public String getDescription() {
        return description;
    }

    public boolean isUserEditable() {
        return userEditable;
    }

    /**
     * Tự động nhận diện kiểu ParamType từ tên Annotation hoặc Kiểu dữ liệu Java.
     */
    public static ParamTypeEnum fromAnnotationOrType(String annotationName, String typeFqn) {
        if (annotationName != null && !annotationName.isBlank()) {
            if (annotationName.contains("PathVariable")) return PATH_VARIABLE;
            if (annotationName.contains("RequestParam")) return QUERY_PARAM;
            if (annotationName.contains("RequestHeader")) return HEADER;
            if (annotationName.contains("CookieValue")) return COOKIE;
            if (annotationName.contains("RequestBody")) return REQUEST_BODY;
            if (annotationName.contains("RequestPart")) return MULTIPART_FILE;
            if (annotationName.contains("ModelAttribute")) return MODEL_ATTRIBUTE;
            if (annotationName.contains("MatrixVariable")) return MATRIX_VARIABLE;
        }

        // Nhận diện theo kiểu dữ liệu đặc thù
        if (typeFqn != null && !typeFqn.isBlank()) {
            if (typeFqn.contains("MultipartFile")) return MULTIPART_FILE;
            if (typeFqn.contains("HttpServletRequest") ||
                    typeFqn.contains("HttpServletResponse") ||
                    typeFqn.contains("HttpSession") ||
                    typeFqn.contains("Principal") ||
                    typeFqn.contains("Authentication") ||
                    typeFqn.contains("BindingResult") ||
                    typeFqn.contains("Errors") ||
                    typeFqn.contains("UriComponentsBuilder")) {
                return FRAMEWORK_INTERNAL;
            }
        }

        // Mặc định nếu không có annotation gì
        return QUERY_PARAM;
    }
}
