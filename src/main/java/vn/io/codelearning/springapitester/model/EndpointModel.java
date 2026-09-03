package vn.io.codelearning.springapitester.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Model trung tâm lưu toàn bộ dữ liệu của một API Endpoint bóc tách từ Spring Controller.
 */
public class EndpointModel {
    private String id;
    private String name;
    private boolean isManual = false;
    private boolean isAbsoluteUrl = false;
    private String folderId;
    private HttpMethodEnum httpMethod;
    private String path;
    private String controllerName;
    private String packageName;
    private String methodName;
    private String methodSignature = "";
    private boolean isSecured;
    private boolean isRestEndpoint;  // true = trả về JSON/data, false = trả về View (Thymeleaf, JSP)
    private List<ParameterModel> parameters;
    private List<HeaderItem> customHeaders;
    private String requestBodyJson;
    private String requestBodyClassFqn;
    private RequestBodyType bodyType = RequestBodyType.NONE;
    private String returnTypeClassFqn;    // Kiểu trả về của hàm (vd: ResponseEntity<UserDTO>)
    private String expectedResponseJson;  // JSON mẫu dự kiến sinh từ DTO trả về
    private AuthConfig authConfig;
    private String moduleName;            // Service chứa endpoint
    private String directBaseUrl;         // URL gọi trực tiếp (http://localhost:8081)
    private boolean allowInsecureTls = false;
    private transient vn.io.codelearning.springapitester.client.InsecureTlsConsent insecureTlsConsent;

    // Cached Response fields
    private String lastResponseBody = "";
    private int lastResponseStatusCode = 0;
    private String lastResponseStatusMessage = "";
    private long lastResponseTimeTakenMs = 0;
    private String lastResponseHeaders = "";
    private String lastResponseFormat = "JSON";

