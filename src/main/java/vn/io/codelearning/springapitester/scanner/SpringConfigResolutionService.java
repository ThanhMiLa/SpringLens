package vn.io.codelearning.springapitester.scanner;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;
import vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dịch vụ phân giải cấu hình Spring Boot và Spring Cloud Gateway tập trung, xác định (deterministic),
 * hỗ trợ đa tài liệu YAML, profile activation, placeholder ${...:...}, và tự động hủy cache khi VFS thay đổi.
 */
public class SpringConfigResolutionService implements Disposable {

    private static final Logger LOG = Logger.getInstance(SpringConfigResolutionService.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Project project;
    private final Map<String, SpringServerConfig> serverConfigCache = new ConcurrentHashMap<>();
    private final Map<String, GatewayConfig> gatewayConfigCache = new ConcurrentHashMap<>();

    public SpringConfigResolutionService(@NotNull Project project) {
        this.project = project;
        registerVfsListener();
    }

    public static SpringConfigResolutionService getInstance(@NotNull Project project) {
        return project.getService(SpringConfigResolutionService.class);
    }

    private void registerVfsListener() {
        VirtualFileManager.getInstance().addAsyncFileListener(events -> {
            boolean hasConfigChange = false;
            for (VFileEvent event : events) {
                VirtualFile file = event.getFile();
                if (file != null && isConfigFile(file.getName())) {
                    hasConfigChange = true;
                    break;
                }
            }
            if (hasConfigChange) {
                return new AsyncFileListener.ChangeApplier() {
                    @Override
                    public void afterVfsChange() {
                        invalidateCache();
                    }
                };
            }
            return null;
        }, this);
    }

    public void invalidateCache() {
        serverConfigCache.clear();
        gatewayConfigCache.clear();
    }

    @Override
    public void dispose() {
        invalidateCache();
    }

    public SpringServerConfig resolveServerConfig() {
        return resolveServerConfig(null);
    }

    public SpringServerConfig resolveServerConfig(@Nullable Module module) {
        String cacheKey = module != null ? module.getName() : "__project__";
        SpringServerConfig cached = serverConfigCache.get(cacheKey);
        if (cached != null) return cached;

        com.intellij.openapi.application.Application app = ApplicationManager.getApplication();
        if (app != null && app.isDispatchThread()) {
            SpringServerConfig fallback = new SpringServerConfig();
            fallback.setFallback(true);
            serverConfigCache.put(cacheKey, fallback);
            app.executeOnPooledThread(() -> {
                try {
                    SpringServerConfig resolved = readServerConfigInternal(module);
                    serverConfigCache.put(cacheKey, resolved);
                } catch (Throwable ignored) {}
            });
            return fallback;
        }

        SpringServerConfig config = readServerConfigInternal(module);
        serverConfigCache.put(cacheKey, config);
        return config;
    }

    public GatewayConfig resolveGatewayConfig() {
        String cacheKey = "__project__";
        GatewayConfig cached = gatewayConfigCache.get(cacheKey);
        if (cached != null) return cached;

        com.intellij.openapi.application.Application app = ApplicationManager.getApplication();
        if (app != null && app.isDispatchThread()) {
            GatewayConfig fallback = new GatewayConfig();
            fallback.isFallback = true;
            gatewayConfigCache.put(cacheKey, fallback);
            app.executeOnPooledThread(() -> {
                try {
                    GatewayConfig resolved = readGatewayConfigInternal();
                    gatewayConfigCache.put(cacheKey, resolved);
                } catch (Throwable ignored) {}
            });
            return fallback;
        }

        GatewayConfig config = readGatewayConfigInternal();
        gatewayConfigCache.put(cacheKey, config);
        return config;
    }

    private SpringServerConfig readServerConfigInternal(@Nullable Module targetModule) {
        SpringServerConfig config = new SpringServerConfig();
        if (project.isDisposed()) return config;

        try {
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                doReadServerConfig(targetModule, config);
            } else {
                ApplicationManager.getApplication().runReadAction(
                        (Computable<Void>) () -> {
                            doReadServerConfig(targetModule, config);
                            return null;
                        }
                );
            }
        } catch (Throwable t) {
            LOG.warn("Failed to read server config: " + t.getMessage(), t);
            config.addDiagnostic("Error reading server config: " + t.getMessage());
        }

        return config;
    }

