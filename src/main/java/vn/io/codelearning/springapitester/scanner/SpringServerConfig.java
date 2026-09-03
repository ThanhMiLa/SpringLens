package vn.io.codelearning.springapitester.scanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Model lưu trữ thông tin cấu hình Server của dự án Spring Boot.
 */
public class SpringServerConfig {
    private int port;
    private String contextPath;
    private boolean sslEnabled;
    private String activeProfile; // dev, local, staging...
    private String sourceFile = "";
    private boolean isFallback = true;
    private boolean hasUnresolvedPlaceholder = false;
    private final List<String> diagnostics = new ArrayList<>();

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
        this.isFallback = false;
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

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = (sourceFile != null) ? sourceFile : "";
    }

    public boolean isFallback() {
        return isFallback;
    }

    public void setFallback(boolean fallback) {
        isFallback = fallback;
    }

    public boolean hasUnresolvedPlaceholder() {
        return hasUnresolvedPlaceholder;
    }

    public void setHasUnresolvedPlaceholder(boolean hasUnresolvedPlaceholder) {
        this.hasUnresolvedPlaceholder = hasUnresolvedPlaceholder;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }

    public void addDiagnostic(String diagnostic) {
        if (diagnostic != null && !diagnostic.isBlank()) {
            this.diagnostics.add(diagnostic);
        }
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
