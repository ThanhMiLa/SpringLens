package vn.io.codelearning.springapitester.client;

import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HeaderItem;
import vn.io.codelearning.springapitester.model.ParameterModel;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;
import vn.io.codelearning.springapitester.model.RequestBodyType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CurlBuilder {

    public static String buildCurl(EndpointModel endpoint, String fullUrlPattern) {
        StringBuilder curl = new StringBuilder("curl");

        // Request Method
        if (endpoint.getHttpMethod() != null) {
            curl.append(" -X ").append(endpoint.getHttpMethod().name());
        }

        // Build URL
        String urlPath = fullUrlPattern;
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.PATH_VARIABLE) {
                String value = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                urlPath = urlPath.replace("{" + param.getName() + "}", value);
            }
        }
        
        StringBuilder queryParams = new StringBuilder();
        boolean firstQuery = true;
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.QUERY_PARAM) {
                String value = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                if (!value.trim().isEmpty()) {
                    queryParams.append(firstQuery ? "?" : "&")
                            .append(param.getName()).append("=").append(value);
                    firstQuery = false;
                }
            }
        }
        
        // Custom Auth Query Param for API Key
        if (endpoint.getAuthConfig() != null && 
            endpoint.getAuthConfig().getAuthType() == vn.io.codelearning.springapitester.model.AuthTypeEnum.API_KEY && 
            !endpoint.getAuthConfig().isApiKeyInHeader()) {
            
            String keyName = endpoint.getAuthConfig().getApiKeyName() != null ? endpoint.getAuthConfig().getApiKeyName() : "";
            String keyValue = endpoint.getAuthConfig().getApiKeyValue() != null ? endpoint.getAuthConfig().getApiKeyValue() : "";
            if (!keyName.isEmpty()) {
                queryParams.append(firstQuery ? "?" : "&").append(keyName).append("=").append(keyValue);
            }
        }

        curl.append(" \"").append(urlPath).append(queryParams).append("\"");

        // Headers
        if (endpoint.getCustomHeaders() != null) {
            for (HeaderItem header : endpoint.getCustomHeaders()) {
                if (header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
                    curl.append(" \\\n  -H \"").append(header.getKey()).append(": ").append(header.getValue() != null ? header.getValue() : "").append("\"");
                }
            }
        }

        // Auth Headers
        if (endpoint.getAuthConfig() != null) {
            switch (endpoint.getAuthConfig().getAuthType()) {
                case BEARER_TOKEN:
                    String token = endpoint.getAuthConfig().getBearerToken() != null ? endpoint.getAuthConfig().getBearerToken().trim() : "";
                    if (!token.isEmpty()) {
                        curl.append(" \\\n  -H \"Authorization: Bearer ").append(token).append("\"");
                    }
                    break;
                case BASIC_AUTH:
                    String user = endpoint.getAuthConfig().getUsername() != null ? endpoint.getAuthConfig().getUsername() : "";
                    String pass = endpoint.getAuthConfig().getPassword() != null ? endpoint.getAuthConfig().getPassword() : "";
                    String encoded = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
                    curl.append(" \\\n  -H \"Authorization: Basic ").append(encoded).append("\"");
                    break;
                case API_KEY:
                    if (endpoint.getAuthConfig().isApiKeyInHeader()) {
                        String kName = endpoint.getAuthConfig().getApiKeyName() != null ? endpoint.getAuthConfig().getApiKeyName() : "";
                        String kVal = endpoint.getAuthConfig().getApiKeyValue() != null ? endpoint.getAuthConfig().getApiKeyValue() : "";
                        if (!kName.isEmpty()) {
                            curl.append(" \\\n  -H \"").append(kName).append(": ").append(kVal).append("\"");
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        // Body
        if (endpoint.getHttpMethod() != vn.io.codelearning.springapitester.model.HttpMethodEnum.GET) {
            if (endpoint.getBodyType() == RequestBodyType.FORM_DATA) {
                for (ParameterModel param : endpoint.getParameters()) {
                    if (param.getParamType() == ParamTypeEnum.FORM_DATA || 
                        param.getParamType() == ParamTypeEnum.MULTIPART_FILE ||
                        param.getParamType() == ParamTypeEnum.MODEL_ATTRIBUTE) {
                        
                        String key = param.getName();
                        String val = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                        if (key != null && !key.isEmpty() && !val.trim().isEmpty()) {
                            if (param.getParamType() == ParamTypeEnum.MULTIPART_FILE) {
                                curl.append(" \\\n  -F \"").append(key).append("=@").append(val).append("\"");
                            } else {
                                curl.append(" \\\n  -F \"").append(key).append("=").append(val).append("\"");
                            }
                        }
                    }
                }
            } else {
                String json = endpoint.getRequestBodyJson();
                if (json != null && !json.trim().isEmpty()) {
                    curl.append(" \\\n  -H \"Content-Type: application/json\"");
                    // Escape single quotes for bash
                    String escapedJson = json.replace("'", "'\\''");
                    curl.append(" \\\n  -d '").append(escapedJson).append("'");
                }
            }
        }

        return curl.toString();
    }
}
