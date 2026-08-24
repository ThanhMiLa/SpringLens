package vn.io.codelearning.springapitester.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Model lưu trữ toàn bộ dữ liệu phản hồi (Response) sau khi gọi HTTP.
 */
public class ResponseModel {
    private int statusCode;
    private String statusMessage;
    private long executionTimeMs;
    private long responseSizeBytes;
    private Map<String, String> headers;
    private String body;
    private String contentType;
    private boolean isSuccess;
    private String errorMessage;

    public ResponseModel() {
        this.statusCode = 0;
        this.statusMessage = "";
        this.executionTimeMs = 0;
        this.responseSizeBytes = 0;
        this.headers = new HashMap<>();
        this.body = "";
        this.contentType = "application/json";
        this.isSuccess = false;
        this.errorMessage = null;
    }

    public static ResponseModel error(String errorMessage, long executionTimeMs) {
        ResponseModel model = new ResponseModel();
        model.setStatusCode(0);
        model.setStatusMessage("Request Error");
        model.setErrorMessage(errorMessage);
        model.setBody("{\"error\": \"" + (errorMessage != null ? errorMessage.replace("\"", "\\\"") : "Unknown Error") + "\"}");
        model.setExecutionTimeMs(executionTimeMs);
        model.setSuccess(false);
        return model;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
        this.isSuccess = (statusCode >= 200 && statusCode < 300);
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public long getResponseSizeBytes() {
        return responseSizeBytes;
    }

    public void setResponseSizeBytes(long responseSizeBytes) {
        this.responseSizeBytes = responseSizeBytes;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = (headers != null) ? headers : new HashMap<>();
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = (body != null) ? body : "";
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFormattedStatus() {
        if (statusCode == 0) {
            return "Error: " + (statusMessage != null ? statusMessage : "Network Error");
        }
        return statusCode + " " + (statusMessage != null ? statusMessage : "");
    }
}