    private void doReadServerConfig(@Nullable Module targetModule, SpringServerConfig config) {
        List<VirtualFile> candidateFiles = findAndSortConfigFiles(targetModule);
        if (candidateFiles.isEmpty()) {
            config.setFallback(true);
            config.addDiagnostic("No Spring configuration files found. Defaulting to port 8080.");
            return;
        }

        Map<String, String> accumulatedProps = new LinkedHashMap<>();
        String activeProfile = "";

        // First pass: detect active profile
        for (VirtualFile vf : candidateFiles) {
            Map<String, String> rawProps = loadPropertiesFromFile(vf);
            String profile = rawProps.get("spring.profiles.active");
            if (profile != null && !profile.isBlank()) {
                activeProfile = profile.trim();
                config.setActiveProfile(activeProfile);
                break;
            }
        }

        // Second pass: apply base then active profile configs with import resolution
        Set<String> visitedFiles = new HashSet<>();
        for (VirtualFile vf : candidateFiles) {
            if (!isConfigFileActive(vf.getName(), activeProfile)) {
                continue;
            }
            visitedFiles.add(vf.getPath());
            Map<String, String> fileProps = loadPropertiesFromFile(vf, activeProfile, config.getDiagnostics());
            if (!fileProps.isEmpty()) {
                // Process spring.config.import
                processConfigImports(fileProps, targetModule, activeProfile, accumulatedProps, visitedFiles, config.getDiagnostics());
                accumulatedProps.putAll(fileProps);
                config.setSourceFile(vf.getPath());
                config.setFallback(false);
            }
        }

        // Resolve port
        String rawPort = accumulatedProps.get("server.port");
        if (rawPort != null && !rawPort.isBlank()) {
            String resolvedPort = resolvePlaceholders(rawPort, accumulatedProps, config.getDiagnostics());
            try {
                int port = Integer.parseInt(resolvedPort.trim());
                config.setPort(port);
            } catch (NumberFormatException nfe) {
                config.setHasUnresolvedPlaceholder(true);
                config.addDiagnostic("Invalid or unresolved server.port: " + rawPort + " (resolved as: " + resolvedPort + ")");
            }
        } else {
            config.setFallback(true);
        }

        // Resolve context-path
        String contextPath = accumulatedProps.get("server.servlet.context-path");
        if (contextPath == null) contextPath = accumulatedProps.get("server.context-path");
        if (contextPath == null) contextPath = accumulatedProps.get("spring.webflux.base-path");
        if (contextPath != null && !contextPath.isBlank()) {
            String resolvedPath = resolvePlaceholders(contextPath, accumulatedProps, config.getDiagnostics());
            config.setContextPath(resolvedPath.trim());
        }

        // Resolve SSL
        String sslEnabled = accumulatedProps.get("server.ssl.enabled");
        String sslKeyStore = accumulatedProps.get("server.ssl.key-store");
        if ("true".equalsIgnoreCase(sslEnabled) || (sslKeyStore != null && !sslKeyStore.isBlank())) {
            config.setSslEnabled(true);
        }
    }

    private GatewayConfig readGatewayConfigInternal() {
        GatewayConfig config = new GatewayConfig();
        if (project.isDisposed()) return config;

        try {
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                doReadGatewayConfig(config);
            } else {
                ApplicationManager.getApplication().runReadAction(
                        (Computable<Void>) () -> {
                            doReadGatewayConfig(config);
                            return null;
                        }
                );
            }
        } catch (Throwable t) {
            LOG.warn("Failed to read gateway config: " + t.getMessage(), t);
            config.diagnostics.add("Error reading gateway config: " + t.getMessage());
        }

