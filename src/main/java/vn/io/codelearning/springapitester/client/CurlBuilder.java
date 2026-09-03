package vn.io.codelearning.springapitester.client;

import okhttp3.HttpUrl;
import okhttp3.Request;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.ParameterModel;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;
import vn.io.codelearning.springapitester.model.RequestBodyType;
import vn.io.codelearning.springapitester.state.CredentialStore;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Xây dựng câu lệnh cURL và PowerShell an toàn, chính xác từ EndpointModel hoặc okhttp3.Request.
 */
public class CurlBuilder {

    /**
     * Escape an argument according to POSIX shell single-quoting rules.
     * Every character inside single quotes is treated literally by POSIX shell (bash, sh, zsh).
     * Single quotes within the string are represented as '\''.
     */
    public static String escapeShellArg(String arg) {
        if (arg == null) return "''";
        if (arg.isEmpty()) return "''";
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    /**
     * Escape an argument for PowerShell single-quoting rules.
     */
    public static String escapePowerShellArg(String arg) {
        if (arg == null) return "''";
        if (arg.isEmpty()) return "''";
        return "'" + arg.replace("'", "''") + "'";
    }

    public static boolean isSensitiveQueryParam(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return k.contains("apikey") || k.contains("token")
                || k.contains("secret") || k.contains("password") || k.equals("key") || k.contains("auth");
    }

    public static String buildCurl(EndpointModel endpoint, String fullUrlPattern) {
        return buildCurl(endpoint, fullUrlPattern, false);
    }

    public static String buildCurl(EndpointModel endpoint, String fullUrlPattern, boolean includeCredentials) {
        if (endpoint == null) return "curl";
        Request request = HttpRequestBuilder.buildRequest(endpoint, fullUrlPattern);
        return buildCurl(request, endpoint, includeCredentials);
    }

    public static String buildCurl(Request request, boolean includeCredentials) {
        return buildCurl(request, null, includeCredentials);
    }

    public static String buildCurl(Request request, EndpointModel endpoint, boolean includeCredentials) {
        if (request == null) return "curl";

        StringBuilder curl = new StringBuilder("curl");
        curl.append(" -X ").append(request.method());

        HttpUrl url = request.url();
        String finalUrl = url.toString();
        if (!includeCredentials) {
            HttpUrl.Builder sanitized = url.newBuilder();
            boolean modified = false;
            for (int i = 0; i < url.querySize(); i++) {
                String qName = url.queryParameterName(i);
                if (isSensitiveQueryParam(qName)) {
                    sanitized.setQueryParameter(qName, "[REDACTED]");
                    modified = true;
                }
            }
            if (modified) {
                finalUrl = sanitized.build().toString();
            }
        }
        curl.append(" ").append(escapeShellArg(finalUrl));

        okhttp3.Headers headers = request.headers();
        boolean hasContentType = headers.get("Content-Type") != null;
        if (!hasContentType && request.body() != null && request.body().contentType() != null) {
            if (endpoint == null || endpoint.getBodyType() != RequestBodyType.FORM_DATA) {
                curl.append(" \\\n  -H ").append(escapeShellArg("Content-Type: " + request.body().contentType()));
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String val = headers.value(i);
            if (!includeCredentials && CredentialStore.isSensitiveHeader(name)) {
                val = "[REDACTED]";
            }
            curl.append(" \\\n  -H ").append(escapeShellArg(name + ": " + val));
        }

        if (endpoint != null && endpoint.getBodyType() == RequestBodyType.FORM_DATA) {
            for (ParameterModel param : endpoint.getParameters()) {
                if (!param.isEnabled()) continue;
                if (param.getParamType() == ParamTypeEnum.FORM_DATA ||
                        param.getParamType() == ParamTypeEnum.MULTIPART_FILE ||
                        param.getParamType() == ParamTypeEnum.MODEL_ATTRIBUTE) {
                    String key = param.getName();
                    String val = RequestValidationUtil.resolveParamValue(param);
                    if (key != null && !key.isEmpty() && !val.trim().isEmpty()) {
                        if (param.getParamType() == ParamTypeEnum.MULTIPART_FILE) {
                            List<File> files = RequestValidationUtil.parseFilePaths(val);
                            for (File file : files) {
                                String mime = RequestValidationUtil.detectMimeType(file);
                                curl.append(" \\\n  -F ").append(escapeShellArg(key + "=@" + file.getAbsolutePath() + ";type=" + mime));
                            }
                        } else {
                            if (RequestValidationUtil.isJson(val)) {
                                curl.append(" \\\n  -F ").append(escapeShellArg(key + "=" + val + ";type=application/json"));
                            } else {
                                curl.append(" \\\n  -F ").append(escapeShellArg(key + "=" + val));
                            }
                        }
                    }
                }
            }
        } else if (request.body() != null) {
            String json = endpoint != null ? endpoint.getRequestBodyJson() : "";
            if (json == null || json.trim().isEmpty()) {
                json = "{}";
            }
            curl.append(" \\\n  --data-raw ").append(escapeShellArg(json));
        }

        return curl.toString();
    }

    public static String buildPowerShell(EndpointModel endpoint, String fullUrlPattern, boolean includeCredentials) {
        if (endpoint == null) return "";
        Request request = HttpRequestBuilder.buildRequest(endpoint, fullUrlPattern);
        StringBuilder ps = new StringBuilder("Invoke-RestMethod");
        ps.append(" -Method ").append(request.method());

        HttpUrl url = request.url();
        String finalUrl = url.toString();
        if (!includeCredentials) {
            HttpUrl.Builder sanitized = url.newBuilder();
            boolean modified = false;
            for (int i = 0; i < url.querySize(); i++) {
                String qName = url.queryParameterName(i);
                if (isSensitiveQueryParam(qName)) {
                    sanitized.setQueryParameter(qName, "[REDACTED]");
                    modified = true;
                }
            }
            if (modified) {
                finalUrl = sanitized.build().toString();
            }
        }
        ps.append(" -Uri ").append(escapePowerShellArg(finalUrl));

        okhttp3.Headers headers = request.headers();
        if (headers.size() > 0) {
            ps.append(" -Headers @{");
            boolean first = true;
            for (int i = 0; i < headers.size(); i++) {
                String name = headers.name(i);
                String val = headers.value(i);
                if (!includeCredentials && CredentialStore.isSensitiveHeader(name)) {
                    val = "[REDACTED]";
                }
                if (!first) ps.append("; ");
                ps.append(escapePowerShellArg(name)).append(" = ").append(escapePowerShellArg(val));
                first = false;
            }
            ps.append("}");
        }

        if (endpoint.getBodyType() == RequestBodyType.JSON && endpoint.getRequestBodyJson() != null && !endpoint.getRequestBodyJson().isBlank()) {
            ps.append(" -Body ").append(escapePowerShellArg(endpoint.getRequestBodyJson()));
        }

        return ps.toString();
    }
}