    public EndpointModel() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.isManual = false;
        this.folderId = "";
        this.httpMethod = HttpMethodEnum.GET;
        this.path = "";
        this.controllerName = "";
        this.packageName = "";
        this.methodName = "";
        this.isSecured = false;
        this.isRestEndpoint = true;
        this.parameters = new ArrayList<>();
        this.customHeaders = new ArrayList<>();
        this.requestBodyJson = "";
        this.requestBodyClassFqn = "";
        this.returnTypeClassFqn = "";
        this.expectedResponseJson = "";
        this.authConfig = new AuthConfig();
    }

    public EndpointModel(HttpMethodEnum httpMethod, String path, String controllerName, String packageName, String methodName) {
        this();
        this.httpMethod = (httpMethod != null) ? httpMethod : HttpMethodEnum.GET;
        this.path = (path != null) ? path : "";
        this.controllerName = (controllerName != null) ? controllerName : "";
        this.packageName = (packageName != null) ? packageName : "";
        this.methodName = (methodName != null) ? methodName : "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public HttpMethodEnum getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethodEnum httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = (path != null) ? path : "";
    }

    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodSignature() {
        return methodSignature != null ? methodSignature : "";
    }

    public void setMethodSignature(String methodSignature) {
        this.methodSignature = methodSignature != null ? methodSignature : "";
    }

    public boolean isSecured() {
        return isSecured;
    }

    public void setSecured(boolean secured) {
        isSecured = secured;
    }

    public List<ParameterModel> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterModel> parameters) {
        this.parameters = (parameters != null) ? parameters : new ArrayList<>();
    }

    public List<HeaderItem> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(List<HeaderItem> customHeaders) {
        this.customHeaders = (customHeaders != null) ? customHeaders : new ArrayList<>();
    }

    public String getRequestBodyJson() {
        return requestBodyJson;
    }

    public void setRequestBodyJson(String requestBodyJson) {
        this.requestBodyJson = (requestBodyJson != null) ? requestBodyJson : "";
    }

    public String getRequestBodyClassFqn() {
        return requestBodyClassFqn;
    }

    public void setRequestBodyClassFqn(String requestBodyClassFqn) {
        this.requestBodyClassFqn = requestBodyClassFqn;
    }

    public String getReturnTypeClassFqn() {
        return returnTypeClassFqn;
    }

    public void setReturnTypeClassFqn(String returnTypeClassFqn) {
        this.returnTypeClassFqn = (returnTypeClassFqn != null) ? returnTypeClassFqn : "";
    }

    public String getExpectedResponseJson() {
        return expectedResponseJson;
    }

    public void setExpectedResponseJson(String expectedResponseJson) {
        this.expectedResponseJson = (expectedResponseJson != null) ? expectedResponseJson : "";
    }

    public boolean isRestEndpoint() {
        return isRestEndpoint;
    }

    public void setRestEndpoint(boolean restEndpoint) {
        isRestEndpoint = restEndpoint;
    }

    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(AuthConfig authConfig) {
        this.authConfig = (authConfig != null) ? authConfig : new AuthConfig();
    }

    public RequestBodyType getBodyType() {
        return bodyType;
    }

    public void setBodyType(RequestBodyType bodyType) {
        this.bodyType = bodyType;
    }

    public void addParameter(ParameterModel param) {
        if (param != null) {
            this.parameters.add(param);
        }
    }

    public void addCustomHeader(HeaderItem header) {
        if (header != null) {
            this.customHeaders.add(header);
        }
    }

    public List<ParameterModel> getPathVariables() {
        return parameters.stream()
                .filter(p -> p.getParamType() == ParamTypeEnum.PATH_VARIABLE)
                .collect(Collectors.toList());
    }

    public List<ParameterModel> getQueryParams() {
        return parameters.stream()
                .filter(p -> p.getParamType() == ParamTypeEnum.QUERY_PARAM)
                .collect(Collectors.toList());
    }

    public List<ParameterModel> getHeaderParameters() {
        return parameters.stream()
                .filter(p -> p.getParamType() == ParamTypeEnum.HEADER)
                .collect(Collectors.toList());
    }

    public List<ParameterModel> getCookieParameters() {
        return parameters.stream()
                .filter(p -> p.getParamType() == ParamTypeEnum.COOKIE)
                .collect(Collectors.toList());
    }

    public List<ParameterModel> getMultipartParameters() {
        return parameters.stream()
                .filter(p -> p.getParamType() == ParamTypeEnum.MULTIPART_FILE)
                .collect(Collectors.toList());
    }

    public List<ParameterModel> getUserEditableParameters() {
        return parameters.stream()
                .filter(p -> p.getParamType().isUserEditable())
                .collect(Collectors.toList());
    }

    /**
     * Trả về tiêu đề hiển thị chuẩn trên cây UI (ví dụ: [GET] /api/v1/users).
     */
    public String getDisplayName() {
        return "[" + httpMethod.name() + "] " + (path.isBlank() ? "/" : path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointModel that = (EndpointModel) o;
        return httpMethod == that.httpMethod &&
                Objects.equals(path, that.path) &&
                Objects.equals(controllerName, that.controllerName) &&
                Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpMethod, path, controllerName, methodName);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public boolean isManual() { return isManual; }
    public void setManual(boolean manual) { isManual = manual; }

    public boolean isAbsoluteUrl() { return isAbsoluteUrl; }
    public void setAbsoluteUrl(boolean absoluteUrl) { this.isAbsoluteUrl = absoluteUrl; }
    
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getDirectBaseUrl() {
        return directBaseUrl;
    }

    public void setDirectBaseUrl(String directBaseUrl) {
        this.directBaseUrl = directBaseUrl;
    }

    public boolean isAllowInsecureTls() {
        return allowInsecureTls;
    }

    public void setAllowInsecureTls(boolean allowInsecureTls) {
        this.allowInsecureTls = allowInsecureTls;
        if (allowInsecureTls && this.insecureTlsConsent == null) {
            this.insecureTlsConsent = new vn.io.codelearning.springapitester.client.InsecureTlsConsent("localhost");
        } else if (!allowInsecureTls) {
            this.insecureTlsConsent = null;
        }
    }

    public vn.io.codelearning.springapitester.client.InsecureTlsConsent getInsecureTlsConsent() {
        return insecureTlsConsent;
    }

    public void setInsecureTlsConsent(vn.io.codelearning.springapitester.client.InsecureTlsConsent consent) {
        this.insecureTlsConsent = consent;
        this.allowInsecureTls = (consent != null);
    }

    public void grantInsecureTlsConsent(String host) {
        this.insecureTlsConsent = new vn.io.codelearning.springapitester.client.InsecureTlsConsent(host);
        this.allowInsecureTls = true;
    }

    public void revokeInsecureTlsConsent() {
        this.insecureTlsConsent = null;
        this.allowInsecureTls = false;
    }

    public String getLastResponseBody() {
        return lastResponseBody != null ? lastResponseBody : "";
    }

    public void setLastResponseBody(String lastResponseBody) {
        this.lastResponseBody = lastResponseBody != null ? lastResponseBody : "";
    }

    public int getLastResponseStatusCode() {
        return lastResponseStatusCode;
    }

    public void setLastResponseStatusCode(int lastResponseStatusCode) {
        this.lastResponseStatusCode = lastResponseStatusCode;
    }

    public String getLastResponseStatusMessage() {
        return lastResponseStatusMessage != null ? lastResponseStatusMessage : "";
    }

    public void setLastResponseStatusMessage(String lastResponseStatusMessage) {
        this.lastResponseStatusMessage = lastResponseStatusMessage != null ? lastResponseStatusMessage : "";
    }

    public long getLastResponseTimeTakenMs() {
        return lastResponseTimeTakenMs;
    }

    public void setLastResponseTimeTakenMs(long lastResponseTimeTakenMs) {
        this.lastResponseTimeTakenMs = lastResponseTimeTakenMs;
    }

    public String getLastResponseHeaders() {
        return lastResponseHeaders != null ? lastResponseHeaders : "";
    }

    public void setLastResponseHeaders(String lastResponseHeaders) {
        this.lastResponseHeaders = lastResponseHeaders != null ? lastResponseHeaders : "";
    }

    public String getLastResponseFormat() {
        return lastResponseFormat != null && !lastResponseFormat.isBlank() ? lastResponseFormat : "JSON";
    }

    public void setLastResponseFormat(String lastResponseFormat) {
        this.lastResponseFormat = (lastResponseFormat != null && !lastResponseFormat.isBlank()) ? lastResponseFormat : "JSON";
    }
}
