package vn.io.codelearning.springapitester.util;

import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.GatewayEndpointResolution;

/**
 * Calculates effective URLs for endpoints with Spring Cloud Gateway configuration.
 */
public class GatewayUrlCalculator {

    public static String[] calculateFull(EndpointModel endpoint, GatewayConfigReader.GatewayConfig gatewayConfig) {
        if (endpoint == null) {
            return new String[]{"", ""};
        }

        GatewayEndpointResolution resolution = GatewayEndpointResolver.resolve(endpoint, gatewayConfig);
        if (resolution.isRoutable()) {
            return new String[]{resolution.getGatewayBaseUrl(), resolution.getGatewayPath()};
        }

        return new String[]{
                endpoint.getDirectBaseUrl() != null ? endpoint.getDirectBaseUrl() : "",
                endpoint.getPath() != null ? endpoint.getPath() : ""
        };
    }

    public static String calculate(EndpointModel endpoint, GatewayConfigReader.GatewayConfig gatewayConfig) {
        if (endpoint == null) {
            return "";
        }
        GatewayEndpointResolution resolution = GatewayEndpointResolver.resolve(endpoint, gatewayConfig);
        if (resolution.isRoutable()) {
            return resolution.getFullGatewayUrl();
        }
        return resolution.getFullDirectUrl();
    }
}
