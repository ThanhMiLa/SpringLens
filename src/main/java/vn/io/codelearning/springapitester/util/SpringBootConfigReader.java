package vn.io.codelearning.springapitester.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

public class SpringBootConfigReader {

    /**
     * Tìm port và context-path từ các file cấu hình (application.yaml, application.yml, application.properties).
     */
    public static class AppConfig {
        public String baseUrl;
        public String appName;
        public AppConfig(String baseUrl, String appName) {
            this.baseUrl = baseUrl;
            this.appName = appName;
        }
    }

    public static AppConfig extractAppConfig(Project project) {
        if (project == null || project.isDisposed()) return new AppConfig("http://localhost:8080", "");
        try {
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                return extractConfigFromScope(project, GlobalSearchScope.projectScope(project));
            } else {
                return ApplicationManager.getApplication().runReadAction(
                    (Computable<AppConfig>) () -> extractConfigFromScope(project, GlobalSearchScope.projectScope(project))
                );
            }
        } catch (Throwable t) {
            return new AppConfig("http://localhost:8080", "");
        }
    }

    public static AppConfig extractAppConfig(Project project, com.intellij.openapi.module.Module module) {
        if (project == null || project.isDisposed()) return new AppConfig("http://localhost:8080", "");
        if (module == null || module.isDisposed()) return extractAppConfig(project);
        try {
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, false);
                return extractConfigFromScope(project, scope);
            } else {
                return ApplicationManager.getApplication().runReadAction(
                    (Computable<AppConfig>) () -> {
                        GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, false);
                        return extractConfigFromScope(project, scope);
                    }
                );
            }
        } catch (Throwable t) {
            return new AppConfig("http://localhost:8080", "");
        }
    }
    
    public static String extractBaseUrl(Project project) {
        return extractAppConfig(project).baseUrl;
    }

    public static String extractBaseUrl(Project project, com.intellij.openapi.module.Module module) {
        return extractAppConfig(project, module).baseUrl;
    }

    private static AppConfig extractConfigFromScope(Project project, GlobalSearchScope scope) {
        String[] port = {"8080"};
        String[] contextPath = {""};
        String[] appName = {""};

        try {
            Collection<VirtualFile> ymlFiles = FilenameIndex.getAllFilesByExt(project, "yml", scope);
            Collection<VirtualFile> yamlFiles = FilenameIndex.getAllFilesByExt(project, "yaml", scope);
            Collection<VirtualFile> propFiles = FilenameIndex.getAllFilesByExt(project, "properties", scope);

            // Process properties
            for (VirtualFile vf : propFiles) {
                if (vf.getName().startsWith("application") || vf.getName().startsWith("bootstrap")) {
                    try (InputStream is = vf.getInputStream()) {
                        Properties props = new Properties();
                        props.load(is);
                        if (props.containsKey("server.port")) {
                            port[0] = resolvePlaceholders(props.getProperty("server.port"), port[0]);
                        }
                        if (props.containsKey("server.servlet.context-path")) {
                            contextPath[0] = props.getProperty("server.servlet.context-path");
                        }
                        if (props.containsKey("spring.application.name")) {
                            appName[0] = props.getProperty("spring.application.name");
                        }
                    } catch (Throwable t) {}
                }
            }

            // Process YAML
            Yaml yaml = new Yaml();
            List<VirtualFile> yamlAll = new ArrayList<>();
            yamlAll.addAll(ymlFiles);
            yamlAll.addAll(yamlFiles);
            for (VirtualFile vf : yamlAll) {
                if (vf.getName().startsWith("application") || vf.getName().startsWith("bootstrap")) {
                    parseYamlConfig(vf, yaml, ref -> port[0] = ref[0], ref -> contextPath[0] = ref[0], ref -> appName[0] = ref[0]);
                }
            }
        } catch (Throwable t) {
            // ignore
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

        return new AppConfig("http://localhost:" + port[0] + cPath, appName[0]);
    }

    private static void parseYamlConfig(VirtualFile vf, Yaml yaml, java.util.function.Consumer<String[]> setPort, java.util.function.Consumer<String[]> setContext, java.util.function.Consumer<String[]> setAppName) {
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
                    
                    Object springObj = map.get("spring");
                    if (springObj instanceof Map) {
                        Map<String, Object> springMap = (Map<String, Object>) springObj;
                        Object applicationObj = springMap.get("application");
                        if (applicationObj instanceof Map) {
                            Map<String, Object> applicationMap = (Map<String, Object>) applicationObj;
                            if (applicationMap.containsKey("name")) {
                                setAppName.accept(new String[]{String.valueOf(applicationMap.get("name"))});
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
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


