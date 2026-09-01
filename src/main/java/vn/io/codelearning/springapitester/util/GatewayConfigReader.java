package vn.io.codelearning.springapitester.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.yaml.snakeyaml.Yaml;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class GatewayConfigReader {

    public static boolean hasGatewayDependency(Module module) {
        if (module == null || module.isDisposed()) return false;
        try {
            OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
            for (OrderEntry entry : orderEntries) {
                if (entry instanceof LibraryOrderEntry) {
                    String libName = ((LibraryOrderEntry) entry).getLibraryName();
                    if (libName != null && (libName.toLowerCase().contains("gateway")
                            || libName.toLowerCase().contains("zuul"))) {
                        return true;
                    }
                }
            }
            // Let's also check if module name contains gateway
            if (module.getName() != null && module.getName().toLowerCase().contains("gateway")) return true;
        } catch (Throwable t) {
            // ignore
        }
        
        return false;
    }

    public static GatewayConfig findGatewayConfig(Project project) {
        if (project == null || project.isDisposed()) return null;
        try {
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                return doFindGatewayConfig(project);
            } else {
                return ApplicationManager.getApplication().runReadAction(
                    (Computable<GatewayConfig>) () -> doFindGatewayConfig(project)
                );
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static GatewayConfig doFindGatewayConfig(Project project) {
        try {
            com.intellij.openapi.module.Module[] modules = com.intellij.openapi.module.ModuleManager.getInstance(project).getModules();
            for (com.intellij.openapi.module.Module m : modules) {
                if (m != null && !m.isDisposed() && hasGatewayDependency(m)) {
                    GatewayConfig config = parseGatewayConfig(project, m);
                    if (config != null) {
                        return config;
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    public static GatewayConfig parseGatewayConfig(Project project, Module module) {
        if (project == null || project.isDisposed() || module == null || module.isDisposed()) {
            return null;
        }
        if (!hasGatewayDependency(module)) {
            return null; 
        }

        try {
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                return doParseGatewayConfig(project, module);
            } else {
                return ApplicationManager.getApplication().runReadAction(
                    (Computable<GatewayConfig>) () -> doParseGatewayConfig(project, module)
                );
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static GatewayConfig doParseGatewayConfig(Project project, Module module) {
        try {
            GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(module, false);
            Collection<VirtualFile> ymlFiles = FilenameIndex.getAllFilesByExt(project, "yml", scope);
            Collection<VirtualFile> yamlFiles = FilenameIndex.getAllFilesByExt(project, "yaml", scope);
            Collection<VirtualFile> propFiles = FilenameIndex.getAllFilesByExt(project, "properties", scope);

            List<VirtualFile> allFiles = new ArrayList<>();
            allFiles.addAll(ymlFiles);
            allFiles.addAll(yamlFiles);

            GatewayConfig config = new GatewayConfig();
            
            // Properties
            for (VirtualFile vf : propFiles) {
                if (!vf.getName().startsWith("application") && !vf.getName().startsWith("bootstrap")) continue;
                try (InputStream is = vf.getInputStream()) {
                    Properties props = new Properties();
                    props.load(is);
                    if (props.containsKey("server.port")) {
                        config.port = props.getProperty("server.port");
                    }
                } catch (Throwable t) {}
            }

            Yaml yaml = new Yaml();
            for (VirtualFile vf : allFiles) {
                if (!vf.getName().startsWith("application") && !vf.getName().startsWith("bootstrap")) continue;
                
                try (InputStream is = vf.getInputStream()) {
                    Iterable<Object> iter = yaml.loadAll(is);
                    for (Object obj : iter) {
                        if (obj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) obj;
                            
                            // Parse Port
                            Object serverObj = map.get("server");
                            if (serverObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> serverMap = (Map<String, Object>) serverObj;
                                if (serverMap.containsKey("port")) {
                                    config.port = String.valueOf(serverMap.get("port"));
                                }
                            }

                            // Parse Gateway
                            Object springObj = map.get("spring");
                            if (springObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> springMap = (Map<String, Object>) springObj;
                                Object cloudObj = springMap.get("cloud");
                                if (cloudObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> cloudMap = (Map<String, Object>) cloudObj;
                                    Object gatewayObj = cloudMap.get("gateway");
                                    if (gatewayObj instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> gatewayMap = (Map<String, Object>) gatewayObj;
                                        
                                        // Discovery Locator
                                        Object discoveryObj = gatewayMap.get("discovery");
                                        if (discoveryObj instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> discoveryMap = (Map<String, Object>) discoveryObj;
                                            Object locatorObj = discoveryMap.get("locator");
                                            if (locatorObj instanceof Map) {
                                                @SuppressWarnings("unchecked")
                                                Map<String, Object> locatorMap = (Map<String, Object>) locatorObj;
                                                if (Boolean.TRUE.equals(locatorMap.get("enabled")) || "true".equals(String.valueOf(locatorMap.get("enabled")))) {
                                                    config.discoveryLocatorEnabled = true;
                                                }
                                            }
                                        }

                                        // Routes
                                        Object routesObj = gatewayMap.get("routes");
                                        if (routesObj instanceof List) {
                                            @SuppressWarnings("unchecked")
                                            List<Map<String, Object>> routesList = (List<Map<String, Object>>) routesObj;
                                            for (Map<String, Object> routeMap : routesList) {
                                                GatewayRouteModel route = new GatewayRouteModel();
                                                if (routeMap.containsKey("id")) route.setId(String.valueOf(routeMap.get("id")));
                                                if (routeMap.containsKey("uri")) route.setUri(String.valueOf(routeMap.get("uri")));
                                                
                                                // Predicates
                                                Object predicatesObj = routeMap.get("predicates");
                                                if (predicatesObj instanceof List) {
                                                    for (Object predicateObj : (List<?>) predicatesObj) {
                                                        String predicateStr = String.valueOf(predicateObj);
                                                        if (predicateStr.startsWith("Path=")) {
                                                            String pathVal = predicateStr.substring(5).trim();
                                                            for (String p : pathVal.split(",")) {
                                                                route.getPathPredicates().add(p.trim());
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                // Filters
                                                Object filtersObj = routeMap.get("filters");
                                                if (filtersObj instanceof List) {
                                                    for (Object filterObj : (List<?>) filtersObj) {
                                                        String filterStr = String.valueOf(filterObj);
                                                        if (filterStr.startsWith("StripPrefix=")) {
                                                            try {
                                                                route.setStripPrefix(Integer.parseInt(filterStr.substring(12).trim()));
                                                            } catch (Throwable t) {}
                                                        } else if (filterStr.startsWith("PrefixPath=")) {
                                                            route.setPrefixPath(filterStr.substring(11).trim());
                                                        } else if (filterStr.startsWith("RewritePath=")) {
                                                            String[] parts = filterStr.substring(12).split(",");
                                                            if (parts.length >= 2) {
                                                                route.setRewritePathRegex(parts[0].trim());
                                                                route.setRewritePathReplacement(parts[1].trim());
                                                            }
                                                        }
                                                    }
                                                }
                                                config.routes.add(route);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    // ignore
                }
            }
            
            return config;
        } catch (Throwable t) {
            return null;
        }
    }
    
    public static class GatewayConfig {
        public String port = "8080";
        public boolean discoveryLocatorEnabled = false;
        public List<GatewayRouteModel> routes = new ArrayList<>();
    }
}
