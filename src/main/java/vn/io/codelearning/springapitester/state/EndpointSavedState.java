package vn.io.codelearning.springapitester.state;

import vn.io.codelearning.springapitester.model.AuthConfig;
import vn.io.codelearning.springapitester.model.HeaderItem;
import vn.io.codelearning.springapitester.model.RequestBodyType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EndpointSavedState {
    public Map<String, String> paramValues = new HashMap<>();
    public Map<String, Boolean> paramEnabled = new HashMap<>();
    public List<HeaderItem> customHeaders = new ArrayList<>();
    public AuthConfig authConfig = new AuthConfig();
    public String credentialId = "";
    public String requestBodyJson = "";
    public RequestBodyType bodyType = RequestBodyType.NONE;
    public boolean allowInsecureTls = false;
    public String insecureTlsConsentHost = "";
    public int insecureTlsConsentVersion = 0;
    public boolean isSecuredOverride = false;
    public boolean hasSecuredOverride = false;

    // Response Cache
    public static final int MAX_RESPONSE_HISTORY_ENTRIES = 20;

    public static class ResponseHistoryEntry {
        public long timestamp = System.currentTimeMillis();
        public int statusCode = 0;
        public String statusMessage = "";
        public long timeTakenMs = 0;
        public String responseBody = "";
        public String responseHeaders = "";
        public String responseFormat = "JSON";

        public ResponseHistoryEntry() {}

        public ResponseHistoryEntry(int statusCode, String statusMessage, long timeTakenMs,
                                    String responseBody, String responseHeaders, String responseFormat) {
            this.timestamp = System.currentTimeMillis();
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.timeTakenMs = timeTakenMs;
            this.responseBody = responseBody;
            this.responseHeaders = responseHeaders;
            this.responseFormat = responseFormat;
        }
    }

    public String lastResponseBody = "";
    public int lastResponseStatusCode = 0;
    public String lastResponseStatusMessage = "";
    public long lastResponseTimeTakenMs = 0;
    public String lastResponseHeaders = "";
    public String lastResponseFormat = "JSON";
    public List<ResponseHistoryEntry> responseHistory = new ArrayList<>();
    
    // Module 7: Fields for manual endpoints
    public String id;
    public String name;
    public boolean isManual = false;
    public boolean isAbsoluteUrl = false;
    public String folderId;
    public vn.io.codelearning.springapitester.model.HttpMethodEnum httpMethod;
    public String path;
    public List<vn.io.codelearning.springapitester.model.ParameterModel> manualParameters = new ArrayList<>();
}
