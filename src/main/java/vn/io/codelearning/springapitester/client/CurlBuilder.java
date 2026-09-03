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

        // Build URL (hỗ trợ cả pattern regex)
        String urlPath = fullUrlPattern;
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.PATH_VARIABLE) {
                String value = RequestValidationUtil.resolveParamValue(param);
                if (param.isRequired() && value.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing required path variable: " + param.getName());
                }
                urlPath = vn.io.codelearning.springapitester.scanner.SpringUrlUtils.replacePathVariable(urlPath, param.getName(), value);
            }
        }
        
        StringBuilder queryParams = new StringBuilder();
        boolean firstQuery = true;
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.QUERY_PARAM && param.isEnabled()) {
                String value = RequestValidationUtil.resolveParamValue(param);
                if (param.isRequired() && value.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing required query parameter: " + param.getName());
                }
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
        java.util.Map<String, String> cookieParams = new java.util.LinkedHashMap<>();

        if (endpoint.getCustomHeaders() != null) {
            for (HeaderItem header : endpoint.getCustomHeaders()) {
                if (header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
                    RequestValidationUtil.validateHeader(header.getKey(), header.getValue());
                    String val = header.getValue() != null ? header.getValue() : "";
                    if ("Cookie".equalsIgnoreCase(header.getKey())) {
                        cookieParams.putAll(RequestValidationUtil.parseCookieHeader(val));
                    } else {
                        curl.append(" \\\n  -H \"").append(header.getKey()).append(": ").append(val).append("\"");
                    }
                }
            }
        }

        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.HEADER && param.isEnabled()) {
                String value = RequestValidationUtil.resolveParamValue(param);
                if (param.isRequired() && value.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing required header: " + param.getName());
                }
                if (!value.trim().isEmpty()) {
                    RequestValidationUtil.validateHeader(param.getName(), value);
                    curl.append(" \\\n  -H \"").append(param.getName()).append(": ").append(value).append("\"");
                }
            }
        }

        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.COOKIE && param.isEnabled()) {
                String value = RequestValidationUtil.resolveParamValue(param);
                if (param.isRequired() && value.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing required cookie: " + param.getName());
                }
                if (!value.trim().isEmpty()) {
                    RequestValidationUtil.validateCookie(param.getName(), value);
                    cookieParams.put(param.getName(), value);
                }
            }
        }

        if (!cookieParams.isEmpty()) {
            curl.append(" \\\n  -H \"Cookie: ").append(RequestValidationUtil.formatCookieHeader(cookieParams)).append("\"");
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
                    if (!param.isEnabled()) continue;
                    if (param.getParamType() == ParamTypeEnum.FORM_DATA || 
                        param.getParamType() == ParamTypeEnum.MULTIPART_FILE ||
                        param.getParamType() == ParamTypeEnum.MODEL_ATTRIBUTE) {
                        
                        String key = param.getName();
                        String val = RequestValidationUtil.resolveParamValue(param);
                        if (param.isRequired() && val.trim().isEmpty()) {
                            throw new IllegalArgumentException("Missing required form parameter: " + key);
                        }
                        if (key != null && !key.isEmpty() && !val.trim().isEmpty()) {
                            if (param.getParamType() == ParamTypeEnum.MULTIPART_FILE) {
                                java.util.List<java.io.File> files = RequestValidationUtil.parseFilePaths(val);
                                if (files.isEmpty() && param.isRequired()) {
                                    throw new IllegalArgumentException("Missing required file for parameter: " + key);
                                }
                                for (java.io.File file : files) {
                                    if (!file.exists() || !file.isFile()) {
                                        throw new IllegalArgumentException("File not found or not a valid file: " + file.getPath() + " for parameter: " + key);
                                    }
                                    String mime = RequestValidationUtil.detectMimeType(file);
                                    curl.append(" \\\n  -F \"").append(key).append("=@").append(file.getAbsolutePath()).append(";type=").append(mime).append("\"");
                                }
                            } else {
                                if (RequestValidationUtil.isJson(val)) {
                                    curl.append(" \\\n  -F \"").append(key).append("=").append(val.replace("\"", "\\\"")).append(";type=application/json\"");
                                } else {
                                    curl.append(" \\\n  -F \"").append(key).append("=").append(val).append("\"");
                                }
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
