package vn.io.codelearning.springapitester.scanner;

import com.intellij.psi.*;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**S
 * Tiện ích bóc tách dữ liệu từ các Annotation của Spring Boot và Java Security.
 */
public final class SpringAnnotationUtils {

    public static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
    );

    private static final String REST_CONTROLLER_FQN = "org.springframework.web.bind.annotation.RestController";
    private static final String CONTROLLER_FQN = "org.springframework.stereotype.Controller";
    private static final String RESPONSE_BODY_FQN = "org.springframework.web.bind.annotation.ResponseBody";

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/auth", "/login", "/register", "/public", "/open-api", "/swagger", "/api-docs", "/actuator"
    );

    private SpringAnnotationUtils() {}

    /**
     * Kiểm tra class có phải là Spring Controller.
     * Sử dụng CHECK_HIERARCHY để bắt meta-annotation (ví dụ: @ApiV1Controller chứa @RestController).
     */
    public static boolean isControllerClass(PsiClass psiClass) {
        if (psiClass == null || psiClass.isInterface() || psiClass.isAnnotationType()) {
            return false;
        }
        return hasAnnotationRecursively(psiClass, REST_CONTROLLER_FQN, new HashSet<>()) ||
               hasAnnotationRecursively(psiClass, CONTROLLER_FQN, new HashSet<>());
    }

    /**
     * Kiểm tra class có trả về JSON (REST) hay View (Thymeleaf/JSP).
     * - @RestController -> luôn REST (true)
     * - @Controller + @ResponseBody trên class -> REST (true)
     * - @Controller không có @ResponseBody -> View (false)
     */
    public static boolean isRestController(PsiClass psiClass) {
        if (psiClass == null) return false;
        if (hasAnnotationRecursively(psiClass, REST_CONTROLLER_FQN, new HashSet<>())) {
            return true;
        }
        return hasAnnotationRecursively(psiClass, CONTROLLER_FQN, new HashSet<>()) &&
               psiClass.hasAnnotation(RESPONSE_BODY_FQN);
    }

    /**
     * Kiểm tra đệ quy xem một class có chứa annotation (kể cả thông qua meta-annotation) hay không.
     */
    private static boolean hasAnnotationRecursively(PsiClass psiClass, String annotationFqn, Set<String> visited) {
        if (psiClass == null || visited.contains(psiClass.getQualifiedName())) {
            return false;
        }
        visited.add(psiClass.getQualifiedName());

        if (psiClass.hasAnnotation(annotationFqn)) {
            return true;
        }

        // Kiểm tra các annotation trên class này (meta-annotations)
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            String qName = annotation.getQualifiedName();
            if (qName != null && !qName.startsWith("java.lang.annotation")) {
                PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
                if (nameReferenceElement != null) {
                    PsiElement resolved = nameReferenceElement.resolve();
                    if (resolved instanceof PsiClass metaClass && metaClass.isAnnotationType()) {
                        if (hasAnnotationRecursively(metaClass, annotationFqn, visited)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Kiểm tra method có trả về JSON hay không.
     */
    public static boolean isRestMethod(PsiMethod method, boolean classIsRest) {
        if (classIsRest) return true;
        return method != null && method.hasAnnotation(RESPONSE_BODY_FQN);
    }

    // ---------- PATH EXTRACTION ----------

    public static List<String> extractPathsFromAnnotation(PsiAnnotation annotation) {
        List<String> paths = new ArrayList<>();
        if (annotation == null) return paths;

        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null || value.getText().equals("{}") || value.getText().isBlank()) {
            value = annotation.findAttributeValue("path");
        }

        if (value != null) {
            extractStringValues(value, paths);
        }

        if (paths.isEmpty()) {
            paths.add("");
        }
        return paths;
    }

    private static void extractStringValues(PsiAnnotationMemberValue memberValue, List<String> result) {
        if (memberValue instanceof PsiLiteralValue literal) {
            Object val = literal.getValue();
            if (val != null) {
                result.add(val.toString());
            }
        } else if (memberValue instanceof PsiArrayInitializerMemberValue arrayMember) {
            for (PsiAnnotationMemberValue initial : arrayMember.getInitializers()) {
                extractStringValues(initial, result);
            }
        } else if (memberValue != null) {
            String text = memberValue.getText().replace("\"", "").trim();
            if (!text.isEmpty() && !text.equals("{}")) {
                result.add(text);
            }
        }
    }

    // ---------- HTTP METHOD EXTRACTION ----------

    /**
     * Trả về danh sách HTTP Methods (hỗ trợ multi-method @RequestMapping(method = {GET, POST})).
     */
    public static List<HttpMethodEnum> extractHttpMethods(PsiAnnotation annotation) {
        List<HttpMethodEnum> methods = new ArrayList<>();
        if (annotation == null) {
            methods.add(HttpMethodEnum.GET);
            return methods;
        }

        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) {
            methods.add(HttpMethodEnum.GET);
            return methods;
        }

        // Nếu là @GetMapping, @PostMapping... -> chỉ có 1 method
        if (!qualifiedName.endsWith("RequestMapping")) {
            methods.add(HttpMethodEnum.fromAnnotation(qualifiedName));
            return methods;
        }

        // Nếu là @RequestMapping -> kiểm tra thuộc tính method = {...}
        PsiAnnotationMemberValue methodAttr = annotation.findAttributeValue("method");
        if (methodAttr == null) {
            methods.add(HttpMethodEnum.GET);
            return methods;
        }

        String text = methodAttr.getText();

        if (methodAttr instanceof PsiArrayInitializerMemberValue arrayValue) {
            for (PsiAnnotationMemberValue item : arrayValue.getInitializers()) {
                HttpMethodEnum m = parseRequestMethodText(item.getText());
                if (!methods.contains(m)) methods.add(m);
            }
        } else {
            methods.add(parseRequestMethodText(text));
        }

        if (methods.isEmpty()) {
            methods.add(HttpMethodEnum.GET);
        }
        return methods;
    }

    private static HttpMethodEnum parseRequestMethodText(String text) {
        if (text == null) return HttpMethodEnum.GET;
        String upper = text.toUpperCase();
        if (upper.contains("POST")) return HttpMethodEnum.POST;
        if (upper.contains("PUT")) return HttpMethodEnum.PUT;
        if (upper.contains("DELETE")) return HttpMethodEnum.DELETE;
        if (upper.contains("PATCH")) return HttpMethodEnum.PATCH;
        if (upper.contains("HEAD")) return HttpMethodEnum.HEAD;
        if (upper.contains("OPTIONS")) return HttpMethodEnum.OPTIONS;
        return HttpMethodEnum.GET;
    }

    // ---------- RESPONSE STATUS ----------

    public static Integer extractResponseStatus(PsiMethod method) {
        if (method == null) return null;
        PsiAnnotation anno = method.getAnnotation("org.springframework.web.bind.annotation.ResponseStatus");
        if (anno == null) return null;

        PsiAnnotationMemberValue codeAttr = anno.findAttributeValue("code");
        if (codeAttr == null) codeAttr = anno.findAttributeValue("value");
        if (codeAttr != null) {
            String text = codeAttr.getText().toUpperCase();
            if (text.contains("CREATED") || text.contains("201")) return 201;
            if (text.contains("ACCEPTED") || text.contains("202")) return 202;
            if (text.contains("NO_CONTENT") || text.contains("204")) return 204;
            if (text.contains("BAD_REQUEST") || text.contains("400")) return 400;
            if (text.contains("NOT_FOUND") || text.contains("404")) return 404;
        }
        return 200;
    }

    // ---------- SECURITY CHECK ----------

    public static boolean isEndpointSecured(PsiMethod method, PsiClass controllerClass, String fullPath) {
        Boolean methodLevel = checkSecurityAnnotation(method);
        if (methodLevel != null) return methodLevel;

        Boolean classLevel = checkSecurityAnnotation(controllerClass);
        if (classLevel != null) return classLevel;

        String lower = fullPath.toLowerCase();
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (lower.startsWith(prefix) || lower.contains(prefix + "/")) {
                return false;
            }
        }

        return true;
    }

    private static Boolean checkSecurityAnnotation(PsiModifierListOwner element) {
        if (element == null) return null;

        if (element.hasAnnotation("jakarta.annotation.security.PermitAll") ||
            element.hasAnnotation("javax.annotation.security.PermitAll")) {
            return false;
        }

        PsiAnnotation preAuth = element.getAnnotation("org.springframework.security.access.prepost.PreAuthorize");
        if (preAuth != null) {
            PsiAnnotationMemberValue val = preAuth.findAttributeValue("value");
            if (val != null && (val.getText().contains("permitAll") || val.getText().contains("isAnonymous"))) {
                return false;
            }
            return true;
        }

        if (element.hasAnnotation("org.springframework.security.access.annotation.Secured") ||
            element.hasAnnotation("jakarta.annotation.security.RolesAllowed") ||
            element.hasAnnotation("javax.annotation.security.RolesAllowed")) {
            return true;
        }

        for (PsiAnnotation anno : element.getAnnotations()) {
            String name = anno.getQualifiedName();
            if (name != null) {
                String simpleName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
                if (simpleName.contains("Public") || simpleName.contains("NoAuth") ||
                    simpleName.contains("Anonymous") || simpleName.contains("PermitAll")) {
                    return false;
                }
                if (simpleName.contains("RequireAuth") || simpleName.contains("CheckToken") ||
                    simpleName.contains("RequirePermission")) {
                    return true;
                }
            }
        }

        return null;
    }
}
