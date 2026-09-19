package vn.io.codelearning.springapitester.util;

import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.GatewayEndpointResolution;
import vn.io.codelearning.springapitester.model.GatewayRouteModel;
import vn.io.codelearning.springapitester.model.GatewayRouteStatus;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Resolves whether an endpoint is exposed through Spring Cloud Gateway routes,
 * and determines the effective gateway base URL and public path.
 */
public final class GatewayEndpointResolver {

    private static final Pattern REWRITE_SEGMENT_REGEX = Pattern.compile("^/?(.*)/\\(\\?<segment>\\.\\*\\)$");

    private GatewayEndpointResolver() {
    }

    public static GatewayEndpointResolution resolve(EndpointModel endpoint, GatewayConfigReader.GatewayConfig gatewayConfig) {
        if (endpoint == null) {
            return GatewayEndpointResolution.notRoutable("", "");
        }

        String directBaseUrl = endpoint.getDirectBaseUrl() != null ? endpoint.getDirectBaseUrl().trim() : "";
        String directPath = endpoint.getPath() != null ? endpoint.getPath().trim() : "";

        if (endpoint.isManual() || endpoint.isAbsoluteUrl() || !hasGatewayConfiguration(gatewayConfig)) {
            return GatewayEndpointResolution.notRoutable(directBaseUrl, directPath);
        }

        String port = (gatewayConfig.port != null && !gatewayConfig.port.isBlank()) ? gatewayConfig.port.trim() : "8080";
        String gatewayBase = "http://localhost:" + port;
        String contextPath = extractContextPath(directBaseUrl);
        String downstreamPath = combinePaths(contextPath, directPath);

        // 1. Discovery locator convention
        if (gatewayConfig.discoveryLocatorEnabled) {
            String moduleName = endpoint.getModuleName() != null ? endpoint.getModuleName().trim() : "";
            if (moduleName.isEmpty()) {
                return GatewayEndpointResolution.unresolved(directBaseUrl, directPath, "Missing service name for discovery locator");
            }
            String mod = !moduleName.isEmpty() ? "/" + moduleName : "";
            String effectiveGatewayBase = gatewayBase + mod + contextPath;
            return GatewayEndpointResolution.routable(null, effectiveGatewayBase, directPath, directBaseUrl, directPath, "discovery.locator");
        }

        // 2. Route matching
        if (gatewayConfig.routes == null || gatewayConfig.routes.isEmpty()) {
            return GatewayEndpointResolution.notRoutable(directBaseUrl, directPath);
        }

        boolean hasUnresolvedFilter = false;

        for (GatewayRouteModel route : gatewayConfig.routes) {
            if (!isRouteTargetingService(route, endpoint)) {
                continue;
            }

            List<String> predicates = route.getPathPredicates();
            // Route with no path predicates targets all endpoints of this service
            if (predicates == null || predicates.isEmpty()) {
                return GatewayEndpointResolution.routable(route, gatewayBase + contextPath, directPath, directBaseUrl, directPath, "all-paths");
            }

            // Filter: StripPrefix
            if (route.getStripPrefix() > 0) {
                int stripCount = route.getStripPrefix();
                for (String predicate : predicates) {
                    String prefixToRestore = extractStrippedPrefix(predicate, stripCount);
                    if (prefixToRestore == null) {
                        hasUnresolvedFilter = true;
                    } else if (matchPathPattern(predicate, combinePaths(prefixToRestore, downstreamPath))) {
                        String publicPath = directPath;
                        String effectiveBase = gatewayBase + prefixToRestore + contextPath;
                        return GatewayEndpointResolution.routable(route, effectiveBase, publicPath, directBaseUrl, directPath, "StripPrefix=" + stripCount);
                    }
                }
            }
            // Filter: PrefixPath
            else if (route.getPrefixPath() != null && !route.getPrefixPath().trim().isEmpty()) {
                String prefixPath = normalizePath(route.getPrefixPath());
                if (downstreamPath.equals(prefixPath) || downstreamPath.startsWith(prefixPath + "/")) {
                    String unprefixedPath = downstreamPath.substring(prefixPath.length());
                    if (!unprefixedPath.startsWith("/")) {
                        unprefixedPath = "/" + unprefixedPath;
                    }
                    for (String predicate : predicates) {
                        if (matchPathPattern(predicate, unprefixedPath) || matchPathPattern(predicate, downstreamPath)) {
                            return GatewayEndpointResolution.routable(route, gatewayBase, unprefixedPath, directBaseUrl, directPath, "PrefixPath=" + prefixPath);
                        }
                    }
                }
            }
            // Filter: RewritePath
            else if (route.getRewritePathRegex() != null && route.getRewritePathReplacement() != null) {
                String rewritePrefix = tryExtractRewritePrefix(route.getRewritePathRegex(), route.getRewritePathReplacement());
                String reversedPublicPath = tryReverseRewritePath(route.getRewritePathRegex(), route.getRewritePathReplacement(), downstreamPath);
                if (reversedPublicPath != null && rewritePrefix != null) {
                    for (String predicate : predicates) {
                        if (matchPathPattern(predicate, reversedPublicPath)) {
                            String effectiveBase = gatewayBase + rewritePrefix + contextPath;
                            return GatewayEndpointResolution.routable(route, effectiveBase, directPath, directBaseUrl, directPath, "RewritePath");
                        }
                    }
                } else {
                    hasUnresolvedFilter = true;
                }
            }
            // No path-altering filters (Direct / Standard matching)
            else {
                for (String predicate : predicates) {
                    if (matchPathPattern(predicate, downstreamPath)) {
                        return GatewayEndpointResolution.routable(route, gatewayBase + contextPath, directPath, directBaseUrl, directPath, "standard");
                    }
                }
            }
        }

        if (hasUnresolvedFilter) {
            return GatewayEndpointResolution.unresolved(directBaseUrl, directPath, "Unsupported route filter rewrite");
        }

        return GatewayEndpointResolution.notRoutable(directBaseUrl, directPath);
    }

