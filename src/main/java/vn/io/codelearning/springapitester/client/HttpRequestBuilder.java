package vn.io.codelearning.springapitester.client;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MultipartBody;
import vn.io.codelearning.springapitester.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Xây dựng okhttp3.Request từ EndpointModel của chúng ta.
 */
public class HttpRequestBuilder {

    public static Request buildRequest(EndpointModel endpoint, String fullUrlPattern) {
        String urlPath = fullUrlPattern;

        // 1. Thay thế Path Variables
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.PATH_VARIABLE) {
                // Thay thế cả ở URL path
                String value = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                urlPath = urlPath.replace("{" + param.getName() + "}", value);
            }
        }

        HttpUrl parsedUrl = HttpUrl.parse(urlPath);
        if (parsedUrl == null) {
            throw new IllegalArgumentException("Invalid URL: " + urlPath);
        }
        HttpUrl.Builder urlBuilder = parsedUrl.newBuilder();

        // 2. Gắn Query Params (?key=value)
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.QUERY_PARAM) {
                String value = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                // Không gửi param nếu value rỗng để tránh Spring Boot báo lỗi ép kiểu (vd Enum, Integer)
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
                    String token = auth.getBearerToken() != null ? auth.getBearerToken() : "";
                    requestBuilder.addHeader("Authorization", "Bearer " + token);
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

        // 4. Gắn Custom Headers
        if (endpoint.getCustomHeaders() != null) {
            for (HeaderItem header : endpoint.getCustomHeaders()) {
                // Chỉ lấy những header nào đang được tick (isEnabled == true)
                if (header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
                    String val = header.getValue() != null ? header.getValue() : "";
                    requestBuilder.addHeader(header.getKey(), val);
                }
            }
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
                    if (param.getParamType() == ParamTypeEnum.FORM_DATA || 
                        param.getParamType() == ParamTypeEnum.MULTIPART_FILE ||
                        param.getParamType() == ParamTypeEnum.MODEL_ATTRIBUTE) {
                        
                        String key = param.getName();
                        String val = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                        if (key != null && !key.isEmpty()) {
                            if (!val.trim().isEmpty()) {
                                if (param.getParamType() == ParamTypeEnum.MULTIPART_FILE) {
                                    // Giả lập một file upload với nội dung là text user nhập vào
                                    RequestBody fileBody = RequestBody.create(val, MediaType.parse("application/octet-stream"));
                                    multipartBuilder.addFormDataPart(key, "dummy.txt", fileBody);
                                } else {
                                    // Gửi text bình thường
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
