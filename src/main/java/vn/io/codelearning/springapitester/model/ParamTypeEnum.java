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
    FORM_DATA("Form Data Field", true),
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

    public static boolean isMultipartFileType(String typeFqn) {
        if (typeFqn == null) return false;
        return typeFqn.contains("MultipartFile")
                || typeFqn.contains("jakarta.servlet.http.Part")
                || typeFqn.contains("javax.servlet.http.Part");
    }

    /**
     * Tự động nhận diện kiểu ParamType từ tên Annotation hoặc Kiểu dữ liệu Java.
     */
    public static ParamTypeEnum fromAnnotationOrType(String annotationName, String typeFqn) {
        if (annotationName != null && !annotationName.isBlank()) {
            if (annotationName.contains("PathVariable")) return PATH_VARIABLE;
            if (annotationName.contains("RequestParam")) {
                if (isMultipartFileType(typeFqn)) return MULTIPART_FILE;
                return QUERY_PARAM;
            }
            if (annotationName.contains("RequestHeader")) return HEADER;
            if (annotationName.contains("CookieValue")) return COOKIE;
            if (annotationName.contains("RequestBody")) return REQUEST_BODY;
            if (annotationName.contains("RequestPart")) {
                if (typeFqn == null || isMultipartFileType(typeFqn)) return MULTIPART_FILE;
                return FORM_DATA;
            }
            if (annotationName.contains("ModelAttribute")) return MODEL_ATTRIBUTE;
            if (annotationName.contains("MatrixVariable")) return MATRIX_VARIABLE;
        }

        // Nhận diện theo kiểu dữ liệu đặc thù
        if (typeFqn != null && !typeFqn.isBlank()) {
            if (isMultipartFileType(typeFqn)) return MULTIPART_FILE;
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
        // Nếu là kiểu nguyên thủy hoặc java.lang.*, java.util.* thì là QUERY_PARAM
        // Nếu là object phức tạp (DTO) thì Spring ngầm định là MODEL_ATTRIBUTE
        if (typeFqn != null && !typeFqn.startsWith("java.") && !typeFqn.isBlank()) {
            // Các kiểu nguyên thủy (int, boolean, double...) sẽ không có typeFqn chứa dấu chấm
            if (typeFqn.contains(".")) {
                return MODEL_ATTRIBUTE;
            }
        }
        
        return QUERY_PARAM;
    }
}
