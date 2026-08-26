package vn.io.codelearning.springapitester.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

public class SpringBootConfigReader {

    /**
     * Tìm port và context-path từ các file cấu hình (application.yaml, application.yml, application.properties).
     * Trả về Base URL mẫu: http://localhost:8080/context-path
     */
    public static String extractBaseUrl(Project project) {
        String[] port = {"8080"};
        String[] contextPath = {""};

        // Tìm tất cả các file có tên application*
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> ymlFiles = FilenameIndex.getAllFilesByExt(project, "yml", scope);
        Collection<VirtualFile> yamlFiles = FilenameIndex.getAllFilesByExt(project, "yaml", scope);
        Collection<VirtualFile> propFiles = FilenameIndex.getAllFilesByExt(project, "properties", scope);

        // Quét Properties trước
        for (VirtualFile vf : propFiles) {
            if (vf.getName().startsWith("application")) {
                try (InputStream is = vf.getInputStream()) {
                    Properties props = new Properties();
                    props.load(is);
                    if (props.containsKey("server.port")) {
                        port[0] = resolvePlaceholders(props.getProperty("server.port"), port[0]);
                    }
                    if (props.containsKey("server.servlet.context-path")) {
                        contextPath[0] = props.getProperty("server.servlet.context-path");
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        // Quét YAML (Ghi đè nếu có)
        Yaml yaml = new Yaml();
        for (VirtualFile vf : ymlFiles) {
            if (vf.getName().startsWith("application")) {
                parseYamlConfig(vf, yaml, ref -> port[0] = ref[0], ref -> contextPath[0] = ref[0]);
            }
        }
        for (VirtualFile vf : yamlFiles) {
            if (vf.getName().startsWith("application")) {
                parseYamlConfig(vf, yaml, ref -> port[0] = ref[0], ref -> contextPath[0] = ref[0]);
            }
        }
        
        String cPath = contextPath[0];
        if (cPath == null || cPath.trim().isEmpty() || cPath.equals("/")) {
            cPath = "";
        } else if (!cPath.startsWith("/")) {
            cPath = "/" + cPath;
        }

        if (cPath.endsWith("/")) {
            cPath = cPath.substring(0, cPath.length() - 1);
        }

        return "http://localhost:" + port[0] + cPath;
    }

    private static void parseYamlConfig(VirtualFile vf, Yaml yaml, java.util.function.Consumer<String[]> setPort, java.util.function.Consumer<String[]> setContext) {
        try (InputStream is = vf.getInputStream()) {
            Iterable<Object> iter = yaml.loadAll(is);
            for (Object obj : iter) {
                if (obj instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) obj;
                    Object serverObj = map.get("server");
                    if (serverObj instanceof Map) {
                        Map<String, Object> serverMap = (Map<String, Object>) serverObj;
                        if (serverMap.containsKey("port")) {
                            String portRaw = String.valueOf(serverMap.get("port"));
                            setPort.accept(new String[]{resolvePlaceholders(portRaw, "8080")});
                        }

                        Object servletObj = serverMap.get("servlet");
                        if (servletObj instanceof Map) {
                            Map<String, Object> servletMap = (Map<String, Object>) servletObj;
                            if (servletMap.containsKey("context-path")) {
                                setContext.accept(new String[]{String.valueOf(servletMap.get("context-path"))});
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Hàm đơn giản xử lý chuỗi kiểu ${PORT:8080}
     */
    private static String resolvePlaceholders(String value, String defaultValue) {
        if (value == null) return defaultValue;
        value = value.trim();
        if (value.startsWith("${") && value.endsWith("}")) {
            String inner = value.substring(2, value.length() - 1);
            int colonIndex = inner.indexOf(':');
            if (colonIndex != -1) {
                return inner.substring(colonIndex + 1);
            }
            return defaultValue; // Fallback if no default provided in ${ENV}
        }
        return value;
    }
}


