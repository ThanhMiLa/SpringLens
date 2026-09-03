package vn.io.codelearning.springapitester.scanner;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bộ đọc cấu hình: Tự động phát hiện và bóc tách server.port, context-path.
 * Hỗ trợ cơ chế ưu tiên: base config → profile config (override) và indentation tracking cho YAML.
 */
public final class SpringConfigReader {

    // Base configs đọc trước (priority thấp)
    private static final List<String> BASE_CONFIG_FILENAMES = List.of(
            "application.properties",
            "application.yml",
            "application.yaml"
    );

    // Profile configs đọc sau (priority cao, ghi đè base)
    private static final List<String> PROFILE_CONFIG_FILENAMES = List.of(
            "application-dev.properties",
            "application-dev.yml",
            "application-dev.yaml",
            "application-local.properties",
            "application-local.yml",
            "application-local.yaml",
            "application-staging.properties",
            "application-staging.yml",
            "application-staging.yaml"
    );

    private SpringConfigReader() {}

    /**
     * Quét và trích xuất cấu hình Server từ Project hiện tại.
     * Thứ tự ưu tiên: base config → profile config (profile ghi đè base).
     */
    public static SpringServerConfig readServerConfig(Project project) {
        if (project == null || project.isDisposed()) {
            return new SpringServerConfig();
        }
        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(project);
        if (service != null) {
            return service.resolveServerConfig();
        }
        SpringServerConfig config = new SpringServerConfig();
        try {
            if (com.intellij.openapi.application.ApplicationManager.getApplication().isReadAccessAllowed()) {
                doReadServerConfig(project, config);
            } else {
                com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction(
                    () -> doReadServerConfig(project, config)
                );
            }
        } catch (Throwable t) {
            // ignore
        }
        return config;
    }

    private static void doReadServerConfig(Project project, SpringServerConfig config) {
        try {
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

            // Bước 1: Đọc base config trước
            for (String filename : BASE_CONFIG_FILENAMES) {
                parseConfigFile(filename, scope, config);
            }

            // Bước 2: Đọc profile config sau (ghi đè lên base nếu có giá trị)
            for (String filename : PROFILE_CONFIG_FILENAMES) {
                parseConfigFile(filename, scope, config);
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    private static void parseConfigFile(String filename, GlobalSearchScope scope, SpringServerConfig config) {
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(filename, scope);
        for (VirtualFile file : files) {
            if (file.isValid() && !file.isDirectory()) {
                String content = readFileContent(file);
                if (content != null && !content.isBlank()) {
                    if (filename.endsWith(".properties")) {
                        parsePropertiesContent(content, config);
                    } else {
                        parseYamlContent(content, config);
                    }
                }
            }
        }
    }

    private static String readFileContent(VirtualFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse nội dung file .properties
     */
    public static void parsePropertiesContent(String content, SpringServerConfig config) {
        if (content == null || content.isBlank()) return;
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(content));

            String portStr = properties.getProperty("server.port");
            if (portStr != null && !portStr.isBlank()) {
                try {
                    config.setPort(Integer.parseInt(portStr.trim()));
                } catch (NumberFormatException ignored) {}
            }

            String contextPath = properties.getProperty("server.servlet.context-path");
            if (contextPath == null || contextPath.isBlank()) {
                contextPath = properties.getProperty("server.context-path");
            }
            if (contextPath == null || contextPath.isBlank()) {
                contextPath = properties.getProperty("spring.webflux.base-path");
            }
            if (contextPath != null && !contextPath.isBlank()) {
                config.setContextPath(contextPath.trim());
            }

            String sslKeyStore = properties.getProperty("server.ssl.key-store");
            String sslEnabled = properties.getProperty("server.ssl.enabled");
            if ("true".equalsIgnoreCase(sslEnabled) || (sslKeyStore != null && !sslKeyStore.isBlank())) {
                config.setSslEnabled(true);
            }

            String activeProfile = properties.getProperty("spring.profiles.active");
            if (activeProfile != null && !activeProfile.isBlank()) {
                config.setActiveProfile(activeProfile.trim());
            }
        } catch (Exception ignored) {}
    }

    /**
     * Parse nội dung file YAML (.yml / .yaml) với Indentation Context Tracking.
     */
    public static void parseYamlContent(String content, SpringServerConfig config) {
        if (content == null || content.isBlank()) return;

        Map<String, String> flatMap = flattenYaml(content);

        // 1. Port
        String portVal = flatMap.get("server.port");
        if (portVal != null && !portVal.isBlank()) {
            try {
                config.setPort(Integer.parseInt(portVal.trim()));
            } catch (NumberFormatException ignored) {}
        }

        // 2. Context Path
        String contextPath = flatMap.get("server.servlet.context-path");
        if (contextPath == null) contextPath = flatMap.get("server.context-path");
        if (contextPath == null) contextPath = flatMap.get("spring.webflux.base-path");
        if (contextPath != null && !contextPath.isBlank()) {
            config.setContextPath(contextPath.replace("\"", "").replace("'", "").trim());
        }

        // 3. SSL
        String sslEnabled = flatMap.get("server.ssl.enabled");
        String sslKeyStore = flatMap.get("server.ssl.key-store");
        if ("true".equalsIgnoreCase(sslEnabled) || (sslKeyStore != null && !sslKeyStore.isBlank())) {
            config.setSslEnabled(true);
        }

        // 4. Active Profile
        String activeProfile = flatMap.get("spring.profiles.active");
        if (activeProfile != null && !activeProfile.isBlank()) {
            config.setActiveProfile(activeProfile.trim());
        }
    }

    /**
     * Chuyển YAML lồng cấp thành dạng flat key bằng cách theo dõi indentation context.
     */
    public static Map<String, String> flattenYaml(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        if (content == null || content.isBlank()) return result;

        List<String> keyStack = new ArrayList<>();
        List<Integer> indentStack = new ArrayList<>();

        for (String rawLine : content.split("\n")) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("---") || trimmed.startsWith("-")) {
                continue;
            }

            int indent = 0;
            for (char c : rawLine.toCharArray()) {
                if (c == ' ') indent++;
                else break;
            }

            int colonIndex = trimmed.indexOf(':');
            if (colonIndex <= 0) continue;

            String key = trimmed.substring(0, colonIndex).trim();
            String value = (colonIndex < trimmed.length() - 1) ? trimmed.substring(colonIndex + 1).trim() : "";

            int commentIdx = value.indexOf('#');
            if (commentIdx > 0) {
                value = value.substring(0, commentIdx).trim();
            }

            value = value.replace("\"", "").replace("'", "");

            while (!indentStack.isEmpty() && indent <= indentStack.get(indentStack.size() - 1)) {
                keyStack.remove(keyStack.size() - 1);
                indentStack.remove(indentStack.size() - 1);
            }

            if (value.isEmpty()) {
                keyStack.add(key);
                indentStack.add(indent);
            } else {
                StringBuilder fullKey = new StringBuilder();
                for (String parentKey : keyStack) {
                    fullKey.append(parentKey).append(".");
                }
                fullKey.append(key);
                result.put(fullKey.toString(), value);
            }
        }

        return result;
    }
}
