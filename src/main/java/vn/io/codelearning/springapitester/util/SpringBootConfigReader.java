package vn.io.codelearning.springapitester.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import vn.io.codelearning.springapitester.scanner.SpringConfigResolutionService;
import vn.io.codelearning.springapitester.scanner.SpringServerConfig;

public class SpringBootConfigReader {

    public static class AppConfig {
        public String baseUrl;
        public String appName;

        public AppConfig(String baseUrl, String appName) {
            this.baseUrl = baseUrl;
            this.appName = appName;
        }
    }

    public static AppConfig extractAppConfig(Project project) {
        return extractAppConfig(project, null);
    }

    public static AppConfig extractAppConfig(Project project, Module module) {
        if (project == null || project.isDisposed()) {
            return defaultConfig();
        }

        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(project);
        if (service == null) {
            return defaultConfig();
        }

        SpringServerConfig config = service.resolveServerConfig(module);
        return new AppConfig(config.getBaseUrl(), config.getAppName());
    }

    public static String extractBaseUrl(Project project) {
        return extractAppConfig(project).baseUrl;
    }

    public static String extractBaseUrl(Project project, Module module) {
        return extractAppConfig(project, module).baseUrl;
    }

    private static AppConfig defaultConfig() {
        return new AppConfig("http://localhost:8080", "");
    }

    static String resolvePlaceholders(String value, String defaultValue) {
        if (value == null) return defaultValue;
        value = value.trim();
        if (value.startsWith("${") && value.endsWith("}")) {
            String inner = value.substring(2, value.length() - 1);
            int colonIndex = inner.indexOf(':');
            if (colonIndex != -1) {
                return inner.substring(colonIndex + 1);
            }
            return defaultValue;
        }
        return value;
    }
}
