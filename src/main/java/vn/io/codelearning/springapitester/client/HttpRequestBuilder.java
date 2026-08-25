package vn.io.codelearning.springapitester.client;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import vn.io.codelearning.springapitester.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Xây dựng okhttp3.Request từ EndpointModel của chúng ta.
 */
public class HttpRequestBuilder {

    public static Request buildRequest(EndpointModel endpoint, String baseUrl) throws Exception {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL cannot be empty");
        }

        // 1. Phân giải URL (Path Variables)
        String urlPath = endpoint.getPath();
        if (urlPath == null) urlPath = "";

        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.PATH_VARIABLE) {
                String value = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                urlPath = urlPath.replace("{" + param.getName() + "}", value);
            }
        }

        // Tạo HttpUrl.Builder an toàn (tránh lỗi duplicate slash)
        String fullUrl = baseUrl;
        if (fullUrl.endsWith("/") && urlPath.startsWith("/")) {
            fullUrl = fullUrl.substring(0, fullUrl.length() - 1) + urlPath;
        } else if (!fullUrl.endsWith("/") && !urlPath.startsWith("/")) {
            fullUrl = fullUrl + "/" + urlPath;
        } else {
            fullUrl = fullUrl + urlPath;
        }

        HttpUrl parsedUrl = HttpUrl.parse(fullUrl);
        if (parsedUrl == null) {
            throw new IllegalArgumentException("Invalid URL: " + fullUrl);
        }
        HttpUrl.Builder urlBuilder = parsedUrl.newBuilder();

        // 2. Gắn Query Params (?key=value)
        for (ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() == ParamTypeEnum.QUERY_PARAM) {
                String value = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                urlBuilder.addQueryParameter(param.getName(), value);
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
            
            String json = endpoint.getRequestBodyJson();
            if (json == null || json.trim().isEmpty()) {
                json = "{}";
            }
            body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
            
        } else if (method == HttpMethodEnum.GET || method == HttpMethodEnum.HEAD) {
             // OkHttp bắt buộc body phải là null đối với GET/HEAD
             body = null;
        }

        requestBuilder.method(method.toString(), body);

        return requestBuilder.build();
    }
}
