package vn.io.codelearning.springapitester.client;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.model.MultipartPartModel;

import java.util.*;

/**
 * Đại diện cho request bất biến đã được resolve đầy đủ:
 * - URL đã thay thế path variables và query parameters.
 * - Headers tuân thủ thứ tự ưu tiên: Auth headers > @RequestHeader > Custom Headers.
 * - Cookies chuẩn hóa theo RFC 6265, ghi đè theo tên tham số.
 * - RequestBody và multipart parts (nếu có).
 */
public final class ResolvedRequest {

    private final HttpUrl url;
    private final HttpMethodEnum method;
    private final List<Map.Entry<String, String>> headerList;
    private final Map<String, String> headers; // case-insensitive ordered (latest value per key)
    private final Map<String, String> cookies;
    private final RequestBody body;
    private final byte[] rawBodyBytes;
    private final List<MultipartPartModel> multipartParts;

    public ResolvedRequest(HttpUrl url, HttpMethodEnum method,
                           List<Map.Entry<String, String>> headerList,
                           Map<String, String> cookies,
                           RequestBody body,
                           byte[] rawBodyBytes,
                           List<MultipartPartModel> multipartParts) {
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.method = method != null ? method : HttpMethodEnum.GET;
        List<Map.Entry<String, String>> list = new ArrayList<>();
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headerList != null) {
            for (Map.Entry<String, String> entry : headerList) {
                if (entry != null && entry.getKey() != null) {
                    list.add(Map.entry(entry.getKey(), entry.getValue() != null ? entry.getValue() : ""));
                    map.put(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
                }
            }
        }
        this.headerList = Collections.unmodifiableList(list);
        this.headers = Collections.unmodifiableMap(map);
        this.cookies = cookies != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(cookies))
                : Collections.emptyMap();
        this.body = body;
        this.rawBodyBytes = rawBodyBytes != null ? rawBodyBytes.clone() : new byte[0];
        this.multipartParts = multipartParts != null
                ? Collections.unmodifiableList(new ArrayList<>(multipartParts))
                : Collections.emptyList();
    }

    public ResolvedRequest(HttpUrl url, HttpMethodEnum method,
                           Map<String, String> headers,
                           Map<String, String> cookies,
                           RequestBody body,
                           byte[] rawBodyBytes,
                           List<MultipartPartModel> multipartParts) {
        this(url, method, toEntryList(headers), cookies, body, rawBodyBytes, multipartParts);
    }

    private static List<Map.Entry<String, String>> toEntryList(Map<String, String> headers) {
        if (headers == null) return Collections.emptyList();
        List<Map.Entry<String, String>> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            list.add(Map.entry(entry.getKey(), entry.getValue() != null ? entry.getValue() : ""));
        }
        return list;
    }

    public HttpUrl getUrl() {
        return url;
    }

    public HttpMethodEnum getMethod() {
        return method;
    }

    public List<Map.Entry<String, String>> getHeaderEntries() {
        return headerList;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public RequestBody getBody() {
        return body;
    }

    public byte[] getRawBodyBytes() {
        return rawBodyBytes.clone();
    }

    public List<MultipartPartModel> getMultipartParts() {
        return multipartParts;
    }

    public Request toOkHttpRequest() {
        Request.Builder builder = new Request.Builder().url(url);

        Headers.Builder headersBuilder = new Headers.Builder();
        for (Map.Entry<String, String> entry : headerList) {
            headersBuilder.add(entry.getKey(), entry.getValue());
        }
        builder.headers(headersBuilder.build());

        String methodName = method.name();
        if ("GET".equalsIgnoreCase(methodName) || "HEAD".equalsIgnoreCase(methodName)) {
            builder.method(methodName, null);
        } else if ("DELETE".equalsIgnoreCase(methodName)) {
            builder.method(methodName, body);
        } else {
            RequestBody b = body != null ? body : RequestBody.create(new byte[0], null);
            builder.method(methodName, b);
        }

        return builder.build();
    }
}
