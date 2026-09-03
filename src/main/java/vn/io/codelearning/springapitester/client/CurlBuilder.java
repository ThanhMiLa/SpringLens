package vn.io.codelearning.springapitester.client;

import okhttp3.HttpUrl;
import okhttp3.Request;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.MultipartPartModel;
import vn.io.codelearning.springapitester.state.CredentialStore;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Xây dựng câu lệnh cURL, PowerShell, và Windows CMD an toàn, chính xác từ ResolvedRequest hoặc EndpointModel.
 */
public class CurlBuilder {

    /**
     * Escape an argument according to POSIX shell single-quoting rules.
     */
    public static String escapeShellArg(String arg) {
        if (arg == null || arg.isEmpty()) return "''";
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    /**
     * Escape an argument for PowerShell single-quoting rules.
     */
    public static String escapePowerShellArg(String arg) {
        if (arg == null || arg.isEmpty()) return "''";
        return "'" + arg.replace("'", "''") + "'";
    }

    /**
     * Escape an argument for Windows Command Prompt (cmd.exe) double-quoting rules.
     */
    public static String escapeCmdArg(String arg) {
        if (arg == null || arg.isEmpty()) return "\"\"";
        String escaped = arg.replace("\\", "\\\\").replace("\"", "\\\"").replace("%", "%%");
        return "\"" + escaped + "\"";
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
        ResolvedRequest resolved = HttpRequestBuilder.resolveRequest(endpoint, fullUrlPattern);
        return buildCurl(resolved, includeCredentials);
    }

    public static String buildCurl(Request request, boolean includeCredentials) {
        return buildCurl(request, null, includeCredentials);
    }

    public static String buildCurl(Request request, EndpointModel endpoint, boolean includeCredentials) {
        if (request == null) return "curl";
        if (endpoint != null) {
            ResolvedRequest resolved = HttpRequestBuilder.resolveRequest(endpoint, request.url().toString());
            return buildCurl(resolved, includeCredentials);
        }
        StringBuilder curl = new StringBuilder("curl -X ").append(request.method());
        curl.append(" ").append(escapeShellArg(sanitizeUrl(request.url(), includeCredentials)));
        for (int i = 0; i < request.headers().size(); i++) {
            String name = request.headers().name(i);
            String val = (!includeCredentials && CredentialStore.isSensitiveHeader(name))
                    ? "[REDACTED]" : request.headers().value(i);
            curl.append(" \\\n  -H ").append(escapeShellArg(name + ": " + val));
        }
        if (request.body() != null) {
            try {
                okio.Buffer buffer = new okio.Buffer();
                request.body().writeTo(buffer);
                String bodyStr = buffer.readUtf8();
                if (!bodyStr.isEmpty()) {
                    curl.append(" \\\n  --data-raw ").append(escapeShellArg(bodyStr));
                }
            } catch (Exception ignored) {
            }
        }
        return curl.toString();
    }

    public static String buildCurl(ResolvedRequest resolved, boolean includeCredentials) {
        if (resolved == null) return "curl";
        StringBuilder curl = new StringBuilder("curl -X ").append(resolved.getMethod().name());
        curl.append(" ").append(escapeShellArg(sanitizeUrl(resolved.getUrl(), includeCredentials)));

        for (Map.Entry<String, String> header : resolved.getHeaderEntries()) {
            String val = (!includeCredentials && CredentialStore.isSensitiveHeader(header.getKey()))
                    ? "[REDACTED]" : header.getValue();
            curl.append(" \\\n  -H ").append(escapeShellArg(header.getKey() + ": " + val));
        }

        boolean hasContentType = resolved.getHeaders().containsKey("Content-Type");
        if (!hasContentType && resolved.getBody() != null && resolved.getBody().contentType() != null && resolved.getMultipartParts().isEmpty()) {
            curl.append(" \\\n  -H ").append(escapeShellArg("Content-Type: " + resolved.getBody().contentType()));
        }

        if (!resolved.getMultipartParts().isEmpty()) {
            for (MultipartPartModel part : resolved.getMultipartParts()) {
                if (part.isFile()) {
                    String mime = part.getContentType() != null ? ";type=" + part.getContentType() : "";
                    curl.append(" \\\n  -F ").append(escapeShellArg(part.getName() + "=@" + part.getFile().getAbsolutePath() + mime));
                } else if (part.isJson()) {
                    curl.append(" \\\n  -F ").append(escapeShellArg(part.getName() + "=" + part.getTextValue() + ";type=application/json"));
                } else {
                    curl.append(" \\\n  -F ").append(escapeShellArg(part.getName() + "=" + part.getTextValue()));
                }
            }
        } else if (resolved.getBody() != null) {
            String bodyStr = new String(resolved.getRawBodyBytes(), StandardCharsets.UTF_8);
            if (!bodyStr.isEmpty()) {
                curl.append(" \\\n  --data-raw ").append(escapeShellArg(bodyStr));
            }
        }

        return curl.toString();
    }

    public static String buildPowerShell(EndpointModel endpoint, String fullUrlPattern, boolean includeCredentials) {
        if (endpoint == null) return "";
        ResolvedRequest resolved = HttpRequestBuilder.resolveRequest(endpoint, fullUrlPattern);
        return buildPowerShell(resolved, includeCredentials);
    }

    public static String buildPowerShell(ResolvedRequest resolved, boolean includeCredentials) {
        if (resolved == null) return "";
        StringBuilder ps = new StringBuilder();

        if (!resolved.getMultipartParts().isEmpty()) {
            ps.append("$form = @{\n");
            for (MultipartPartModel part : resolved.getMultipartParts()) {
                if (part.isFile()) {
                    ps.append("  ").append(escapePowerShellArg(part.getName())).append(" = Get-Item ")
                            .append(escapePowerShellArg(part.getFile().getAbsolutePath())).append("\n");
                } else {
                    ps.append("  ").append(escapePowerShellArg(part.getName())).append(" = ")
                            .append(escapePowerShellArg(part.getTextValue())).append("\n");
                }
            }
            ps.append("}\n");
            ps.append("Invoke-RestMethod -Method ").append(resolved.getMethod().name());
            ps.append(" -Uri ").append(escapePowerShellArg(sanitizeUrl(resolved.getUrl(), includeCredentials)));
            ps.append(" -Form $form");
        } else {
            ps.append("Invoke-RestMethod -Method ").append(resolved.getMethod().name());
            ps.append(" -Uri ").append(escapePowerShellArg(sanitizeUrl(resolved.getUrl(), includeCredentials)));
            if (resolved.getBody() != null) {
                String bodyStr = new String(resolved.getRawBodyBytes(), StandardCharsets.UTF_8);
                if (!bodyStr.isEmpty()) {
                    ps.append(" -Body ").append(escapePowerShellArg(bodyStr));
                }
            }
        }

        if (!resolved.getHeaderEntries().isEmpty()) {
            ps.append(" -Headers @{");
            boolean first = true;
            for (Map.Entry<String, String> header : resolved.getHeaderEntries()) {
                String val = (!includeCredentials && CredentialStore.isSensitiveHeader(header.getKey()))
                        ? "[REDACTED]" : header.getValue();
                if (!first) ps.append("; ");
                ps.append(escapePowerShellArg(header.getKey())).append(" = ").append(escapePowerShellArg(val));
                first = false;
            }
            ps.append("}");
        }

        return ps.toString();
    }

    public static String buildWindowsCmd(EndpointModel endpoint, String fullUrlPattern, boolean includeCredentials) {
        if (endpoint == null) return "curl";
        ResolvedRequest resolved = HttpRequestBuilder.resolveRequest(endpoint, fullUrlPattern);
        return buildWindowsCmd(resolved, includeCredentials);
    }

    public static String buildWindowsCmd(ResolvedRequest resolved, boolean includeCredentials) {
        if (resolved == null) return "curl";
        StringBuilder cmd = new StringBuilder("curl -X ").append(resolved.getMethod().name());
        cmd.append(" ").append(escapeCmdArg(sanitizeUrl(resolved.getUrl(), includeCredentials)));

        for (Map.Entry<String, String> header : resolved.getHeaderEntries()) {
            String val = (!includeCredentials && CredentialStore.isSensitiveHeader(header.getKey()))
                    ? "[REDACTED]" : header.getValue();
            cmd.append(" ^\n  -H ").append(escapeCmdArg(header.getKey() + ": " + val));
        }

        boolean hasContentType = resolved.getHeaders().containsKey("Content-Type");
        if (!hasContentType && resolved.getBody() != null && resolved.getBody().contentType() != null && resolved.getMultipartParts().isEmpty()) {
            cmd.append(" ^\n  -H ").append(escapeCmdArg("Content-Type: " + resolved.getBody().contentType()));
        }

        if (!resolved.getMultipartParts().isEmpty()) {
            for (MultipartPartModel part : resolved.getMultipartParts()) {
                if (part.isFile()) {
                    String mime = part.getContentType() != null ? ";type=" + part.getContentType() : "";
                    cmd.append(" ^\n  -F ").append(escapeCmdArg(part.getName() + "=@" + part.getFile().getAbsolutePath() + mime));
                } else if (part.isJson()) {
                    cmd.append(" ^\n  -F ").append(escapeCmdArg(part.getName() + "=" + part.getTextValue() + ";type=application/json"));
                } else {
                    cmd.append(" ^\n  -F ").append(escapeCmdArg(part.getName() + "=" + part.getTextValue()));
                }
            }
        } else if (resolved.getBody() != null) {
            String bodyStr = new String(resolved.getRawBodyBytes(), StandardCharsets.UTF_8);
            if (!bodyStr.isEmpty()) {
                cmd.append(" ^\n  --data-raw ").append(escapeCmdArg(bodyStr));
            }
        }

        return cmd.toString();
    }

    public static String sanitizeUrl(HttpUrl url, boolean includeCredentials) {
        if (url == null) return "";
        if (includeCredentials) return url.toString();
        if (url.querySize() == 0) return url.toString();

        boolean hasSensitive = false;
        for (int i = 0; i < url.querySize(); i++) {
            if (isSensitiveQueryParam(url.queryParameterName(i))) {
                hasSensitive = true;
                break;
            }
        }
        if (!hasSensitive) return url.toString();

        HttpUrl.Builder sanitized = url.newBuilder();
        sanitized.query(null);
        for (int i = 0; i < url.querySize(); i++) {
            String qName = url.queryParameterName(i);
            String qVal = url.queryParameterValue(i);
            if (isSensitiveQueryParam(qName)) {
                sanitized.addQueryParameter(qName, "[REDACTED]");
            } else {
                sanitized.addQueryParameter(qName, qVal);
            }
        }
        return sanitized.build().toString();
    }
}
