package vn.io.codelearning.springapitester.scanner;

/**
 * Model lưu trữ thông tin cấu hình Server của dự án Spring Boot.
 */
public class SpringServerConfig {
    private int port;
    private String contextPath;
    private boolean sslEnabled;
    private String activeProfile; // dev, local, staging...

    public SpringServerConfig() {
        this.port = 8080;
        this.contextPath = "";
        this.sslEnabled = false;
        this.activeProfile = "";
    }

    public SpringServerConfig(int port, String contextPath, boolean sslEnabled) {
        this.port = (port > 0) ? port : 8080;
        this.contextPath = SpringUrlUtils.normalizePath(contextPath);
        this.sslEnabled = sslEnabled;
        this.activeProfile = "";
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = (port > 0) ? port : 8080;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = SpringUrlUtils.normalizePath(contextPath);
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = (activeProfile != null) ? activeProfile : "";
    }

    /**
     * Sinh Base URL hoàn chỉnh (ví dụ: "http://localhost:8088/api").
     */
    public String getBaseUrl() {
        String scheme = sslEnabled ? "https" : "http";
        String normalizedPath = (contextPath != null && !contextPath.isBlank()) ? contextPath : "";
        if (!normalizedPath.isEmpty() && !normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return scheme + "://localhost:" + port + normalizedPath;
    }

    @Override
    public String toString() {
        return getBaseUrl();
    }
}