        return config;
    }

    private void doReadGatewayConfig(GatewayConfig config) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        Module gatewayModule = null;
        for (Module m : modules) {
            if (m != null && !m.isDisposed() && vn.io.codelearning.springapitester.util.GatewayConfigReader.hasGatewayDependency(m)) {
                gatewayModule = m;
                break;
            }
        }

        List<VirtualFile> candidateFiles = findAndSortConfigFiles(gatewayModule);
        if (candidateFiles.isEmpty()) {
            config.isFallback = true;
            config.diagnostics.add("No Gateway configuration files found. Defaulting to port 8080.");
            return;
        }

        Map<String, String> accumulatedProps = new LinkedHashMap<>();
        String activeProfile = "";

        for (VirtualFile vf : candidateFiles) {
            Map<String, String> rawProps = loadPropertiesFromFile(vf);
            String profile = rawProps.get("spring.profiles.active");
            if (profile != null && !profile.isBlank()) {
                activeProfile = profile.trim();
                break;
            }
        }

        Yaml yaml = new Yaml();
        for (VirtualFile vf : candidateFiles) {
            if (!isConfigFileActive(vf.getName(), activeProfile)) {
                continue;
            }
            if (vf.getName().endsWith(".properties")) {
                Map<String, String> p = loadPropertiesFromFile(vf, activeProfile, config.diagnostics);
                accumulatedProps.putAll(p);
                String pPort = p.get("server.port");
                if (pPort != null) {
                    config.port = resolvePlaceholders(pPort, accumulatedProps, config.diagnostics);
                    config.sourceFile = vf.getPath();
                    config.isFallback = false;
                }
            } else {
                // YAML parsing with multi-document & structured gateway support
                try (InputStream is = vf.getInputStream()) {
                    Iterable<Object> documents = yaml.loadAll(is);
                    for (Object docObj : documents) {
                        if (docObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> doc = (Map<String, Object>) docObj;
                            if (!isDocumentActive(doc, activeProfile)) {
                                continue;
                            }
                            config.sourceFile = vf.getPath();
                            config.isFallback = false;
                            parseGatewayYamlDocument(doc, config, accumulatedProps);
                        }
                    }
                } catch (Exception e) {
                    config.diagnostics.add("Error parsing YAML file " + vf.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseGatewayYamlDocument(Map<String, Object> doc, GatewayConfig config, Map<String, String> props) {
        // Port
        Object serverObj = doc.get("server");
        if (serverObj instanceof Map) {
            Map<String, Object> serverMap = (Map<String, Object>) serverObj;
            Object portObj = serverMap.get("port");
            if (portObj != null) {
                config.port = resolvePlaceholders(String.valueOf(portObj), props, config.diagnostics);
            }
        }

        // Spring -> Cloud -> Gateway
        Object springObj = doc.get("spring");
        if (springObj instanceof Map) {
            Map<String, Object> springMap = (Map<String, Object>) springObj;
            Object cloudObj = springMap.get("cloud");
            if (cloudObj instanceof Map) {
                Map<String, Object> cloudMap = (Map<String, Object>) cloudObj;
                Object gatewayObj = cloudMap.get("gateway");
                if (gatewayObj instanceof Map) {
                    Map<String, Object> gatewayMap = (Map<String, Object>) gatewayObj;

                    // Discovery locator
                    Object discoveryObj = gatewayMap.get("discovery");
                    if (discoveryObj instanceof Map) {
                        Map<String, Object> discMap = (Map<String, Object>) discoveryObj;
                        Object locatorObj = discMap.get("locator");
                        if (locatorObj instanceof Map) {
                            Map<String, Object> locMap = (Map<String, Object>) locatorObj;
                            if (Boolean.TRUE.equals(locMap.get("enabled")) || "true".equals(String.valueOf(locMap.get("enabled")))) {
                                config.discoveryLocatorEnabled = true;
                            }
                        }
                    }

                    // Routes
                    Object routesObj = gatewayMap.get("routes");
                    if (routesObj instanceof List) {
                        List<Object> routesList = (List<Object>) routesObj;
                        for (Object rItem : routesList) {
                            if (rItem instanceof Map) {
                                Map<String, Object> routeMap = (Map<String, Object>) rItem;
                                GatewayRouteModel route = new GatewayRouteModel();
                                if (routeMap.containsKey("id")) route.setId(String.valueOf(routeMap.get("id")));
                                if (routeMap.containsKey("uri")) route.setUri(String.valueOf(routeMap.get("uri")));

                                parseRoutePredicates(routeMap.get("predicates"), route);
                                parseRouteFilters(routeMap.get("filters"), route);

                                mergeRoute(config.routes, route);
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseRoutePredicates(Object predicatesObj, GatewayRouteModel route) {
        if (!(predicatesObj instanceof List)) return;
        List<?> list = (List<?>) predicatesObj;
        for (Object pObj : list) {
            if (pObj instanceof String) {
                String str = (String) pObj;
                if (str.startsWith("Path=")) {
                    String pathVal = str.substring(5).trim();
                    for (String p : pathVal.split(",")) {
                        route.getPathPredicates().add(p.trim());
                    }
                }
            } else if (pObj instanceof Map) {
                Map<String, Object> pMap = (Map<String, Object>) pObj;
                String name = String.valueOf(pMap.get("name"));
                if ("Path".equalsIgnoreCase(name)) {
                    Object argsObj = pMap.get("args");
                    if (argsObj instanceof Map) {
                        Map<String, Object> args = (Map<String, Object>) argsObj;
                        for (Object val : args.values()) {
                            if (val != null) {
                                for (String p : String.valueOf(val).split(",")) {
                                    route.getPathPredicates().add(p.trim());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseRouteFilters(Object filtersObj, GatewayRouteModel route) {
        if (!(filtersObj instanceof List)) return;
        List<?> list = (List<?>) filtersObj;
        for (Object fObj : list) {
            if (fObj instanceof String) {
                String str = (String) fObj;
                if (str.startsWith("StripPrefix=")) {
                    try {
                        route.setStripPrefix(Integer.parseInt(str.substring(12).trim()));
                    } catch (Exception ignored) {}
                } else if (str.startsWith("PrefixPath=")) {
                    route.setPrefixPath(str.substring(11).trim());
                } else if (str.startsWith("RewritePath=")) {
                    String[] parts = str.substring(12).split(",");
                    if (parts.length >= 2) {
                        route.setRewritePathRegex(parts[0].trim());
                        route.setRewritePathReplacement(parts[1].trim());
                    }
                }
            } else if (fObj instanceof Map) {
                Map<String, Object> fMap = (Map<String, Object>) fObj;
                String name = String.valueOf(fMap.get("name"));
                Object argsObj = fMap.get("args");
                Map<String, Object> args = (argsObj instanceof Map) ? (Map<String, Object>) argsObj : Collections.emptyMap();

                if ("StripPrefix".equalsIgnoreCase(name)) {
                    Object parts = args.containsKey("parts") ? args.get("parts") : args.get("_genkey_0");
                    if (parts != null) {
                        try {
                            route.setStripPrefix(Integer.parseInt(String.valueOf(parts).trim()));
                        } catch (Exception ignored) {}
                    }
                } else if ("PrefixPath".equalsIgnoreCase(name)) {
                    Object prefix = args.containsKey("prefix") ? args.get("prefix") : args.get("_genkey_0");
                    if (prefix != null) {
                        route.setPrefixPath(String.valueOf(prefix).trim());
                    }
                } else if ("RewritePath".equalsIgnoreCase(name)) {
                    Object regex = args.containsKey("regexp") ? args.get("regexp") : args.get("_genkey_0");
                    Object replacement = args.containsKey("replacement") ? args.get("replacement") : args.get("_genkey_1");
                    if (regex != null && replacement != null) {
                        route.setRewritePathRegex(String.valueOf(regex).trim());
                        route.setRewritePathReplacement(String.valueOf(replacement).trim());
                    }
                }
            }
        }
    }

    private List<VirtualFile> findAndSortConfigFiles(@Nullable Module module) {
        GlobalSearchScope scope = module != null
                ? GlobalSearchScope.moduleRuntimeScope(module, false)
                : GlobalSearchScope.projectScope(project);

        List<VirtualFile> result = new ArrayList<>();
        String[] extensions = {"properties", "yml", "yaml"};
        for (String ext : extensions) {
            Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, ext, scope);
            for (VirtualFile file : files) {
                if (file.isValid() && !file.isDirectory() && !isGeneratedOrBuildFile(file)) {
                    String name = file.getName().toLowerCase(Locale.ROOT);
                    if (name.startsWith("application") || name.startsWith("bootstrap")) {
                        result.add(file);
                    }
                }
            }
        }

        // Deterministic sort: bootstrap base -> bootstrap profile -> application base -> application profile, path
        result.sort((f1, f2) -> {
            int p1 = getFilePrecedence(f1.getName());
            int p2 = getFilePrecedence(f2.getName());
            if (p1 != p2) return Integer.compare(p1, p2);
            return f1.getPath().compareTo(f2.getPath());
        });

        return result;
    }

    public static boolean isGeneratedOrBuildFile(VirtualFile file) {
        if (file == null) return false;
        String path = file.getPath();
        return path.contains("/build/")
                || path.contains("/target/")
                || path.contains("/.gradle/")
                || path.contains("/out/")
                || path.contains("/.idea/")
                || path.contains("/node_modules/");
    }

    public static void mergeRoute(List<GatewayRouteModel> routes, GatewayRouteModel newRoute) {
        if (routes == null || newRoute == null) return;
        String id = newRoute.getId();
        if (id != null && !id.isBlank()) {
            for (int i = 0; i < routes.size(); i++) {
                GatewayRouteModel existing = routes.get(i);
                if (id.equalsIgnoreCase(existing.getId())) {
                    routes.set(i, newRoute);
                    return;
                }
            }
        }
        routes.add(newRoute);
    }

    private void processConfigImports(Map<String, String> fileProps, @Nullable Module module,
                                      String activeProfile, Map<String, String> targetProps,
                                      Set<String> visitedFiles, @Nullable List<String> diagnostics) {
        String importDirective = fileProps.get("spring.config.import");
        if (importDirective == null || importDirective.isBlank()) return;

        for (String item : importDirective.split(",")) {
            String clean = item.trim();
            if (clean.startsWith("optional:")) clean = clean.substring("optional:".length()).trim();
            if (clean.startsWith("classpath:")) clean = clean.substring("classpath:".length()).trim();
            if (clean.startsWith("file:")) clean = clean.substring("file:".length()).trim();
            if (clean.startsWith("/")) clean = clean.substring(1).trim();

            VirtualFile importedFile = findConfigFileByName(clean, module);
            if (importedFile != null && visitedFiles.add(importedFile.getPath())) {
                Map<String, String> imported = loadPropertiesFromFile(importedFile, activeProfile, diagnostics);
                processConfigImports(imported, module, activeProfile, targetProps, visitedFiles, diagnostics);
                targetProps.putAll(imported);
            }
        }
    }

    private VirtualFile findConfigFileByName(String filename, @Nullable Module module) {
        if (filename == null || filename.isBlank()) return null;
        GlobalSearchScope scope = module != null
                ? GlobalSearchScope.moduleRuntimeScope(module, false)
                : GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(project, filename, scope);
        for (VirtualFile file : files) {
            if (file.isValid() && !file.isDirectory() && !isGeneratedOrBuildFile(file)) {
                return file;
            }
        }
        return null;
    }

    public static int getFilePrecedence(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        boolean isProperties = name.endsWith(".properties");
        if (name.equals("bootstrap.properties") || name.equals("bootstrap.yml") || name.equals("bootstrap.yaml")) {
            return isProperties ? 11 : 10;
        }
        if (name.startsWith("bootstrap-")) {
            return isProperties ? 21 : 20;
        }
        if (name.equals("application.properties") || name.equals("application.yml") || name.equals("application.yaml")) {
            return isProperties ? 31 : 30;
        }
        if (name.startsWith("application-")) {
            return isProperties ? 41 : 40;
        }
        return 100;
    }

    public static String getProfileFromFilename(String filename) {
        if (filename == null) return null;
        String name = filename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        if (name.startsWith("application-")) {
            return name.substring("application-".length());
        }
        if (name.startsWith("bootstrap-")) {
            return name.substring("bootstrap-".length());
        }
        return null;
    }

    public static boolean isConfigFileActive(String filename, String activeProfile) {
        String profile = getProfileFromFilename(filename);
        if (profile == null) {
            return true;
        }
        if (activeProfile == null || activeProfile.isBlank()) {
            return false;
        }
        return matchesProfile(profile, activeProfile);
    }

    private static boolean isConfigFile(String filename) {
        if (filename == null) return false;
        String name = filename.toLowerCase(Locale.ROOT);
        return (name.startsWith("application") || name.startsWith("bootstrap")) &&
                (name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    @SuppressWarnings("unchecked")
    private static boolean isDocumentActive(Map<String, Object> doc, String activeProfile) {
        // Spring Boot 2.4+: spring.config.activate.on-profile
        Object springObj = doc.get("spring");
        if (springObj instanceof Map) {
            Map<String, Object> springMap = (Map<String, Object>) springObj;
            Object configObj = springMap.get("config");
            if (configObj instanceof Map) {
                Map<String, Object> configMap = (Map<String, Object>) configObj;
                Object activateObj = configMap.get("activate");
                if (activateObj instanceof Map) {
                    Map<String, Object> actMap = (Map<String, Object>) activateObj;
                    Object onProfile = actMap.get("on-profile");
                    if (onProfile != null) {
                        return matchesProfile(String.valueOf(onProfile), activeProfile);
                    }
                }
            }
            // Spring Boot 2.3 and earlier: spring.profiles
            Object profilesObj = springMap.get("profiles");
            if (profilesObj != null && !(profilesObj instanceof Map)) {
                return matchesProfile(String.valueOf(profilesObj), activeProfile);
            }
        }
        return true;
    }

    private static boolean matchesProfile(String profileSpec, String activeProfile) {
        if (profileSpec == null || profileSpec.isBlank()) return true;
        if (activeProfile == null || activeProfile.isBlank()) return false;
        for (String p : profileSpec.split(",")) {
            if (p.trim().equalsIgnoreCase(activeProfile.trim())) return true;
        }
        return false;
    }

    private Map<String, String> loadPropertiesFromFile(VirtualFile file) {
        return loadPropertiesFromFile(file, "", null);
    }

    private Map<String, String> loadPropertiesFromFile(VirtualFile file, String activeProfile, @Nullable List<String> diagnostics) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!file.isValid()) return result;

        try {
            if (file.getName().endsWith(".properties")) {
                Properties props = new Properties();
                try (InputStreamReader isr = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
                    props.load(isr);
                }
                for (String name : props.stringPropertyNames()) {
                    result.put(name, props.getProperty(name));
                }
            } else {
                Yaml yaml = new Yaml();
                try (InputStream is = file.getInputStream()) {
                    Iterable<Object> docs = yaml.loadAll(is);
                    for (Object docObj : docs) {
                        if (docObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) docObj;
                            if (isDocumentActive(map, activeProfile)) {
                                flattenYamlMap("", map, result);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (diagnostics != null) {
                diagnostics.add("Failed to parse config file " + file.getName() + ": " + e.getMessage());
            }
            LOG.warn("Failed to parse " + file.getPath() + ": " + e.getMessage());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private static void flattenYamlMap(String prefix, Map<String, Object> map, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Map) {
                flattenYamlMap(key, (Map<String, Object>) val, target);
            } else if (val != null) {
                target.put(key, String.valueOf(val));
            }
        }
    }

    private static final Pattern INNERMOST_PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");

    /**
     * Resolves placeholders in the form ${property:default} or ${property}, supporting nested placeholders.
     */
    public static String resolvePlaceholders(String text, Map<String, String> properties, @Nullable List<String> diagnostics) {
        if (text == null || text.isBlank() || !text.contains("${")) {
            return text != null ? text.trim() : "";
        }

        String current = text;
        int maxPasses = 10;
        Set<String> reportedUnresolved = new HashSet<>();

        while (current.contains("${") && maxPasses-- > 0) {
            Matcher matcher = INNERMOST_PLACEHOLDER.matcher(current);
            if (!matcher.find()) break;

            StringBuilder sb = new StringBuilder();
            matcher.reset();
            boolean replacedAny = false;

            while (matcher.find()) {
                String fullExpr = matcher.group(1);
                String key = fullExpr;
                String defaultValue = null;

                int colonIdx = fullExpr.indexOf(':');
                if (colonIdx >= 0) {
                    key = fullExpr.substring(0, colonIdx).trim();
                    defaultValue = fullExpr.substring(colonIdx + 1).trim();
                }

                String val = properties != null ? properties.get(key) : null;
                if (val == null) {
                    val = System.getProperty(key);
                }
                if (val == null) {
                    val = System.getenv(key);
                }

                if (val != null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(val));
                    replacedAny = true;
                } else if (defaultValue != null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(defaultValue));
                    replacedAny = true;
                } else {
                    if (diagnostics != null && reportedUnresolved.add(key)) {
                        diagnostics.add("Unresolved placeholder: ${" + key + "}");
                    }
                    matcher.appendReplacement(sb, Matcher.quoteReplacement("${" + key + "}"));
                }
            }
            matcher.appendTail(sb);
            String next = sb.toString();
            if (!replacedAny || next.equals(current)) {
                break;
            }
            current = next;
        }

        return current;
    }
}
