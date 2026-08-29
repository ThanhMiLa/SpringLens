package vn.io.codelearning.springapitester.util;

import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;

import java.util.List;

public class GatewayUrlCalculator {

    public static String[] calculateFull(EndpointModel endpoint, GatewayConfigReader.GatewayConfig gatewayConfig) {
        if (gatewayConfig == null || endpoint == null) {
            return new String[]{endpoint != null ? endpoint.getDirectBaseUrl() : "", endpoint != null ? endpoint.getPath() : ""};
        }

        String moduleName = endpoint.getModuleName();
        String originalPath = endpoint.getPath();
        String port = gatewayConfig.port;
        String gatewayBase = "http://localhost:" + port;

        // Extract context-path from directBaseUrl
        String contextPath = "";
        if (endpoint.getDirectBaseUrl() != null) {
            String dbUrl = endpoint.getDirectBaseUrl();
            int portIndex = dbUrl.indexOf(":", 7); // skip http:// or https://
            if (portIndex != -1) {
                int slashIndex = dbUrl.indexOf("/", portIndex);
                if (slashIndex != -1) {
                    contextPath = dbUrl.substring(slashIndex);
                }
            }
        }

        // 1. Check if Discovery Locator is enabled
        if (gatewayConfig.discoveryLocatorEnabled) {
            // Default rule: http://localhost:{port}/{service-name}/{context-path}/{path}
            return new String[]{gatewayBase + "/" + moduleName + contextPath, originalPath};
        }

        // 2. Check routes
        for (GatewayRouteModel route : gatewayConfig.routes) {
            // Match route URI or ID
            boolean matchByUriContains = route.getUri() != null && route.getUri().contains(moduleName);
            boolean matchById = route.getId() != null && route.getId().contains(moduleName);
            boolean matchByUriExact = route.getUri() != null && endpoint.getDirectBaseUrl() != null && endpoint.getDirectBaseUrl().startsWith(route.getUri());
            
            if (matchByUriContains || matchById || matchByUriExact) {
                for (String predicate : route.getPathPredicates()) {
                    String prefixPattern = predicate.replace("**", "").replace("*", "");
                    
                    if (prefixPattern.endsWith("/")) {
                        prefixPattern = prefixPattern.substring(0, prefixPattern.length() - 1);
                    }
                    
                    String newPath = originalPath;
                    String effectiveBase = gatewayBase;
                    
                    if (route.getPrefixPath() != null && !route.getPrefixPath().isEmpty()) {
                        if (newPath.startsWith(route.getPrefixPath())) {
                            newPath = newPath.substring(route.getPrefixPath().length());
                        }
                    }
                    
                    if (route.getStripPrefix() > 0) {
                        String[] parts = prefixPattern.split("/");
                        StringBuilder prefixToRestore = new StringBuilder();
                        int partsToRestore = Math.min(route.getStripPrefix(), parts.length - 1);
                        int counted = 0;
                        for (int i = 1; i < parts.length; i++) {
                            if (counted < partsToRestore && !parts[i].isEmpty()) {
                                prefixToRestore.append("/").append(parts[i]);
                                counted++;
                            }
                        }
                        effectiveBase = gatewayBase + prefixToRestore.toString();
                    } else if (route.getRewritePathRegex() != null && route.getRewritePathReplacement() != null) {
                        effectiveBase = gatewayBase + prefixPattern;
                    }
                    
                    return new String[]{effectiveBase + contextPath, newPath};
                }
            }
        }

        // 3. Fallback: just use Gateway Base URL with context path and original path
        return new String[]{gatewayBase + "/" + moduleName + contextPath, originalPath};

    }
    
    public static String calculate(EndpointModel endpoint, GatewayConfigReader.GatewayConfig gatewayConfig) {
        String[] parts = calculateFull(endpoint, gatewayConfig);
        String full = parts[0] + parts[1];
        return full.replace("//", "/").replace("http:/l", "http://l");
    }
}
