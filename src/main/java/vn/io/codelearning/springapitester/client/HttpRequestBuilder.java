package vn.io.codelearning.springapitester.client;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MultipartBody;
import okhttp3.Headers;
import vn.io.codelearning.springapitester.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Xây dựng okhttp3.Request từ EndpointModel của chúng ta.
 */
public class HttpRequestBuilder {

    public static Request buildRequest(EndpointModel endpoint, String fullUrlPattern) {
        String urlPath = fullUrlPattern;

        // 1. Thay thế Path Variables (hỗ trợ cả pattern regex)
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.PATH_VARIABLE) {
                String value = RequestValidationUtil.resolveParamValue(param);
                if (param.isRequired() && value.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing required path variable: " + param.getName());
                }
                urlPath = vn.io.codelearning.springapitester.scanner.SpringUrlUtils.replacePathVariable(urlPath, param.getName(), value);
            }
        }

        HttpUrl parsedUrl = HttpUrl.parse(urlPath);
        if (parsedUrl == null) {
            throw new IllegalArgumentException("Invalid URL: " + urlPath);
        }
        HttpUrl.Builder urlBuilder = parsedUrl.newBuilder();

        // 2. Gắn Query Params (?key=value)
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.QUERY_PARAM && param.isEnabled()) {
                String value = RequestValidationUtil.resolveParamValue(param);
                if (param.isRequired() && value.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing required query parameter: " + param.getName());
                }
                // Không gửi param nếu value rỗng để tránh Spring Boot báo lỗi ép kiểu
                if (!value.trim().isEmpty()) {
                    urlBuilder.addQueryParameter(param.getName(), value);
                }
            }
        }

        Request.Builder requestBuilder = new Request.Builder();

        // 3. Xử lý Authentication (AuthConfig)
        AuthConfig auth = endpoint.getAuthConfig();
        if (auth != null) {
            switch (auth.getAuthType()) {
                case BEARER_TOKEN:
                    String token = auth.getBearerToken() != null ? auth.getBearerToken().trim() : "";
                    if (!token.isEmpty()) {
                        requestBuilder.addHeader("Authorization", "Bearer " + token);
                    }
                    break;
                case BASIC_AUTH:
                    String user = auth.getUsername() != null ? auth.getUsername() : "";
                    String pass = auth.getPassword() != null ? auth.getPassword() : "";
                    String creds = user + ":" + pass;
                    String encoded = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
                    requestBuilder.addHeader("Authorization", "Basic " + encoded);
                    break;
                case API_KEY:
                    String keyName = auth.getApiKeyName() != null ? auth.getApiKeyName() : "";
                    String keyValue = auth.getApiKeyValue() != null ? auth.getApiKeyValue() : "";
                    if (auth.isApiKeyInHeader()) {
                        if (!keyName.isEmpty()) requestBuilder.addHeader(keyName, keyValue);
                    } else {
                        // Nhúng vào Query Param thay vì Header
                        if (!keyName.isEmpty()) urlBuilder.addQueryParameter(keyName, keyValue);
                    }
                    break;
                case NO_AUTH:
                case INHERIT:
                default:
                    break;
            }
        }

        requestBuilder.url(urlBuilder.build());

        // 4. Gắn Custom Headers, Header Parameters và Cookie Parameters
        java.util.Map<String, String> cookieParams = new java.util.LinkedHashMap<>();

        if (endpoint.getCustomHeaders() != null) {
            for (HeaderItem header : endpoint.getCustomHeaders()) {
                if (header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
                    RequestValidationUtil.validateHeader(header.getKey(), header.getValue());
                    String val = header.getValue() != null ? header.getValue() : "";
                    if ("Cookie".equalsIgnoreCase(header.getKey())) {
                        cookieParams.putAll(RequestValidationUtil.parseCookieHeader(val));
                    } else {
                        requestBuilder.addHeader(header.getKey(), val);
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
                    requestBuilder.addHeader(param.getName(), value);
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
            requestBuilder.header("Cookie", RequestValidationUtil.formatCookieHeader(cookieParams));
        }

        // 5. Gắn Body và HTTP Method
        HttpMethodEnum method = endpoint.getHttpMethod();
        if (method == null) method = HttpMethodEnum.GET;

        RequestBody body = null;
        if (method == HttpMethodEnum.POST || method == HttpMethodEnum.PUT ||
            method == HttpMethodEnum.PATCH || method == HttpMethodEnum.DELETE) {
            
            if (endpoint.getBodyType() == RequestBodyType.FORM_DATA) {
                // Form Data Builder
                MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM);
                
                boolean hasData = false;
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
                                    RequestBody fileBody = RequestBody.create(file, MediaType.parse(mime));
                                    multipartBuilder.addFormDataPart(key, file.getName(), fileBody);
                                    hasData = true;
                                }
                            } else {
                                if (RequestValidationUtil.isJson(val)) {
                                    RequestBody partBody = RequestBody.create(val, MediaType.parse("application/json; charset=utf-8"));
                                    Headers partHeaders = new Headers.Builder()
                                            .addUnsafeNonAscii("Content-Disposition", "form-data; name=\"" + key + "\"")
                                            .build();
                                    multipartBuilder.addPart(partHeaders, partBody);
                                } else {
                                    multipartBuilder.addFormDataPart(key, val);
                                }
                                hasData = true;
                            }
                        }
                    }
                }
                
                // If form is empty, we must add an empty part or OkHttp will crash. 
                // Alternatively, use an empty RequestBody
                if (!hasData) {
                    body = RequestBody.create("", MediaType.parse("text/plain"));
                } else {
                    body = multipartBuilder.build();
                }
                
            } else {
                // JSON Builder
                String json = endpoint.getRequestBodyJson();
                if (json == null || json.trim().isEmpty()) {
                    json = "{}";
                }
                body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
            }
            
        } else if (method == HttpMethodEnum.GET || method == HttpMethodEnum.HEAD) {
             // OkHttp bắt buộc body phải là null đối với GET/HEAD
             body = null;
        }

        requestBuilder.method(method.toString(), body);

        return requestBuilder.build();
    }
}
