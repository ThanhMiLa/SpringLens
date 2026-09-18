package vn.io.codelearning.springapitester.scanner;

import com.intellij.openapi.project.Project;

import java.io.StringReader;
import java.util.*;

/**
 * Bộ đọc cấu hình: Tự động phát hiện và bóc tách server.port, context-path.
 * Hỗ trợ cơ chế ưu tiên: base config → profile config (override) và indentation tracking cho YAML.
 */
public final class SpringConfigReader {

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
        return service != null ? service.resolveServerConfig() : new SpringServerConfig();
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
