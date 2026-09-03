package vn.io.codelearning.springapitester.client;

import java.util.List;
import java.util.Map;

/**
 * Model đóng gói kết quả trả về từ Spring Boot Server.
 */
public class HttpResponseModel {
    private int statusCode;
    private String statusMessage;
    private String body;
    // Dùng List<String> để giữ nguyên các header bị trùng tên (vd: Set-Cookie)
    private Map<String, List<String>> headers;
    private long timeTakenMs;
    private boolean isTruncated = false;
    private boolean isBinary = false;
    private long totalBytes = 0;
    private byte[] rawBytes;
    private String contentType;

    public HttpResponseModel() {
    }

    public HttpResponseModel(int statusCode, String statusMessage, String body, Map<String, List<String>> headers, long timeTakenMs) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.body = body;
        this.headers = headers;
        this.timeTakenMs = timeTakenMs;
    }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Map<String, List<String>> getHeaders() { return headers; }
    public void setHeaders(Map<String, List<String>> headers) { this.headers = headers; }

    public long getTimeTakenMs() { return timeTakenMs; }
    public void setTimeTakenMs(long timeTakenMs) { this.timeTakenMs = timeTakenMs; }

    public boolean isTruncated() { return isTruncated; }
    public void setTruncated(boolean truncated) { isTruncated = truncated; }

    public boolean isBinary() { return isBinary; }
    public void setBinary(boolean binary) { isBinary = binary; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public byte[] getRawBytes() { return rawBytes; }
    public void setRawBytes(byte[] rawBytes) { this.rawBytes = rawBytes; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