    public static boolean hasGatewayConfiguration(GatewayConfigReader.GatewayConfig gatewayConfig) {
        return gatewayConfig != null && (gatewayConfig.gatewayDetected
                || gatewayConfig.discoveryLocatorEnabled
                || (gatewayConfig.routes != null && !gatewayConfig.routes.isEmpty()));
    }

    public static boolean isRouteTargetingService(GatewayRouteModel route, EndpointModel endpoint) {
        if (route == null || endpoint == null) return false;

        String endpointModuleName = normalizeServiceName(endpoint.getModuleName());
        String routeId = normalizeServiceName(route.getId());
        String routeUri = route.getUri() != null ? route.getUri().trim() : "";
        String routeUriLower = routeUri.toLowerCase(Locale.ROOT);
        String directBaseUrl = endpoint.getDirectBaseUrl() != null ? endpoint.getDirectBaseUrl().trim() : "";

        // 1. Direct Base URL match (http/https)
        if (!routeUri.isEmpty() && (routeUriLower.startsWith("http://") || routeUriLower.startsWith("https://"))) {
            if (!directBaseUrl.isEmpty()) {
                try {
                    URI directUri = URI.create(directBaseUrl);
                    URI rUri = URI.create(routeUri);
                    if (directUri.getHost() != null && directUri.getHost().equalsIgnoreCase(rUri.getHost())
                            && directUri.getPort() == rUri.getPort()) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. Loadbalancer URI match (lb://service-name)
        if (routeUriLower.startsWith("lb://")) {
            String lbService = normalizeServiceName(routeUri.substring(5));
            if (matchesServiceName(endpointModuleName, lbService)) {
                return true;
            }
        }

        // 3. Route ID match
        if (!routeId.isEmpty() && matchesServiceName(endpointModuleName, routeId)) {
            return true;
        }

        return false;
    }

    public static boolean matchesServiceName(String name1, String name2) {
        if (name1 == null || name2 == null || name1.isEmpty() || name2.isEmpty()) return false;
        if (name1.equals(name2)) return true;

        String base1 = stripServiceAffixes(name1);
        String base2 = stripServiceAffixes(name2);
        return !base1.isEmpty() && base1.equals(base2);
    }

    private static String stripServiceAffixes(String name) {
        String res = name;
        if (res.startsWith("route-")) {
            res = res.substring(6);
        }
        if (res.endsWith("-service")) {
            res = res.substring(0, res.length() - 8);
        }
        return res;
    }

    public static String normalizeServiceName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        String p = path.trim().replaceAll("/+", "/");
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private static String combinePaths(String prefix, String path) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "" : normalizePath(prefix);
        String normalizedPath = normalizePath(path);
        if (normalizedPrefix.isEmpty() || "/".equals(normalizedPrefix)) {
            return normalizedPath;
        }
        if ("/".equals(normalizedPath)) {
            return normalizedPrefix;
        }
        return normalizedPrefix + normalizedPath;
    }

    public static boolean matchPathPattern(String pattern, String path) {
        if (pattern == null || path == null) return false;

        String normPattern = normalizePath(pattern);
        String normPath = normalizePath(path);

        if (normPattern.equals(normPath)) return true;

        if (normPattern.contains(",")) {
            for (String subPattern : normPattern.split(",")) {
                if (matchPathPattern(subPattern.trim(), path)) return true;
            }
            return false;
        }

        String cleanPath = normPath.replaceAll("\\{[^}]+\\}", "__VAR__");

        StringBuilder regex = new StringBuilder("^");
        int i = 0;
        int len = normPattern.length();
        while (i < len) {
            if (normPattern.charAt(i) == '/' && i + 2 < len
                    && normPattern.charAt(i + 1) == '*' && normPattern.charAt(i + 2) == '*'
                    && i + 3 == len) {
                // Suffix "/**": matches base without trailing slash, or base followed by "/" and anything
                regex.append("(/.*)?");
                i += 3;
                break;
            }

            char c = normPattern.charAt(i);
            if (c == '*' && i + 1 < len && normPattern.charAt(i + 1) == '*') {
                regex.append(".*");
                i += 2;
            } else if (c == '*') {
                regex.append("[^/]*");
                i++;
            } else if (c == '{') {
                int close = normPattern.indexOf('}', i);
                if (close != -1) {
                    regex.append("[^/]+");
                    i = close + 1;
                } else {
                    regex.append("\\{");
                    i++;
                }
            } else if (c == '?') {
                regex.append("[^/]");
                i++;
            } else if (".[]()\\+^$|".indexOf(c) != -1) {
                regex.append('\\').append(c);
                i++;
            } else {
                regex.append(c);
                i++;
            }
        }
        regex.append("/?$");

        try {
            return Pattern.compile(regex.toString()).matcher(cleanPath).matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static String extractStrippedPrefix(String predicate, int stripCount) {
        String[] parts = normalizePath(predicate).split("/");
        StringBuilder prefix = new StringBuilder();
        int counted = 0;
        for (int i = 1; i < parts.length; i++) {
            if (counted < stripCount && !parts[i].isEmpty()) {
                if (parts[i].contains("*") || parts[i].contains("?") || parts[i].contains("{")) {
                    return null;
                }
                prefix.append("/").append(parts[i]);
                counted++;
            }
        }
        return counted == stripCount ? prefix.toString() : null;
    }

    private static String tryReverseRewritePath(String regex, String replacement, String directPath) {
        if (regex == null || replacement == null || directPath == null) return null;
        String normDirect = normalizePath(directPath);

        // Pattern 1: regex = "/prefix/(?<segment>.*)", replacement = "/${segment}"
        Matcher m1 = REWRITE_SEGMENT_REGEX.matcher(regex);
        if (m1.matches() && ("/${segment}".equals(replacement) || "/$1".equals(replacement))) {
            String prefix = normalizePath(m1.group(1));
            return prefix + normDirect;
        }

        // Pattern 2: Simple prefix replacement: regex = "/prefix/(.*)", replacement = "/${1}"
        if (regex.endsWith("/(.*)") && ("/$1".equals(replacement) || "/${1}".equals(replacement))) {
            String prefix = normalizePath(regex.substring(0, regex.length() - 5));
            return prefix + normDirect;
        }

        return null;
    }

    private static String tryExtractRewritePrefix(String regex, String replacement) {
        if (regex == null || replacement == null) return null;
        Matcher m1 = REWRITE_SEGMENT_REGEX.matcher(regex);
        if (m1.matches() && ("/${segment}".equals(replacement) || "/$1".equals(replacement))) {
            return normalizePath(m1.group(1));
        }
        if (regex.endsWith("/(.*)") && ("/$1".equals(replacement) || "/${1}".equals(replacement))) {
            return normalizePath(regex.substring(0, regex.length() - 5));
        }
        return null;
    }

    public static String extractContextPath(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            String path = URI.create(url).getPath();
            return path != null && !path.isBlank() && !"/".equals(path) ? normalizePath(path) : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
