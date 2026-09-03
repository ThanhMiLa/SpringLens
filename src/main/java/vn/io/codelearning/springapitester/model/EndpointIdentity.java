package vn.io.codelearning.springapitester.model;

import java.util.Objects;

/**
 * Định danh duy nhất cho một Endpoint để tránh xung đột state giữa các controller,
 * module, overload method hoặc manual endpoint.
 */
public final class EndpointIdentity {

    private final String module;
    private final String controllerFqn;
    private final String methodSignature;
    private final HttpMethodEnum httpMethod;
    private final String normalizedPath;
    private final boolean isManual;
    private final String manualId;

    public EndpointIdentity(String module, String controllerFqn, String methodSignature,
                            HttpMethodEnum httpMethod, String normalizedPath) {
        this.module = module != null ? module.trim() : "";
        this.controllerFqn = controllerFqn != null ? controllerFqn.trim() : "";
        this.methodSignature = methodSignature != null ? methodSignature.trim() : "";
        this.httpMethod = httpMethod != null ? httpMethod : HttpMethodEnum.GET;
        this.normalizedPath = normalizePath(normalizedPath);
        this.isManual = false;
        this.manualId = "";
    }

    public EndpointIdentity(String manualId) {
        this.module = "";
        this.controllerFqn = "";
        this.methodSignature = "";
        this.httpMethod = HttpMethodEnum.GET;
        this.normalizedPath = "";
        this.isManual = true;
        this.manualId = manualId != null ? manualId.trim() : "";
    }

    public static EndpointIdentity fromEndpoint(EndpointModel endpoint) {
        if (endpoint == null) {
            return new EndpointIdentity("");
        }
        if (endpoint.isManual()) {
            String id = endpoint.getId();
            if (id == null || id.isBlank()) {
                id = java.util.UUID.randomUUID().toString();
                endpoint.setId(id);
            }
            return new EndpointIdentity(id);
        }

        String module = endpoint.getModuleName() != null && !endpoint.getModuleName().isBlank()
                ? endpoint.getModuleName().trim()
                : "default";
        String ctrlFqn = resolveControllerFqn(endpoint);
        String sig = resolveMethodSignature(endpoint);
        HttpMethodEnum method = endpoint.getHttpMethod();
        String path = endpoint.getPath();

        return new EndpointIdentity(module, ctrlFqn, sig, method, path);
    }

    public static String createKey(EndpointModel endpoint) {
        return fromEndpoint(endpoint).getKey();
    }

    public String getKey() {
        if (isManual) {
            return "manual:" + manualId;
        }
        String mod = (module == null || module.isBlank()) ? "default" : module;
        return "scanned:" + mod + ":" + controllerFqn + "#" + methodSignature + ":" + httpMethod.name() + ":" + normalizedPath;
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        p = p.replaceAll("/+", "/");
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    public static String resolveControllerFqn(EndpointModel endpoint) {
        if (endpoint == null) return "";
        String pkg = endpoint.getPackageName() != null ? endpoint.getPackageName().trim() : "";
        String ctrl = endpoint.getControllerName() != null ? endpoint.getControllerName().trim() : "";
        if (pkg.isEmpty()) return ctrl;
        if (ctrl.isEmpty()) return pkg;
        if (ctrl.startsWith(pkg + ".")) return ctrl;
        return pkg + "." + ctrl;
    }

    public static String resolveMethodSignature(EndpointModel endpoint) {
        if (endpoint == null) return "";
        if (endpoint.getMethodSignature() != null && !endpoint.getMethodSignature().isBlank()) {
            return endpoint.getMethodSignature().trim();
        }
        return endpoint.getMethodName() != null ? endpoint.getMethodName().trim() : "";
    }

    public String getModule() { return module; }
    public String getControllerFqn() { return controllerFqn; }
    public String getMethodSignature() { return methodSignature; }
    public HttpMethodEnum getHttpMethod() { return httpMethod; }
    public String getNormalizedPath() { return normalizedPath; }
    public boolean isManual() { return isManual; }
    public String getManualId() { return manualId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointIdentity that = (EndpointIdentity) o;
        return isManual == that.isManual &&
                Objects.equals(manualId, that.manualId) &&
                Objects.equals(module, that.module) &&
                Objects.equals(controllerFqn, that.controllerFqn) &&
                Objects.equals(methodSignature, that.methodSignature) &&
                httpMethod == that.httpMethod &&
                Objects.equals(normalizedPath, that.normalizedPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, controllerFqn, methodSignature, httpMethod, normalizedPath, isManual, manualId);
    }

    @Override
    public String toString() {
        return getKey();
    }
}
