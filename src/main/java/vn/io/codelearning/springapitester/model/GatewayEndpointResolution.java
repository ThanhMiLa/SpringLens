package vn.io.codelearning.springapitester.model;

/**
 * Encapsulates the result of resolving an endpoint against Spring Cloud Gateway routes.
 */
public class GatewayEndpointResolution {

    private final GatewayRouteStatus status;
    private final GatewayRouteModel matchedRoute;
    private final String gatewayBaseUrl;
    private final String gatewayPath;
    private final String directBaseUrl;
    private final String directPath;
    private final String filterDescription;

    public GatewayEndpointResolution(GatewayRouteStatus status,
                                     GatewayRouteModel matchedRoute,
                                     String gatewayBaseUrl,
                                     String gatewayPath,
                                     String directBaseUrl,
                                     String directPath,
                                     String filterDescription) {
        this.status = status != null ? status : GatewayRouteStatus.NOT_ROUTABLE;
        this.matchedRoute = matchedRoute;
        this.gatewayBaseUrl = gatewayBaseUrl != null ? gatewayBaseUrl : "";
        this.gatewayPath = gatewayPath != null ? gatewayPath : "";
        this.directBaseUrl = directBaseUrl != null ? directBaseUrl : "";
        this.directPath = directPath != null ? directPath : "";
        this.filterDescription = filterDescription != null ? filterDescription : "";
    }

    public static GatewayEndpointResolution routable(GatewayRouteModel route,
                                                     String gatewayBaseUrl,
                                                     String gatewayPath,
                                                     String directBaseUrl,
                                                     String directPath,
                                                     String filterDescription) {
        return new GatewayEndpointResolution(GatewayRouteStatus.ROUTABLE, route, gatewayBaseUrl, gatewayPath, directBaseUrl, directPath, filterDescription);
    }

    public static GatewayEndpointResolution notRoutable(String directBaseUrl, String directPath) {
        return new GatewayEndpointResolution(GatewayRouteStatus.NOT_ROUTABLE, null, "", "", directBaseUrl, directPath, "");
    }

    public static GatewayEndpointResolution unresolved(String directBaseUrl, String directPath, String reason) {
        return new GatewayEndpointResolution(GatewayRouteStatus.UNRESOLVED, null, "", "", directBaseUrl, directPath, reason);
    }

    public GatewayRouteStatus getStatus() {
        return status;
    }

    public boolean isRoutable() {
        return status == GatewayRouteStatus.ROUTABLE;
    }

    public GatewayRouteModel getMatchedRoute() {
        return matchedRoute;
    }

    public String getGatewayBaseUrl() {
        return gatewayBaseUrl;
    }

    public String getGatewayPath() {
        return gatewayPath;
    }

    public String getDirectBaseUrl() {
        return directBaseUrl;
    }

    public String getDirectPath() {
        return directPath;
    }

    public String getFilterDescription() {
        return filterDescription;
    }

    public String getFullGatewayUrl() {
        if (!isRoutable()) {
            return getFullDirectUrl();
        }
        return combineBaseAndPath(gatewayBaseUrl, gatewayPath);
    }

    public String getFullDirectUrl() {
        return combineBaseAndPath(directBaseUrl, directPath);
    }

    private static String combineBaseAndPath(String base, String path) {
        String baseClean = base != null ? base.trim() : "";
        String pathClean = path != null ? path.trim() : "";
        if (baseClean.endsWith("/") && pathClean.startsWith("/")) {
            pathClean = pathClean.substring(1);
        } else if (!baseClean.isEmpty() && !baseClean.endsWith("/") && !pathClean.isEmpty() && !pathClean.startsWith("/")) {
            pathClean = "/" + pathClean;
        }
        return baseClean + pathClean;
    }
}
